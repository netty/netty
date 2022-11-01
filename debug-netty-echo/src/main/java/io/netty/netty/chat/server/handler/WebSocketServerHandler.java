package io.netty.netty.chat.server.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.netty.chat.processor.MsgProcessor;
import lombok.extern.slf4j.Slf4j;

/**
 * 对 WebSocket 协议的支持：处理浏览器发送过来的 WebSocket 请求
 *
 * @author lxcecho 909231497@qq.com
 * @since 20:16 29-10-2022
 */
@Slf4j
public class WebSocketServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private MsgProcessor processor = new MsgProcessor();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        processor.sendMsg(ctx.channel(), String.valueOf(msg));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel channel = ctx.channel();
        String addr = processor.getAddress(channel);
        log.info("WebSocketClient: " + addr + " 异常");
        cause.printStackTrace();
        ctx.close();
    }

}
