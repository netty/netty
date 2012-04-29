package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

public class ChannelOutboundHandlerAdapter<O> implements ChannelOutboundHandler<O> {
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
        ctx.next().bind(localAddress, future);
    }

    @Override
    public void connect(ChannelOutboundHandlerContext<O> ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception {
        ctx.next().connect(remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.next().disconnect(future);
    }

    @Override
    public void close(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.next().close(future);
    }

    @Override
    public void deregister(ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
        ctx.next().deregister(future);
    }

    @Override
    public ChannelBufferHolder<O> newOutboundBuffer(ChannelOutboundHandlerContext<O> ctx) throws Exception {
        return ChannelBufferHolders.messageBuffer(new ArrayDeque<O>());
    }

    @Override
    public void outboundBufferUpdated(ChannelOutboundHandlerContext<O> ctx) throws Exception {
        if (ctx.out().hasMessageBuffer()) {
            Queue<O> out = ctx.out().messageBuffer();
            Queue<Object> nextOut = ctx.next().out().messageBuffer();
            for (;;) {
                O msg = out.poll();
                if (msg == null) {
                    break;
                }
                nextOut.add(msg);
            }
        } else {
            ChannelBuffer out = ctx.out().byteBuffer();
            ChannelBuffer nextOut = ctx.next().out().byteBuffer();
            nextOut.writeBytes(out);
        }
    }
}
