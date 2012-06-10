package io.netty.channel;

import io.netty.buffer.MessageBuf;

public interface ChannelInboundMessageHandler<I> extends ChannelInboundHandler {
    @Override
    MessageBuf<I> newInboundBuffer(ChannelHandlerContext ctx) throws Exception;
}
