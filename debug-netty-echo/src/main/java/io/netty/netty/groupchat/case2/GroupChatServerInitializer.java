package io.netty.netty.groupchat.case2;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

/**
 * @author lxcecho 909231497@qq.com
 * @since 11.12.2021
 */
public class GroupChatServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        // get pipeline
        ChannelPipeline pipeline = socketChannel.pipeline();
        // Add a decoder to pipeline
        pipeline.addLast("decoder", new StringDecoder());
        // Add a encoder to pipeline
        pipeline.addLast("encoder", new StringEncoder());

        // Add custom business handler
        pipeline.addLast(new GroupChatServerHandler());
    }
}
