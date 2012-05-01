package io.netty.channel;

public interface ChannelInboundHandlerContext<I> extends ChannelHandlerContext {
    ChannelBufferHolder<I> in();
}
