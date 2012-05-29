package io.netty.handler.codec;

import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerContext;

import java.util.Queue;

public abstract class MessageToMessageEncoder<I, O> extends ChannelOutboundHandlerAdapter<I> {

    @Override
    public ChannelBufferHolder<I> newOutboundBuffer(
            ChannelOutboundHandlerContext<I> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<I> ctx, ChannelFuture future) throws Exception {
        Queue<I> in = ctx.outbound().messageBuffer();
        boolean notify = false;
        for (;;) {
            try {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }

                if (!isEncodable(msg)) {
                    ctx.nextOutboundMessageBuffer().add(msg);
                    notify = true;
                    continue;
                }

                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                O omsg = encode(ctx, imsg);
                if (omsg == null) {
                    // encode() might be waiting for more inbound messages to generate
                    // an aggregated message - keep polling.
                    continue;
                }

                if (unfoldAndAdd(ctx, ctx.nextOutboundMessageBuffer(), omsg)) {
                    notify = true;
                }
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new EncoderException(t));
                }
            }
        }

        if (notify) {
            ctx.flush(future);
        }
    }

    /**
     * Returns {@code true} if and only if the specified message can be encoded by this encoder.
     *
     * @param msg the message
     */
    public boolean isEncodable(Object msg) throws Exception {
        return true;
    }

    public abstract O encode(ChannelOutboundHandlerContext<I> ctx, I msg) throws Exception;

    static <T> boolean unfoldAndAdd(
            ChannelHandlerContext ctx, Queue<Object> dst, Object msg) throws Exception {
        if (msg == null) {
            return false;
        }

        if (msg instanceof Object[]) {
            Object[] array = (Object[]) msg;
            if (array.length == 0) {
                return false;
            }

            boolean added = false;
            for (Object m: array) {
                if (m == null) {
                    break;
                }
                if (unfoldAndAdd(ctx, dst, m)) {
                    added = true;
                }
            }
            return added;
        }

        if (msg instanceof Iterable) {
            boolean added = false;
            @SuppressWarnings("unchecked")
            Iterable<Object> i = (Iterable<Object>) msg;
            for (Object m: i) {
                if (m == null) {
                    break;
                }
                if (unfoldAndAdd(ctx, dst, m)) {
                    added = true;
                }
            }
            return added;
        }

        dst.add(msg);
        return true;
    }
}
