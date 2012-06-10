package io.netty.channel;

import io.netty.buffer.ByteBuf;

public interface ChannelOutboundByteHandler extends ChannelOutboundHandler {
    @Override
    ByteBuf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception;
}
