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
        long t0 = System.currentTimeMillis();
        System.out.println("[ENCODER] T+0ms: start init");

        // Проверка: не висит ли уже какой-то ffmpeg
        ProcessHandle.allProcesses()
            .filter(p -> p.info().command().map(c -> c.endsWith("ffmpeg.exe")).orElse(false))
            .forEach(p -> System.out.println("[Warning] Existing ffmpeg process: PID=" + p.pid()));

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
            System.out.println("[ENCODER] T+" + (System.currentTimeMillis() - t0) + "ms: WASAPI started");
            WasapiLoopbackCapture.Format fmt = localWasapiCapture.getFormat();

            System.out.println("[ENCODER] WASAPI loopback: sr=" + fmt.sampleRate
                    + " ch=" + fmt.channels
                    + " bits=" + fmt.bitsPerSample
                    + " float=" + fmt.isFloat);

// === Вход 0: видео (dshow) ===
            cmd.add("-thread_queue_size"); cmd.add("4096");
            cmd.add("-rtbufsize"); cmd.add("512M");
            cmd.add("-use_wallclock_as_timestamps"); cmd.add("1");   // ← оставить
            cmd.add("-f"); cmd.add("dshow");
            cmd.add("-framerate"); cmd.add(String.valueOf(fps));
            cmd.add("-video_size"); cmd.add(width + "x" + height);
            cmd.add("-i"); cmd.add("video=screen-capture-recorder");

// === Вход 1: аудио (pipe:0) ===
            cmd.add("-thread_queue_size"); cmd.add("4096");
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

        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add("ultrafast");
        cmd.add("-tune"); cmd.add("zerolatency");
        cmd.add("-pix_fmt"); cmd.add("yuv420p");
        cmd.add("-g"); cmd.add(String.valueOf(fps));
        cmd.add("-b:v"); cmd.add(videoBitrate != null ? videoBitrate : "2000k");
// Ограничиваем threads, чтобы encoder не съел всё:
        cmd.add("-threads"); cmd.add("4");

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
        System.out.println("[ENCODER] T+" + (System.currentTimeMillis() - t0) + "ms: ffmpeg process started, PID=" + process.pid());

        this.ffmpegOutput = new BufferedInputStream(process.getInputStream(), 256 * 1024);

        // Запускаем поток копирования PCM в stdin ffmpeg (только для WASAPI)
        if (localWasapiCapture != null) {
            final WasapiLoopbackCapture capture = localWasapiCapture;
            final OutputStream stdin = process.getOutputStream();
            pipeCopyThread = new Thread(() -> {
                long totalBytes = 0;
                System.out.println("[ENCODER] T+" + (System.currentTimeMillis() - t0) + "ms: pipe copy thread started");
                try (InputStream audioIn = capture.getOutputStream()) {
                    byte[] buffer = new byte[4096];  // мелкий буфер для низкой задержки
                    int n;
                    boolean firstRead = true;
                    while ((n = audioIn.read(buffer)) > 0) {
                        if (firstRead) {
                            System.out.println("[ENCODER] T+" + (System.currentTimeMillis() - t0) + "ms: first audio chunk read (" + n + " bytes)");
                            firstRead = false;
                        }
                        stdin.write(buffer, 0, n);
                        stdin.flush();  // ОБЯЗАТЕЛЬНО flush — иначе stdout буферизуется в OS
                        totalBytes += n;
                    }
                } catch (IOException e) {
                    System.err.println("[AUDIO-PIPE] error after " + (totalBytes / 1024) + "KB: " + e.getMessage());
                } finally {
                    try { stdin.close(); } catch (IOException ignored) {}
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
                pipeCopyThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Убиваем процесс ffmpeg
        if (process != null && process.isAlive()) {
            // Попытка graceful shutdown — ffmpeg реагирует на 'q' в stdin
            // Но в нашем случае stdin занят аудио-pipe, так что просто destroy
            process.destroy();   // мягкий signal (CTRL+BREAK на Windows)
            try {
                if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    System.err.println("[ENCODER] ffmpeg did not exit gracefully, forcing");
                    process.destroyForcibly();
                    process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Пауза для освобождения DirectShow устройства Windows-ом
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}

            System.out.println("[ENCODER] ffmpeg process closed");
        }
    }
}
