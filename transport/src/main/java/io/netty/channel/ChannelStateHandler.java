package io.netty.channel;

public interface ChannelStateHandler extends ChannelHandler {
    void channelRegistered(ChannelHandlerContext ctx) throws Exception;
    void channelUnregistered(ChannelHandlerContext ctx) throws Exception;

    void channelActive(ChannelHandlerContext ctx) throws Exception;
    void channelInactive(ChannelHandlerContext ctx) throws Exception;

    void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception;
}
