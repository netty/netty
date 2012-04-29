package io.netty.channel;


public interface ChannelOutboundHandlerContext<O> extends ChannelHandlerContext {
    @Override
    ChannelOutboundHandler<O> handler();
    ChannelBufferHolder<O> out();
}
