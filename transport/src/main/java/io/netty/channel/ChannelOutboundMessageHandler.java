package io.netty.channel;

import io.netty.buffer.MessageBuf;

public interface ChannelOutboundMessageHandler<I> extends ChannelOutboundHandler {
    @Override
    MessageBuf<I> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception;
}
