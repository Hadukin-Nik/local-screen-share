package ru.hniApplications.testApplication.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import ru.hniApplications.testApplication.FramePacket;


public class RelayClient {

    private static final int MAX_FRAME_LENGTH = 16 * 1024 * 1024;
    private static final int LENGTH_FIELD_LENGTH = 4;

    private final String host;
    private final int port;
    private final FrameListener frameListener;
    private final ConnectionListener connectionListener;

    private EventLoopGroup group;
    private Channel channel;

    
    public RelayClient(String host, int port,
                       FrameListener frameListener,
                       ConnectionListener connectionListener) {
        this.host = host;
        this.port = port;
        this.frameListener = frameListener;
        this.connectionListener = connectionListener;
    }

    
    public void connect() throws InterruptedException {
        group = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();

                        
                        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                                MAX_FRAME_LENGTH,
                                0,
                                LENGTH_FIELD_LENGTH,
                                0,
                                LENGTH_FIELD_LENGTH
                        ));
                        p.addLast("packetDecoder", new FramePacketDecoder());

                        p.addLast("framePrepender", new LengthFieldPrepender(LENGTH_FIELD_LENGTH));
                        p.addLast("packetEncoder", new FramePacketEncoder());

                        p.addLast("handler", new ClientHandler());
                    }
                });

        channel = bootstrap.connect(host, port).sync().channel();
    }

    
    public void send(FramePacket packet) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(packet);
        }
    }

    
    public void disconnect() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    private class ClientHandler extends SimpleChannelInboundHandler<FramePacket> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            if (connectionListener != null) {
                connectionListener.onConnected(
                        ctx.channel().remoteAddress().toString());
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (connectionListener != null) {
                connectionListener.onDisconnected(
                        ctx.channel().remoteAddress().toString(), null);
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FramePacket packet) {
            if (frameListener != null) {
                frameListener.onFrame(packet);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (connectionListener != null) {
                connectionListener.onDisconnected(
                        ctx.channel().remoteAddress().toString(), cause);
            }
            ctx.close();
        }
    }
}