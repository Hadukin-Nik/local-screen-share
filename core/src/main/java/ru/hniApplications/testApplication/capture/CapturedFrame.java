package ru.hniApplications.testApplication.capture;


public class CapturedFrame {

    private final byte[] rgbData;
    private final int width;
    private final int height;
    private final long timestampNanos;

    public CapturedFrame(byte[] rgbData, int width, int height, long timestampNanos) {
        this.rgbData = rgbData;
        this.width = width;
        this.height = height;
        this.timestampNanos = timestampNanos;
    }

    
    public byte[] getRgbData() {
        return rgbData;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    
    public long getTimestampNanos() {
        return timestampNanos;
    }

    
    public int getDataSize() {
        return rgbData.length;
    }
}