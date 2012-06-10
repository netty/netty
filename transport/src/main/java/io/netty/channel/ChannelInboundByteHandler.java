package io.netty.channel;

import io.netty.buffer.ByteBuf;

public interface ChannelInboundByteHandler extends ChannelInboundHandler {
    @Override
    ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception;
}
