package ru.hniApplications.testApplication.capture;


@FunctionalInterface
public interface FrameCaptureCallback {

    
    void onFrameCaptured(CapturedFrame frame);
}