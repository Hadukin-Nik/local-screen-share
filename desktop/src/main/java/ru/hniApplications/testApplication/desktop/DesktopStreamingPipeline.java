package ru.hniApplications.testApplication.desktop;

import ru.hniApplications.testApplication.FramePacket;
import ru.hniApplications.testApplication.FrameType;
import ru.hniApplications.testApplication.ScreenCaptureEncoder;
import ru.hniApplications.testApplication.net.RelayServer;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


public class DesktopStreamingPipeline {

    private final int port;
    private final int fps;

    private RelayServer server;
    private ScreenCaptureEncoder encoder;

    private Thread captureThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong framesSent = new AtomicLong(0);

    private volatile int capturedWidth;
    private volatile int capturedHeight;

    public DesktopStreamingPipeline(int port, int fps) {
        this.port = port;
        this.fps = fps;
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


        encoder = new ScreenCaptureEncoder(fps, capturedWidth, capturedHeight);


        captureThread = new Thread(this::readLoop, "h264-read-loop");
        captureThread.setDaemon(true);
        captureThread.start();

        System.out.println("[DesktopStreamingPipeline] Started: "
                + capturedWidth + "×" + capturedHeight
                + " @ " + fps + " FPS, port " + getLocalPort());
    }

    // ── Добавить поле ──
    private volatile BroadcastRecorder recorder;

    // ── В readLoop(), после server.broadcast(packet): ──
    private void readLoop() {
        long startTime = System.currentTimeMillis();

        try {
            while (running.get()) {
                byte[] nalUnit = encoder.readChunk();
                if (nalUnit == null) {
                    System.err.println(
                            "[DesktopStreamingPipeline] FFmpeg stream ended");
                    break;
                }

                int nalType = getNalType(nalUnit);
                long timestampMs = System.currentTimeMillis() - startTime;
                FrameType type = nalTypeToFrameType(nalType);

                FramePacket packet = new FramePacket(type, timestampMs, nalUnit);
                server.broadcast(packet);

                // ── Пишем в рекордер (если запись идёт) ──
                BroadcastRecorder rec = this.recorder;
                if (rec != null && rec.isRecording()) {
                    rec.writePacket(packet);
                }

                if (nalType == 1 || nalType == 5) {
                    framesSent.incrementAndGet();
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println(
                        "[DesktopStreamingPipeline] H.264 stream read error: "
                                + e.getMessage());
            }
        }
    }

// ── Добавить методы управления записью ──

    public void startRecording(Path outputDir) throws IOException {
        if (recorder != null && recorder.isRecording()) return;
        recorder = new BroadcastRecorder(outputDir, fps);
        recorder.startRecording();
    }

    public void stopRecording(BroadcastRecorder.RecordingCompleteCallback onComplete) {
        BroadcastRecorder rec = this.recorder;
        if (rec != null) {
            rec.stopAndSave(onComplete);
            this.recorder = null;
        }
    }

    public boolean isRecording() {
        BroadcastRecorder rec = this.recorder;
        return rec != null && rec.isRecording();
    }

    private int getNalType(byte[] nalUnit) {
        int offset = 0;
        if (nalUnit.length >= 4
                && nalUnit[0] == 0 && nalUnit[1] == 0
                && nalUnit[2] == 0 && nalUnit[3] == 1) {
            offset = 4;
        } else if (nalUnit.length >= 3
                && nalUnit[0] == 0 && nalUnit[1] == 0
                && nalUnit[2] == 1) {
            offset = 3;
        }
        if (offset >= nalUnit.length) return -1;
        return nalUnit[offset] & 0x1F;
    }


    private FrameType nalTypeToFrameType(int nalType) {
        switch (nalType) {
            case 7:
                return FrameType.SPS;
            case 8:
                return FrameType.PPS;
            case 5:
                return FrameType.I_FRAME;
            default:
                return FrameType.P_FRAME;
        }
    }


    public void stop() {
        running.set(false);

        // Если шла запись — сохраняем
        BroadcastRecorder rec = this.recorder;
        if (rec != null && rec.isRecording()) {
            rec.stopAndSave(mp4Path -> {
                if (mp4Path != null) {
                    System.out.println("[Pipeline] Запись сохранена: " + mp4Path);
                }
            });
            this.recorder = null;
        }

        if (encoder != null) {
            encoder.close();
            encoder = null;
        }

        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(3000);
            } catch (InterruptedException ignored) {
            }
            captureThread = null;
        }

        if (server != null) {
            server.stop();
            server = null;
        }

        System.out.println("[DesktopStreamingPipeline] Stopped. "
                + "Frames sent: " + framesSent.get());
    }


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