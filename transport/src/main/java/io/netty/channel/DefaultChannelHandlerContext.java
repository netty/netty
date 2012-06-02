package io.netty.channel;

import io.netty.buffer.ChannelBuffer;
import io.netty.util.DefaultAttributeMap;

import java.net.SocketAddress;
import java.util.Queue;

final class DefaultChannelHandlerContext extends DefaultAttributeMap implements ChannelInboundHandlerContext<Object>, ChannelOutboundHandlerContext<Object> {
    volatile DefaultChannelHandlerContext next;
    volatile DefaultChannelHandlerContext prev;
    private final Channel channel;
    private final DefaultChannelPipeline pipeline;
    final EventExecutor executor;
    private final String name;
    private final ChannelHandler handler;
    private final boolean canHandleInbound;
    private final boolean canHandleOutbound;
    final ChannelBufferHolder<Object> in;
    private final ChannelBufferHolder<Object> out;

    // Runnables that calls handlers
    final Runnable fireChannelRegisteredTask = new Runnable() {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelInboundHandler<Object>) ctx.handler()).channelRegistered(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    final Runnable fireChannelUnregisteredTask = new Runnable() {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelInboundHandler<Object>) ctx.handler()).channelUnregistered(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    final Runnable fireChannelActiveTask = new Runnable() {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelInboundHandler<Object>) ctx.handler()).channelActive(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    final Runnable fireChannelInactiveTask = new Runnable() {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelInboundHandler<Object>) ctx.handler()).channelInactive(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    final Runnable fireInboundBufferUpdatedTask = new Runnable() {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelInboundHandler<Object>) ctx.handler()).inboundBufferUpdated(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            } finally {
                ChannelBufferHolder<Object> inbound = ctx.inbound();
                if (!inbound.isBypass() && inbound.isEmpty() && inbound.hasByteBuffer()) {
                    inbound.byteBuffer().discardReadBytes();
                }
            }
        }
    };

    @SuppressWarnings("unchecked")
    DefaultChannelHandlerContext(
            DefaultChannelPipeline pipeline, EventExecutor executor,
            DefaultChannelHandlerContext prev, DefaultChannelHandlerContext next,
            String name, ChannelHandler handler) {

        if (name == null) {
            throw new NullPointerException("name");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        canHandleInbound = handler instanceof ChannelInboundHandler;
        canHandleOutbound = handler instanceof ChannelOutboundHandler;

        if (!canHandleInbound && !canHandleOutbound) {
            throw new IllegalArgumentException(
                    "handler must be either " +
                    ChannelInboundHandler.class.getName() + " or " +
                    ChannelOutboundHandler.class.getName() + '.');
        }

        this.prev = prev;
        this.next = next;

        channel = pipeline.channel;
        this.pipeline = pipeline;
        this.name = name;
        this.handler = handler;

        if (executor != null) {
            // Pin one of the child executors once and remember it so that the same child executor
            // is used to fire events for the same channel.
            EventExecutor childExecutor = pipeline.childExecutors.get(executor);
            if (childExecutor == null) {
                childExecutor = executor.unsafe().nextChild();
                pipeline.childExecutors.put(executor, childExecutor);
            }
            this.executor = childExecutor;
        } else {
            this.executor = null;
        }

        if (canHandleInbound) {
            try {
                in = ((ChannelInboundHandler<Object>) handler).newInboundBuffer(this);
            } catch (Exception e) {
                throw new ChannelPipelineException("A user handler failed to create a new inbound buffer.", e);
            }
        } else {
            in = null;
        }
        if (canHandleOutbound) {
            try {
                out = ((ChannelOutboundHandler<Object>) handler).newOutboundBuffer(this);
            } catch (Exception e) {
                throw new ChannelPipelineException("A user handler failed to create a new outbound buffer.", e);
            } finally {
                if (in != null) {
                    // TODO Release the inbound buffer once pooling is implemented.
                }
            }
        } else {
            out = null;
        }
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public EventExecutor executor() {
        if (executor == null) {
            return channel.eventLoop();
        } else {
            return executor;
        }
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean canHandleInbound() {
        return canHandleInbound;
    }

    @Override
    public boolean canHandleOutbound() {
        return canHandleOutbound;
    }

    @Override
    public ChannelBufferHolder<Object> inbound() {
        return in;
    }

    @Override
    public ChannelBufferHolder<Object> outbound() {
        return out;
    }

    @Override
    public ChannelBuffer nextInboundByteBuffer() {
        return DefaultChannelPipeline.nextInboundByteBuffer(next);
    }

    @Override
    public Queue<Object> nextInboundMessageBuffer() {
        return DefaultChannelPipeline.nextInboundMessageBuffer(next);
    }

    @Override
    public ChannelBuffer nextOutboundByteBuffer() {
        return pipeline.nextOutboundByteBuffer(prev);
    }

    @Override
    public Queue<Object> nextOutboundMessageBuffer() {
        return pipeline.nextOutboundMessageBuffer(prev);
    }

    @Override
    public void fireChannelRegistered() {
        DefaultChannelHandlerContext next = DefaultChannelPipeline.nextInboundContext(this.next);
        if (next != null) {
            DefaultChannelPipeline.fireChannelRegistered(next);
        }
    }

    @Override
    public void fireChannelUnregistered() {
        DefaultChannelHandlerContext next = DefaultChannelPipeline.nextInboundContext(this.next);
        if (next != null) {
            DefaultChannelPipeline.fireChannelUnregistered(next);
        }
    }

    @Override
    public void fireChannelActive() {
        DefaultChannelHandlerContext next = DefaultChannelPipeline.nextInboundContext(this.next);
        if (next != null) {
            DefaultChannelPipeline.fireChannelActive(next);
        }
    }

    @Override
    public void fireChannelInactive() {
        DefaultChannelHandlerContext next = DefaultChannelPipeline.nextInboundContext(this.next);
        if (next != null) {
            DefaultChannelPipeline.fireChannelInactive(next);
        }
    }

    @Override
    public void fireExceptionCaught(Throwable cause) {
        DefaultChannelHandlerContext next = DefaultChannelPipeline.nextInboundContext(this.next);
        if (next != null) {
            pipeline.fireExceptionCaught(next, cause);
        } else {
            DefaultChannelPipeline.logTerminalException(cause);
        }
    }

    @Override
    public void fireUserEventTriggered(Object event) {
        DefaultChannelHandlerContext next = DefaultChannelPipeline.nextInboundContext(this.next);
        if (next != null) {
            pipeline.fireUserEventTriggered(next, event);
        }
    }

    @Override
    public void fireInboundBufferUpdated() {
        DefaultChannelHandlerContext next = DefaultChannelPipeline.nextInboundContext(this.next);
        if (next != null) {
            DefaultChannelPipeline.fireInboundBufferUpdated(next);
        }
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, newFuture());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newFuture());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newFuture());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(newFuture());
    }

    @Override
    public ChannelFuture close() {
        return close(newFuture());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(newFuture());
    }

    @Override
    public ChannelFuture flush() {
        return flush(newFuture());
    }

    @Override
    public ChannelFuture write(Object message) {
        return write(message, newFuture());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelFuture future) {
        return pipeline.bind(DefaultChannelPipeline.nextOutboundContext(prev), localAddress, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelFuture future) {
        return connect(remoteAddress, null, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
        return pipeline.connect(DefaultChannelPipeline.nextOutboundContext(prev), remoteAddress, localAddress, future);
    }

    @Override
    public ChannelFuture disconnect(ChannelFuture future) {
        return pipeline.disconnect(DefaultChannelPipeline.nextOutboundContext(prev), future);
    }

    @Override
    public ChannelFuture close(ChannelFuture future) {
        return pipeline.close(DefaultChannelPipeline.nextOutboundContext(prev), future);
    }

    @Override
    public ChannelFuture deregister(ChannelFuture future) {
        return pipeline.deregister(DefaultChannelPipeline.nextOutboundContext(prev), future);
    }

    @Override
    public ChannelFuture flush(ChannelFuture future) {
        return pipeline.flush(DefaultChannelPipeline.nextOutboundContext(prev), future);
    }

    @Override
    public ChannelFuture write(Object message, ChannelFuture future) {
        return pipeline.write(DefaultChannelPipeline.nextOutboundContext(prev), message, future);
    }

    @Override
    public ChannelFuture newFuture() {
        return channel.newFuture();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return channel.newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return channel.newFailedFuture(cause);
    }
}