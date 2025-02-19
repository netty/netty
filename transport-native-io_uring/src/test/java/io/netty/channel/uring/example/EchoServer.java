package io.netty.channel.uring.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.uring.IoUringBufferRingConfig;
import io.netty.channel.uring.IoUringChannelOption;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringIoHandlerConfig;
import io.netty.channel.uring.IoUringServerSocketChannel;

public class EchoServer {
    private static final int PORT = Integer.parseInt(System.getProperty("port", "8081"));

    public static void main(String []args) {
        boolean useNio = false;
        IoHandlerFactory ioHandlerFactory;
        Class<? extends ServerChannel> serverChannelClass;
        if (useNio) {
            ioHandlerFactory = NioIoHandler.newFactory();
            serverChannelClass = NioServerSocketChannel.class;
        } else {
            ioHandlerFactory = IoUringIoHandler.newFactory(
                    new IoUringIoHandlerConfig()
                            .setBufferRingConfig((channel, guessedSize) -> (short) 0,
                                    new IoUringBufferRingConfig((short) 0, (short) 16,
                                            64 * 1024, false, ByteBufAllocator.DEFAULT)));
            serverChannelClass = IoUringServerSocketChannel.class;
        }
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, ioHandlerFactory);
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(IoUringChannelOption.USE_IO_URING_BUFFER_GROUP, true)
                    .channel(serverChannelClass)
                    .childHandler(serverHandler);

            // Start the server.
            ChannelFuture f = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // Shut down all event loops to terminate all threads.
            group.shutdownGracefully();
        }
    }

    private static final class EchoServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ctx.write(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Close the connection when an exception is raised.
            ctx.close();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            // Ensure we are not writing to fast by stop reading if we can not flush out data fast enough.
            if (ctx.channel().isWritable()) {
                ctx.channel().config().setAutoRead(true);
            } else {
                ctx.flush();
                if (!ctx.channel().isWritable()) {
                    ctx.channel().config().setAutoRead(false);
                }
            }
        }
    }
}
