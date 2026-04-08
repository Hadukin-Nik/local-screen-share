package ru.hniApplications.testApplication.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.GlobalEventExecutor;
import ru.hniApplications.testApplication.FramePacket;

import java.net.InetSocketAddress;


public class RelayServer {

    private static final int MAX_FRAME_LENGTH = 16 * 1024 * 1024; 

    
    private static final int LENGTH_FIELD_LENGTH = 4;

    private int port;
    private FrameListener frameListener;
    private ConnectionListener connectionListener;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    
    private final ChannelGroup clients =
            new DefaultChannelGroup("clients", GlobalEventExecutor.INSTANCE);

    
    public RelayServer(int port, FrameListener frameListener, ConnectionListener connectionListener) {
        this.port = port;
        this.frameListener = frameListener;
        this.connectionListener = connectionListener;
    }

    
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)  
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
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

                
                p.addLast("handler", new ServerHandler());
            }
        });

        
        serverChannel = bootstrap.bind(port).sync().channel();
    }

    
    public void stop() {
        if (clients != null) {
            clients.close();
        }
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    
    public void broadcast(FramePacket packet) {
        clients.writeAndFlush(packet);
    }

    
    public int getLocalPort() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    
    public int getClientCount() {
        return clients.size();
    }

    
    private class ServerHandler extends SimpleChannelInboundHandler<FramePacket> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            
            clients.add(ctx.channel());
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