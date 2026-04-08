
import org.junit.jupiter.api.Test;
import ru.hniApplications.testApplication.FramePacket;
import ru.hniApplications.testApplication.FrameType;

import static org.junit.jupiter.api.Assertions.*;

class FramePacketTest {

    @Test
    void constructorStoresFields() {
        byte[] payload = {0x00, 0x01, 0x02};
        FramePacket packet = new FramePacket(FrameType.I_FRAME, 123456789L, payload);

        assertEquals(FrameType.I_FRAME, packet.getType());
        assertEquals(123456789L, packet.getTimestamp());
        assertEquals(3, packet.getPayloadLength());
        assertArrayEquals(payload, packet.getPayload());
    }

    @Test
    void getPayloadReturnsCopy() {
        byte[] original = {0x0A, 0x0B, 0x0C};
        FramePacket packet = new FramePacket(FrameType.P_FRAME, 0L, original);

        byte[] copy = packet.getPayload();
        copy[0] = 0x00; 

        
        assertEquals(0x0A, packet.getPayload()[0]);
    }

    @Test
    void nullTypeThrows() {
        assertThrows(NullPointerException.class,
                () -> new FramePacket(null, 0L, new byte[0]));
    }

    @Test
    void nullPayloadThrows() {
        assertThrows(NullPointerException.class,
                () -> new FramePacket(FrameType.I_FRAME, 0L, null));
    }

    @Test
    void emptyPayloadIsValid() {
        FramePacket packet = new FramePacket(FrameType.SPS, 0L, new byte[0]);
        assertEquals(0, packet.getPayloadLength());
    }

    @Test
    void toStringIsReadable() {
        FramePacket packet = new FramePacket(FrameType.I_FRAME, 100L, new byte[1024]);
        String str = packet.toString();

        assertTrue(str.contains("I_FRAME"));
        assertTrue(str.contains("100"));
        assertTrue(str.contains("1024"));
    }
}