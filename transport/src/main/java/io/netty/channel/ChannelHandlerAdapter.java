package io.netty.channel;

import java.net.SocketAddress;

public abstract class ChannelHandlerAdapter<I, O> implements ChannelInboundHandler<I>, ChannelOutboundHandler<O> {

    public static <I, O> ChannelHandlerAdapter<I, O> combine(
            ChannelInboundHandler<I> inboundHandler, ChannelOutboundHandler<O> outboundHandler) {
        if (inboundHandler == null) {
            throw new NullPointerException("inboundHandler");
        }
        if (outboundHandler == null) {
            throw new NullPointerException("outboundHandler");
        }

        final ChannelInboundHandler<I> in = inboundHandler;
        final ChannelOutboundHandler<O> out = outboundHandler;
        return new ChannelHandlerAdapter<I, O>() {

            @Override
            public ChannelBufferHolder<I> newInboundBuffer(
                    ChannelInboundHandlerContext<I> ctx) throws Exception {
                return in.newInboundBuffer(ctx);
            }

            @Override
            public ChannelBufferHolder<O> newOutboundBuffer(
                    ChannelOutboundHandlerContext<O> ctx) throws Exception {
                return out.newOutboundBuffer(ctx);
            }

            @Override
            public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
                try {
                    in.beforeAdd(ctx);
                } finally {
                    out.beforeAdd(ctx);
                }
            }

            @Override
            public void afterAdd(ChannelHandlerContext ctx) throws Exception {
                try {
                    in.afterAdd(ctx);
                } finally {
                    out.afterAdd(ctx);
                }
            }

            @Override
            public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
                try {
                    in.beforeRemove(ctx);
                } finally {
                    out.beforeRemove(ctx);
                }
            }

            @Override
            public void afterRemove(ChannelHandlerContext ctx) throws Exception {
                try {
                    in.afterRemove(ctx);
                } finally {
                    out.afterRemove(ctx);
                }
            }

            @Override
            public void channelRegistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
                in.channelRegistered(ctx);
            }

            @Override
            public void channelUnregistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
                in.channelUnregistered(ctx);
            }

            @Override
            public void channelActive(ChannelInboundHandlerContext<I> ctx) throws Exception {
                in.channelActive(ctx);
            }

            @Override
            public void channelInactive(ChannelInboundHandlerContext<I> ctx) throws Exception {
                in.channelInactive(ctx);
            }

            @Override
            public void exceptionCaught(
                    ChannelInboundHandlerContext<I> ctx, Throwable cause) throws Exception {
                in.exceptionCaught(ctx, cause);
            }

            @Override
            public void userEventTriggered(
                    ChannelInboundHandlerContext<I> ctx, Object evt) throws Exception {
                in.userEventTriggered(ctx, evt);
            }

            @Override
            public void inboundBufferUpdated(ChannelInboundHandlerContext<I> ctx) throws Exception {
                in.inboundBufferUpdated(ctx);
            }

            @Override
            public void bind(
                    ChannelOutboundHandlerContext<O> ctx,
                    SocketAddress localAddress, ChannelFuture future) throws Exception {
                out.bind(ctx, localAddress, future);
            }

            @Override
            public void connect(
                    ChannelOutboundHandlerContext<O> ctx,
                    SocketAddress remoteAddress, SocketAddress localAddress,
                    ChannelFuture future) throws Exception {
                out.connect(ctx, remoteAddress, localAddress, future);
            }

            @Override
            public void disconnect(
                    ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
                out.disconnect(ctx, future);
            }

            @Override
            public void close(
                    ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
                out.close(ctx, future);
            }

            @Override
            public void deregister(
                    ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
                out.deregister(ctx, future);
            }

            @Override
            public void flush(
                    ChannelOutboundHandlerContext<O> ctx, ChannelFuture future) throws Exception {
                out.flush(ctx, future);
            }
        };
    }

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
    public void channelRegistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<I> ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelInboundHandlerContext<I> ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<I> ctx) throws Exception {
        ChannelInboundHandlerAdapter.inboundBufferUpdated0(ctx);
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
        ChannelOutboundHandlerAdapter.flush0(ctx, future);
    }
}
