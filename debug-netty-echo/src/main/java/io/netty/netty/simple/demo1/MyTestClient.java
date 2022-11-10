package io.netty.netty.simple.demo1;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:12 09-11-2022
 */
public class MyTestClient {
    public static void main(String[] args) throws Exception {

        EventLoopGroup worker = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();

        try {
            bootstrap.group(worker)
                    .channel(NioSocketChannel.class)
                    .handler(new MyTestClientInitializer());

            ChannelFuture future = bootstrap.connect("localhost", 8090).sync();
            future.channel().closeFuture().sync();
        } finally {
            worker.shutdownGracefully();
        }

    }
}
