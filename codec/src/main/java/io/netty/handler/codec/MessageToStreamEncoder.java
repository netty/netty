package io.netty.handler.codec;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerContext;

import java.util.Queue;

public abstract class MessageToStreamEncoder<I> extends ChannelOutboundHandlerAdapter<I> {

    @Override
    public ChannelBufferHolder<I> newOutboundBuffer(
            ChannelOutboundHandlerContext<I> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<I> ctx, ChannelFuture future) throws Exception {
        Queue<I> in = ctx.outbound().messageBuffer();
        ChannelBuffer out = ctx.nextOutboundByteBuffer();

        boolean notify = false;
        int oldOutSize = out.readableBytes();
        for (;;) {
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
            try {
                encode(ctx, imsg, out);
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new EncoderException(t));
                }
            }
        }

        if (out.readableBytes() > oldOutSize || notify) {
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

    public abstract void encode(ChannelOutboundHandlerContext<I> ctx, I msg, ChannelBuffer out) throws Exception;
}
