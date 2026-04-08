package ru.hniApplications.testApplication.desktop;

import ru.hniApplications.testApplication.ScreenCaptureEncoder.AudioDevice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public class BroadcastManager {
    private final String name;
    private final int port;
    private final int fps;

    private DesktopStreamingPipeline pipeline;

    // Храним слушатель, чтобы передать его в pipeline после создания
    private Consumer<Double> audioLevelListener;
    private AudioDevice selectedAudioDevice;

    public BroadcastManager(String name, int port, int fps) {
        this.name = name;
        this.port = port;
        this.fps = fps;
    }

    public void setAudioLevelListener(Consumer<Double> listener) {
        this.audioLevelListener = listener; // Сохраняем
        if (pipeline != null) {
            pipeline.setAudioLevelListener(listener);
        }
    }

    public void setAudioDevice(AudioDevice device) {
        this.selectedAudioDevice = device;
    }

    public void start() throws Exception {
        pipeline = new DesktopStreamingPipeline(port, fps);

        // Передаем устройство
        if (selectedAudioDevice != null) {
            pipeline.setAudioDevice(selectedAudioDevice);
        } else {
            pipeline.detectAudioDevice();
        }

        // Передаем слушатель прямо в ядро энкодера
        if (audioLevelListener != null) {
            pipeline.setAudioLevelListener(audioLevelListener);
        }

        // Всю работу делает сам Pipeline
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