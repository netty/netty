package io.netty.channel;

import io.netty.util.internal.QueueFactory;

import java.net.SocketAddress;
import java.util.Queue;

public class ChannelHandlerAdapter<I, O> implements ChannelReader<I>, ChannelWriter<O> {
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
    public void channelRegistered(ChannelReaderContext<I> ctx) throws Exception {
        ctx.next().channelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelReaderContext<I> ctx) throws Exception {
        ctx.next().channelUnregistered();
    }

    @Override
    public void channelActive(ChannelReaderContext<I> ctx) throws Exception {
        ctx.next().channelActive();
    }

    @Override
    public void channelInactive(ChannelReaderContext<I> ctx) throws Exception {
        ctx.next().channelInactive();
    }

    @Override
    public void exceptionCaught(ChannelReaderContext<I> ctx, Throwable cause) throws Exception {
        ctx.next().exceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelReaderContext<I> ctx, Object evt) throws Exception {
        ctx.next().userEventTriggered(evt);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Queue<I> newReceiveBuffer(ChannelReaderContext<I> ctx) throws Exception {
        return (Queue<I>) QueueFactory.createQueue(Object.class);
    }

    @Override
    public void receiveBufferUpdated(ChannelReaderContext<I> ctx) throws Exception {
        ctx.in().transferTo(ctx.next().in());
    }

    @Override
    public void receiveBufferClosed(ChannelReaderContext<I> ctx) throws Exception {
        ctx.next().in().close();
    }

    @Override
    public void bind(ChannelWriterContext<O> ctx, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.next().bind(localAddress, future);
    }

    @Override
    public void connect(ChannelWriterContext<O> ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.next().connect(remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelWriterContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.next().disconnect(future);
    }

    @Override
    public void close(ChannelWriterContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.next().close(future);
    }

    @Override
    public void deregister(ChannelWriterContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.next().deregister(future);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Queue<O> newSendBuffer(ChannelWriterContext<O> ctx) throws Exception {
        return (Queue<O>) QueueFactory.createQueue(Object.class);
    }

    @Override
    public void sendBufferUpdated(ChannelWriterContext<O> ctx) throws Exception {
        ctx.out().transferTo(ctx.next().out());
    }

    @Override
    public void sendBufferClosed(ChannelWriterContext<O> ctx) throws Exception {
        ctx.next().out().close();
    }
}
