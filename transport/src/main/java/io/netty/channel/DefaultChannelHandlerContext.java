/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import static io.netty.channel.DefaultChannelPipeline.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ChannelBuf;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.internal.QueueFactory;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

final class DefaultChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext {

    private static final EnumSet<ChannelHandlerType> EMPTY_TYPE = EnumSet.noneOf(ChannelHandlerType.class);

    static final int DIR_INBOUND  = 0x00000001;
    static final int DIR_OUTBOUND = 0x80000000;

    volatile DefaultChannelHandlerContext next;
    volatile DefaultChannelHandlerContext prev;
    private final Channel channel;
    private final DefaultChannelPipeline pipeline;
    EventExecutor executor; // not thread-safe but OK because it never changes once set.
    private final String name;
    private final Set<ChannelHandlerType> type;
    final int directions;
    private final ChannelHandler handler;

    final MessageBuf<Object> inMsgBuf;
    final ByteBuf inByteBuf;
    final MessageBuf<Object> outMsgBuf;
    final ByteBuf outByteBuf;

    // When the two handlers run in a different thread and they are next to each other,
    // each other's buffers can be accessed at the same time resulting in a race condition.
    // To avoid such situation, we lazily creates an additional thread-safe buffer called
    // 'bridge' so that the two handlers access each other's buffer only via the bridges.
    // The content written into a bridge is flushed into the actual buffer by flushBridge().
    final AtomicReference<MessageBridge> inMsgBridge;
    final AtomicReference<MessageBridge> outMsgBridge;
    final AtomicReference<ByteBridge> inByteBridge;
    final AtomicReference<ByteBridge> outByteBridge;

    // Runnables that calls handlers
    final Runnable fireChannelRegisteredTask = new Runnable() {
        @Override
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelStateHandler) ctx.handler).channelRegistered(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    final Runnable fireChannelUnregisteredTask = new Runnable() {
        @Override
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelStateHandler) ctx.handler).channelUnregistered(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    final Runnable fireChannelActiveTask = new Runnable() {
        @Override
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelStateHandler) ctx.handler).channelActive(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    final Runnable fireChannelInactiveTask = new Runnable() {
        @Override
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelStateHandler) ctx.handler).channelInactive(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    final Runnable curCtxFireInboundBufferUpdatedTask = new Runnable() {
        @Override
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            flushBridge();
            try {
                ((ChannelStateHandler) ctx.handler).inboundBufferUpdated(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            } finally {
                ByteBuf buf = inByteBuf;
                if (buf != null) {
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
            DefaultChannelHandlerContext next = nextContext(
                    DefaultChannelHandlerContext.this.next, DIR_INBOUND);
            if (next != null) {
                next.fillBridge();
                EventExecutor executor = next.executor();
                if (executor.inEventLoop()) {
                    next.curCtxFireInboundBufferUpdatedTask.run();
                } else {
                    executor.execute(next.curCtxFireInboundBufferUpdatedTask);
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

        // Determine the type of the specified handler.
        int typeValue = 0;
        EnumSet<ChannelHandlerType> type = EMPTY_TYPE.clone();
        if (handler instanceof ChannelStateHandler) {
            type.add(ChannelHandlerType.STATE);
            typeValue |= DIR_INBOUND;
            if (handler instanceof ChannelInboundHandler) {
                type.add(ChannelHandlerType.INBOUND);
            }
        }
        if (handler instanceof ChannelOperationHandler) {
            type.add(ChannelHandlerType.OPERATION);
            typeValue |= DIR_OUTBOUND;
            if (handler instanceof ChannelOutboundHandler) {
                type.add(ChannelHandlerType.OUTBOUND);
            }
        }
        this.type = Collections.unmodifiableSet(type);
        directions = typeValue;

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

        if (type.contains(ChannelHandlerType.INBOUND)) {
            ChannelBuf buf;
            try {
                buf = ((ChannelInboundHandler) handler).newInboundBuffer(this);
            } catch (Exception e) {
                throw new ChannelPipelineException("A user handler failed to create a new inbound buffer.", e);
            }

            if (buf == null) {
                throw new ChannelPipelineException("A user handler's newInboundBuffer() returned null");
            }

            if (buf instanceof ByteBuf) {
                inByteBuf = (ByteBuf) buf;
                inByteBridge = new AtomicReference<ByteBridge>();
                inMsgBuf = null;
                inMsgBridge = null;
            } else if (buf instanceof MessageBuf) {
                inByteBuf = null;
                inByteBridge = null;
                inMsgBuf = (MessageBuf<Object>) buf;
                inMsgBridge = new AtomicReference<MessageBridge>();
            } else {
                throw new Error();
            }
        } else {
            inByteBuf = null;
            inByteBridge = null;
            inMsgBuf = null;
            inMsgBridge = null;
        }

        if (type.contains(ChannelHandlerType.OUTBOUND)) {
            ChannelBuf buf;
            try {
                buf = ((ChannelOutboundHandler) handler).newOutboundBuffer(this);
            } catch (Exception e) {
                throw new ChannelPipelineException("A user handler failed to create a new outbound buffer.", e);
            }

            if (buf == null) {
                throw new ChannelPipelineException("A user handler's newOutboundBuffer() returned null");
            }

            if (buf instanceof ByteBuf) {
                outByteBuf = (ByteBuf) buf;
                outByteBridge = new AtomicReference<ByteBridge>();
                outMsgBuf = null;
                outMsgBridge = null;
            } else if (buf instanceof MessageBuf) {
                outByteBuf = null;
                outByteBridge = null;
                outMsgBuf = (MessageBuf<Object>) buf;
                outMsgBridge = new AtomicReference<MessageBridge>();
            } else {
                throw new Error();
            }
        } else {
            outByteBuf = null;
            outByteBridge = null;
            outMsgBuf = null;
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
            ByteBridge bridge = inByteBridge.get();
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
            ByteBridge bridge = outByteBridge.get();
            if (bridge != null) {
                bridge.fill();
            }
        }
    }

    void flushBridge() {
        if (inMsgBridge != null) {
            MessageBridge bridge = inMsgBridge.get();
            if (bridge != null) {
                bridge.flush(inMsgBuf);
            }
        } else if (inByteBridge != null) {
            ByteBridge bridge = inByteBridge.get();
            if (bridge != null) {
                bridge.flush(inByteBuf);
            }
        }

        if (outMsgBridge != null) {
            MessageBridge bridge = outMsgBridge.get();
            if (bridge != null) {
                bridge.flush(outMsgBuf);
            }
        } else if (outByteBridge != null) {
            ByteBridge bridge = outByteBridge.get();
            if (bridge != null) {
                bridge.flush(outByteBuf);
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
    public Set<ChannelHandlerType> type() {
        return type;
    }

    @Override
    public boolean hasInboundByteBuffer() {
        return inByteBuf != null;
    }

    @Override
    public boolean hasInboundMessageBuffer() {
        return inMsgBuf != null;
    }

    @Override
    public ByteBuf inboundByteBuffer() {
        if (inByteBuf == null) {
            throw new NoSuchBufferException();
        }
        return inByteBuf;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> inboundMessageBuffer() {
        if (inMsgBuf == null) {
            throw new NoSuchBufferException();
        }
        return (MessageBuf<T>) inMsgBuf;
    }

    @Override
    public boolean hasOutboundByteBuffer() {
        return outByteBuf != null;
    }

    @Override
    public boolean hasOutboundMessageBuffer() {
        return outMsgBuf != null;
    }

    @Override
    public ByteBuf outboundByteBuffer() {
        if (outByteBuf == null) {
            throw new NoSuchBufferException();
        }
        return outByteBuf;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> outboundMessageBuffer() {
        if (outMsgBuf == null) {
            throw new NoSuchBufferException();
        }
        return (MessageBuf<T>) outMsgBuf;
    }

    @Override
    public boolean hasNextInboundByteBuffer() {
        DefaultChannelHandlerContext ctx = next;
        for (;;) {
            if (ctx == null) {
                return false;
            }
            if (ctx.inByteBridge != null) {
                return true;
            }
            ctx = ctx.next;
        }
    }

    @Override
    public boolean hasNextInboundMessageBuffer() {
        DefaultChannelHandlerContext ctx = next;
        for (;;) {
            if (ctx == null) {
                return false;
            }
            if (ctx.inMsgBridge != null) {
                return true;
            }
            ctx = ctx.next;
        }
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
    public ByteBuf nextInboundByteBuffer() {
        DefaultChannelHandlerContext ctx = next;
        final Thread currentThread = Thread.currentThread();
        for (;;) {
            if (ctx == null) {
                throw new NoSuchBufferException();
            }
            if (ctx.inByteBuf != null) {
                if (ctx.executor().inEventLoop(currentThread)) {
                    return ctx.inByteBuf;
                } else {
                    ByteBridge bridge = ctx.inByteBridge.get();
                    if (bridge == null) {
                        bridge = new ByteBridge();
                        if (!ctx.inByteBridge.compareAndSet(null, bridge)) {
                            bridge = ctx.inByteBridge.get();
                        }
                    }
                    return bridge.byteBuf;
                }
            }
            ctx = ctx.next;
        }
    }

    @Override
    public MessageBuf<Object> nextInboundMessageBuffer() {
        DefaultChannelHandlerContext ctx = next;
        final Thread currentThread = Thread.currentThread();
        for (;;) {
            if (ctx == null) {
                throw new NoSuchBufferException();
            }

            if (ctx.inMsgBuf != null) {
                if (ctx.executor().inEventLoop(currentThread)) {
                    return ctx.inMsgBuf;
                } else {
                    MessageBridge bridge = ctx.inMsgBridge.get();
                    if (bridge == null) {
                        bridge = new MessageBridge();
                        if (!ctx.inMsgBridge.compareAndSet(null, bridge)) {
                            bridge = ctx.inMsgBridge.get();
                        }
                    }
                    return bridge.msgBuf;
                }
            }
            ctx = ctx.next;
        }
    }

    @Override
    public ByteBuf nextOutboundByteBuffer() {
        return pipeline.nextOutboundByteBuffer(prev);
    }

    @Override
    public MessageBuf<Object> nextOutboundMessageBuffer() {
        return pipeline.nextOutboundMessageBuffer(prev);
    }

    @Override
    public void fireChannelRegistered() {
        DefaultChannelHandlerContext next = nextContext(this.next, DIR_INBOUND);
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop()) {
                next.fireChannelRegisteredTask.run();
            } else {
                executor.execute(next.fireChannelRegisteredTask);
            }
        }
    }

    @Override
    public void fireChannelUnregistered() {
        DefaultChannelHandlerContext next = nextContext(this.next, DIR_INBOUND);
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop()) {
                next.fireChannelUnregisteredTask.run();
            } else {
                executor.execute(next.fireChannelUnregisteredTask);
            }
        }
    }

    @Override
    public void fireChannelActive() {
        DefaultChannelHandlerContext next = nextContext(this.next, DIR_INBOUND);
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop()) {
                next.fireChannelActiveTask.run();
            } else {
                executor.execute(next.fireChannelActiveTask);
            }
        }
    }

    @Override
    public void fireChannelInactive() {
        DefaultChannelHandlerContext next = nextContext(this.next, DIR_INBOUND);
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop()) {
                next.fireChannelInactiveTask.run();
            } else {
                executor.execute(next.fireChannelInactiveTask);
            }
        }
    }

    @Override
    public void fireExceptionCaught(final Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        DefaultChannelHandlerContext next = this.next;
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop()) {
                try {
                    next.handler().exceptionCaught(next, cause);
                } catch (Throwable t) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "An exception was thrown by a user handler's " +
                                "exceptionCaught() method while handling the following exception:", cause);
                    }
                }
            } else {
                try {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            fireExceptionCaught(cause);
                        }
                    });
                } catch (Throwable t) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Failed to submit an exceptionCaught() event.", t);
                        logger.warn("The exceptionCaught() event that was failed to submit was:", cause);
                    }
                }
            }
        } else {
            logger.warn(
                    "An exceptionCaught() event was fired, and it reached at the end of the " +
                    "pipeline.  It usually means the last inbound handler in the pipeline did not " +
                    "handle the exception.", cause);
        }
    }

    @Override
    public void fireUserEventTriggered(final Object event) {
        if (event == null) {
            throw new NullPointerException("event");
        }

        DefaultChannelHandlerContext next = this.next;
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop()) {
                try {
                    next.handler().userEventTriggered(next, event);
                } catch (Throwable t) {
                    pipeline.notifyHandlerException(t);
                }
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        fireUserEventTriggered(event);
                    }
                });
            }
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
        return pipeline.bind(nextContext(prev, DIR_OUTBOUND), localAddress, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelFuture future) {
        return connect(remoteAddress, null, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
        return pipeline.connect(nextContext(prev, DIR_OUTBOUND), remoteAddress, localAddress, future);
    }

    @Override
    public ChannelFuture disconnect(ChannelFuture future) {
        return pipeline.disconnect(nextContext(prev, DIR_OUTBOUND), future);
    }

    @Override
    public ChannelFuture close(ChannelFuture future) {
        return pipeline.close(nextContext(prev, DIR_OUTBOUND), future);
    }

    @Override
    public ChannelFuture deregister(ChannelFuture future) {
        return pipeline.deregister(nextContext(prev, DIR_OUTBOUND), future);
    }

    @Override
    public ChannelFuture flush(final ChannelFuture future) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext prev = nextContext(this.prev, DIR_OUTBOUND);
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
        final MessageBuf<Object> msgBuf = Unpooled.messageBuffer();
        final BlockingQueue<Object[]> exchangeBuf = QueueFactory.createQueue();

        void fill() {
            if (msgBuf.isEmpty()) {
                return;
            }
            Object[] data = msgBuf.toArray();
            msgBuf.clear();
            exchangeBuf.add(data);
        }

        void flush(MessageBuf<Object> out) {
            for (;;) {
                Object[] data = exchangeBuf.poll();
                if (data == null) {
                    break;
                }

                Collections.addAll(out, data);
            }
        }
    }

    static final class ByteBridge {
        final ByteBuf byteBuf = Unpooled.buffer();
        final BlockingQueue<ByteBuf> exchangeBuf = QueueFactory.createQueue();

        void fill() {
            if (!byteBuf.readable()) {
                return;
            }
            ByteBuf data = byteBuf.readBytes(byteBuf.readableBytes());
            byteBuf.discardReadBytes();
            exchangeBuf.add(data);
        }

        void flush(ByteBuf out) {
            for (;;) {
                ByteBuf data = exchangeBuf.poll();
                if (data == null) {
                    break;
                }

                out.writeBytes(data);
            }
        }
    }
}
