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
        for (;;) {
            I msg = in.poll();
            if (msg == null) {
                break;
            }
            try {
                messageReceived(ctx, msg);
                ctx.fireInboundBufferUpdated();
            } catch (Throwable t) {
                ctx.fireExceptionCaught(t);
            }
        }
    }

    public void messageReceived(ChannelInboundHandlerContext<I> ctx, I msg) throws Exception {
        ctx.nextIn().messageBuffer().add(msg);
    }
}
