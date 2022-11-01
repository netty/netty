package io.netty.netty.chat.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.netty.chat.processor.MsgProcessor;
import io.netty.netty.chat.protocol.IMMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * 自定义协议的支持：用于处理 Java 控制台发过来的 JavaObject 消息体
 *
 * @author lxcecho 909231497@qq.com
 * @since 20:16 29-10-2022
 */
@Slf4j
public class TerminalServerHandler extends SimpleChannelInboundHandler<IMMessage> {

    private MsgProcessor processor = new MsgProcessor();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) throws Exception {
        processor.sendMsg(ctx.channel(), msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.info("SocketClient: 与客户端断开连接，" + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}
