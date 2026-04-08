import ru.hniApplications.testApplication.*;
import ru.hniApplications.testApplication.capture.CapturedFrame;
import ru.hniApplications.testApplication.net.RelayServer;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class StreamingPipeline {

    private final int port;
    private final int fps;

    private RelayServer server;
    private ScreenCaptureEncoder encoder;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong framesSent = new AtomicLong(0);

    private volatile int capturedWidth;
    private volatile int capturedHeight;

    public StreamingPipeline(int port, int fps) {
        this.port = port;
        this.fps = fps;
    }

    public void start() throws Exception {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Pipeline already started");
        }

        server = new RelayServer(port, null, null);
        server.start();

        
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        capturedWidth = screenSize.width;
        capturedHeight = screenSize.height;

        
        encoder = new ScreenCaptureEncoder(fps, capturedWidth, capturedHeight);

        
        Thread captureThread = new Thread(this::readLoop, "h264-read-loop");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void readLoop() {
        long startTime = System.currentTimeMillis();
        int chunkIndex = 0;
        try {
            while (running.get()) {
                byte[] nalUnit = encoder.readChunk();
                if (nalUnit == null) {
                    System.err.println("FFmpeg stream ended");
                    break;
                }

                chunkIndex++;
                int nalType = getNalType(nalUnit);
                int nalCount = countNalUnits(nalUnit);

                System.out.println("[readLoop] chunk #" + chunkIndex
                        + ": size=" + nalUnit.length
                        + ", firstNalType=" + nalType
                        + ", nalUnitsInside=" + nalCount);

                long timestampMs = System.currentTimeMillis() - startTime;
                FrameType type = nalTypeToFrameType(nalType);

                FramePacket packet = new FramePacket(type, timestampMs, nalUnit);
                server.broadcast(packet);

                if (nalType == 1 || nalType == 5) {
                    framesSent.incrementAndGet();
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("H.264 stream read error: " + e.getMessage());
            }
        }
    }

    
    private int countNalUnits(byte[] data) {
        int count = 0;
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == 0 && data[i + 1] == 0) {
                if (data[i + 2] == 1) {
                    count++;
                    i += 2;
                } else if (i < data.length - 4
                        && data[i + 2] == 0 && data[i + 3] == 1) {
                    count++;
                    i += 3;
                }
            }
        }
        return count;
    }
    private FrameType nalTypeToFrameType(int nalType) {
        switch (nalType) {
            case 7:  return FrameType.SPS;
            case 8:  return FrameType.PPS;
            case 5:  return FrameType.I_FRAME;  
            default: return FrameType.P_FRAME;  
        }
    }

    private int getNalType(byte[] nalUnit) {
        
        int offset = 0;
        if (nalUnit.length >= 4
                && nalUnit[0] == 0 && nalUnit[1] == 0
                && nalUnit[2] == 0 && nalUnit[3] == 1) {
            offset = 4;
        } else if (nalUnit.length >= 3
                && nalUnit[0] == 0 && nalUnit[1] == 0 && nalUnit[2] == 1) {
            offset = 3;
        }
        if (offset >= nalUnit.length) return -1;
        return nalUnit[offset] & 0x1F;
    }
    
    private BufferedImage toBufferedImage(CapturedFrame frame) {
        int w = frame.getWidth();
        int h = frame.getHeight();
        byte[] rgb = frame.getRgbData();

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] bgrData = ((java.awt.image.DataBufferByte)
                image.getRaster().getDataBuffer()).getData();

        
        for (int i = 0; i < rgb.length; i += 3) {
            bgrData[i]     = rgb[i + 2]; 
            bgrData[i + 1] = rgb[i + 1]; 
            bgrData[i + 2] = rgb[i];     
        }

        return image;
    }

    
    private FrameType detectFrameType(byte[] encoded) {
        int offset = 0;

        
        if (encoded.length >= 4
                && encoded[0] == 0x00 && encoded[1] == 0x00
                && encoded[2] == 0x00 && encoded[3] == 0x01) {
            offset = 4;
        } else if (encoded.length >= 3
                && encoded[0] == 0x00 && encoded[1] == 0x00
                && encoded[2] == 0x01) {
            offset = 3;
        }

        if (offset < encoded.length) {
            int nalType = encoded[offset] & 0x1F;
            
            
            if (nalType == 5 || nalType == 7 || nalType == 8) {
                return FrameType.I_FRAME;
            }
        }

        return FrameType.P_FRAME;
    }

    
    public void stop() {
        running.set(false);
        if (encoder != null) encoder.close();
        if (server != null) server.stop();
    }

    public int getCapturedWidth() {
        return capturedWidth;
    }

    public int getCapturedHeight() {
        return capturedHeight;
    }

    public long getFramesSent() {
        return framesSent.get();
    }

    public int getLocalPort() {
        return server.getLocalPort();
    }

    public int getClientCount() {
        return server.getClientCount();
    }
}