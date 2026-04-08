package ru.hniApplications.testApplication;

import ru.hniApplications.testApplication.capture.CaptureException;
import ru.hniApplications.testApplication.capture.CapturedFrame;
import ru.hniApplications.testApplication.capture.FrameCaptureCallback;
import ru.hniApplications.testApplication.capture.ScreenCapture;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


public class DesktopScreenCapture implements ScreenCapture {

    private final Robot robot;
    private final Rectangle screenRect;

    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final AtomicReference<Thread> captureThread = new AtomicReference<>();

    

    
    public DesktopScreenCapture() throws CaptureException {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new CaptureException("Failed to create java.awt.Robot", e);
        }
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.screenRect = new Rectangle(screenSize);
    }

    
    public DesktopScreenCapture(Rectangle captureArea) throws CaptureException {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new CaptureException("Failed to create java.awt.Robot", e);
        }
        this.screenRect = new Rectangle(captureArea);
    }

    

    @Override
    public CapturedFrame captureFrame() throws CaptureException {
        try {
            long tsMillis = System.currentTimeMillis();
            BufferedImage screenshot = robot.createScreenCapture(screenRect);

            
            int w = screenshot.getWidth();
            int h = screenshot.getHeight();
            int[] pixels = screenshot.getRGB(0, 0, w, h, null, 0, w);

            
            byte[] rgb = new byte[w * h * 3];
            for (int i = 0; i < pixels.length; i++) {
                int p = pixels[i];
                int off = i * 3;
                rgb[off]     = (byte) (p >>> 16);
                rgb[off + 1] = (byte) (p >>> 8);
                rgb[off + 2] = (byte)  p;
            }

            return new CapturedFrame(rgb, w, h, tsMillis);
        } catch (Exception e) {
            throw new CaptureException("Error capturing frame", e);
        }
    }

    @Override
    public void startCapture(int fps, FrameCaptureCallback callback) throws CaptureException {
        if (fps < 1 || fps > 60) {
            throw new CaptureException("FPS must be 1-60, got: " + fps);
        }
        if (callback == null) {
            throw new CaptureException("callback cannot be null");
        }
        if (!capturing.compareAndSet(false, true)) {
            return; 
        }

        long frameDurationNanos = 1_000_000_000L / fps;

        Thread thread = new Thread(() -> {
            while (capturing.get() && !Thread.currentThread().isInterrupted()) {
                long frameStart = System.nanoTime();
                try {
                    CapturedFrame frame = captureFrame();
                    callback.onFrameCaptured(frame);
                } catch (CaptureException e) {
                    
                    System.err.println("Frame capture error: " + e.getMessage());
                }

                
                long elapsed = System.nanoTime() - frameStart;
                long sleepNanos = frameDurationNanos - elapsed;
                if (sleepNanos > 0) {
                    try {
                        long sleepMs = sleepNanos / 1_000_000;
                        int sleepNanosRemainder = (int) (sleepNanos % 1_000_000);
                        Thread.sleep(sleepMs, sleepNanosRemainder);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            capturing.set(false);
        }, "screen-capture-thread");

        thread.setDaemon(true);
        captureThread.set(thread);
        thread.start();
    }

    @Override
    public void stopCapture() {
        capturing.set(false);
        Thread thread = captureThread.getAndSet(null);
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isCapturing() {
        return capturing.get();
    }

    
    public int getWidth() {
        return screenRect.width;
    }

    
    public int getHeight() {
        return screenRect.height;
    }

    
    private byte[] extractRgbBytes(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] rgb = new byte[w * h * 3];

        if (image.getType() == BufferedImage.TYPE_3BYTE_BGR
                && image.getRaster().getDataBuffer() instanceof DataBufferByte) {
            
            byte[] bgr = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < w * h; i++) {
                int srcOff = i * 3;
                rgb[srcOff] = bgr[srcOff + 2]; 
                rgb[srcOff + 1] = bgr[srcOff + 1]; 
                rgb[srcOff + 2] = bgr[srcOff];     
            }
        } else {
            
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                rgb[i * 3] = (byte) ((pixel >> 16) & 0xFF); 
                rgb[i * 3 + 1] = (byte) ((pixel >> 8) & 0xFF); 
                rgb[i * 3 + 2] = (byte) (pixel & 0xFF); 
            }
        }

        return rgb;
    }
}