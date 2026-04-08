package ru.hniApplications.testApplication;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;


public final class PacketSerializer {

    private PacketSerializer() {
        
    }

    
    public static byte[] serialize(FramePacket packet) {
        Objects.requireNonNull(packet, "Packet must not be null");

        byte[] payload = packet.getPayloadDirect();
        int totalLength = FramePacket.HEADER_SIZE + payload.length;

        
        if (totalLength < FramePacket.HEADER_SIZE) {
            throw new ProtocolException(
                    "Payload too large: " + payload.length + " bytes");
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putShort(FramePacket.MAGIC);           
        buffer.put(packet.getType().getCode());        
        buffer.putLong(packet.getTimestamp());          
        buffer.putInt(payload.length);                  
        buffer.put(payload);                            

        return buffer.array();
    }
}