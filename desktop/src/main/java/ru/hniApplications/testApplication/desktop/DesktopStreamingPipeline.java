package ru.hniApplications.testApplication.desktop;

import ru.hniApplications.testApplication.*;
import ru.hniApplications.testApplication.capture.AudioCapture;
import ru.hniApplications.testApplication.capture.CaptureException;
import ru.hniApplications.testApplication.capture.CapturedAudioChunk;
import ru.hniApplications.testApplication.net.RelayServer;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Конвейер потоковой передачи экрана и аудио.
 * Использует раздельные потоки для видео и аудио с последующим мультиплексированием.
 * <p>
 * Архитектура:
 * <pre>
 *   ScreenCaptureEncoder --(H.264)--▶ MediaMuxer --(MPEG-TS)--▶ RelayServer
 *   AudioCapture         --(PCM)----/
 * </pre>
 */
public class DesktopStreamingPipeline {

    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_CHANNELS    = 2;

    private final int port;
    private final int fps;

    // Компоненты конвейера
    private RelayServer server;
    private ScreenCaptureEncoder videoEncoder;
    private AudioCapture audioCapture;        // ← интерфейс, а не конкретный класс
    private MediaMuxer mediaMuxer;

    // Потоки
    private Thread videoCaptureThread;
    private Thread audioCaptureThread;
    private Thread muxerReadThread;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong framesSent = new AtomicLong(0);

    private volatile int capturedWidth;
    private volatile int capturedHeight;
    private volatile BroadcastRecorder recorder;

    private ScreenCaptureEncoder.AudioDevice audioDevice;
    private Consumer<Double> audioLevelListener;

    public DesktopStreamingPipeline(int port, int fps) {
        this.port = port;
        this.fps = fps;
    }

    public void setAudioLevelListener(Consumer<Double> audioLevelListener) {
        this.audioLevelListener = audioLevelListener;
    }

    public void detectAudioDevice() {
        List<ScreenCaptureEncoder.AudioDevice> devices = ScreenCaptureEncoder.listAllAudioDevices();
        this.audioDevice = devices.isEmpty() ? null : devices.get(0);
    }

    public static List<ScreenCaptureEncoder.AudioDevice> getAvailableAudioDevices() {
        return ScreenCaptureEncoder.listAllAudioDevices();
    }

    public void setAudioDevice(ScreenCaptureEncoder.AudioDevice device) {
        this.audioDevice = device;
    }

    public ScreenCaptureEncoder.AudioDevice getAudioDevice() {
        return audioDevice;
    }

    // -------------------------------------------------------------- start

    /**
     * Запускает конвейер: захват видео + аудио → мультиплексирование → отправка в сеть.
     */
    public void start() throws Exception {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Pipeline already started");
        }

        // Запускаем сервер
        server = new RelayServer(port, null, null);
        server.start();

        // Определяем размер экрана
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        capturedWidth  = screenSize.width;
        capturedHeight = screenSize.height;

        // 1. Видео-кодировщик
        videoEncoder = new ScreenCaptureEncoder(fps, capturedWidth, capturedHeight);

        // 2. Аудио-захват (только если устройство выбрано)
        if (audioDevice != null) {
            try {
                AudioCaptureEncoder enc = AudioCaptureEncoder.fromDevice(audioDevice);
                enc.start();
                audioCapture = enc;
            } catch (CaptureException e) {
                System.err.println("[Pipeline] Не удалось запустить захват аудио: " + e.getMessage());
                audioCapture = null;
            }
        }

        // 3. Мультиплексор
        mediaMuxer = new MediaMuxer(
                capturedWidth, capturedHeight, fps,
                AUDIO_SAMPLE_RATE, AUDIO_CHANNELS
        );
        mediaMuxer.start();

        // 4. Потоки чтения
        videoCaptureThread = new Thread(this::readVideoLoop, "video-read-loop");
        videoCaptureThread.setDaemon(true);
        videoCaptureThread.start();

        if (audioCapture != null) {
            audioCaptureThread = new Thread(this::readAudioLoop, "audio-read-loop");
            audioCaptureThread.setDaemon(true);
            audioCaptureThread.start();
        }

        // 5. Поток чтения из мультиплексора и отправки в сеть
        muxerReadThread = new Thread(this::readMuxerOutputLoop, "muxer-output-loop");
        muxerReadThread.setDaemon(true);
        muxerReadThread.start();

        System.out.println("[DesktopStreamingPipeline] Запущен: "
                + capturedWidth + "x" + capturedHeight + "@" + fps + "fps"
                + (audioCapture != null ? " + аудио" : " (без аудио)"));
    }

    // -------------------------------------------------------- read loops

    /**
     * Цикл чтения видео из ScreenCaptureEncoder и записи в MediaMuxer.
     */
    private void readVideoLoop() {
        try {
            while (running.get()) {
                byte[] h264Chunk = videoEncoder.readChunk();
                if (h264Chunk == null) break;
                if (mediaMuxer != null && mediaMuxer.isRunning()) {
                    mediaMuxer.writeVideo(h264Chunk);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("[Pipeline] Ошибка чтения потока видео: " + e.getMessage());
            }
        }
    }

    private void readAudioLoop() {
        try {
            while (running.get()) {
                CapturedAudioChunk chunk = audioCapture.read();
                if (chunk == null) break;

                byte[] pcm = chunk.getPcmDataDirect();   // ← БЕЗ копирования

                // Мониторинг уровня (честный RMS по PCM s16le)
                if (audioLevelListener != null) {
                    audioLevelListener.accept(calculatePcmLevel(pcm));
                }

                if (mediaMuxer != null && mediaMuxer.isRunning()) {
                    mediaMuxer.writeAudio(pcm);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("[Pipeline] Ошибка чтения потока аудио: " + e.getMessage());
            }
        }
    }

    /**
     * Цикл чтения MPEG-TS из MediaMuxer и отправки в сеть.
     */
    private void readMuxerOutputLoop() {
        try {
            InputStream muxerOutput = mediaMuxer.getOutputStream();
            byte[] buffer = new byte[64 * 1024];

            while (running.get()) {
                int read = muxerOutput.read(buffer);
                if (read == -1) break;

                byte[] chunk = (read == buffer.length) ? buffer : copyOf(buffer, read);
                long timestampMs = System.currentTimeMillis();
                framesSent.incrementAndGet();

                FramePacket packet = new FramePacket(FrameType.P_FRAME, timestampMs, chunk);

                BroadcastRecorder rec = this.recorder;
                if (rec != null && rec.isRecording()) {
                    rec.writePacket(packet);
                }
                if (server != null) {
                    server.broadcast(packet);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("[Pipeline] Ошибка чтения выхода мультиплексора: " + e.getMessage());
            }
        }
    }

    /**
     * Считает RMS-уровень PCM s16le чанка, нормализованный в [0..1].
     * <p>
     * Формула: rms = sqrt(mean(sample²)), нормировано на 32768.
     */
    private double calculatePcmLevel(byte[] pcm) {
        if (pcm == null || pcm.length < 2) return 0.0;

        int samples = pcm.length / 2;
        long sumSquares = 0L;

        for (int i = 0; i < samples; i++) {
            int lo = pcm[2 * i]     & 0xFF;
            int hi = pcm[2 * i + 1];                // знаковый старший байт
            int sample = (hi << 8) | lo;            // s16le
            sumSquares += (long) sample * sample;
        }

        double rms = Math.sqrt((double) sumSquares / samples);
        return Math.min(1.0, rms / 32768.0);
    }

    private static byte[] copyOf(byte[] original, int newLength) {
        byte[] copy = new byte[newLength];
        System.arraycopy(original, 0, copy, 0, newLength);
        return copy;
    }

    // ---------------------------------------------------------- recording

    public void startRecording(Path outputDir) throws IOException {
        if (recorder != null && recorder.isRecording()) return;
        recorder = new BroadcastRecorder(outputDir);
        recorder.startRecording();
    }

    public void stopRecording(BroadcastRecorder.RecordingCompleteCallback onComplete) {
        if (recorder != null) {
            recorder.stopAndSave(onComplete);
            this.recorder = null;
        }
    }

    public boolean isRecording() {
        return recorder != null && recorder.isRecording();
    }

    // --------------------------------------------------------------- stop

    /**
     * Останавливает конвейер и освобождает ресурсы.
     */
    public void stop() {
        running.set(false);

        // Рекордер
        if (recorder != null && recorder.isRecording()) {
            recorder.stopAndSave(null);
            recorder = null;
        }

        // Аудио
        if (audioCaptureThread != null) {
            audioCaptureThread.interrupt();
            audioCaptureThread = null;
        }
        if (audioCapture != null) {
            audioCapture.close();
            audioCapture = null;
        }

        // Видео
        if (videoCaptureThread != null) {
            videoCaptureThread.interrupt();
            videoCaptureThread = null;
        }
        if (videoEncoder != null) {
            videoEncoder.close();
            videoEncoder = null;
        }

        // Мультиплексор
        if (mediaMuxer != null) {
            mediaMuxer.close();
            mediaMuxer = null;
        }

        // Поток чтения muxer
        if (muxerReadThread != null) {
            muxerReadThread.interrupt();
            muxerReadThread = null;
        }

        // Сервер
        if (server != null) {
            server.stop();
            server = null;
        }

        System.out.println("[DesktopStreamingPipeline] Остановлен");
    }

    // ----------------------------------------------------------- getters

    public int getLocalPort() {
        return server != null ? server.getLocalPort() : port;
    }

    public long getFramesSent() {
        return framesSent.get();
    }

    public int getClientCount() {
        return server != null ? server.getClientCount() : 0;
    }

    public int getCapturedWidth() {
        return capturedWidth;
    }

    public int getCapturedHeight() {
        return capturedHeight;
    }

    public boolean isRunning() {
        return running.get();
    }
}
