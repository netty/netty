package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

public class ChannelHandlerAdapter<I, O> implements ChannelInboundHandler<I>, ChannelOutboundHandler<O> {
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
    public void channelRegistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<I> ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelInboundHandlerContext<I> ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public ChannelBufferHolder<I> newInboundBuffer(ChannelInboundHandlerContext<I> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer(new ArrayDeque<I>());
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<I> ctx) throws Exception {
        if (ctx.in().hasMessageBuffer()) {
            Queue<I> in = ctx.in().messageBuffer();
            Queue<Object> nextIn = ctx.nextIn().messageBuffer();
            for (;;) {
                I msg = in.poll();
                if (msg == null) {
                    break;
                }
                nextIn.add(msg);
            }
        } else {
            ChannelBuffer in = ctx.in().byteBuffer();
            ChannelBuffer nextIn = ctx.nextIn().byteBuffer();
            nextIn.writeBytes(in);
        }
        ctx.fireInboundBufferUpdated();
    }

    @Override
    public void bind(ChannelOutboundHandlerContext<O> ctx, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.bind(localAddress, future);
    }

    @Override
    public void connect(ChannelOutboundHandlerContext<O> ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.connect(remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.disconnect(future);
    }

    @Override
    public void close(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.close(future);
    }

    @Override
    public void deregister(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.deregister(future);
    }

    @Override
    public ChannelBufferHolder<O> newOutboundBuffer(ChannelOutboundHandlerContext<O> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer(new ArrayDeque<O>());
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        if (ctx.prevOut().hasMessageBuffer()) {
            Queue<O> out = ctx.prevOut().messageBuffer();
            Queue<Object> nextOut = ctx.out().messageBuffer();
            for (;;) {
                O msg = out.poll();
                if (msg == null) {
                    break;
                }
                nextOut.add(msg);
            }
        } else {
            ChannelBuffer out = ctx.prevOut().byteBuffer();
            ChannelBuffer nextOut = ctx.out().byteBuffer();
            nextOut.writeBytes(out);
        }
        ctx.flush(future);
    }
}
