package io.netty.channel;

import java.net.SocketAddress;
import java.util.Queue;

public interface ChannelWriter<T> extends ChannelHandler {
    void bind(ChannelWriterContext<T> ctx, SocketAddress localAddress, ChannelFuture future) throws Exception;
    void connect(ChannelWriterContext<T> ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception;
    void disconnect(ChannelWriterContext<T> ctx, ChannelFuture future) throws Exception;
    void close(ChannelWriterContext<T> ctx, ChannelFuture future) throws Exception;
    void deregister(ChannelWriterContext<T> ctx, ChannelFuture future) throws Exception;

    Queue<T> newSendBuffer(ChannelWriterContext<T> ctx) throws Exception;
    void sendBufferUpdated(ChannelWriterContext<T> ctx) throws Exception;
    void sendBufferClosed(ChannelWriterContext<T> ctx) throws Exception;
}
