package ru.hniApplications.testApplication.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import ru.hniApplications.testApplication.FramePacket;
import ru.hniApplications.testApplication.PacketSerializer;


public class FramePacketEncoder extends MessageToByteEncoder<FramePacket> {

    @Override
    protected void encode(ChannelHandlerContext ctx, FramePacket packet, ByteBuf out) {
        byte[] data = PacketSerializer.serialize(packet);
        out.writeBytes(data);
    }
}