package io.netty.channel.uring;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assume.assumeTrue;

public class PollRemoveTest {

    @BeforeClass
    public static void loadJNI() {
        assumeTrue(IOUring.isAvailable());
    }

    @Sharable
    class EchoUringServerHandler extends ChannelInboundHandlerAdapter {

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
            cause.printStackTrace();
            ctx.close();
        }

    }

    void io_uring_test() throws Exception {
        Class clazz;
        final EventLoopGroup bossGroup = new IOUringEventLoopGroup(1);
        final EventLoopGroup workerGroup = new IOUringEventLoopGroup(1);
        clazz = IOUringServerSocketChannel.class;

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
         .channel(clazz)
         .handler(new LoggingHandler(LogLevel.TRACE))
         .childHandler(new ChannelInitializer<SocketChannel>() {
             @Override
             public void initChannel(SocketChannel ch) throws Exception {
                 ChannelPipeline p = ch.pipeline();

                 p.addLast(new EchoUringServerHandler());
             }
         });

        Channel sc = b.bind(2020).sync().channel();
        Thread.sleep(1500);

        //close ServerChannel
        sc.close().sync();

    }

    @Test
    public void test() throws Exception {

        io_uring_test();

        System.out.println("io_uring --------------------------------");

        io_uring_test();

    }
}

