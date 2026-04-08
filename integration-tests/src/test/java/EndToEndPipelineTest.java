import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.GraphicsEnvironment;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndPipelineTest {

    private static final int FPS = 30;
    private static final int STREAMING_DURATION_SEC = 3;
    private static final int MIN_EXPECTED_FRAMES = 13;
    private static final long MAX_LATENCY_MS = 300;

    private StreamingPipeline streamingPipeline;
    private ReceivingPipeline receivingPipeline;

    @BeforeAll
    void setUp() throws Exception {
        assumeTrue(!GraphicsEnvironment.isHeadless(),
                "Test skipped: no graphical display");

        streamingPipeline = new StreamingPipeline(0, FPS);
        streamingPipeline.start();

        int port = streamingPipeline.getLocalPort();
        int width = streamingPipeline.getCapturedWidth();
        int height = streamingPipeline.getCapturedHeight();

        System.out.println("Server started on port " + port
                + ", resolution " + width + "×" + height);

        receivingPipeline = new ReceivingPipeline("localhost", port, width, height);
        receivingPipeline.start();

        Thread.sleep(500);
        assertTrue(receivingPipeline.isConnected(),
                "Client could not connect to server");

        System.out.println("Streaming for " + STREAMING_DURATION_SEC + " seconds...");
        Thread.sleep(TimeUnit.SECONDS.toMillis(STREAMING_DURATION_SEC));

        
        System.out.println("Stopping streaming, waiting for decoder to drain...");
        streamingPipeline.stop();

        
        waitForDecoderDrain();

        System.out.println("Streaming complete, collected frames: "
                + receivingPipeline.getReceivedCount());
    }

    
    private void waitForDecoderDrain() throws InterruptedException {
        int previousCount = 0;
        int stableIterations = 0;

        for (int i = 0; i < 25; i++) { 
            Thread.sleep(200);
            int currentCount = receivingPipeline.getReceivedCount();

            if (currentCount == previousCount) {
                stableIterations++;
                
                if (stableIterations >= 3) {
                    System.out.println("  Decoder drained in " + (i * 200) + " ms");
                    return;
                }
            } else {
                stableIterations = 0;
            }

            previousCount = currentCount;
        }

        System.err.println("  WARN: decoder did not drain in 5 seconds, "
                + "current count: " + receivingPipeline.getReceivedCount());
    }

    @AfterAll
    void tearDown() {
        if (receivingPipeline != null) {
            receivingPipeline.stop();
        }
        
    }

    

    @Test
    @Order(1)
    void serverSentFrames() {
        long sent = streamingPipeline.getFramesSent();
        System.out.println("  Server sent: " + sent + " frames");
        assertTrue(sent > 0, "Server did not send any frames");
    }

    @Test
    @Order(2)
    void clientReceivedEnoughFrames() {
        List<ReceivingPipeline.ReceivedFrame> frames = receivingPipeline.getReceivedFrames();

        System.out.println("  Client received: " + frames.size() + " frames");
        System.out.println("  Minimum expected: " + MIN_EXPECTED_FRAMES);

        assertTrue(frames.size() >= MIN_EXPECTED_FRAMES,
                "Client received too few frames: " + frames.size()
                        + ", expected minimum " + MIN_EXPECTED_FRAMES);
    }

    @Test
    @Order(3)
    void lastFrameIsValidImage() throws Exception {
        List<ReceivingPipeline.ReceivedFrame> frames = receivingPipeline.getReceivedFrames();
        assertFalse(frames.isEmpty(), "No received frames to verify");

        BufferedImage lastImage = frames.get(frames.size() - 1).image();
        assertNotNull(lastImage, "Last decoded frame is null");

        int expectedWidth = streamingPipeline.getCapturedWidth();
        int expectedHeight = streamingPipeline.getCapturedHeight();

        System.out.println("  Capture resolution: " + expectedWidth + "×" + expectedHeight);
        System.out.println("  Frame resolution: "
                + lastImage.getWidth() + "×" + lastImage.getHeight());

        assertEquals(expectedWidth, lastImage.getWidth(), "Width mismatch");
        assertEquals(expectedHeight, lastImage.getHeight(), "Height mismatch");

        
        boolean hasNonBlack = false;
        outer:
        for (int y = 0; y < lastImage.getHeight(); y += lastImage.getHeight() / 10 + 1) {
            for (int x = 0; x < lastImage.getWidth(); x += lastImage.getWidth() / 10 + 1) {
                if ((lastImage.getRGB(x, y) & 0x00FFFFFF) != 0) {
                    hasNonBlack = true;
                    break outer;
                }
            }
        }
        assertTrue(hasNonBlack, "Frame is completely black - decoding not working");

        
        File outputDir = new File("build/test-output");
        outputDir.mkdirs();
        File outputFile = new File(outputDir, "last-decoded-frame.png");
        ImageIO.write(lastImage, "PNG", outputFile);
        System.out.println("  Saved: " + outputFile.getAbsolutePath());
    }

    @Test
    @Order(4)
    void latencyIsAcceptable() {
        List<ReceivingPipeline.ReceivedFrame> frames = receivingPipeline.getReceivedFrames();
        assertFalse(frames.isEmpty(), "No frames for latency measurement");

        long[] latencies = frames.stream()
                .mapToLong(ReceivingPipeline.ReceivedFrame::latencyMs)
                .sorted()
                .toArray();

        long median = latencies[latencies.length / 2];
        long min = latencies[0];
        long max = latencies[latencies.length - 1];
        double avg = frames.stream()
                .mapToLong(ReceivingPipeline.ReceivedFrame::latencyMs)
                .average()
                .orElse(0);

        System.out.println("  Latency (ms): min=" + min
                + ", max=" + max
                + ", median=" + median
                + ", avg=" + String.format("%.1f", avg));

        assertTrue(median < MAX_LATENCY_MS,
                "Median latency " + median + " ms > limit " + MAX_LATENCY_MS + " ms");
    }

    @Test
    @Order(5)
    void acceptablePacketLoss() {
        long sent = streamingPipeline.getFramesSent();
        int received = receivingPipeline.getReceivedFrames().size();
        double ratio = (double) received / sent;

        System.out.println("  Sent: " + sent + ", received: " + received
                + ", ratio: " + String.format("%.1f%%", ratio * 100));

        assertTrue(ratio >= 0.9,
                "Loss: received " + received + " out of " + sent
                        + " (" + String.format("%.1f%%", ratio * 100) + ")");
    }
}