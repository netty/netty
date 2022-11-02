package io.netty.netty.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * @author lxcecho 909231497@qq.com
 * @since 08.10.2021
 */
public class TestServerInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        // 向管道加入处理器
        // 得到管道
        ChannelPipeline pipeline = socketChannel.pipeline();

        // 加入一个 netty 提供的 httpServerCodec codec ==> [coder - decoder]
        // HttpServerCodec 说明：
        // 1 HttpServerCodec 是 Netty 提供的处理 Http 的编解码器
        pipeline.addLast("MyHttpServerCodec", new HttpServerCodec());
        // 2 增加一个自定义的 handler
        pipeline.addLast("MyTestHttpServerHandler", new TestHttpServerHandler());

        System.out.println("ok...");
    }
}