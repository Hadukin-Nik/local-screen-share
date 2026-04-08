package ru.hniApplications.testApplication;

import java.util.Objects;


public final class FramePacket {
    public static final short MAGIC = 0x5C01;

    public static final int HEADER_SIZE = 2 + 1 + 8 + 4; 

    private final FrameType type;
    private final long timestamp;
    private final byte[] payload;

    public FramePacket(FrameType type, long timestamp, byte[] payload) {
        this.type = Objects.requireNonNull(type, "Frame type must not be null");
        this.timestamp = timestamp;
        this.payload = Objects.requireNonNull(payload, "Payload must not be null");
    }

    public FrameType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public byte[] getPayloadDirect() {
        return payload;
    }

    public int getPayloadLength() {
        return payload.length;
    }

    @Override
    public String toString() {
        return "FramePacket{type=" + type
                + ", timestamp=" + timestamp
                + ", payloadSize=" + payload.length + "}";
    }
}