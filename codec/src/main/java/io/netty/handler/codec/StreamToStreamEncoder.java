package io.netty.handler.codec;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerContext;

public abstract class StreamToStreamEncoder extends ChannelOutboundHandlerAdapter<Byte> {

    @Override
    public ChannelBufferHolder<Byte> newOutboundBuffer(
            ChannelOutboundHandlerContext<Byte> ctx) throws Exception {
        return ChannelBufferHolders.byteBuffer();
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<Byte> ctx, ChannelFuture future) throws Exception {
        ChannelBuffer in = ctx.outbound().byteBuffer();
        ChannelBuffer out = ctx.nextOutboundByteBuffer();

        int oldOutSize = out.readableBytes();
        while (in.readable()) {
            int oldInSize = in.readableBytes();
            try {
                encode(ctx, in, out);
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new EncoderException(t));
                }
            }
            if (oldInSize == in.readableBytes()) {
                break;
            }
        }

        if (out.readableBytes() > oldOutSize) {
            in.discardReadBytes();
            ctx.flush(future);
        }
    }

    public abstract void encode(ChannelOutboundHandlerContext<Byte> ctx, ChannelBuffer in, ChannelBuffer out) throws Exception;
}
