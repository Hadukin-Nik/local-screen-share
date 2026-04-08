import ru.hniApplications.testApplication.FramePacket;
import ru.hniApplications.testApplication.H264VideoDecoder;
import ru.hniApplications.testApplication.net.ConnectionListener;
import ru.hniApplications.testApplication.net.FrameListener;
import ru.hniApplications.testApplication.net.RelayClient;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class ReceivingPipeline {

    private final String host;
    private final int port;
    private final int width;
    private final int height;

    private RelayClient client;
    private H264VideoDecoder decoder;

    private final CopyOnWriteArrayList<ReceivedFrame> receivedFrames =
            new CopyOnWriteArrayList<>();

    private volatile boolean connected = false;
    private volatile boolean running = false;

    private final CountDownLatch connectedLatch = new CountDownLatch(1);

    
    private Thread frameCollectorThread;

    
    private volatile long lastReceiveTimeMs = 0;

    public ReceivingPipeline(String host, int port, int width, int height) {
        this.host = host;
        this.port = port;
        this.width = width;
        this.height = height;
    }

    public void start() throws Exception {
        decoder = new H264VideoDecoder(width, height);
        running = true;

        FrameListener frameListener = (FramePacket packet) -> {
            onRawFrameReceived(packet.getPayload(), packet.getTimestamp());
        };

        ConnectionListener connectionListener = new ConnectionListener() {
            @Override
            public void onConnected(String remoteAddress) {
                connected = true;
                connectedLatch.countDown();
                System.out.println("[ReceivingPipeline] Connected to " + remoteAddress);
            }

            @Override
            public void onDisconnected(String remoteAddress, Throwable cause) {
                connected = false;
                String reason = (cause != null) ? cause.getMessage() : "normal disconnect";
                System.out.println("[ReceivingPipeline] Disconnected from " + remoteAddress
                        + ", reason: " + reason);
            }
        };

        
        frameCollectorThread = new Thread(this::frameCollectorLoop, "frame-collector");
        frameCollectorThread.setDaemon(true);
        frameCollectorThread.start();

        client = new RelayClient(host, port, frameListener, connectionListener);
        client.connect();

        boolean ok = connectedLatch.await(5, TimeUnit.SECONDS);
        if (!ok) {
            throw new IllegalStateException(
                    "Failed to connect to " + host + ":" + port + " in 5 seconds");
        }
    }

    
    private void onRawFrameReceived(byte[] data, long sendTimestampMs) {
        lastReceiveTimeMs = System.currentTimeMillis();
        decoder.feedData(data);
    }

    
    private void frameCollectorLoop() {
        System.out.println("[ReceivingPipeline] Frame collector started");

        while (running) {
            try {
                
                BufferedImage image = decoder.waitForFrame(100);

                if (image == null) {
                    continue;
                }

                long now = System.currentTimeMillis();
                long latencyMs = now - lastReceiveTimeMs;

                ReceivedFrame frame = new ReceivedFrame(
                        image,
                        lastReceiveTimeMs,
                        now,
                        latencyMs
                );

                receivedFrames.add(frame);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[ReceivingPipeline] Frame collector error: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        System.out.println("[ReceivingPipeline] Frame collector stopped");
    }

    public void stop() {
        running = false;

        if (frameCollectorThread != null) {
            frameCollectorThread.interrupt();
            try {
                frameCollectorThread.join(2000);
            } catch (InterruptedException ignored) {}
        }

        if (client != null) {
            try {
                client.disconnect();
            } catch (Exception e) {
                System.err.println("[ReceivingPipeline] Disconnect error: "
                        + e.getMessage());
            }
        }

        if (decoder != null) {
            decoder.close();
        }
    }

    

    public List<ReceivedFrame> getReceivedFrames() {
        return receivedFrames;
    }

    public boolean isConnected() {
        return connected;
    }

    public int getReceivedCount() {
        return receivedFrames.size();
    }

    

    public static class ReceivedFrame {
        private final BufferedImage image;
        private final long sendTimestampMs;
        private final long receiveTimestampMs;
        private final long latencyMs;

        public ReceivedFrame(BufferedImage image,
                             long sendTimestampMs,
                             long receiveTimestampMs,
                             long latencyMs) {
            this.image = image;
            this.sendTimestampMs = sendTimestampMs;
            this.receiveTimestampMs = receiveTimestampMs;
            this.latencyMs = latencyMs;
        }

        public BufferedImage image()             { return image; }
        public BufferedImage getImage()          { return image; }
        public long latencyMs()                  { return latencyMs; }
        public long getSendTimestampMs()         { return sendTimestampMs; }
        public long getReceiveTimestampMs()      { return receiveTimestampMs; }
    }
}