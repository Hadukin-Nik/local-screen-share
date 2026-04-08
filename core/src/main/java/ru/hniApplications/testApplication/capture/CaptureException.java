package ru.hniApplications.testApplication.capture;


public class CaptureException extends Exception {

    public CaptureException(String message) {
        super(message);
    }

    public CaptureException(String message, Throwable cause) {
        super(message, cause);
    }
}