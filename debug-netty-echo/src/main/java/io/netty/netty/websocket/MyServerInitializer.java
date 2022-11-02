package io.netty.netty.websocket;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @author lxcecho 909231497@qq.com
 * @since 12.12.2021
 */
public class MyServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        // 因为基于 http 协议，使用 http 的编码和解码器
        pipeline.addLast(new HttpServerCodec());
        // 以 块 方式写，添加 ChunkedWriteHandler 处理器
        pipeline.addLast(new ChunkedWriteHandler());

        /**
         * 1.http 数据在传输过程中是分段，HttpObjectAggregator 就是可以将多个段聚合
         * 2.这就是为什么，当浏览器发送大量数据时，就会发出多次 http 请求
         */
        pipeline.addLast(new HttpObjectAggregator(8192));

        /**
         * 1.对应 websocket，它的数据是以 帧（frame) 形式传递
         * 2.可以看到 WebSocketFrame 下面有六个子类
         * 3.浏览器请求时 ws://localhost:7000/hello 表示请求的 uri
         * 4.WebSocketServerProtocolHandler 核心功能是将 http 协议升级为 ws 协议，保持长连接
         * 5.是通过一个 状态码 101
         */
        pipeline.addLast(new WebSocketServerProtocolHandler("/hello"));
//        pipeline.addLast(new WebSocketServerProtocolHandler("/hello2"));

        // 自定义的 handler，处理业务逻辑
        pipeline.addLast(new MyTextWebSocketFrameHandler());

    }
}
