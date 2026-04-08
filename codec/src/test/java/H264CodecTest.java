import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import ru.hniApplications.testApplication.EncoderConfig;
import ru.hniApplications.testApplication.H264Codec;
import ru.hniApplications.testApplication.H264VideoDecoder;
import ru.hniApplications.testApplication.ScreenCaptureEncoder;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class H264CodecTest {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final int FPS = 30;

    @Test
    void encodeDecodeRoundTrip() {
        int width = 320;
        int height = 240;

        try (H264Codec codec = new H264Codec(FPS, width, height)) {

            BufferedImage original = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    original.setRGB(x, y, (x & 0xFF) << 16 | (y & 0xFF) << 8);
                }
            }

            
            byte[] encoded = codec.encode(original);
            assertNotNull(encoded);
            assertTrue(encoded.length > 0);

            
            byte[] originalBytes = getPixelBytes(original);
            assertFalse(java.util.Arrays.equals(encoded, originalBytes),
                    "Encoded data should not match original pixels");

            
            BufferedImage decoded = codec.decode(encoded);
            assertNotNull(decoded);
            assertEquals(width, decoded.getWidth());
            assertEquals(height, decoded.getHeight());

            
            
            double psnr = calculatePSNR(original, decoded);
            assertTrue(psnr > 25.0,
                    "PSNR should be above 25 dB (actual: " + psnr + " dB). "
                            + "Decoded image differs too much from original");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test() throws IOException, InterruptedException {
        ScreenCaptureEncoder encoder = new ScreenCaptureEncoder(30, 1920, 1080);
        H264VideoDecoder decoder = new H264VideoDecoder(1920, 1080);

        int chunkCount = 0;
        int frameCount = 0;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < 3000) {
            
            byte[] chunk = encoder.readChunk();
            if (chunk == null) break;
            chunkCount++;

            
            decoder.feedData(chunk);

            
            BufferedImage frame = decoder.getLatestFrame();
            if (frame != null) {
                frameCount++;
                System.out.printf("Frame #%d (chunk #%d, %d bytes)%n",
                        frameCount, chunkCount, chunk.length);
            }
        }

        System.out.printf("%nTotal: %d chunks transferred, %d frames decoded in 3 sec%n",
                chunkCount, frameCount);

        decoder.close();
        encoder.close();
    }

    
    private byte[] getPixelBytes(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        byte[] bytes = new byte[width * height * 3];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                bytes[idx++] = (byte) ((rgb >> 16) & 0xFF);
                bytes[idx++] = (byte) ((rgb >> 8) & 0xFF);
                bytes[idx++] = (byte) (rgb & 0xFF);
            }
        }
        return bytes;
    }

    
    private double calculatePSNR(BufferedImage a, BufferedImage b) {
        int width = a.getWidth();
        int height = a.getHeight();
        long mseSum = 0;
        int totalSamples = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgbA = a.getRGB(x, y);
                int rgbB = b.getRGB(x, y);

                int dr = ((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF);
                int dg = ((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF);
                int db = (rgbA & 0xFF) - (rgbB & 0xFF);

                mseSum += dr * dr + dg * dg + db * db;
                totalSamples += 3;
            }
        }

        double mse = (double) mseSum / totalSamples;
        if (mse == 0) return Double.POSITIVE_INFINITY; 
        return 10.0 * Math.log10(255.0 * 255.0 / mse);
    }
}