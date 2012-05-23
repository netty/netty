package io.netty.channel;

import java.util.Queue;

public class ChannelInboundMessageHandlerAdapter<I> extends
        ChannelInboundHandlerAdapter<I> {

    @Override
    public ChannelBufferHolder<I> newInboundBuffer(
            ChannelInboundHandlerContext<I> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<I> ctx)
            throws Exception {
        Queue<I> in = ctx.in().messageBuffer();
        Queue<Object> out = ctx.nextIn().messageBuffer();
        int oldOutSize = out.size();
        for (;;) {
            I msg = in.poll();
            if (msg == null) {
                break;
            }
            try {
                messageReceived(ctx, msg);
            } catch (Throwable t) {
                ctx.fireExceptionCaught(t);
            }
        }

        if (out.size() != oldOutSize) {
            ctx.fireInboundBufferUpdated();
        }
    }

    public void messageReceived(ChannelInboundHandlerContext<I> ctx, I msg) throws Exception {
        ctx.nextIn().messageBuffer().add(msg);
    }
}
