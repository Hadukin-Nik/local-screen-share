package ru.hniApplications.testApplication.desktop;

import ru.hniApplications.testApplication.FramePacket;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Записывает все отправляемые NAL unit'ы в сырой .h264 файл.
 * После вызова stopAndSave() — конвертирует через FFmpeg в .mp4.
 */
public class BroadcastRecorder {

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final Path outputDir;
    private Path rawFile;
    private OutputStream rawStream;
    private int fps;

    /**
     * @param outputDir папка, куда сохранять записи
     * @param fps       FPS трансляции (нужен для правильного ремукса)
     */
    public BroadcastRecorder(Path outputDir, int fps) {
        this.outputDir = outputDir;
        this.fps = fps;
    }

    /**
     * Начинает запись. Создаёт .h264 файл с текущей датой в имени.
     */
    public void startRecording() throws IOException {
        if (recording.getAndSet(true)) return;

        Files.createDirectories(outputDir);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        rawFile = outputDir.resolve("broadcast_" + timestamp + ".ts");
        rawStream = new BufferedOutputStream(
                Files.newOutputStream(rawFile), 512 * 1024);

        System.out.println("[Recorder] Запись начата: " + rawFile);
    }

    /**
     * Записывает пакет. Вызывается из потока отправки.
     * Пишем только NAL unit'ы видеокадров (SPS, PPS, I-frame, P-frame).
     */
    public void writePacket(FramePacket packet) {
        if (!recording.get() || rawStream == null) return;

        try {
            byte[] data = packet.getPayload();
            rawStream.write(data);
        } catch (IOException e) {
            System.err.println("[Recorder] Ошибка записи: " + e.getMessage());
        }
    }

    /**
     * Останавливает запись, закрывает .h264 файл и запускает конвертацию в .mp4.
     * Конвертация идёт в фоновом потоке, callback вызывается по завершении.
     *
     * @param onComplete вызывается с путём к .mp4 файлу (или null при ошибке)
     */
    public void stopAndSave(RecordingCompleteCallback onComplete) {
        if (!recording.getAndSet(false)) return;

        // Закрываем поток
        try {
            if (rawStream != null) {
                rawStream.flush();
                rawStream.close();
                rawStream = null;
            }
        } catch (IOException e) {
            System.err.println("[Recorder] Ошибка закрытия: " + e.getMessage());
        }

        if (rawFile == null || !Files.exists(rawFile)) {
            if (onComplete != null) onComplete.onComplete(null);
            return;
        }

        // Проверяем размер
        try {
            long size = Files.size(rawFile);
            System.out.println("[Recorder] Сырой файл: " + rawFile
                    + " (" + (size / 1024) + " КБ)");
            if (size == 0) {
                System.err.println("[Recorder] Файл пуст, нечего конвертировать");
                Files.deleteIfExists(rawFile);
                if (onComplete != null) onComplete.onComplete(null);
                return;
            }
        } catch (IOException e) {
            // ignore
        }

        // Конвертация в фоне
        Thread convertThread = new Thread(() -> {
            Path mp4File = convertToMp4(rawFile);
            if (onComplete != null) onComplete.onComplete(mp4File);
        }, "recorder-convert");
        convertThread.setDaemon(true);
        convertThread.start();
    }

    private Path convertToMp4(Path rawFile) {
        // Переименуем расширение для ясности, но это не обязательно
        Path mp4File = outputDir.resolve(
                rawFile.getFileName().toString()
                        .replace(".h264", ".mp4")
                        .replace(".ts", ".mp4"));

        try {
            System.out.println("[Recorder] Конвертация: " + rawFile + " -> " + mp4File);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-hide_banner",
                    "-y",
                    "-f", "mpegts",           // <--- вот ключевое изменение
                    "-i", rawFile.toAbsolutePath().toString(),
                    "-c:v", "copy",
                    "-movflags", "+faststart",
                    mp4File.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[ffmpeg-remux] " + line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0 && Files.exists(mp4File) && Files.size(mp4File) > 0) {
                System.out.println("[Recorder] Сохранено: " + mp4File
                        + " (" + (Files.size(mp4File) / 1024) + " КБ)");
                Files.deleteIfExists(rawFile);
                return mp4File;
            } else {
                System.err.println("[Recorder] FFmpeg завершился с кодом " + exitCode);
                return null;
            }

        } catch (Exception e) {
            System.err.println("[Recorder] Ошибка конвертации: " + e.getMessage());
            return null;
        }
    }

    public boolean isRecording() {
        return recording.get();
    }

    /**
     * Callback по завершении конвертации.
     */
    @FunctionalInterface
    public interface RecordingCompleteCallback {
        /**
         * @param mp4Path путь к готовому .mp4 файлу, или null при ошибке
         */
        void onComplete(Path mp4Path);
    }
}