package io.netty.channel;

import io.netty.util.internal.QueueFactory;

import java.util.Queue;

public class ChannelReaderAdapter<T> implements ChannelReader<T> {
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
    public void channelRegistered(ChannelReaderContext<T> ctx) throws Exception {
        ctx.next().channelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelReaderContext<T> ctx) throws Exception {
        ctx.next().channelUnregistered();
    }

    @Override
    public void channelActive(ChannelReaderContext<T> ctx) throws Exception {
        ctx.next().channelActive();
    }

    @Override
    public void channelInactive(ChannelReaderContext<T> ctx) throws Exception {
        ctx.next().channelInactive();
    }

    @Override
    public void exceptionCaught(ChannelReaderContext<T> ctx, Throwable cause) throws Exception {
        ctx.next().exceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelReaderContext<T> ctx, Object evt) throws Exception {
        ctx.next().userEventTriggered(evt);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Queue<T> newReceiveBuffer(ChannelReaderContext<T> ctx) throws Exception {
        return (Queue<T>) QueueFactory.createQueue(Object.class);
    }

    @Override
    public void receiveBufferUpdated(ChannelReaderContext<T> ctx) throws Exception {
        ctx.in().transferTo(ctx.next().in());
    }

    @Override
    public void receiveBufferClosed(ChannelReaderContext<T> ctx) throws Exception {
        ctx.next().in().close();
    }
}
