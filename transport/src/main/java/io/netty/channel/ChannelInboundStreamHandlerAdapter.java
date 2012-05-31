package io.netty.channel;

import io.netty.buffer.ChannelBuffer;


public class ChannelInboundStreamHandlerAdapter extends ChannelInboundHandlerAdapter<Byte> {
    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(
            ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        return ChannelBufferHolders.byteBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx)
            throws Exception {
        inboundBufferUpdated(ctx, ctx.inbound().byteBuffer());
    }

    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in)
            throws Exception {
        ctx.nextInboundByteBuffer().writeBytes(in);
        in.discardReadBytes();
    }
}
