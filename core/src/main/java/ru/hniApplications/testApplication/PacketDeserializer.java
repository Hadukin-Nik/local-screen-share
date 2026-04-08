package ru.hniApplications.testApplication;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;


public final class PacketDeserializer {
    public static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024; 

    private PacketDeserializer() {}

    
    public static FramePacket deserialize(byte[] data) {
        Objects.requireNonNull(data, "Data must not be null");

        if (data.length < FramePacket.HEADER_SIZE) {
            throw new ProtocolException(
                    "Data too short for header: expected at least "
                            + FramePacket.HEADER_SIZE + " bytes, got " + data.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        short magic = buffer.getShort();
        if (magic != FramePacket.MAGIC) {
            throw new ProtocolException(
                    "Invalid magic number: expected 0x"
                            + String.format("%04X", FramePacket.MAGIC & 0xFFFF)
                            + ", got 0x"
                            + String.format("%04X", magic & 0xFFFF));
        }

        byte typeCode = buffer.get();
        FrameType type = FrameType.fromCode(typeCode); 

        long timestamp = buffer.getLong();

        int payloadLength = buffer.getInt();

        if (payloadLength < 0) {
            throw new ProtocolException(
                    "Negative payload length: " + payloadLength);
        }

        if (payloadLength > MAX_PAYLOAD_SIZE) {
            throw new ProtocolException(
                    "Payload length exceeds maximum: "
                            + payloadLength + " > " + MAX_PAYLOAD_SIZE);
        }

        int remaining = buffer.remaining();
        if (remaining < payloadLength) {
            throw new ProtocolException(
                    "Payload truncated: header declares "
                            + payloadLength + " bytes, but only "
                            + remaining + " bytes available");
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        return new FramePacket(type, timestamp, payload);
    }
}