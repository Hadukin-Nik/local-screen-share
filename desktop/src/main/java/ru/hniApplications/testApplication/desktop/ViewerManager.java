package ru.hniApplications.testApplication.desktop;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import ru.hniApplications.testApplication.FramePacket;
import ru.hniApplications.testApplication.StreamDecoder;
import ru.hniApplications.testApplication.net.ConnectionListener;
import ru.hniApplications.testApplication.net.FrameListener;
import ru.hniApplications.testApplication.net.RelayClient;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


public class ViewerManager {

    private final String host;
    private final int port;
    private final Consumer<WritableImage> frameCallback;

    private RelayClient client;
    private StreamDecoder decoder;
    private Thread renderThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile int width = 1920;
    private volatile int height = 1080;

    public ViewerManager(String host, int port,
                         Consumer<WritableImage> frameCallback) {
        this.host = host;
        this.port = port;
        this.frameCallback = frameCallback;
    }

    
    public void start() throws Exception {
        running.set(true);

        decoder = new StreamDecoder(width, height);

        
        client = new RelayClient(host, port,
                
                (FramePacket packet) -> {
                    if (!running.get()) return;
                    decoder.feedData(packet.getPayload());
                },
                
                new ConnectionListener() {
                    @Override
                    public void onConnected(String address) {
                        System.out.println("[ViewerManager] Connected to " + address);
                    }

                    @Override
                    public void onDisconnected(String address, Throwable cause) {
                        System.err.println("[ViewerManager] Disconnected from " + address
                                + (cause != null ? ": " + cause.getMessage() : ""));
                    }
                }
        );

        
        client.connect();

        
        renderThread = new Thread(this::renderLoop, "viewer-render");
        renderThread.setDaemon(true);
        renderThread.start();

        System.out.println("[ViewerManager] Connected to " + host + ":" + port);
    }

    private void renderLoop() {
        while (running.get()) {
            try {
                BufferedImage frame = decoder.waitForFrame(50);
                if (frame == null) continue;

                WritableImage fxImage = convertToFxImage(frame);
                if (fxImage != null) {
                    frameCallback.accept(fxImage);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("[ViewerManager] Render error: "
                            + e.getMessage());
                }
            }
        }
    }

    
    private WritableImage convertToFxImage(BufferedImage bgrImage) {
        int w = bgrImage.getWidth();
        int h = bgrImage.getHeight();

        WritableImage fxImg = new WritableImage(w, h);
        PixelWriter pw = fxImg.getPixelWriter();

        
        if (bgrImage.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            byte[] pixels = ((DataBufferByte)
                    bgrImage.getRaster().getDataBuffer()).getData();
            int idx = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int b = pixels[idx++] & 0xFF;
                    int g = pixels[idx++] & 0xFF;
                    int r = pixels[idx++] & 0xFF;
                    pw.setArgb(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
                }
            }
        } else {
            
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pw.setArgb(x, y, bgrImage.getRGB(x, y));
                }
            }
        }

        return fxImg;
    }

    
    public void stop() {
        running.set(false);

        if (renderThread != null) {
            renderThread.interrupt();
        }

        
        if (client != null) {
            try {
                client.disconnect();
            } catch (Exception ignored) {}
            client = null;
        }

        if (decoder != null) {
            decoder.close();
            decoder = null;
        }

        System.out.println("[ViewerManager] Stopped");
    }
}