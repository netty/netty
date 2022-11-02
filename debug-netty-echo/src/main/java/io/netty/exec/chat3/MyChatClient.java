package io.netty.exec.chat3;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author lxcecho 909231497@qq.com
 * @since 30.03.2022
 */
public class MyChatClient {
    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new MyChatClientInitializer());

            ChannelFuture channelFuture = bootstrap.connect("localhost", 40000).sync();
            Channel channel = channelFuture.channel();

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            for (; ; ) {
                channel.writeAndFlush(reader.readLine() + "\r\n");
            }
        } finally {
            group.shutdownGracefully();
        }

    }
}
