package ru.hniApplications.testApplication.desktop;

import javafx.application.Platform;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


public class BroadcastPreviewCapture {

    private final Consumer<WritableImage> frameCallback;
    private final int previewFps;
    private final double maxPreviewWidth;
    private final double maxPreviewHeight;

    private Thread captureThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    
    public BroadcastPreviewCapture(Consumer<WritableImage> frameCallback,
                                   int previewFps,
                                   double maxPreviewWidth,
                                   double maxPreviewHeight) {
        this.frameCallback = frameCallback;
        this.previewFps = previewFps;
        this.maxPreviewWidth = maxPreviewWidth;
        this.maxPreviewHeight = maxPreviewHeight;
    }

    public BroadcastPreviewCapture(Consumer<WritableImage> frameCallback) {
        this(frameCallback, 8, 640, 360);
    }

    public void start() {
        if (running.getAndSet(true)) return;

        captureThread = new Thread(this::captureLoop, "broadcast-preview");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    public void stop() {
        running.set(false);
        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(2000);
            } catch (InterruptedException ignored) {}
            captureThread = null;
        }
    }

    private void captureLoop() {
        try {
            Robot robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle screenRect = new Rectangle(screenSize);

            
            double scale = Math.min(
                    maxPreviewWidth / screenSize.width,
                    maxPreviewHeight / screenSize.height);
            scale = Math.min(scale, 1.0);

            int previewW = (int) (screenSize.width * scale);
            int previewH = (int) (screenSize.height * scale);

            long frameDurationMs = 1000 / previewFps;

            while (running.get()) {
                long start = System.currentTimeMillis();

                
                BufferedImage fullScreen = robot.createScreenCapture(screenRect);

                
                BufferedImage preview = new BufferedImage(
                        previewW, previewH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = preview.createGraphics();
                g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(fullScreen, 0, 0, previewW, previewH, null);
                g2.dispose();

                
                WritableImage fxImage = new WritableImage(previewW, previewH);
                PixelWriter pw = fxImage.getPixelWriter();
                for (int y = 0; y < previewH; y++) {
                    for (int x = 0; x < previewW; x++) {
                        pw.setArgb(x, y, preview.getRGB(x, y));
                    }
                }

                
                Platform.runLater(() -> frameCallback.accept(fxImage));

                
                long elapsed = System.currentTimeMillis() - start;
                long sleep = frameDurationMs - elapsed;
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
        } catch (InterruptedException ignored) {
            
        } catch (AWTException e) {
            System.err.println(
                    "[BroadcastPreviewCapture] Robot unavailable: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}