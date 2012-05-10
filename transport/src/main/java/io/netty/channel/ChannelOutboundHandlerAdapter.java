package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.net.SocketAddress;
import java.util.Queue;

public abstract class ChannelOutboundHandlerAdapter<O> implements ChannelOutboundHandler<O> {
    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // Do nothing by default.
    }

    @Override
    public void bind(ChannelOutboundHandlerContext<O> ctx, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.bind(localAddress, future);
    }

    @Override
    public void connect(ChannelOutboundHandlerContext<O> ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.connect(remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.disconnect(future);
    }

    @Override
    public void close(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.close(future);
    }

    @Override
    public void deregister(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.deregister(future);
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        flush0(ctx, future);
    }

    static <O> void flush0(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) {
        if (ctx.prevOut().isBypass()) {
            ctx.flush(future);
            return;
        }

        if (ctx.prevOut().hasMessageBuffer()) {
            Queue<O> out = ctx.prevOut().messageBuffer();
            Queue<Object> nextOut = ctx.out().messageBuffer();
            for (;;) {
                O msg = out.poll();
                if (msg == null) {
                    break;
                }
                nextOut.add(msg);
            }
        } else {
            ChannelBuffer out = ctx.prevOut().byteBuffer();
            ChannelBuffer nextOut = ctx.out().byteBuffer();
            nextOut.writeBytes(out);
            out.discardReadBytes();
        }
        ctx.flush(future);
    }
}
