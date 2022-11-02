package io.netty.netty.inoroutboundhandler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 12.12.2021
 */
public class MyServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        // 下一个断点调试
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new MyByteToLongDecoder());
//        pipeline.addLast(new MyByteToLongDecoder2());

        // 出站的 handler 进行编码
        pipeline.addLast(new MyLongToByteEncoder());
        // 自定义的 handler 处理业务逻辑
        pipeline.addLast(new MyServerHandler());

        System.out.println("cccccccccc");
    }
}
