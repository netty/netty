package io.netty.channel;

import java.net.SocketAddress;

public interface ChannelOutboundHandler<T> extends ChannelHandler {
    ChannelBufferHolder<T> newOutboundBuffer(ChannelOutboundHandlerContext<T> ctx) throws Exception;

    void bind(ChannelOutboundHandlerContext<T> ctx, SocketAddress localAddress, ChannelFuture future) throws Exception;
    void connect(ChannelOutboundHandlerContext<T> ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception;
    void disconnect(ChannelOutboundHandlerContext<T> ctx, ChannelFuture future) throws Exception;
    void close(ChannelOutboundHandlerContext<T> ctx, ChannelFuture future) throws Exception;
    void deregister(ChannelOutboundHandlerContext<T> ctx, ChannelFuture future) throws Exception;
    void flush(ChannelOutboundHandlerContext<T> ctx, ChannelFuture future) throws Exception;
}
