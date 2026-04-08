package ru.hniApplications.testApplication.desktop;

import ru.hniApplications.testApplication.ScreenCaptureEncoder.AudioDevice;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AudioLevelMonitor {
    private final AudioDevice device;
    private final Consumer<Double> levelCallback;
    private Process process;
    private Thread readThread;
    private volatile boolean running = false;

    public AudioLevelMonitor(AudioDevice device, Consumer<Double> levelCallback) {
        this.device = device;
        this.levelCallback = levelCallback;
    }

    public void start() {
        running = true;
        readThread = new Thread(() -> {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add("ffmpeg"); cmd.add("-hide_banner"); cmd.add("-loglevel"); cmd.add("error");
                cmd.add("-f"); cmd.add("wasapi");
                if (device.isLoopback) {
                    cmd.add("-loopback"); cmd.add("1");
                }
                cmd.add("-i"); cmd.add(device.ffmpegArg);

                // Перегоняем в RAW PCM, чтобы легко посчитать "громкость" байтов математикой
                cmd.add("-f"); cmd.add("s16le"); // 16-bit PCM
                cmd.add("-ac"); cmd.add("1");    // Моно звук (для индикатора хватит)
                cmd.add("-ar"); cmd.add("8000"); // 8kHz для экономии CPU
                cmd.add("pipe:1");

                process = new ProcessBuilder(cmd).start();
                InputStream is = process.getInputStream();

                byte[] buffer = new byte[1024];
                int bytesRead;

                while (running && (bytesRead = is.read(buffer)) != -1) {
                    long sumSq = 0;
                    int sampleCount = bytesRead / 2; // каждые 2 байта = 1 сэмпл (16 бит)
                    for (int i = 0; i < bytesRead; i += 2) {
                        short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
                        sumSq += (long) sample * sample; // суммируем квадраты амплитуд
                    }

                    double rms = Math.sqrt((double) sumSq / sampleCount); // Среднеквадратичное
                    double volume = Math.min(1.0, (rms / 32768.0) * 3.0); // Нормализуем 0..1 (х3 для визуального буста)

                    if (levelCallback != null) levelCallback.accept(volume); // Двигаем ProgressBar
                }
            } catch (Exception ignored) {}
            finally {
                if (levelCallback != null) levelCallback.accept(0.0);
            }
        }, "AudioLVL-Thread");
        readThread.setDaemon(true); readThread.start();
    }

    public void stop() {
        running = false;
        if (process != null) process.destroy();
    }
}