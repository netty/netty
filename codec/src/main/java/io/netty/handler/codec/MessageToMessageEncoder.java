package io.netty.handler.codec;

import io.netty.buffer.ChannelBuffer;
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
        Queue<I> in = ctx.prevOut().messageBuffer();
        boolean encoded = false;
        for (;;) {
            try {
                I msg = in.poll();
                if (msg == null) {
                    break;
                }

                O emsg = encode(ctx, msg);
                if (emsg == null) {
                    // encode() might be waiting for more inbound messages to generate
                    // an aggregated message - keep polling.
                    continue;
                }

                if (unfoldAndAdd(ctx, ctx.out(), emsg)) {
                    encoded = true;
                }
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new EncoderException(t));
                }
            }
        }

        if (encoded) {
            ctx.flush(future);
        }
    }

    public abstract O encode(ChannelOutboundHandlerContext<I> ctx, I msg) throws Exception;

    static <T> boolean unfoldAndAdd(
            ChannelHandlerContext ctx, ChannelBufferHolder<Object> dst, Object msg) throws Exception {
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

        if (dst.hasMessageBuffer()) {
            dst.messageBuffer().add(msg);
        } else if (msg instanceof ChannelBuffer) {
            ChannelBuffer buf = (ChannelBuffer) msg;
            if (!buf.readable()) {
                return false;
            }
            dst.byteBuffer().writeBytes(buf, buf.readerIndex(), buf.readableBytes());
        } else {
            throw new UnsupportedMessageTypeException(msg, ChannelBuffer.class);
        }

        return true;
    }
}
