package io.netty.channel;

import java.net.SocketAddress;

public interface ChannelHandlerInvoker {
    ChannelBufferHolder<Object> nextIn();
    ChannelBufferHolder<Object> nextOut();

    void fireChannelRegistered();
    void fireChannelUnregistered();
    void fireChannelActive();
    void fireChannelInactive();
    void fireExceptionCaught(Throwable cause);
    void fireUserEventTriggered(Object event);
    void fireInboundBufferUpdated();

    void bind(SocketAddress localAddress, ChannelFuture future);
    void connect(SocketAddress remoteAddress, ChannelFuture future);
    void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future);
    void disconnect(ChannelFuture future);
    void close(ChannelFuture future);
    void deregister(ChannelFuture future);
    void flush(ChannelFuture future);

    void write(Object message, ChannelFuture future);
}
