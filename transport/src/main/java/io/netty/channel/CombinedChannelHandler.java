package io.netty.channel;

import java.net.SocketAddress;

public class CombinedChannelHandler implements ChannelInboundHandler<Object>,
                                               ChannelOutboundHandler<Object> {

    private ChannelOutboundHandler<Object> out;
    private ChannelInboundHandler<Object> in;

    protected CombinedChannelHandler() {
        // User will call init in the subclass constructor.
    }

    public CombinedChannelHandler(
            ChannelInboundHandler<?> inboundHandler, ChannelOutboundHandler<?> outboundHandler) {
        init(inboundHandler, outboundHandler);
    }

    @SuppressWarnings("unchecked")
    protected void init(ChannelInboundHandler<?> inboundHandler,
            ChannelOutboundHandler<?> outboundHandler) {
        if (inboundHandler == null) {
            throw new NullPointerException("inboundHandler");
        }
        if (outboundHandler == null) {
            throw new NullPointerException("outboundHandler");
        }

        if (in != null) {
            throw new IllegalStateException("init() cannot be called more than once.");
        }

        in = (ChannelInboundHandler<Object>) inboundHandler;
        out = (ChannelOutboundHandler<Object>) outboundHandler;
    }

    @Override
    public ChannelBufferHolder<Object> newInboundBuffer(
            ChannelInboundHandlerContext<Object> ctx) throws Exception {
        return in.newInboundBuffer(ctx);
    }

    @Override
    public ChannelBufferHolder<Object> newOutboundBuffer(
            ChannelOutboundHandlerContext<Object> ctx) throws Exception {
        return out.newOutboundBuffer(ctx);
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        if (in == null) {
            throw new IllegalStateException(
                    "not initialized yet - call init() in the constructor of the subclass");
        }

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
    public void channelRegistered(ChannelInboundHandlerContext<Object> ctx) throws Exception {
        in.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelInboundHandlerContext<Object> ctx) throws Exception {
        in.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelInboundHandlerContext<Object> ctx) throws Exception {
        in.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<Object> ctx) throws Exception {
        in.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(
            ChannelInboundHandlerContext<Object> ctx, Throwable cause) throws Exception {
        in.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(
            ChannelInboundHandlerContext<Object> ctx, Object evt) throws Exception {
        in.userEventTriggered(ctx, evt);
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Object> ctx) throws Exception {
        in.inboundBufferUpdated(ctx);
    }

    @Override
    public void bind(
            ChannelOutboundHandlerContext<Object> ctx,
            SocketAddress localAddress, ChannelFuture future) throws Exception {
        out.bind(ctx, localAddress, future);
    }

    @Override
    public void connect(
            ChannelOutboundHandlerContext<Object> ctx,
            SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelFuture future) throws Exception {
        out.connect(ctx, remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(
            ChannelOutboundHandlerContext<Object> ctx, ChannelFuture future) throws Exception {
        out.disconnect(ctx, future);
    }

    @Override
    public void close(
            ChannelOutboundHandlerContext<Object> ctx, ChannelFuture future) throws Exception {
        out.close(ctx, future);
    }

    @Override
    public void deregister(
            ChannelOutboundHandlerContext<Object> ctx, ChannelFuture future) throws Exception {
        out.deregister(ctx, future);
    }

    @Override
    public void flush(
            ChannelOutboundHandlerContext<Object> ctx, ChannelFuture future) throws Exception {
        out.flush(ctx, future);
    }
}