package ru.hniApplications.testApplication.desktop;

import java.io.IOException;
import java.nio.file.Path;

public class BroadcastManager {

    private final String name;
    private final int port;
    private final int fps;
    private DesktopStreamingPipeline pipeline;

    public BroadcastManager(String name, int port, int fps) {
        this.name = name;
        this.port = port;
        this.fps = fps;
    }

    public void start() throws Exception {
        pipeline = new DesktopStreamingPipeline(port, fps);
        pipeline.start();
    }

    public void stop() {
        if (pipeline != null) {
            pipeline.stop();
            pipeline = null;
        }
    }

    // ── Запись ──

    public void startRecording(Path outputDir) throws IOException {
        if (pipeline != null) {
            pipeline.startRecording(outputDir);
        }
    }

    public void stopRecording(BroadcastRecorder.RecordingCompleteCallback onComplete) {
        if (pipeline != null) {
            pipeline.stopRecording(onComplete);
        }
    }

    public boolean isRecording() {
        return pipeline != null && pipeline.isRecording();
    }

    // ── Геттеры ──

    public boolean isRunning() {
        return pipeline != null && pipeline.isRunning();
    }

    public int getActualPort() {
        return pipeline != null ? pipeline.getLocalPort() : port;
    }

    public long getFramesSent() {
        return pipeline != null ? pipeline.getFramesSent() : 0;
    }

    public int getClientCount() {
        return pipeline != null ? pipeline.getClientCount() : 0;
    }

    public int getCapturedWidth() {
        return pipeline != null ? pipeline.getCapturedWidth() : 0;
    }

    public int getCapturedHeight() {
        return pipeline != null ? pipeline.getCapturedHeight() : 0;
    }

    public String getName() {
        return name;
    }
}