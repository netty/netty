package io.netty.netty.protocaltcp;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 11.12.2021
 */
public class MyClientInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new MyMessageEncoder()); // 加入编码器
        pipeline.addLast(new MyMessageDecoder()); // 加入解码器
        pipeline.addLast(new MyClientHandler());
    }
}
