package ru.hniApplications.testApplication;

import ru.hniApplications.testApplication.capture.AudioDevice;
import ru.hniApplications.testApplication.codec.wasapi.WasapiLoopbackCapture;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ScreenCaptureEncoder implements AutoCloseable {
    private final Process process;
    private final InputStream ffmpegOutput;
    private final WasapiLoopbackCapture wasapiCapture;
    private Thread pipeCopyThread;

    public ScreenCaptureEncoder(int fps, int width, int height) throws IOException {
        this(fps, width, height, null, "2000k");
    }

    public ScreenCaptureEncoder(int fps, int width, int height, boolean useAudioPipe) throws IOException {
        this(fps, width, height, null, "2000k");
    }

    public ScreenCaptureEncoder(int fps, int width, int height, AudioDevice device) throws IOException {
        this(fps, width, height, device, "2000k");
    }

    // НОВЫЙ ГЛАВНЫЙ КОНСТРУКТОР С БИТРЕЙТОМ
    public ScreenCaptureEncoder(int fps, int width, int height, AudioDevice device, String videoBitrate) throws IOException {
        List<String> cmd = new ArrayList<>();

        cmd.add(FFmpegLocator.getPath());
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("warning");

        WasapiLoopbackCapture localWasapiCapture = null;

        // Определяем режим работы
        if (device != null && device.isLoopback() && device.getFfmpegArg().startsWith("wasapi-loopback://")) {
            // === Ветка WASAPI loopback ===
            String deviceId = device.getFfmpegArg().substring("wasapi-loopback://".length());
            localWasapiCapture = new WasapiLoopbackCapture(deviceId);
            localWasapiCapture.start();
            WasapiLoopbackCapture.Format fmt = localWasapiCapture.getFormat();

            System.out.println("[ENCODER] WASAPI loopback: sr=" + fmt.sampleRate
                    + " ch=" + fmt.channels
                    + " bits=" + fmt.bitsPerSample
                    + " float=" + fmt.isFloat);

// Вход 0: видео (dshow) — большой буфер, чтобы кадры не дропались,
// пока ffmpeg ждёт аудио
            cmd.add("-thread_queue_size"); cmd.add("1024");
            cmd.add("-rtbufsize"); cmd.add("256M");
            cmd.add("-f"); cmd.add("dshow");
            cmd.add("-framerate"); cmd.add(String.valueOf(fps));
            cmd.add("-video_size"); cmd.add(width + "x" + height);
            cmd.add("-i"); cmd.add("video=screen-capture-recorder");

// Вход 1: аудио (pipe:0) — тоже thread_queue, чтобы pipe не стопорил
            cmd.add("-thread_queue_size"); cmd.add("1024");
            cmd.add("-f"); cmd.add(fmt.isFloat ? "f32le" : "s16le");
            cmd.add("-ar"); cmd.add(String.valueOf(fmt.sampleRate));
            cmd.add("-ac"); cmd.add(String.valueOf(fmt.channels));
            cmd.add("-i"); cmd.add("pipe:0");


            // map: video из первого входа, audio из второго
            cmd.add("-map"); cmd.add("0:v:0");
            cmd.add("-map"); cmd.add("1:a:0");

        } else if (device != null) {
            // === Старая ветка dshow (микрофон) ===
            System.out.println("[ENCODER] Инициализация видео + аудио: " + device.getFfmpegArg());
            cmd.add("-f");
            cmd.add("dshow");
            cmd.add("-framerate");
            cmd.add(String.valueOf(fps));
            cmd.add("-video_size");
            cmd.add(width + "x" + height);
            cmd.add("-i");
            cmd.add("video=screen-capture-recorder:audio=" + device.getFfmpegArg());
        } else {
            // === Только видео (без звука) ===
            System.out.println("[ENCODER] Инициализация только видео (без звука)");
            cmd.add("-f");
            cmd.add("dshow");
            cmd.add("-framerate");
            cmd.add(String.valueOf(fps));
            cmd.add("-video_size");
            cmd.add(width + "x" + height);
            cmd.add("-i");
            cmd.add("video=screen-capture-recorder");
        }

        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("ultrafast");
        cmd.add("-tune");
        cmd.add("zerolatency");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-g");
        cmd.add(String.valueOf(fps));
        cmd.add("-b:v");
        cmd.add(videoBitrate != null && !videoBitrate.isEmpty() ? videoBitrate : "2000k");

        if (device != null) {
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("128k");
            cmd.add("-ac");
            cmd.add("2");
            cmd.add("-ar");
            cmd.add("44100");
        }

        cmd.add("-flush_packets");
        cmd.add("1");
        cmd.add("-f");
        cmd.add("mpegts");
        cmd.add("pipe:1");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        this.process = pb.start();

        this.ffmpegOutput = new BufferedInputStream(process.getInputStream(), 256 * 1024);

        // Запускаем поток копирования PCM в stdin ffmpeg (только для WASAPI)
        if (localWasapiCapture != null) {
            final WasapiLoopbackCapture capture = localWasapiCapture;
            final OutputStream stdin = process.getOutputStream();
            pipeCopyThread = new Thread(() -> {
                try (InputStream audioIn = capture.getOutputStream();
                     BufferedOutputStream bufOut = new BufferedOutputStream(stdin, 256 * 1024)) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = audioIn.read(buffer)) > 0) {
                        bufOut.write(buffer, 0, n);
                        bufOut.flush();
                    }
                } catch (IOException e) {
                    System.err.println("[ENCODER] Pipe copy thread error: " + e.getMessage());
                }
            }, "wasapi-pipe-copier");
            pipeCopyThread.setDaemon(true);
            pipeCopyThread.start();
        }

        this.wasapiCapture = localWasapiCapture;

        // Поток логирования stderr
        Thread errThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("[ffmpeg-encode] " + line);
                }
            } catch (Exception ignored) {
            }
        }, "ffmpeg-encoder-stderr");
        errThread.setDaemon(true);
        errThread.start();
    }

    /**
     * @return InputStream с MPEG-TS потоком (H.264+AAC)
     */
    public InputStream getAudioInputStream() {
        return ffmpegOutput;
    }

    /**
     * Читает следующий чанк из выходного потока ffmpeg.
     *
     * @return байтовый массив или null при EOF
     */
    public byte[] readChunk() throws IOException {
        byte[] buf = new byte[16 * 1024];
        int read = ffmpegOutput.read(buf);
        if (read == -1) return null;
        if (read == buf.length) return buf;
        byte[] result = new byte[read];
        System.arraycopy(buf, 0, result, 0, read);
        return result;
    }

    @Override
    public void close() {
        // Сначала останавливаем WASAPI захват (если есть)
        if (wasapiCapture != null) {
            wasapiCapture.close();
        }

        // Ждём завершения потока копирования
        if (pipeCopyThread != null) {
            try {
                pipeCopyThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Убиваем процесс ffmpeg
        process.destroyForcibly();
    }
}
