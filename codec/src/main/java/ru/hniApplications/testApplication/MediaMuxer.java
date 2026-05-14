package ru.hniApplications.testApplication;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Медиа-мультиплексор: объединяет видео (H.264) и аудио (AAC) в MPEG-TS.
 * <p>
 * Архитектура (использует TCP loopback для двух входов FFmpeg):
 * <pre>
 *   Java                                FFmpeg                          Java
 *  writeVideo() ──▶ [TCP :videoPort] ──▶  -i tcp://...:videoPort  ──┐
 *  writeAudio() ──▶ [TCP :audioPort] ──▶  -i tcp://...:audioPort  ──┴─▶ mpegts ──▶ stdout ──▶ getOutputStream()
 * </pre>
 */
public class MediaMuxer implements AutoCloseable {

    private final int width;
    private final int height;
    private final int videoFps;
    private final int audioSampleRate;
    private final int audioChannels;

    private Process ffmpegProcess;
    private ServerSocket videoServer;
    private ServerSocket audioServer;
    private Socket videoSocket;
    private Socket audioSocket;
    private OutputStream videoOut;
    private OutputStream audioOut;
    private InputStream muxedOutput;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread errorReaderThread;

    public MediaMuxer(int width, int height, int videoFps,
                      int audioSampleRate, int audioChannels) {
        this.width = width;
        this.height = height;
        this.videoFps = videoFps;
        this.audioSampleRate = audioSampleRate;
        this.audioChannels = audioChannels;
    }

    public void start() throws IOException {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("MediaMuxer already started");
        }

        // 1. Открываем два TCP-сервера на свободных портах
        videoServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        audioServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        int videoPort = videoServer.getLocalPort();
        int audioPort = audioServer.getLocalPort();

        videoServer.setSoTimeout(10_000);
        audioServer.setSoTimeout(10_000);

        // 2. Запускаем FFmpeg, который будет коннектиться к нашим сокетам
        List<String> cmd = buildFFmpegCommand(videoPort, audioPort);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        ffmpegProcess = pb.start();

        // 3. Параллельно принимаем обе входящие коннекта (FFmpeg откроет оба входа)
        //    Делаем это в отдельных потоках, чтобы порядок коннекта не имел значения.
        CountDownLatch connected = new CountDownLatch(2);
        IOException[] connectErr = new IOException[1];

        Thread videoAcceptor = new Thread(() -> {
            try {
                videoSocket = videoServer.accept();
                videoSocket.setTcpNoDelay(true);
                videoOut = new BufferedOutputStream(videoSocket.getOutputStream(), 256 * 1024);
                connected.countDown();
            } catch (IOException e) {
                connectErr[0] = e;
            }
        }, "muxer-video-accept");

        Thread audioAcceptor = new Thread(() -> {
            try {
                audioSocket = audioServer.accept();
                audioSocket.setTcpNoDelay(true);
                audioOut = new BufferedOutputStream(audioSocket.getOutputStream(), 128 * 1024);
                connected.countDown();
            } catch (IOException e) {
                connectErr[0] = e;
            }
        }, "muxer-audio-accept");

        videoAcceptor.setDaemon(true);
        audioAcceptor.setDaemon(true);
        videoAcceptor.start();
        audioAcceptor.start();

        // 4. Читаем stderr FFmpeg для диагностики
        errorReaderThread = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(ffmpegProcess.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.err.println("[ffmpeg-muxer] " + line);
                }
            } catch (IOException ignored) {}
        }, "ffmpeg-muxer-stderr");
        errorReaderThread.setDaemon(true);
        errorReaderThread.start();

        // 5. Ждём, пока FFmpeg подключится к обоим сокетам
        try {
            if (!connected.await(10, TimeUnit.SECONDS)) {
                throw new IOException("FFmpeg did not connect to both input pipes in 10s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for FFmpeg", e);
        }
        if (connectErr[0] != null) {
            throw connectErr[0];
        }

        // 6. Получаем выходной MPEG-TS поток из stdout FFmpeg
        muxedOutput = new BufferedInputStream(ffmpegProcess.getInputStream(), 512 * 1024);

        System.out.println("[MediaMuxer] Запущен: " + width + "x" + height + "@" + videoFps + "fps, "
                + audioSampleRate + "Hz/" + audioChannels + "ch (video=" + videoPort + ", audio=" + audioPort + ")");
    }

    private List<String> buildFFmpegCommand(int videoPort, int audioPort) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("warning");

        // ВАЖНО: используем listen=0 (по умолчанию) — FFmpeg будет КЛИЕНТОМ TCP
        // Сервер — это мы (Java).
        cmd.add("-f"); cmd.add("h264");
        cmd.add("-i"); cmd.add("tcp://127.0.0.1:" + videoPort);

        cmd.add("-f"); cmd.add("aac");
        cmd.add("-i"); cmd.add("tcp://127.0.0.1:" + audioPort);

        // Просто копируем потоки в MPEG-TS (без перекодирования)
        cmd.add("-c:v"); cmd.add("copy");
        cmd.add("-c:a"); cmd.add("copy");

        // Контейнер
        cmd.add("-f"); cmd.add("mpegts");
        cmd.add("-flush_packets"); cmd.add("1");
        cmd.add("pipe:1");

        return cmd;
    }

    public void writeVideo(byte[] h264Data) throws IOException {
        if (!running.get() || videoOut == null) return;
        videoOut.write(h264Data);
        videoOut.flush();
    }

    public void writeAudio(byte[] aacData) throws IOException {
        if (!running.get() || audioOut == null) return;
        audioOut.write(aacData);
        audioOut.flush();
    }

    public InputStream getOutputStream() {
        return muxedOutput;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);

        closeQuietly(videoOut);
        closeQuietly(audioOut);
        closeQuietly(videoSocket);
        closeQuietly(audioSocket);
        closeQuietly(videoServer);
        closeQuietly(audioServer);

        if (ffmpegProcess != null) {
            ffmpegProcess.destroyForcibly();
            try {
                ffmpegProcess.waitFor(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ffmpegProcess = null;
        }

        closeQuietly(muxedOutput);
        videoOut = null;
        audioOut = null;
        videoSocket = null;
        audioSocket = null;
        videoServer = null;
        audioServer = null;
        muxedOutput = null;

        System.out.println("[MediaMuxer] Остановлен");
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try { c.close(); } catch (IOException ignored) {}
        }
    }
}
