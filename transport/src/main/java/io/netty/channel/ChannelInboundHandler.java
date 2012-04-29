package io.netty.channel;


public interface ChannelInboundHandler<T> extends ChannelHandler {

    void channelRegistered(ChannelInboundHandlerContext<T> ctx) throws Exception;
    void channelUnregistered(ChannelInboundHandlerContext<T> ctx) throws Exception;

    void channelActive(ChannelInboundHandlerContext<T> ctx) throws Exception;
    void channelInactive(ChannelInboundHandlerContext<T> ctx) throws Exception;

    void exceptionCaught(ChannelInboundHandlerContext<T> ctx, Throwable cause) throws Exception;
    void userEventTriggered(ChannelInboundHandlerContext<T> ctx, Object evt) throws Exception;

    ChannelBufferHolder<T> newInboundBuffer(ChannelInboundHandlerContext<T> ctx) throws Exception;
    void inboundBufferUpdated(ChannelInboundHandlerContext<T> ctx) throws Exception;
}
