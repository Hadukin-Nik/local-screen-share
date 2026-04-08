package ru.hniApplications.testApplication.desktop;

import ru.hniApplications.testApplication.FramePacket;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Записывает MPEG-TS поток (видео + аудио) в файл.
 * После остановки — ремуксит в .mp4.
 */
public class BroadcastRecorder {

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final Path outputDir;
    private Path rawFile;
    private OutputStream rawStream;

    public BroadcastRecorder(Path outputDir) {
        this.outputDir = outputDir;
    }

    // Обратная совместимость
    public BroadcastRecorder(Path outputDir, int fps) {
        this(outputDir);
    }

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
     * Записывает MPEG-TS чанк. Вызывается из потока отправки/получения.
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

    public void stopAndSave(RecordingCompleteCallback onComplete) {
        if (!recording.getAndSet(false)) return;

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
        } catch (IOException ignored) {}

        Thread convertThread = new Thread(() -> {
            Path mp4File = convertToMp4(rawFile);
            if (onComplete != null) onComplete.onComplete(mp4File);
        }, "recorder-convert");
        convertThread.setDaemon(true);
        convertThread.start();
    }

    private Path convertToMp4(Path tsFile) {
        Path mp4File = outputDir.resolve(
                tsFile.getFileName().toString().replace(".ts", ".mp4"));

        try {
            System.out.println("[Recorder] Конвертация: " + tsFile + " -> " + mp4File);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-hide_banner",
                    "-y",
                    "-f", "mpegts",
                    "-i", tsFile.toAbsolutePath().toString(),
                    "-c:v", "copy",
                    "-c:a", "aac",           // перекодируем аудио для совместимости
                    "-b:a", "128k",
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
                Files.deleteIfExists(tsFile);
                return mp4File;
            } else {
                System.err.println("[Recorder] FFmpeg завершился с кодом "
                        + exitCode);
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

    @FunctionalInterface
    public interface RecordingCompleteCallback {
        void onComplete(Path mp4Path);
    }
}