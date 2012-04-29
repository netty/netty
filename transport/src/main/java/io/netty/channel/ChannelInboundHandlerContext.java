package io.netty.channel;

public interface ChannelInboundHandlerContext<I> extends ChannelHandlerContext {
    @Override
    ChannelInboundHandler<I> handler();
    ChannelBufferHolder<I> in();
}
