package io.netty.channel;

import io.netty.util.internal.QueueFactory;

import java.net.SocketAddress;
import java.util.Queue;

public class ChannelWriterAdapter<T> implements ChannelWriter<T> {
    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void bind(ChannelWriterContext<T> ctx, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.next().bind(localAddress, future);
    }

    @Override
    public void connect(ChannelWriterContext<T> ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.next().connect(remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelWriterContext<T> ctx, ChannelFuture future) throws Exception {
        ctx.next().disconnect(future);
    }

    @Override
    public void close(ChannelWriterContext<T> ctx, ChannelFuture future) throws Exception {
        ctx.next().close(future);
    }

    @Override
    public void deregister(ChannelWriterContext<T> ctx, ChannelFuture future) throws Exception {
        ctx.next().deregister(future);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Queue<T> newSendBuffer(ChannelWriterContext<T> ctx) throws Exception {
        return (Queue<T>) QueueFactory.createQueue(Object.class);
    }

    @Override
    public void sendBufferUpdated(ChannelWriterContext<T> ctx) throws Exception {
        ctx.out().transferTo(ctx.next().out());
    }

    @Override
    public void sendBufferClosed(ChannelWriterContext<T> ctx) throws Exception {
        ctx.next().out().close();
    }
}
