package ru.hniApplications.testApplication.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import ru.hniApplications.testApplication.FramePacket;
import ru.hniApplications.testApplication.PacketDeserializer;

import java.util.List;


public class FramePacketDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        byte[] data = new byte[msg.readableBytes()];
        msg.readBytes(data);

        FramePacket packet = PacketDeserializer.deserialize(data);
        out.add(packet);
    }
}