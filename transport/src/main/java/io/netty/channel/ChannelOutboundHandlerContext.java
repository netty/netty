package io.netty.channel;


public interface ChannelOutboundHandlerContext<O> extends ChannelHandlerContext {
    ChannelBufferHolder<O> out();
}
