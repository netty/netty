package io.netty.channel;

import java.net.SocketAddress;

public interface ChannelOperationHandler extends ChannelHandler {
    void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelFuture future) throws Exception;
    void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception;
    void disconnect(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;
    void close(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;
    void deregister(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;
    void flush(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;
}
