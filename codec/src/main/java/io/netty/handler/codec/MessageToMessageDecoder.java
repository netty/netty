package io.netty.handler.codec;

import static io.netty.handler.codec.MessageToMessageEncoder.*;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerContext;

import java.util.Queue;

public abstract class MessageToMessageDecoder<I, O> extends ChannelInboundHandlerAdapter<I> {

    @Override
    public ChannelBufferHolder<I> newInboundBuffer(
            ChannelInboundHandlerContext<I> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<I> ctx)
            throws Exception {
        Queue<I> in = ctx.inbound().messageBuffer();
        boolean notify = false;
        for (;;) {
            try {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }
                if (!isDecodable(msg)) {
                    ctx.nextInboundMessageBuffer().add(msg);
                    notify = true;
                    continue;
                }

                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                O omsg = decode(ctx, imsg);
                if (omsg == null) {
                    // Decoder consumed a message but returned null.
                    // Probably it needs more messages because it's an aggregator.
                    continue;
                }

                if (unfoldAndAdd(ctx, ctx.nextInboundMessageBuffer(), omsg)) {
                    notify = true;
                }
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new DecoderException(t));
                }
            }
        }
        if (notify) {
            ctx.fireInboundBufferUpdated();
        }
    }

    /**
     * Returns {@code true} if and only if the specified message can be decoded by this decoder.
     *
     * @param msg the message
     */
    public boolean isDecodable(Object msg) throws Exception {
        return true;
    }

    public abstract O decode(ChannelInboundHandlerContext<I> ctx, I msg) throws Exception;
}
