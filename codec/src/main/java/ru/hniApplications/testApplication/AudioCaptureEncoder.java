package ru.hniApplications.testApplication;

import ru.hniApplications.testApplication.capture.AudioCapture;
import ru.hniApplications.testApplication.capture.AudioFormat;
import ru.hniApplications.testApplication.capture.CaptureException;
import ru.hniApplications.testApplication.capture.CapturedAudioChunk;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Захват аудио через FFmpeg + DirectShow (Windows).
 * <p>
 * Выдаёт <b>сырой PCM</b> (signed 16-bit little-endian) в формате, указанном при создании.
 * Кодирование в AAC/Opus и мультиплексирование — задача {@link MediaMuxer}.
 * <p>
 * Модель работы — pull: потребитель сам вызывает {@link #read()} в своём потоке.
 * <p>
 * Поток данных:
 * <pre>
 *   Аудиоустройство --(dshow)--> FFmpeg --(PCM s16le)--> read()
 * </pre>
 */
public class AudioCaptureEncoder implements AudioCapture {

    private static final int READ_BUFFER_SIZE = 8 * 1024;

    private final String deviceName;
    private final String ffmpegPath;
    private final AudioFormat format;

    private Process process;
    private InputStream ffmpegOutput;
    private Thread stderrReader;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed  = new AtomicBoolean(false);

    // ---------------------------------------------------------------- ctors

    /**
     * Создаёт захват с устройства {@code deviceName} в формате по умолчанию
     * (44100 Hz, 2 канала, 16 бит PCM).
     */
    public AudioCaptureEncoder(String deviceName) {
        this(deviceName, AudioFormat.defaultFormat(), null);
    }

    /**
     * Создаёт захват с указанным форматом.
     *
     * @param deviceName имя устройства dshow (см. {@code ffmpeg -list_devices})
     * @param format     желаемый формат PCM (реально применится, если устройство его поддерживает)
     */
    public AudioCaptureEncoder(String deviceName, AudioFormat format) {
        this(deviceName, format, null);
    }

    /**
     * Полная форма конструктора.
     *
     * @param deviceName имя устройства dshow
     * @param format     желаемый формат PCM
     * @param ffmpegPath путь к ffmpeg.exe или {@code null} для поиска в PATH
     */
    public AudioCaptureEncoder(String deviceName, AudioFormat format, String ffmpegPath) {
        if (deviceName == null || deviceName.isEmpty()) {
            throw new IllegalArgumentException("deviceName must not be empty");
        }
        this.deviceName = deviceName;
        this.format     = (format != null) ? format : AudioFormat.defaultFormat();
        this.ffmpegPath = (ffmpegPath != null) ? ffmpegPath : "ffmpeg";

        // На текущий момент мы поддерживаем только PCM 16-bit signed LE.
        // Под другие форматы нужно расширить buildFFmpegCommand().
        if (this.format.getBitDepth() != 16) {
            throw new IllegalArgumentException(
                    "Only 16-bit PCM is supported, got: " + this.format.getBitDepth());
        }
    }

    // ---------------------------------------------------------- AudioCapture

    @Override
    public void start() throws CaptureException {
        if (closed.get()) {
            throw new CaptureException("Capture is closed");
        }
        if (!started.compareAndSet(false, true)) {
            throw new CaptureException("Capture already started");
        }
        try {
            launchFFmpeg();
            System.out.println("[AudioCaptureEncoder] Запущен: '" + deviceName + "' @ " + format);
        } catch (IOException e) {
            started.set(false);
            throw new CaptureException("Failed to start audio capture: " + e.getMessage(), e);
        }
    }

    @Override
    public CapturedAudioChunk read() throws IOException {
        if (!started.get()) {
            throw new IllegalStateException("Capture is not started");
        }
        if (closed.get()) {
            return null;
        }

        byte[] buf = new byte[READ_BUFFER_SIZE];
        int n = ffmpegOutput.read(buf);
        if (n == -1) {
            return null; // EOF — процесс завершился
        }

        byte[] data = (n == buf.length) ? buf : Arrays.copyOf(buf, n);
        return new CapturedAudioChunk(data, format, System.nanoTime());
    }

    @Override
    public AudioFormat getFormat() {
        return started.get() ? format : null;
    }

    @Override
    public boolean isCapturing() {
        return started.get() && !closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        if (process != null) {
            process.destroyForcibly();
            try {
                process.waitFor(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            process = null;
        }

        if (stderrReader != null) {
            stderrReader.interrupt();
            stderrReader = null;
        }

        ffmpegOutput = null;
        started.set(false);

        System.out.println("[AudioCaptureEncoder] Остановлен");
    }

    // ------------------------------------------------------------- helpers

    public String getDeviceName() {
        return deviceName;
    }

    private void launchFFmpeg() throws IOException {
        List<String> cmd = buildFFmpegCommand();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        process = pb.start();

        ffmpegOutput = new BufferedInputStream(process.getInputStream(), 128 * 1024);

        // stderr читаем отдельным daemon-потоком, чтобы пайп не забивался
        stderrReader = new Thread(this::drainStderr, "ffmpeg-audio-stderr");
        stderrReader.setDaemon(true);
        stderrReader.start();
    }

    private List<String> buildFFmpegCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-hide_banner");
        cmd.add("-loglevel"); cmd.add("warning");

        // Вход: dshow с указанным устройством
        cmd.add("-f");  cmd.add("dshow");
        cmd.add("-i");  cmd.add("audio=" + deviceName);

        // Выход: сырой PCM s16le с целевыми параметрами
        cmd.add("-vn"); // на всякий случай — никаких видео-потоков
        cmd.add("-acodec"); cmd.add("pcm_s16le");
        cmd.add("-ar"); cmd.add(String.valueOf(format.getSampleRate()));
        cmd.add("-ac"); cmd.add(String.valueOf(format.getChannels()));
        cmd.add("-f");  cmd.add("s16le");
        cmd.add("-flush_packets"); cmd.add("1");
        cmd.add("pipe:1");

        return cmd;
    }

    private void drainStderr() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.err.println("[ffmpeg-audio] " + line);
            }
        } catch (IOException ignored) {
            // процесс завершён — выходим
        }
    }

    // ---------------------------------------- factories для совместимости

    /**
     * Удобный метод создания из устройства, найденного через
     * {@link ScreenCaptureEncoder#listAllAudioDevices()}.
     */
    public static AudioCaptureEncoder fromDevice(ScreenCaptureEncoder.AudioDevice device) {
        return new AudioCaptureEncoder(device.ffmpegArg);
    }

    public static AudioCaptureEncoder fromDevice(ScreenCaptureEncoder.AudioDevice device,
                                                 AudioFormat format) {
        return new AudioCaptureEncoder(device.ffmpegArg, format);
    }

    public static AudioCaptureEncoder fromDevice(ScreenCaptureEncoder.AudioDevice device,
                                                 AudioFormat format,
                                                 String ffmpegPath) {
        return new AudioCaptureEncoder(device.ffmpegArg, format, ffmpegPath);
    }
}
