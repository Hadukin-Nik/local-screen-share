package ru.hniApplications.testApplication.desktop;

public class AudioDevice {
    public String displayName;
    public String ffmpegArg;
    public AudioCaptureMode mode;

    public AudioDevice(String name, String arg, AudioCaptureMode mode) {
        this.displayName = name;
        this.ffmpegArg = arg;
        this.mode = mode;
    }
    @Override
    public String toString() { return displayName; }
}