package io.netty.channel;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio2.AsyncEventLoop;
import io.netty.channel.socket.nio2.AsyncServerSocketChannel;
import io.netty.channel.socket.nio2.AsyncSocketChannel;

public class AsyncTransportTest {

    public static void main(String args[]) {
        AsyncEventLoop loop = new AsyncEventLoop();
     // Configure a test server
        ServerBootstrap sb = new ServerBootstrap();
        sb.eventLoop(loop, loop)
          .channel(new AsyncServerSocketChannel())
          .localAddress(new InetSocketAddress(9191))
          .childHandler(new ChannelInitializer<AsyncSocketChannel>() {
              @Override
              public void initChannel(AsyncSocketChannel ch) throws Exception {
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
