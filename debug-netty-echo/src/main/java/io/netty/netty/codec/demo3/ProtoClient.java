package io.netty.netty.codec.demo3;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:04 10-11-2022
 */
public class ProtoClient {

    public static void main(String[] args) throws Exception {
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();

            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ProtoClientInitializer());

            ChannelFuture cf = bootstrap.connect("localhost", 8090).sync();
            cf.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}
