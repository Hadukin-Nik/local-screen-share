package ru.hniApplications.testApplication.desktop;

import ru.hniApplications.testApplication.FramePacket;
import ru.hniApplications.testApplication.FrameType;
import ru.hniApplications.testApplication.ScreenCaptureEncoder;
import ru.hniApplications.testApplication.ScreenCaptureEncoder.AudioDevice;
import ru.hniApplications.testApplication.net.RelayServer;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class DesktopStreamingPipeline {
    private final int port;
    private final int fps;

    private RelayServer server;
    private ScreenCaptureEncoder encoder;

    private Process audioCaptureProcess;
    private Thread audioCaptureThread;

    private Thread captureThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong chunksSent = new AtomicLong(0);

    private volatile int capturedWidth;
    private volatile int capturedHeight;

    private volatile BroadcastRecorder recorder;
    private AudioDevice audioDevice;
    private Consumer<Double> audioLevelListener;

    public DesktopStreamingPipeline(int port, int fps) {
        this.port = port;
        this.fps = fps;
    }

    public void setAudioLevelListener(Consumer<Double> audioLevelListener) {
        this.audioLevelListener = audioLevelListener;
    }

    public void detectAudioDevice() {
        this.audioDevice = ScreenCaptureEncoder.listAllAudioDevices().getFirst();
    }

    public static List<AudioDevice> getAvailableAudioDevices() {
        return ScreenCaptureEncoder.listAllAudioDevices();
    }

    public void setAudioDevice(AudioDevice device) {
        this.audioDevice = device;
    }

    public AudioDevice getAudioDevice() {
        return audioDevice;
    }

    public void start() throws Exception {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Pipeline already started");
        }

        server = new RelayServer(port, null, null);
        server.start();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        capturedWidth = screenSize.width;
        capturedHeight = screenSize.height;

        // 1. Всегда запускаем независимый видео-кодировщик
        encoder = new ScreenCaptureEncoder(fps, capturedWidth, capturedHeight, audioDevice);

        captureThread = new Thread(this::readVideoLoop, "video-read-loop");
        captureThread.setDaemon(true);
        captureThread.start();

    }


    private void readVideoLoop() {
        try {
            while (running.get()) {
                byte[] chunk = encoder.readChunk();
                if (chunk == null) break;

                long timestampMs = System.currentTimeMillis();
                chunksSent.incrementAndGet();

                // Примечание: тут желательно парсить NAL-юнит, чтобы отличать I_FRAME от P_FRAME
                // Для упрощения оставляю вашу реализацию
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
            if (running.get()) System.err.println("[Pipeline] Ошибка чтения потока видео: " + e.getMessage());
        }
    }

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

    public void stop() {
        running.set(false);

        if (recorder != null && recorder.isRecording()) {
            recorder.stopAndSave(null);
            recorder = null;
        }

        if (audioCaptureThread != null) {
            audioCaptureThread.interrupt();
            audioCaptureThread = null;
        }

        if (audioCaptureProcess != null) {
            audioCaptureProcess.destroyForcibly();
            audioCaptureProcess = null;
        }

        if (encoder != null) {
            encoder.close();
            encoder = null;
        }

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }

        if (server != null) {
            server.stop();
            server = null;
        }
    }

    public int getLocalPort() { return server != null ? server.getLocalPort() : port; }
    public long getFramesSent() { return chunksSent.get(); }
    public int getClientCount() { return server != null ? server.getClientCount() : 0; }
    public int getCapturedWidth() { return capturedWidth; }
    public int getCapturedHeight() { return capturedHeight; }
    public boolean isRunning() { return running.get(); }
}