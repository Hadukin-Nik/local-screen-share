package ru.hniApplications.testApplication.desktop;

import ru.hniApplications.testApplication.capture.AudioDevice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public class BroadcastManager {
    private final String name;
    private final int port;
    private final int fps;
    private String videoBitrate = "2000k"; // Значение по умолчанию

    private DesktopStreamingPipeline pipeline;

    private Consumer<Double> audioLevelListener;
    private AudioDevice selectedAudioDevice;

    public BroadcastManager(String name, int port, int fps) {
        this.name = name;
        this.port = port;
        this.fps = fps;
    }

    public void setVideoBitrate(String videoBitrate) {
        this.videoBitrate = videoBitrate;
    }

    public void setAudioLevelListener(Consumer<Double> listener) {
        this.audioLevelListener = listener;
        if (pipeline != null) {
            pipeline.setAudioLevelListener(listener);
        }
    }

    public void setAudioDevice(AudioDevice device) {
        this.selectedAudioDevice = device;
    }

    public void start() throws Exception {
        pipeline = new DesktopStreamingPipeline(port, fps);

        // УСТАНАВЛИВАЕМ БИТРЕЙТ ДЛЯ PIPELINE
        pipeline.setVideoBitrate(this.videoBitrate != null ? this.videoBitrate : "2000k");

        if (selectedAudioDevice != null) {
            pipeline.setAudioDevice(selectedAudioDevice);
        } else {
            pipeline.detectAudioDevice();
        }

        if (audioLevelListener != null) {
            pipeline.setAudioLevelListener(audioLevelListener);
        }

        pipeline.start();
    }

    public void stop() {
        if (pipeline != null) {
            pipeline.stop();
            pipeline = null;
        }
    }

    public void startRecording(Path outputDir) throws IOException {
        if (pipeline != null) {
            pipeline.startRecording(outputDir);
        }
    }

    public void stopRecording(BroadcastRecorder.RecordingCompleteCallback cb) {
        if (pipeline != null) {
            pipeline.stopRecording(cb);
        }
    }

    public boolean isRecording() { return pipeline != null && pipeline.isRecording(); }
    public boolean isRunning() { return pipeline != null && pipeline.isRunning(); }
    public int getActualPort() { return pipeline != null ? pipeline.getLocalPort() : port; }
    public long getFramesSent() { return pipeline != null ? pipeline.getFramesSent() : 0; }
    public int getClientCount() { return pipeline != null ? pipeline.getClientCount() : 0; }
    public int getCapturedWidth() { return pipeline != null ? pipeline.getCapturedWidth() : 0; }
    public int getCapturedHeight() { return pipeline != null ? pipeline.getCapturedHeight() : 0; }
    public String getName() { return name; }
    public AudioDevice getAudioDevice() { return pipeline != null ? pipeline.getAudioDevice() : null; }
}