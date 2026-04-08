import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.hniApplications.testApplication.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PacketSerializationTest {

    

    @Test
    void roundTripPreservesAllFields() {
        byte[] payload = {0x48, 0x26, 0x34, (byte) 0xFF, 0x00};
        FramePacket original = new FramePacket(FrameType.I_FRAME, 1700000000000L, payload);

        byte[] serialized = PacketSerializer.serialize(original);
        FramePacket restored = PacketDeserializer.deserialize(serialized);

        assertEquals(original.getType(), restored.getType());
        assertEquals(original.getTimestamp(), restored.getTimestamp());
        assertEquals(original.getPayloadLength(), restored.getPayloadLength());
        assertArrayEquals(original.getPayload(), restored.getPayload());
    }

    @ParameterizedTest
    @EnumSource(FrameType.class)
    void roundTripForAllFrameTypes(FrameType type) {
        byte[] payload = {0x01, 0x02};
        FramePacket original = new FramePacket(type, 42L, payload);

        byte[] serialized = PacketSerializer.serialize(original);
        FramePacket restored = PacketDeserializer.deserialize(serialized);

        assertEquals(type, restored.getType());
    }

    

    @Test
    void emptyPayload() {
        FramePacket original = new FramePacket(FrameType.SPS, 0L, new byte[0]);

        byte[] serialized = PacketSerializer.serialize(original);

        
        assertEquals(FramePacket.HEADER_SIZE, serialized.length);

        FramePacket restored = PacketDeserializer.deserialize(serialized);
        assertEquals(0, restored.getPayloadLength());
    }

    @Test
    void largePayload() {
        byte[] payload = new byte[1024 * 1024]; 
        new Random(12345).nextBytes(payload); 

        FramePacket original = new FramePacket(FrameType.I_FRAME, Long.MAX_VALUE, payload);

        byte[] serialized = PacketSerializer.serialize(original);
        FramePacket restored = PacketDeserializer.deserialize(serialized);

        assertArrayEquals(payload, restored.getPayload());
    }

    @Test
    void singleBytePayload() {
        byte[] payload = {0x42};
        FramePacket original = new FramePacket(FrameType.PPS, 1L, payload);

        byte[] serialized = PacketSerializer.serialize(original);
        FramePacket restored = PacketDeserializer.deserialize(serialized);

        assertArrayEquals(payload, restored.getPayload());
    }

    

    @Test
    void zeroTimestamp() {
        FramePacket original = new FramePacket(FrameType.P_FRAME, 0L, new byte[]{0x01});

        byte[] serialized = PacketSerializer.serialize(original);
        FramePacket restored = PacketDeserializer.deserialize(serialized);

        assertEquals(0L, restored.getTimestamp());
    }

    @Test
    void maxTimestamp() {
        FramePacket original = new FramePacket(FrameType.P_FRAME, Long.MAX_VALUE, new byte[]{0x01});

        byte[] serialized = PacketSerializer.serialize(original);
        FramePacket restored = PacketDeserializer.deserialize(serialized);

        assertEquals(Long.MAX_VALUE, restored.getTimestamp());
    }

    

    @Test
    void binaryFormatIsCorrect() {
        byte[] payload = {0x0A, 0x0B};
        FramePacket packet = new FramePacket(FrameType.I_FRAME, 1000L, payload);

        byte[] data = PacketSerializer.serialize(packet);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

        assertEquals(FramePacket.MAGIC, buf.getShort());         
        assertEquals(FrameType.I_FRAME.getCode(), buf.get());    
        assertEquals(1000L, buf.getLong());                        
        assertEquals(2, buf.getInt());                             
        assertEquals(0x0A, buf.get());                             
        assertEquals(0x0B, buf.get());                             
        assertFalse(buf.hasRemaining());                           
    }

    @Test
    void headerSizeConstant() {
        assertEquals(15, FramePacket.HEADER_SIZE);
    }

    

    @Test
    void deserializeNullThrows() {
        assertThrows(NullPointerException.class,
                () -> PacketDeserializer.deserialize(null));
    }

    @Test
    void deserializeEmptyArrayThrows() {
        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> PacketDeserializer.deserialize(new byte[0]));

        assertTrue(ex.getMessage().contains("too short"));
    }

    @Test
    void deserializeTooShortThrows() {
        byte[] tooShort = new byte[FramePacket.HEADER_SIZE - 1];

        assertThrows(ProtocolException.class,
                () -> PacketDeserializer.deserialize(tooShort));
    }

    @Test
    void wrongMagicThrows() {
        byte[] payload = {0x01};
        FramePacket packet = new FramePacket(FrameType.I_FRAME, 0L, payload);
        byte[] data = PacketSerializer.serialize(packet);

        
        data[0] = 0x00;
        data[1] = 0x00;

        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> PacketDeserializer.deserialize(data));

        assertTrue(ex.getMessage().contains("magic"));
    }

    @Test
    void unknownFrameTypeThrows() {
        byte[] payload = {0x01};
        FramePacket packet = new FramePacket(FrameType.I_FRAME, 0L, payload);
        byte[] data = PacketSerializer.serialize(packet);

        
        data[2] = (byte) 0xFF;

        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> PacketDeserializer.deserialize(data));

        assertTrue(ex.getMessage().contains("Unknown frame type"));
    }

    @Test
    void truncatedPayloadThrows() {
        byte[] payload = {0x01, 0x02, 0x03, 0x04, 0x05};
        FramePacket packet = new FramePacket(FrameType.I_FRAME, 0L, payload);
        byte[] data = PacketSerializer.serialize(packet);

        
        byte[] truncated = Arrays.copyOf(data, data.length - 2);

        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> PacketDeserializer.deserialize(truncated));

        assertTrue(ex.getMessage().contains("truncated"));
    }

    @Test
    void negativePayloadLengthThrows() {
        byte[] data = new byte[FramePacket.HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        buf.putShort(FramePacket.MAGIC);
        buf.put(FrameType.I_FRAME.getCode());
        buf.putLong(0L);
        buf.putInt(-1); 

        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> PacketDeserializer.deserialize(data));

        assertTrue(ex.getMessage().contains("Negative"));
    }

    @Test
    void excessivePayloadLengthThrows() {
        byte[] data = new byte[FramePacket.HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        buf.putShort(FramePacket.MAGIC);
        buf.put(FrameType.I_FRAME.getCode());
        buf.putLong(0L);
        buf.putInt(PacketDeserializer.MAX_PAYLOAD_SIZE + 1);

        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> PacketDeserializer.deserialize(data));

        assertTrue(ex.getMessage().contains("exceeds maximum"));
    }

    @Test
    void serializeNullThrows() {
        assertThrows(NullPointerException.class,
                () -> PacketSerializer.serialize(null));
    }

    

    @Test
    void multiplePacketsAreIndependent() {
        FramePacket sps = new FramePacket(FrameType.SPS, 100L, new byte[]{0x67});
        FramePacket pps = new FramePacket(FrameType.PPS, 100L, new byte[]{0x68});
        FramePacket iframe = new FramePacket(FrameType.I_FRAME, 100L, new byte[]{0x65, 0x01});
        FramePacket pframe = new FramePacket(FrameType.P_FRAME, 133L, new byte[]{0x41, 0x02});

        
        byte[] d1 = PacketSerializer.serialize(sps);
        byte[] d2 = PacketSerializer.serialize(pps);
        byte[] d3 = PacketSerializer.serialize(iframe);
        byte[] d4 = PacketSerializer.serialize(pframe);

        
        FramePacket r4 = PacketDeserializer.deserialize(d4);
        FramePacket r3 = PacketDeserializer.deserialize(d3);
        FramePacket r2 = PacketDeserializer.deserialize(d2);
        FramePacket r1 = PacketDeserializer.deserialize(d1);

        assertEquals(FrameType.SPS, r1.getType());
        assertEquals(FrameType.PPS, r2.getType());
        assertEquals(FrameType.I_FRAME, r3.getType());
        assertEquals(FrameType.P_FRAME, r4.getType());
        assertEquals(133L, r4.getTimestamp());
    }

    

    @Test
    void frameTypeFromCodeValid() {
        assertEquals(FrameType.I_FRAME, FrameType.fromCode((byte) 0x01));
        assertEquals(FrameType.P_FRAME, FrameType.fromCode((byte) 0x02));
        assertEquals(FrameType.SPS, FrameType.fromCode((byte) 0x03));
        assertEquals(FrameType.PPS, FrameType.fromCode((byte) 0x04));
    }

    @Test
    void frameTypeFromCodeInvalid() {
        assertThrows(ProtocolException.class, () -> FrameType.fromCode((byte) 0x00));
        assertThrows(ProtocolException.class, () -> FrameType.fromCode((byte) 0x05));
        assertThrows(ProtocolException.class, () -> FrameType.fromCode((byte) 0xFF));
    }
}