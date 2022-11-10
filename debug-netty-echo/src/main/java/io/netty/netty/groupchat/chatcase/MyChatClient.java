package io.netty.netty.groupchat.chatcase;

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
 * @since 23:12 09-11-2022
 */
public class MyChatClient {
    public static void main(String[] args) throws Exception {

        EventLoopGroup worker = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();

        try {
            bootstrap.group(worker)
                    .channel(NioSocketChannel.class)
                    .handler(new MyChatClientInitializer());

            ChannelFuture future = bootstrap.connect("localhost", 8090).sync();

            Channel channel = future.channel();

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            for (; ; ) {
                channel.writeAndFlush(br.readLine() + "\r\n");
            }

        } finally {
            worker.shutdownGracefully();
        }

    }
}
