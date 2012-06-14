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
import io.netty.channel.socket.nio2.AsyncSocketchannel;
import io.netty.util.CharsetUtil;

public class AsyncTransportTest {

    public static void main(String args[]) {
     // Configure a test server
        ServerBootstrap sb = new ServerBootstrap();
        sb.eventLoop(new AsyncEventLoop(), new AsyncEventLoop())
          .channel(new AsyncServerSocketChannel())
          .localAddress(new InetSocketAddress(9999))
          .childHandler(new ChannelInitializer<AsyncSocketchannel>() {
              @Override
              public void initChannel(AsyncSocketchannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundByteHandlerAdapter() {
                    
                    @Override
                    public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                        System.out.print(in.toString(CharsetUtil.US_ASCII));
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
