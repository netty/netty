package io.netty.channel;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.aio.AioEventLoop;
import io.netty.channel.socket.aio.AioServerSocketChannel;
import io.netty.channel.socket.aio.AioSocketChannel;

public class AsyncTransportTest {

    public static void main(String args[]) {
        AioEventLoop loop = new AioEventLoop();
     // Configure a test server
        ServerBootstrap sb = new ServerBootstrap();
        sb.eventLoop(loop, loop)
          .channel(new AioServerSocketChannel())
          .localAddress(new InetSocketAddress(9191))
          .childHandler(new ChannelInitializer<AioSocketChannel>() {
              @Override
              public void initChannel(AioSocketChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundByteHandlerAdapter() {
                    
                    @Override
                    public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                        ctx.write(in.slice());
                    }
                });
              }
          });
        ChannelFuture future = sb.bind().awaitUninterruptibly();
        if (!future.isSuccess()) {
            future.cause().printStackTrace();
        }
        future.channel().closeFuture().awaitUninterruptibly();
    }
}
