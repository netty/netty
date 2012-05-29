package io.netty.handler.codec;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelPipeline;

public abstract class StreamToMessageDecoder<O> extends ChannelInboundHandlerAdapter<Byte> {

    private ChannelInboundHandlerContext<Byte> ctx;

    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(
            ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        this.ctx = ctx;
        return ChannelBufferHolders.byteBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        callDecode(ctx);
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        ChannelBuffer in = ctx.inbound().byteBuffer();
        if (in.readable()) {
            callDecode(ctx);
        }

        try {
            if (CodecUtil.unfoldAndAdd(ctx, decodeLast(ctx, in), true)) {
                in.discardReadBytes();
                ctx.fireInboundBufferUpdated();
            }
        } catch (Throwable t) {
            if (t instanceof CodecException) {
                ctx.fireExceptionCaught(t);
            } else {
                ctx.fireExceptionCaught(new DecoderException(t));
            }
        }

        ctx.fireChannelInactive();
    }

    protected void callDecode(ChannelInboundHandlerContext<Byte> ctx) {
        ChannelBuffer in = ctx.inbound().byteBuffer();

        boolean decoded = false;
        for (;;) {
            try {
                int oldInputLength = in.readableBytes();
                O o = decode(ctx, in);
                if (o == null) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                } else {
                    if (oldInputLength == in.readableBytes()) {
                        throw new IllegalStateException(
                                "decode() did not read anything but decoded a message.");
                    }
                }

                if (CodecUtil.unfoldAndAdd(ctx, o, true)) {
                    decoded = true;
                } else {
                    break;
                }
            } catch (Throwable t) {
                if (decoded) {
                    decoded = false;
                    in.discardReadBytes();
                    ctx.fireInboundBufferUpdated();
                }

                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new DecoderException(t));
                }
            }
        }

        if (decoded) {
            in.discardReadBytes();
            ctx.fireInboundBufferUpdated();
        }
    }

    /**
     * Replace this decoder in the {@link ChannelPipeline} with the given handler.
     * All remaining bytes in the inbound buffer will be forwarded to the new handler's
     * inbound buffer.
     */
    public void replace(String newHandlerName, ChannelInboundHandler<Byte> newHandler) {
        if (!ctx.channel().eventLoop().inEventLoop()) {
            throw new IllegalStateException("not in event loop");
        }

        // We do not use ChannelPipeline.replace() here so that the current context points
        // the new handler.
        ctx.pipeline().addAfter(ctx.name(), newHandlerName, newHandler);

        ChannelBuffer in = ctx.inbound().byteBuffer();
        try {
            if (in.readable()) {
                ctx.nextInboundByteBuffer().writeBytes(ctx.inbound().byteBuffer());
                ctx.fireInboundBufferUpdated();
            }
        } finally {
            ctx.pipeline().remove(this);
        }
    }

    public abstract O decode(ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in) throws Exception;

    public O decodeLast(ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in) throws Exception {
        return decode(ctx, in);
    }
}
