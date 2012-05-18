package io.netty.handler.codec;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerContext;

public abstract class StreamToStreamDecoder extends ChannelInboundHandlerAdapter<Byte> {

    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(
            ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        return ChannelBufferHolders.byteBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        callDecode(ctx);
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        ChannelBuffer in = ctx.in().byteBuffer();
        if (!in.readable()) {
            callDecode(ctx);
        }

        ChannelBuffer out = ctx.nextIn().byteBuffer();
        int oldOutSize = out.readableBytes();
        try {
            decodeLast(ctx, in, out);
        } catch (Throwable t) {
            if (t instanceof CodecException) {
                ctx.fireExceptionCaught(t);
            } else {
                ctx.fireExceptionCaught(new DecoderException(t));
            }
        }

        if (out.readableBytes() > oldOutSize) {
            in.discardReadBytes();
            ctx.fireInboundBufferUpdated();
        }

        ctx.fireChannelInactive();
    }

    private void callDecode(ChannelInboundHandlerContext<Byte> ctx) {
        ChannelBuffer in = ctx.in().byteBuffer();
        ChannelBuffer out = ctx.nextIn().byteBuffer();

        int oldOutSize = out.readableBytes();
        while (in.readable()) {
            int oldInSize = in.readableBytes();
            try {
                decode(ctx, in, out);
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new DecoderException(t));
                }
            }
            if (oldInSize == in.readableBytes()) {
                break;
            }
        }

        if (out.readableBytes() > oldOutSize) {
            in.discardReadBytes();
            ctx.fireInboundBufferUpdated();
        }
    }

    public abstract void decode(ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in, ChannelBuffer out) throws Exception;

    public void decodeLast(ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in, ChannelBuffer out) throws Exception {
        decode(ctx, in, out);
    }
}
