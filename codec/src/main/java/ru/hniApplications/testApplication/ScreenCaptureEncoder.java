package ru.hniApplications.testApplication;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Кодировщик видео с экрана.
 * Захватывает экран через dshow (screen-capture-recorder) и кодирует в H.264.
 * <p>
 * ТОЛЬКО ВИДЕО — для захвата звука использовать {@link AudioCaptureEncoder}.
 * Для объединения видео и аудио использовать {@link MediaMuxer}.
 * <p>
 * Поток данных:
 * <pre>
 *   Экран --(dshow)--> FFmpeg --(H.264 raw)--> readChunk()
 * </pre>
 */
public class ScreenCaptureEncoder implements AutoCloseable {

    private final Process process;
    private final InputStream ffmpegOutput;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final int fps;
    private final int width;
    private final int height;

    /**
     * Создаёт кодировщик видео.
     *
     * @param fps частота кадров
     * @param width ширина захвата
     * @param height высота захвата
     * @throws IOException если не удалось запустить FFmpeg
     */
    public ScreenCaptureEncoder(int fps, int width, int height) throws IOException {
        this(fps, width, height, null);
    }

    /**
     * Создаёт кодировщик видео с явным путём к FFmpeg.
     *
     * @param fps частота кадров
     * @param width ширина захвата
     * @param height высота захвата
     * @param ffmpegPath путь к исполняемому файлу FFmpeg (или null для PATH)
     * @throws IOException если не удалось запустить FFmpeg
     */
    public ScreenCaptureEncoder(int fps, int width, int height, String ffmpegPath) throws IOException {
        this.fps = fps;
        this.width = width;
        this.height = height;

        List<String> cmd = new ArrayList<>();

        // Путь к FFmpeg
        cmd.add(ffmpegPath != null ? ffmpegPath : "ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("warning");

        // Захват экрана через dshow (screen-capture-recorder)
        cmd.add("-f");
        cmd.add("dshow");
        cmd.add("-framerate");
        cmd.add(String.valueOf(fps));
        cmd.add("-video_size");
        cmd.add(width + "x" + height);
        cmd.add("-i");
        cmd.add("video=screen-capture-recorder");

        // Параметры кодирования H.264
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("ultrafast");
        cmd.add("-tune");
        cmd.add("zerolatency");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-g");
        cmd.add(String.valueOf(fps)); // Ключевой кадр каждую секунду
        cmd.add("-b:v");
        cmd.add("2000k");

        // Выход: raw H.264 поток (не MPEG-TS!)
        cmd.add("-flush_packets");
        cmd.add("1");
        cmd.add("-f");
        cmd.add("h264");
        cmd.add("pipe:1");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        this.process = pb.start();

        this.ffmpegOutput = new BufferedInputStream(process.getInputStream(), 256 * 1024);

        // Поток для чтения stderr
        Thread errThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("[ffmpeg-video] " + line);
                }
            } catch (Exception ignored) {
            }
        }, "ffmpeg-video-stderr");
        errThread.setDaemon(true);
        errThread.start();

        System.out.println("[ScreenCaptureEncoder] Запущен: " + width + "x" + height + "@" + fps + "fps");
    }

    /**
     * Читает следующий чанк закодированного H.264 видео.
     *
     * @return байтовый массив с H.264 данными или null при EOF
     * @throws IOException если произошла ошибка чтения
     */
    public byte[] readChunk() throws IOException {
        if (closed.get()) {
            return null;
        }

        byte[] buf = new byte[64 * 1024]; // 64KB буфер
        int read = ffmpegOutput.read(buf);

        if (read == -1) {
            return null; // EOF
        }

        if (read == buf.length) {
            return buf;
        }

        byte[] result = new byte[read];
        System.arraycopy(buf, 0, result, 0, read);
        return result;
    }

    public int getFps() {
        return fps;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }

        if (process != null) {
            process.destroyForcibly();
            try {
                process.waitFor(3000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("[ScreenCaptureEncoder] Остановлен");
    }

    // ========================================================================
    // Методы для работы с аудиоустройствами (перенесены из старой версии)
    // ========================================================================

    /**
     * Модель аудиоустройства для захвата.
     */
    public static class AudioDevice {
        public final String displayName;
        public final String ffmpegArg;
        public final boolean isLoopback;

        public AudioDevice(String displayName, String ffmpegArg, boolean isLoopback) {
            this.displayName = displayName;
            this.ffmpegArg = ffmpegArg;
            this.isLoopback = isLoopback;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Возвращает список доступных аудиоустройств.
     *
     * @return список AudioDevice
     */
    public static List<AudioDevice> listAllAudioDevices() {
        return listAllAudioDevices(null);
    }

    /**
     * Возвращает список доступных аудиоустройств.
     *
     * @param ffmpegPath путь к FFmpeg (или null для PATH)
     * @return список AudioDevice
     */
    public static List<AudioDevice> listAllAudioDevices(String ffmpegPath) {
        List<AudioDevice> list = new ArrayList<>();
        String path = ffmpegPath != null ? ffmpegPath : "ffmpeg";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    path, "-list_devices", "true", "-f", "dshow", "-i", "dummy"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), "UTF-8"))) {

                String line;
                String currentAudioName = null;

                while ((line = br.readLine()) != null) {
                    if (line.contains("(audio)")) {
                        int startQuote = line.indexOf('"');
                        int endQuote = line.indexOf('"', startQuote + 1);
                        if (startQuote != -1 && endQuote > startQuote) {
                            currentAudioName = line.substring(startQuote + 1, endQuote);

                            if (currentAudioName.equals("virtual-audio-capturer")) {
                                list.add(new AudioDevice(
                                        "Системный звук (Virtual Capturer)",
                                        currentAudioName, false));
                                currentAudioName = null;
                            }
                        }
                    } else if (line.contains("Alternative name") && currentAudioName != null) {
                        int startQuote = line.indexOf('"');
                        int endQuote = line.lastIndexOf('"');
                        if (startQuote != -1 && endQuote > startQuote) {
                            String altName = line.substring(startQuote + 1, endQuote);
                            list.add(new AudioDevice(currentAudioName, altName, false));
                            currentAudioName = null;
                        }
                    } else if (line.contains("(video)")) {
                        currentAudioName = null;
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.println("[ScreenCaptureEncoder] Ошибка получения списка устройств: " + e.getMessage());
        }
        return list;
    }
}
