package io.netty.handler.codec;

import static io.netty.handler.codec.MessageToMessageEncoder.*;
import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerContext;

public abstract class StreamToMessageDecoder<O> extends ChannelInboundHandlerAdapter<Byte> {

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
        if (in.readable()) {
            callDecode(ctx);
        }

        try {
            if (unfoldAndAdd(ctx, ctx.nextIn(), decodeLast(ctx, in))) {
                in.discardReadBytes();
                ctx.fireInboundBufferUpdated();
            }
        } catch (Throwable t) {
            ctx.fireExceptionCaught(t);
        }

        ctx.fireChannelInactive();
    }

    private void callDecode(ChannelInboundHandlerContext<Byte> ctx) {
        ChannelBuffer in = ctx.in().byteBuffer();

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

                if (unfoldAndAdd(ctx, ctx.nextIn(), o)) {
                    decoded = true;
                } else {
                    break;
                }
            } catch (Throwable t) {
                ctx.fireExceptionCaught(t);
            }
        }

        if (decoded) {
            in.discardReadBytes();
            ctx.fireInboundBufferUpdated();
        }
    }

    public abstract O decode(ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in) throws Exception;

    public O decodeLast(ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in) throws Exception {
        return decode(ctx, in);
    }
}
