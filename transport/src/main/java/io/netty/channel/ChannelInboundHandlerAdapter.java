package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.util.ArrayDeque;
import java.util.Queue;

public class ChannelInboundHandlerAdapter<I> implements ChannelInboundHandler<I> {
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

}
