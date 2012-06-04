package io.netty.channel;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.internal.QueueFactory;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

final class DefaultChannelHandlerContext extends DefaultAttributeMap implements ChannelInboundHandlerContext<Object>, ChannelOutboundHandlerContext<Object> {
    volatile DefaultChannelHandlerContext next;
    volatile DefaultChannelHandlerContext prev;
    private final Channel channel;
    private final DefaultChannelPipeline pipeline;
    EventExecutor executor; // not thread-safe but OK because it never changes once set.
    private final String name;
    private final ChannelHandler handler;
    final ChannelBufferHolder<Object> in;
    final ChannelBufferHolder<Object> out;

    // When the two handlers run in a different thread and they are next to each other,
    // each other's buffers can be accessed at the same time resulting in a race condition.
    // To avoid such situation, we lazily creates an additional thread-safe buffer called
    // 'bridge' so that the two handlers access each other's buffer only via the bridges.
    // The content written into a bridge is flushed into the actual buffer by flushBridge().
    final AtomicReference<MessageBridge> inMsgBridge;
    final AtomicReference<MessageBridge> outMsgBridge;
    final AtomicReference<StreamBridge> inByteBridge;
    final AtomicReference<StreamBridge> outByteBridge;

    // Runnables that calls handlers
    final Runnable fireChannelRegisteredTask = new Runnable() {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelInboundHandler<Object>) ctx.handler).channelRegistered(ctx);
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
                ((ChannelInboundHandler<Object>) ctx.handler).channelUnregistered(ctx);
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
                ((ChannelInboundHandler<Object>) ctx.handler).channelActive(ctx);
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
                ((ChannelInboundHandler<Object>) ctx.handler).channelInactive(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    final Runnable curCtxFireInboundBufferUpdatedTask = new Runnable() {
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            flushBridge();
            try {
                ((ChannelInboundHandler<Object>) ctx.handler).inboundBufferUpdated(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            } finally {
                if (inByteBridge != null) {
                    ChannelBuffer buf = ctx.in.byteBuffer();
                    if (!buf.readable()) {
                        buf.discardReadBytes();
                    }
                }
            }
        }
    };
    private final Runnable nextCtxFireInboundBufferUpdatedTask = new Runnable() {
        @Override
        public void run() {
            DefaultChannelHandlerContext next =
                    DefaultChannelPipeline.nextInboundContext(DefaultChannelHandlerContext.this.next);
            if (next != null) {
                next.fillBridge();
                DefaultChannelPipeline.fireInboundBufferUpdated(next);
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

        boolean canHandleInbound = handler instanceof ChannelInboundHandler;
        boolean canHandleOutbound = handler instanceof ChannelOutboundHandler;
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
        } else if (channel.isRegistered()) {
            this.executor = channel.eventLoop();
        } else {
            this.executor = null;
        }

        if (canHandleInbound) {
            try {
                in = ((ChannelInboundHandler<Object>) handler).newInboundBuffer(this);
            } catch (Exception e) {
                throw new ChannelPipelineException("A user handler failed to create a new inbound buffer.", e);
            }

            if (!in.isBypass()) {
                if (in.hasByteBuffer()) {
                    inByteBridge = new AtomicReference<StreamBridge>();
                    inMsgBridge = null;
                } else {
                    inByteBridge = null;
                    inMsgBridge = new AtomicReference<MessageBridge>();
                }
            } else {
                inByteBridge = null;
                inMsgBridge = null;
            }
        } else {
            in = null;
            inByteBridge = null;
            inMsgBridge = null;
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

            if (!out.isBypass()) {
                if (out.hasByteBuffer()) {
                    outByteBridge = new AtomicReference<StreamBridge>();
                    outMsgBridge = null;
                } else {
                    outByteBridge = null;
                    outMsgBridge = new AtomicReference<MessageBridge>();
                }
            } else {
                outByteBridge = null;
                outMsgBridge = null;
            }
        } else {
            out = null;
            outByteBridge = null;
            outMsgBridge = null;
        }
    }

    void fillBridge() {
        if (inMsgBridge != null) {
            MessageBridge bridge = inMsgBridge.get();
            if (bridge != null) {
                bridge.fill();
            }
        } else if (inByteBridge != null) {
            StreamBridge bridge = inByteBridge.get();
            if (bridge != null) {
                bridge.fill();
            }
        }

        if (outMsgBridge != null) {
            MessageBridge bridge = outMsgBridge.get();
            if (bridge != null) {
                bridge.fill();
            }
        } else if (outByteBridge != null) {
            StreamBridge bridge = outByteBridge.get();
            if (bridge != null) {
                bridge.fill();
            }
        }
    }

    void flushBridge() {
        if (inMsgBridge != null) {
            MessageBridge bridge = inMsgBridge.get();
            if (bridge != null) {
                bridge.flush(in.messageBuffer());
            }
        } else if (inByteBridge != null) {
            StreamBridge bridge = inByteBridge.get();
            if (bridge != null) {
                bridge.flush(in.byteBuffer());
            }
        }

        if (outMsgBridge != null) {
            MessageBridge bridge = outMsgBridge.get();
            if (bridge != null) {
                bridge.flush(out.messageBuffer());
            }
        } else if (outByteBridge != null) {
            StreamBridge bridge = outByteBridge.get();
            if (bridge != null) {
                bridge.flush(out.byteBuffer());
            }
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
            return executor = channel.eventLoop();
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
        return in != null;
    }

    @Override
    public boolean canHandleOutbound() {
        return out != null;
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
    public boolean hasNextInboundByteBuffer() {
        return DefaultChannelPipeline.hasNextInboundByteBuffer(next);
    }

    @Override
    public boolean hasNextInboundMessageBuffer() {
        return DefaultChannelPipeline.hasNextInboundMessageBuffer(next);
    }

    @Override
    public boolean hasNextOutboundByteBuffer() {
        return pipeline.hasNextOutboundByteBuffer(prev);
    }

    @Override
    public boolean hasNextOutboundMessageBuffer() {
        return pipeline.hasNextOutboundMessageBuffer(prev);
    }

    @Override
    public ChannelBuffer nextInboundByteBuffer() {
        return DefaultChannelPipeline.nextInboundByteBuffer(executor(), next);
    }

    @Override
    public Queue<Object> nextInboundMessageBuffer() {
        return DefaultChannelPipeline.nextInboundMessageBuffer(executor(), next);
    }

    @Override
    public ChannelBuffer nextOutboundByteBuffer() {
        return pipeline.nextOutboundByteBuffer(executor(), prev);
    }

    @Override
    public Queue<Object> nextOutboundMessageBuffer() {
        return pipeline.nextOutboundMessageBuffer(executor(), prev);
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
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            nextCtxFireInboundBufferUpdatedTask.run();
        } else {
            executor.execute(nextCtxFireInboundBufferUpdatedTask);
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
    public ChannelFuture flush(final ChannelFuture future) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext prev = DefaultChannelPipeline.nextOutboundContext(this.prev);
            prev.fillBridge();
            pipeline.flush(prev, future);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    flush(future);
                }
            });
        }

        return future;
    }

    @Override
    public ChannelFuture write(Object message, ChannelFuture future) {
        return pipeline.write(prev, message, future);
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

    static final class MessageBridge {
        final Queue<Object> msgBuf = new ArrayDeque<Object>();
        final BlockingQueue<Object[]> exchangeBuf = QueueFactory.createQueue();

        void fill() {
            if (msgBuf.isEmpty()) {
                return;
            }
            Object[] data = msgBuf.toArray();
            msgBuf.clear();
            exchangeBuf.add(data);
        }

        void flush(Queue<Object> out) {
            for (;;) {
                Object[] data = exchangeBuf.poll();
                if (data == null) {
                    break;
                }

                for (Object d: data) {
                    out.add(d);
                }
            }
        }
    }

    static final class StreamBridge {
        final ChannelBuffer byteBuf = ChannelBuffers.dynamicBuffer();
        final BlockingQueue<ChannelBuffer> exchangeBuf = QueueFactory.createQueue();

        void fill() {
            if (!byteBuf.readable()) {
                return;
            }
            ChannelBuffer data = byteBuf.readBytes(byteBuf.readableBytes());
            byteBuf.discardReadBytes();
            exchangeBuf.add(data);
        }

        void flush(ChannelBuffer out) {
            for (;;) {
                ChannelBuffer data = exchangeBuf.poll();
                if (data == null) {
                    break;
                }

                out.writeBytes(data);
            }
        }
    }
}