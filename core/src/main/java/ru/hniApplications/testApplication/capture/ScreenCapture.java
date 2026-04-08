package ru.hniApplications.testApplication.capture;


public interface ScreenCapture {

    
    CapturedFrame captureFrame() throws CaptureException;

    
    void startCapture(int fps, FrameCaptureCallback callback) throws CaptureException;

    
    void stopCapture();

    
    boolean isCapturing();
}
