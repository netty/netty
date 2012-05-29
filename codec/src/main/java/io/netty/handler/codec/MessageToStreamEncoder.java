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

        int oldOutSize = out.readableBytes();
        for (;;) {
            I msg = in.poll();
            if (msg == null) {
                break;
            }

            try {
                encode(ctx, msg, out);
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new EncoderException(t));
                }
            }
        }

        if (out.readableBytes() > oldOutSize) {
            ctx.flush(future);
        }
    }

    public abstract void encode(ChannelOutboundHandlerContext<I> ctx, I msg, ChannelBuffer out) throws Exception;
}
