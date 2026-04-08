import org.junit.jupiter.api.*;
import ru.hniApplications.testApplication.DesktopScreenCapture;
import ru.hniApplications.testApplication.capture.CapturedFrame;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesktopScreenCaptureTest {

    private DesktopScreenCapture capture;

    @BeforeEach
    void setUp() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            Assumptions.assumeFalse(true,
                    "Test skipped: no graphical environment (headless)");
        }
        capture = new DesktopScreenCapture();
    }

    @AfterEach
    void tearDown() {
        if (capture != null) {
            capture.stopCapture();
        }
    }

    

    @Test
    @Order(1)
    void singleFrameHasCorrectResolution() throws Exception {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        CapturedFrame frame = capture.captureFrame();

        assertNotNull(frame);
        assertEquals(screenSize.width, frame.getWidth(),
                "Frame width should match screen width");
        assertEquals(screenSize.height, frame.getHeight(),
                "Frame height should match screen height");

        int expectedSize = screenSize.width * screenSize.height * 3;
        assertEquals(expectedSize, frame.getDataSize(),
                "Data size = width × height × 3 (RGB)");
        assertTrue(frame.getDataSize() > 0);
        assertTrue(frame.getTimestampNanos() > 0);
    }

    

    @Test
    @Order(2)
    void frameDataIsNotAllZeros() throws Exception {
        CapturedFrame frame = capture.captureFrame();
        byte[] data = frame.getRgbData();

        boolean hasNonZero = false;
        for (byte b : data) {
            if (b != 0) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue(hasNonZero,
                "Frame should not be completely black - screen contains UI elements");
    }

    

    @Test
    @Order(3)
    void continuousCaptureAt30Fps() throws Exception {
        List<CapturedFrame> frames = new CopyOnWriteArrayList<>();

        capture.startCapture(30, frames::add);
        assertTrue(capture.isCapturing());

        Thread.sleep(2000);

        capture.stopCapture();

        int count = frames.size();
        int expected = 60; 
        int tolerance = (int) (expected * 0.20); 

        System.out.printf("Captured frames: %d (expected %d ±%d)%n",
                count, expected, tolerance);

        assertTrue(count >= expected - tolerance,
                String.format("Too few frames: %d (minimum %d)", count, expected - tolerance));
        assertTrue(count <= expected + tolerance,
                String.format("Too many frames: %d (maximum %d)", count, expected + tolerance));
    }

    

    @Test
    @Order(4)
    void noFramesAfterStop() throws Exception {
        List<CapturedFrame> frames = new CopyOnWriteArrayList<>();

        capture.startCapture(30, frames::add);
        Thread.sleep(500);

        capture.stopCapture();
        assertFalse(capture.isCapturing());

        int countAtStop = frames.size();
        assertTrue(countAtStop > 0, "There should be at least one frame in 0.5 sec");

        
        Thread.sleep(1000);

        assertEquals(countAtStop, frames.size(),
                "After stopCapture() no new frames should arrive");
    }

    

    @Test
    @Order(5)
    void doubleStartIsIgnored() throws Exception {
        List<CapturedFrame> frames1 = new CopyOnWriteArrayList<>();
        List<CapturedFrame> frames2 = new CopyOnWriteArrayList<>();

        capture.startCapture(10, frames1::add);
        assertTrue(capture.isCapturing());

        
        capture.startCapture(10, frames2::add);

        Thread.sleep(500);
        capture.stopCapture();

        assertTrue(frames1.size() > 0,
                "First callback should have received frames");
        assertEquals(0, frames2.size(),
                "Second callback should not have received frames");
    }

    

    @Test
    @Order(6)
    void stopWithoutStartIsSafe() {
        assertDoesNotThrow(() -> capture.stopCapture());
        assertFalse(capture.isCapturing());
    }

    

    @Test
    @Order(7)
    void captureCustomArea() throws Exception {
        Rectangle area = new Rectangle(0, 0, 100, 100);
        DesktopScreenCapture smallCapture = new DesktopScreenCapture(area);

        CapturedFrame frame = smallCapture.captureFrame();

        assertEquals(100, frame.getWidth());
        assertEquals(100, frame.getHeight());
        assertEquals(100 * 100 * 3, frame.getDataSize());
    }

    

    @Test
    @Order(8)
    void timestampsAreMonotonicallyIncreasing() throws Exception {
        List<CapturedFrame> frames = new CopyOnWriteArrayList<>();

        capture.startCapture(10, frames::add);
        Thread.sleep(1000);
        capture.stopCapture();

        assertTrue(frames.size() >= 5, "There should be at least 5 frames in one second at 10 fps");

        for (int i = 1; i < frames.size(); i++) {
            long prev = frames.get(i - 1).getTimestampNanos();
            long curr = frames.get(i).getTimestampNanos();
            assertTrue(curr > prev,
                    String.format("Frame %d (ts=%d) not later than frame %d (ts=%d)", i, curr, i - 1, prev));
        }
    }

    

    @Test
    @Order(9)
    void invalidFpsThrows() {
        assertThrows(Exception.class, () -> capture.startCapture(0, f -> {}));
        assertThrows(Exception.class, () -> capture.startCapture(-5, f -> {}));
        assertThrows(Exception.class, () -> capture.startCapture(61, f -> {}));
    }
}