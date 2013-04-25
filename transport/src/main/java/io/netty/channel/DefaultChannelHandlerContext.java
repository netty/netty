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

import io.netty.buffer.Buf;
import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.netty.channel.DefaultChannelPipeline.*;

final class DefaultChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext {

    private static final int FLAG_REMOVED = 1;
    private static final int FLAG_FREED = 2;
    private static final int FLAG_FREED_INBOUND = 4;
    private static final int FLAG_FREED_OUTBOUND = 8;

    volatile DefaultChannelHandlerContext next;
    volatile DefaultChannelHandlerContext prev;

    private final Channel channel;
    private final DefaultChannelPipeline pipeline;
    private final String name;
    private final ChannelHandler handler;

    // Will be set to null if no child executor should be used, otherwise it will be set to the
    // child executor.
    final EventExecutor executor;
    private ChannelFuture succeededFuture;

    private final MessageBuf<Object> inMsgBuf;
    private final ByteBuf inByteBuf;
    private MessageBuf<Object> outMsgBuf;
    private ByteBuf outByteBuf;

    private int flags;

    // When the two handlers run in a different thread and they are next to each other,
    // each other's buffers can be accessed at the same time resulting in a race condition.
    // To avoid such situation, we lazily creates an additional thread-safe buffer called
    // 'bridge' so that the two handlers access each other's buffer only via the bridges.
    // The content written into a bridge is flushed into the actual buffer by flushBridge().
    //
    // Note we use an AtomicReferenceFieldUpdater for atomic operations on these to save memory. This will save us
    // 64 bytes per Bridge.
    @SuppressWarnings("UnusedDeclaration")
    private volatile Queue<Object> inBridge;
    @SuppressWarnings("UnusedDeclaration")
    private volatile Queue<Object> outBridge;
    @SuppressWarnings("UnusedDeclaration")
    private volatile NextBridgeFeeder nextInBridgeFeeder;
    @SuppressWarnings("UnusedDeclaration")
    private volatile NextBridgeFeeder nextOutBridgeFeeder;

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultChannelHandlerContext, Queue> IN_BRIDGE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DefaultChannelHandlerContext.class, Queue.class, "inBridge");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultChannelHandlerContext, Queue> OUT_BRIDGE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DefaultChannelHandlerContext.class, Queue.class, "outBridge");
    private static final AtomicReferenceFieldUpdater<DefaultChannelHandlerContext, NextBridgeFeeder>
            NEXT_IN_BRIDGE_FEEDER = AtomicReferenceFieldUpdater.newUpdater(
                    DefaultChannelHandlerContext.class, NextBridgeFeeder.class, "nextInBridgeFeeder");
    private static final AtomicReferenceFieldUpdater<DefaultChannelHandlerContext, NextBridgeFeeder>
            NEXT_OUT_BRIDGE_FEEDER = AtomicReferenceFieldUpdater.newUpdater(
                    DefaultChannelHandlerContext.class, NextBridgeFeeder.class, "nextOutBridgeFeeder");

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    private Runnable invokeInboundBufferUpdatedTask;
    private Runnable fireInboundBufferUpdated0Task;
    private Runnable invokeChannelReadSuspendedTask;
    private Runnable invokeRead0Task;

    @SuppressWarnings("unchecked")
    DefaultChannelHandlerContext(
            DefaultChannelPipeline pipeline, EventExecutorGroup group, String name, ChannelHandler handler) {

        if (name == null) {
            throw new NullPointerException("name");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        channel = pipeline.channel;
        this.pipeline = pipeline;
        this.name = name;
        this.handler = handler;

        if (group != null) {
            // Pin one of the child executors once and remember it so that the same child executor
            // is used to fire events for the same channel.
            EventExecutor childExecutor = pipeline.childExecutors.get(group);
            if (childExecutor == null) {
                childExecutor = group.next();
                pipeline.childExecutors.put(group, childExecutor);
            }
            executor = childExecutor;
        } else {
            executor = null;
        }

        if (handler instanceof ChannelInboundHandler) {
            Buf buf;
            try {
                buf = ((ChannelInboundHandler) handler).newInboundBuffer(this);
            } catch (Exception e) {
                throw new ChannelPipelineException(
                        handler.getClass().getSimpleName() + ".newInboundBuffer() raised an exception.", e);
            }

            if (buf instanceof ByteBuf) {
                inByteBuf = (ByteBuf) buf;
                inMsgBuf = null;
            } else if (buf instanceof MessageBuf) {
                inMsgBuf = (MessageBuf<Object>) buf;
                inByteBuf = null;
            } else {
                throw new ChannelPipelineException(
                        handler.getClass().getSimpleName() + ".newInboundBuffer() returned neither " +
                        ByteBuf.class.getSimpleName() + " nor " + MessageBuf.class.getSimpleName() + ": " + buf);
            }
        } else {
            inByteBuf = null;
            inMsgBuf = null;
        }

        if (handler instanceof ChannelOutboundHandler) {
            Buf buf;
            try {
                buf = ((ChannelOutboundHandler) handler).newOutboundBuffer(this);
            } catch (Exception e) {
                throw new ChannelPipelineException(
                        handler.getClass().getSimpleName() + ".newOutboundBuffer() raised an exception.", e);
            }

            if (buf instanceof ByteBuf) {
                outByteBuf = (ByteBuf) buf;
            } else if (buf instanceof MessageBuf) {
                @SuppressWarnings("unchecked")
                MessageBuf<Object> msgBuf = (MessageBuf<Object>) buf;
                outMsgBuf = msgBuf;
            } else {
                throw new ChannelPipelineException(
                        handler.getClass().getSimpleName() + ".newOutboundBuffer() returned neither " +
                        ByteBuf.class.getSimpleName() + " nor " + MessageBuf.class.getSimpleName() + ": " + buf);
            }
        }
    }

    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, String name, HeadHandler handler) {
        channel = pipeline.channel;
        this.pipeline = pipeline;
        this.name = name;
        this.handler = handler;
        executor = null;
        inByteBuf = null;
        inMsgBuf = null;
    }

    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, String name, TailHandler handler) {
        channel = pipeline.channel;
        this.pipeline = pipeline;
        this.name = name;
        this.handler = handler;
        executor = null;
        inByteBuf = handler.byteSink;
        inMsgBuf = handler.msgSink;
        outByteBuf = null;
        outMsgBuf = null;
    }

    void forwardBufferContentAndRemove(
            final DefaultChannelHandlerContext forwardPrev, final DefaultChannelHandlerContext forwardNext) {
        try {
            boolean flush = false;
            boolean inboundBufferUpdated = false;
            if (hasOutboundByteBuffer() && outboundByteBuffer().isReadable()) {
                ByteBuf forwardPrevBuf;
                if (forwardPrev.hasOutboundByteBuffer()) {
                    forwardPrevBuf = forwardPrev.outboundByteBuffer();
                } else {
                    forwardPrevBuf = forwardPrev.nextOutboundByteBuffer();
                }
                forwardPrevBuf.writeBytes(outboundByteBuffer());
                flush = true;
            }
            if (hasOutboundMessageBuffer() && !outboundMessageBuffer().isEmpty()) {
                MessageBuf<Object> forwardPrevBuf;
                if (forwardPrev.hasOutboundMessageBuffer()) {
                    forwardPrevBuf = forwardPrev.outboundMessageBuffer();
                } else {
                    forwardPrevBuf = forwardPrev.nextOutboundMessageBuffer();
                }
                if (outboundMessageBuffer().drainTo(forwardPrevBuf) > 0) {
                    flush = true;
                }
            }
            if (hasInboundByteBuffer() && inboundByteBuffer().isReadable()) {
                ByteBuf forwardNextBuf;
                if (forwardNext.hasInboundByteBuffer()) {
                    forwardNextBuf = forwardNext.inboundByteBuffer();
                } else {
                    forwardNextBuf = forwardNext.nextInboundByteBuffer();
                }
                forwardNextBuf.writeBytes(inboundByteBuffer());
                inboundBufferUpdated = true;
            }
            if (hasInboundMessageBuffer() && !inboundMessageBuffer().isEmpty()) {
                MessageBuf<Object> forwardNextBuf;
                if (forwardNext.hasInboundMessageBuffer()) {
                    forwardNextBuf = forwardNext.inboundMessageBuffer();
                } else {
                    forwardNextBuf = forwardNext.nextInboundMessageBuffer();
                }
                if (inboundMessageBuffer().drainTo(forwardNextBuf) > 0) {
                    inboundBufferUpdated = true;
                }
            }
            if (flush) {
                EventExecutor executor = executor();
                Thread currentThread = Thread.currentThread();
                if (executor.inEventLoop(currentThread)) {
                    invokePrevFlush(newPromise(), currentThread, findContextOutboundInclusive(forwardPrev));
                } else {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            invokePrevFlush(newPromise(), Thread.currentThread(),
                                    findContextOutboundInclusive(forwardPrev));
                        }
                    });
                }
            }
            if (inboundBufferUpdated) {
                EventExecutor executor = executor();
                if (executor.inEventLoop()) {
                    fireInboundBufferUpdated0(findContextInboundInclusive(forwardNext));
                } else {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            fireInboundBufferUpdated0(findContextInboundInclusive(forwardNext));
                        }
                    });
                }
            }
        } finally {
            flags |= FLAG_REMOVED;

            // Free all buffers before completing removal.
            if (!channel.isRegistered()) {
                freeHandlerBuffersAfterRemoval();
            }
        }
    }

    private static DefaultChannelHandlerContext findContextOutboundInclusive(DefaultChannelHandlerContext ctx) {
        if (ctx.handler() instanceof ChannelOperationHandler) {
            return ctx;
        }
        return ctx.findContextOutbound();
    }

    private static DefaultChannelHandlerContext findContextInboundInclusive(DefaultChannelHandlerContext ctx) {
        if (ctx.handler() instanceof ChannelStateHandler) {
            return ctx;
        }
        return ctx.findContextInbound();
    }

    void clearBuffer() {
        if (hasOutboundByteBuffer()) {
            outboundByteBuffer().clear();
        }
        if (hasOutboundMessageBuffer()) {
            outboundMessageBuffer().clear();
        }
        if (hasInboundByteBuffer()) {
            inboundByteBuffer().clear();
        }
        if (hasInboundMessageBuffer()) {
            inboundMessageBuffer().clear();
        }
    }

    void initHeadHandler() {
        // Must be called for the head handler.
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            HeadHandler h = (HeadHandler) handler;
            if (h.initialized) {
                return;
            }

            h.init(this);
            h.initialized = true;
            outByteBuf = h.byteSink;
            outMsgBuf = h.msgSink;
        } else {
            Future<?> f = executor.submit(new Runnable() {
                @Override
                public void run() {
                    initHeadHandler();
                }
            });

            boolean interrupted = false;
            try {
                while (!f.isDone()) {
                    try {
                        f.get();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    } catch (ExecutionException e) {
                        PlatformDependent.throwException(e);
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private boolean flushInboundBridge() {
        Queue<Object> inBridge = this.inBridge;
        if (inBridge == null) {
            return true;
        }
        return flushBridge(inBridge, inMsgBuf, inByteBuf);
    }

    private boolean flushOutboundBridge() {
        Queue<Object> outBridge = this.outBridge;
        if (outBridge == null) {
            return true;
        }
        return flushBridge(outBridge, outMsgBuf, outByteBuf);
    }

    private static boolean flushBridge(Queue<Object> bridge, MessageBuf<Object> msgBuf, ByteBuf byteBuf) {
        if (bridge == null) {
            return true;
        }

        boolean nextBufferHadEnoughRoom = true;
        for (;;) {
            Object o = bridge.peek();
            if (o == null) {
                break;
            }

            try {
                if (o instanceof Object[]) {
                    Object[] data = (Object[]) o;
                    int i;
                    for (i = 0; i < data.length; i ++) {
                        Object m = data[i];
                        if (m == null) {
                            break;
                        }

                        if (msgBuf.offer(m)) {
                            data[i] = null;
                        } else {
                            System.arraycopy(data, i, data, 0, data.length - i);
                            for (int j = i + 1; j < data.length; j ++) {
                                data[j] = null;
                            }
                            nextBufferHadEnoughRoom = false;
                            break;
                        }
                    }
                } else if (o instanceof ByteBuf) {
                    ByteBuf data = (ByteBuf) o;
                    if (byteBuf.writerIndex() > byteBuf.maxCapacity() - data.readableBytes()) {
                        // The target buffer is not going to be able to accept all data in the bridge.
                        byteBuf.capacity(byteBuf.maxCapacity());
                        byteBuf.writeBytes(data, byteBuf.writableBytes());
                        nextBufferHadEnoughRoom = false;
                        break;
                    } else {
                        try {
                            byteBuf.writeBytes(data);
                        } finally {
                            data.release();
                        }
                    }
                } else {
                    throw new Error();
                }
            } finally {
                if (nextBufferHadEnoughRoom) {
                    Object removed = bridge.remove();
                    assert removed == o;
                }
            }
        }

        return nextBufferHadEnoughRoom;
    }

    private void freeHandlerBuffersAfterRemoval() {
        int flags = this.flags;
        if ((flags & FLAG_REMOVED) != 0 && (flags & FLAG_FREED) == 0) { // Removed, but not freed yet
            try {
                freeBuffer(inByteBuf);
                freeBuffer(inMsgBuf);
                freeBuffer(outByteBuf);
                freeBuffer(outMsgBuf);
            } finally {
                free();
            }
        }
    }

    private void freeBuffer(Buf buf) {
        if (buf != null) {
            try {
                buf.release();
            } catch (Exception e) {
                notifyHandlerException(e);
            }
        }
    }

    private void free() {
        flags |= FLAG_FREED;
        freeInbound();
        freeOutbound();
    }

    private boolean isInboundFreed() {
        return (flags & FLAG_FREED_INBOUND) != 0;
    }

    private void freeInbound() {
        // Release the bridge feeder
        flags |= FLAG_FREED_INBOUND;

        NextBridgeFeeder feeder;
        feeder = nextInBridgeFeeder;
        if (feeder != null) {
            feeder.release();
            nextInBridgeFeeder = null;
        }

        // Warn if the bridge has unflushed elements.
        if (logger.isWarnEnabled()) {
            Queue<Object> bridge;
            bridge = inBridge;
            if (bridge != null && !bridge.isEmpty()) {
                logger.warn("inbound bridge not empty - bug?: {}", bridge.size());
            }
        }
    }

    private boolean isOutboundFreed() {
        return (flags & FLAG_FREED_OUTBOUND) != 0;
    }

    private void freeOutbound() {
        // Release the bridge feeder
        flags |= FLAG_FREED_OUTBOUND;

        NextBridgeFeeder feeder = nextOutBridgeFeeder;
        if (feeder != null) {
            feeder.release();
            nextOutBridgeFeeder = null;
        }

        // Warn if the bridge has unflushed elements.
        if (logger.isWarnEnabled()) {
            Queue<Object> bridge = outBridge;
            if (bridge != null && !bridge.isEmpty()) {
                logger.warn("outbound bridge not empty - bug?: {}", bridge.size());
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
    public ByteBufAllocator alloc() {
        return channel().config().getAllocator();
    }

    @Override
    public EventExecutor executor() {
        if (executor == null) {
            return channel().eventLoop();
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
            throw new NoSuchBufferException(String.format(
                    "the handler '%s' has no inbound byte buffer; it does not implement %s.",
                    name, ChannelInboundByteHandler.class.getSimpleName()));
        }
        return inByteBuf;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> inboundMessageBuffer() {
        if (inMsgBuf == null) {
            throw new NoSuchBufferException(String.format(
                    "the handler '%s' has no inbound message buffer; it does not implement %s.",
                    name, ChannelInboundMessageHandler.class.getSimpleName()));
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
            throw new NoSuchBufferException(String.format(
                    "the handler '%s' has no outbound byte buffer; it does not implement %s.",
                    name, ChannelOutboundByteHandler.class.getSimpleName()));
        }
        return outByteBuf;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> outboundMessageBuffer() {
        if (outMsgBuf == null) {
            throw new NoSuchBufferException(String.format(
                    "the handler '%s' has no outbound message buffer; it does not implement %s.",
                    name, ChannelOutboundMessageHandler.class.getSimpleName()));
        }
        return (MessageBuf<T>) outMsgBuf;
    }

    @Override
    public ByteBuf nextInboundByteBuffer() {
        DefaultChannelHandlerContext ctx = next;
        for (;;) {
            if (ctx.hasInboundByteBuffer()) {
                Thread currentThread = Thread.currentThread();
                if (ctx.executor().inEventLoop(currentThread)) {
                    return ctx.inByteBuf;
                }
                if (executor().inEventLoop(currentThread)) {
                    return nextInBridgeFeeder().byteBuf;
                }
                throw new IllegalStateException("nextInboundByteBuffer() called from outside the eventLoop");
            }
            ctx = ctx.next;
        }
    }

    @Override
    public MessageBuf<Object> nextInboundMessageBuffer() {
        DefaultChannelHandlerContext ctx = next;
        for (;;) {
            if (ctx.hasInboundMessageBuffer()) {
                Thread currentThread = Thread.currentThread();
                if (ctx.executor().inEventLoop(currentThread)) {
                    return ctx.inMsgBuf;
                }
                if (executor().inEventLoop(currentThread)) {
                    return nextInBridgeFeeder().msgBuf;
                }
                throw new IllegalStateException("nextInboundMessageBuffer() called from outside the eventLoop");
            }
            ctx = ctx.next;
        }
    }

    private NextBridgeFeeder nextInBridgeFeeder() {
        NextBridgeFeeder feeder = nextInBridgeFeeder;
        if (feeder == null) {
            feeder = new NextInboundBridgeFeeder();
            if (!NEXT_IN_BRIDGE_FEEDER.compareAndSet(this, null, feeder)) {
                feeder.release();
                feeder = nextInBridgeFeeder;
            }
        }
        return feeder;
    }

    @Override
    public ByteBuf nextOutboundByteBuffer() {
        DefaultChannelHandlerContext ctx = prev;
        for (;;) {
            if (ctx.hasOutboundByteBuffer()) {
                Thread currentThread = Thread.currentThread();
                if (ctx.executor().inEventLoop(currentThread)) {
                    return ctx.outboundByteBuffer();
                }
                if (executor().inEventLoop(currentThread)) {
                    return nextOutBridgeFeeder().byteBuf;
                }
                throw new IllegalStateException("nextOutboundByteBuffer() called from outside the eventLoop");
            }
            ctx = ctx.prev;
        }
    }

    @Override
    public MessageBuf<Object> nextOutboundMessageBuffer() {
        DefaultChannelHandlerContext ctx = prev;
        for (;;) {
            if (ctx.hasOutboundMessageBuffer()) {
                Thread currentThread = Thread.currentThread();
                if (ctx.executor().inEventLoop(currentThread)) {
                    return ctx.outboundMessageBuffer();
                }
                if (executor().inEventLoop(currentThread)) {
                    return nextOutBridgeFeeder().msgBuf;
                }
                throw new IllegalStateException("nextOutboundMessageBuffer() called from outside the eventLoop");
            }
            ctx = ctx.prev;
        }
    }

    private NextBridgeFeeder nextOutBridgeFeeder() {
        NextBridgeFeeder feeder = nextOutBridgeFeeder;
        if (feeder == null) {
            feeder = new NextOutboundBridgeFeeder();
            if (!NEXT_OUT_BRIDGE_FEEDER.compareAndSet(this, null, feeder)) {
                feeder.release();
                feeder = nextOutBridgeFeeder;
            }
        }
        return feeder;
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelRegistered();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelRegistered();
                }
            });
        }
        return this;
    }

    private void invokeChannelRegistered() {
        try {
            ((ChannelStateHandler) handler()).channelRegistered(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelUnregistered();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelUnregistered();
                }
            });
        }
        return this;
    }

    private void invokeChannelUnregistered() {
        try {
            ((ChannelStateHandler) handler()).channelUnregistered(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelActive();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelActive();
                }
            });
        }
        return this;
    }

    private void invokeChannelActive() {
        try {
            ((ChannelStateHandler) handler()).channelActive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelInactive();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelInactive();
                }
            });
        }
        return this;
    }

    private void invokeChannelInactive() {
        try {
            ((ChannelStateHandler) handler()).channelInactive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        next.invokeExceptionCaught(cause);
        return this;
    }

    private void invokeExceptionCaught(final Throwable cause) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeExceptionCaught0(cause);
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeExceptionCaught0(cause);
                    }
                });
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to submit an exceptionCaught() event.", t);
                    logger.warn("The exceptionCaught() event that was failed to submit was:", cause);
                }
            }
        }
    }

    private void invokeExceptionCaught0(Throwable cause) {
        ChannelHandler handler = handler();
        try {
            handler.exceptionCaught(this, cause);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by a user handler's " +
                        "exceptionCaught() method while handling the following exception:", cause);
            }
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(final Object event) {
        if (event == null) {
            throw new NullPointerException("event");
        }

        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeUserEventTriggered(event);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeUserEventTriggered(event);
                }
            });
        }
        return this;
    }

    private void invokeUserEventTriggered(Object event) {
        ChannelStateHandler handler = (ChannelStateHandler) handler();

        try {
            handler.userEventTriggered(this, event);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelHandlerContext fireInboundBufferUpdated() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            fireInboundBufferUpdated0(findContextInbound());
        } else {
            Runnable task = fireInboundBufferUpdated0Task;
            if (task == null) {
                fireInboundBufferUpdated0Task = task = new Runnable() {
                    @Override
                    public void run() {
                        fireInboundBufferUpdated0(findContextInbound());
                    }
                };
            }
            executor.execute(task);
        }
        return this;
    }

    private void fireInboundBufferUpdated0(final DefaultChannelHandlerContext next) {
        feedNextInBridge();
        // This comparison is safe because this method is always executed from the executor.
        if (next.executor == executor) {
            next.invokeInboundBufferUpdated();
        } else {
            Runnable task = next.invokeInboundBufferUpdatedTask;
            if (task == null) {
                next.invokeInboundBufferUpdatedTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeInboundBufferUpdated();
                    }
                };
            }
            next.executor().execute(task);
        }
    }

    private void feedNextInBridge() {
        NextBridgeFeeder feeder = nextInBridgeFeeder;
        if (feeder != null) {
            feeder.feed();
        }
    }

    private void invokeInboundBufferUpdated() {
        if (isInboundFreed()) {
            return;
        }

        ChannelStateHandler handler = (ChannelStateHandler) handler();
        if (handler instanceof ChannelInboundHandler) {
            for (;;) {
                try {
                    boolean flushedAll = flushInboundBridge();
                    handler.inboundBufferUpdated(this);
                    if (flushedAll) {
                        break;
                    }
                } catch (Throwable t) {
                    notifyHandlerException(t);
                    break;
                } finally {
                    if (handler instanceof ChannelInboundByteHandler && !isInboundFreed()) {
                        try {
                            ((ChannelInboundByteHandler) handler).discardInboundReadBytes(this);
                        } catch (Throwable t) {
                            notifyHandlerException(t);
                        }
                    }
                    freeHandlerBuffersAfterRemoval();
                }
            }
        } else {
            try {
                handler.inboundBufferUpdated(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        }
    }

    @Override
    public ChannelHandlerContext fireChannelReadSuspended() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelReadSuspended();
        } else {
            Runnable task = next.invokeChannelReadSuspendedTask;
            if (task == null) {
                next.invokeChannelReadSuspendedTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeChannelReadSuspended();
                    }
                };
            }
            executor.execute(task);
        }
        return this;
    }

    private void invokeChannelReadSuspended() {
        try {
            ((ChannelStateHandler) handler()).channelReadSuspended(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newPromise());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(newPromise());
    }

    @Override
    public ChannelFuture flush() {
        return flush(newPromise());
    }

    @Override
    public ChannelFuture write(Object message) {
        return write(message, newPromise());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        validateFuture(promise);
        return findContextOutbound().invokeBind(localAddress, promise);
    }

    private ChannelFuture invokeBind(final SocketAddress localAddress, final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeBind0(localAddress, promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeBind0(localAddress, promise);
                }
            });
        }
        return promise;
    }

    private void invokeBind0(SocketAddress localAddress, ChannelPromise promise) {
        try {
            ((ChannelOperationHandler) handler()).bind(this, localAddress, promise);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validateFuture(promise);
        return findContextOutbound().invokeConnect(remoteAddress, localAddress, promise);
    }

    private ChannelFuture invokeConnect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeConnect0(remoteAddress, localAddress, promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeConnect0(remoteAddress, localAddress, promise);
                }
            });
        }

        return promise;
    }

    private void invokeConnect0(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        try {
            ((ChannelOperationHandler) handler()).connect(this, remoteAddress, localAddress, promise);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        validateFuture(promise);

        // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
        // So far, UDP/IP is the only transport that has such behavior.
        if (!channel().metadata().hasDisconnect()) {
            return findContextOutbound().invokeClose(promise);
        }

        return findContextOutbound().invokeDisconnect(promise);
    }

    private ChannelFuture invokeDisconnect(final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeDisconnect0(promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeDisconnect0(promise);
                }
            });
        }

        return promise;
    }

    private void invokeDisconnect0(ChannelPromise promise) {
        try {
            ((ChannelOperationHandler) handler()).disconnect(this, promise);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        validateFuture(promise);
        return findContextOutbound().invokeClose(promise);
    }

    private ChannelFuture invokeClose(final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeClose0(promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeClose0(promise);
                }
            });
        }

        return promise;
    }

    private void invokeClose0(ChannelPromise promise) {
        try {
            ((ChannelOperationHandler) handler()).close(this, promise);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        validateFuture(promise);
        return findContextOutbound().invokeDeregister(promise);
    }

    private ChannelFuture invokeDeregister(final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeDeregister0(promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeDeregister0(promise);
                }
            });
        }

        return promise;
    }

    private void invokeDeregister0(ChannelPromise promise) {
        try {
            ((ChannelOperationHandler) handler()).deregister(this, promise);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public void read() {
        findContextOutbound().invokeRead();
    }

    private void invokeRead() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeRead0();
        } else {
            Runnable task = invokeRead0Task;
            if (task == null) {
                invokeRead0Task = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeRead0();
                    }
                };
            }
            executor.execute(task);
        }
    }

    private void invokeRead0() {
        try {
            ((ChannelOperationHandler) handler()).read(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelFuture flush(final ChannelPromise promise) {
        validateFuture(promise);

        EventExecutor executor = executor();
        Thread currentThread = Thread.currentThread();
        if (executor.inEventLoop(currentThread)) {
            invokePrevFlush(promise, currentThread, findContextOutbound());
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokePrevFlush(promise, Thread.currentThread(), findContextOutbound());
                }
            });
        }

        return promise;
    }

    private void invokePrevFlush(ChannelPromise promise, Thread currentThread, DefaultChannelHandlerContext prev) {
        feedNextOutBridge();
        prev.invokeFlush(promise, currentThread);
    }

    private void feedNextOutBridge() {
        NextBridgeFeeder feeder = nextOutBridgeFeeder;
        if (feeder != null) {
            feeder.feed();
        }
    }

    private ChannelFuture invokeFlush(final ChannelPromise promise, Thread currentThread) {
        EventExecutor executor = executor();
        if (executor.inEventLoop(currentThread)) {
            invokeFlush0(promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeFlush0(promise);
                }
            });
        }

        return promise;
    }

    private void invokeFlush0(ChannelPromise promise) {
        if (isOutboundFreed()) {
            promise.setFailure(new ChannelPipelineException(
                    "Unable to flush as outbound buffer of next handler was freed already"));
            return;
        }

        Channel channel = channel();
        if (!channel.isActive() && !channel.isRegistered()) {
            promise.setFailure(new ClosedChannelException());
            return;
        }

        ChannelOperationHandler handler = (ChannelOperationHandler) handler();
        if (handler instanceof ChannelOutboundHandler) {
            flushOutboundBridge();
        }

        try {
            handler.flush(this, promise);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            if (handler instanceof ChannelOutboundByteHandler && !isOutboundFreed()) {
                try {
                    ((ChannelOutboundByteHandler) handler).discardOutboundReadBytes(this);
                } catch (Throwable t) {
                    notifyHandlerException(t);
                }
            }
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelFuture sendFile(FileRegion region) {
        return sendFile(region, newPromise());
    }

    @Override
    public ChannelFuture sendFile(FileRegion region, ChannelPromise promise) {
        if (region == null) {
            throw new NullPointerException("region");
        }
        validateFuture(promise);
        return findContextOutbound().invokeSendFile(region, promise);
    }

    private ChannelFuture invokeSendFile(final FileRegion region, final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeSendFile0(region, promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeSendFile0(region, promise);
                }
            });
        }

        return promise;
    }

    private void invokeSendFile0(FileRegion region, ChannelPromise promise) {
        ChannelOperationHandler handler = (ChannelOperationHandler) handler();
        if (handler instanceof ChannelOutboundHandler) {
            flushOutboundBridge();
        }

        try {
            handler.sendFile(this, region, promise);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public ChannelFuture write(final Object message, final ChannelPromise promise) {
        if (message instanceof FileRegion) {
            return sendFile((FileRegion) message, promise);
        }

        if (message == null) {
            throw new NullPointerException("message");
        }
        validateFuture(promise);

        DefaultChannelHandlerContext ctx = prev;
        EventExecutor executor;
        final boolean msgBuf;

        if (message instanceof ByteBuf) {
            for (;;) {
                if (ctx.hasOutboundByteBuffer()) {
                    msgBuf = false;
                    executor = ctx.executor();
                    break;
                }

                if (ctx.hasOutboundMessageBuffer()) {
                    msgBuf = true;
                    executor = ctx.executor();
                    break;
                }

                ctx = ctx.prev;
            }
        } else {
            msgBuf = true;
            for (;;) {
                if (ctx.hasOutboundMessageBuffer()) {
                    executor = ctx.executor();
                    break;
                }

                ctx = ctx.prev;
            }
        }

        if (executor.inEventLoop()) {
            ctx.write0(message, promise, msgBuf);
            return promise;
        }

        final DefaultChannelHandlerContext ctx0 = ctx;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                ctx0.write0(message, promise, msgBuf);
            }
        });

        return promise;
    }

    private void write0(Object message, ChannelPromise promise, boolean msgBuf) {
        Channel channel = channel();
        if (!channel.isRegistered() && !channel.isActive()) {
            promise.setFailure(new ClosedChannelException());
            return;
        }

        if (isOutboundFreed()) {
            promise.setFailure(new ChannelPipelineException(
                    "Unable to write as outbound buffer of next handler was freed already"));
            return;
        }
        if (msgBuf) {
            outboundMessageBuffer().add(message);
        } else {
            ByteBuf buf = (ByteBuf) message;
            try {
                outboundByteBuffer().writeBytes(buf, buf.readerIndex(), buf.readableBytes());
            } finally {
                buf.release();
            }
        }
        invokeFlush0(promise);
    }

    void invokeFreeInboundBuffer() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeFreeInboundBuffer0();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeFreeInboundBuffer0();
                }
            });
        }
    }

    private void invokeFreeInboundBuffer0() {
        try {
            freeBuffer(inByteBuf);
            freeBuffer(inMsgBuf);
        } finally {
            freeInbound();
        }

        if (next != null) {
            DefaultChannelHandlerContext nextCtx = findContextInbound();
            nextCtx.invokeFreeInboundBuffer();
        } else {
            // Freed all inbound buffers. Free all outbound buffers in a reverse order.
            findContextOutbound().invokeFreeOutboundBuffer();
        }
    }

    /** Invocation initiated by {@link #invokeFreeInboundBuffer0()} after freeing all inbound buffers. */
    private void invokeFreeOutboundBuffer() {
        EventExecutor executor = executor();
        if (next == null) {
            if (executor.inEventLoop()) {
                invokeFreeOutboundBuffer0();
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeFreeOutboundBuffer0();
                    }
                });
            }
        } else {
            if (executor.inEventLoop()) {
                invokeFreeOutboundBuffer0();
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeFreeOutboundBuffer0();
                    }
                });
            }
        }
    }

    private void invokeFreeOutboundBuffer0() {
        try {
            freeBuffer(outByteBuf);
            freeBuffer(outMsgBuf);
        } finally {
            freeOutbound();
        }

        if (prev != null) {
            findContextOutbound().invokeFreeOutboundBuffer();
        }
    }

    private void notifyHandlerException(Throwable cause) {
        if (inExceptionCaught(cause)) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by a user handler " +
                                "while handling an exceptionCaught event", cause);
            }
            return;
        }

        invokeExceptionCaught(cause);
    }

    private static boolean inExceptionCaught(Throwable cause) {
        do {
            StackTraceElement[] trace = cause.getStackTrace();
            if (trace != null) {
                for (StackTraceElement t : trace) {
                    if (t == null) {
                        break;
                    }
                    if ("exceptionCaught".equals(t.getMethodName())) {
                        return true;
                    }
                }
            }

            cause = cause.getCause();
        } while (cause != null);

        return false;
    }

    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel(), executor());
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return new DefaultChannelProgressivePromise(channel(), executor());
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        ChannelFuture succeededFuture = this.succeededFuture;
        if (succeededFuture == null) {
            this.succeededFuture = succeededFuture = new SucceededChannelFuture(channel(), executor());
        }
        return succeededFuture;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(channel(), executor(), cause);
    }

    private void validateFuture(ChannelFuture future) {
        if (future == null) {
            throw new NullPointerException("future");
        }
        if (future.channel() != channel()) {
            throw new IllegalArgumentException(String.format(
                    "future.channel does not match: %s (expected: %s)", future.channel(), channel()));
        }
        if (future.isDone()) {
            throw new IllegalArgumentException("future already done");
        }
        if (future instanceof ChannelFuture.Unsafe) {
            throw new IllegalArgumentException("internal use only future not allowed");
        }
    }

    private DefaultChannelHandlerContext findContextInbound() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while (!(ctx.handler() instanceof ChannelStateHandler));
        return ctx;
    }

    @Override
    public BufType nextInboundBufferType() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while (!(ctx.handler() instanceof ChannelInboundHandler));

        if (ctx.handler() instanceof ChannelInboundByteHandler) {
            return BufType.BYTE;
        }  else {
            return BufType.MESSAGE;
        }
    }

    @Override
    public BufType nextOutboundBufferType() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while (!(ctx.handler() instanceof ChannelOutboundHandler));

        if (ctx.handler() instanceof ChannelOutboundByteHandler) {
            return BufType.BYTE;
        }  else {
            return BufType.MESSAGE;
        }
    }

    private DefaultChannelHandlerContext findContextOutbound() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while (!(ctx.handler() instanceof ChannelOperationHandler));
        return ctx;
    }

    private abstract class NextBridgeFeeder {
        final MessageBuf<Object> msgBuf;
        final ByteBuf byteBuf;

        protected NextBridgeFeeder() {
            msgBuf = Unpooled.messageBuffer();
            byteBuf = ChannelHandlerUtil.allocate(DefaultChannelHandlerContext.this);
        }

        final void feed() {
            int dataLen = byteBuf.readableBytes();
            if (dataLen != 0) {
                ByteBuf data;
                if (byteBuf.isDirect()) {
                    data = alloc().directBuffer(dataLen, dataLen);
                } else {
                    data = alloc().heapBuffer(dataLen, dataLen);
                }

                byteBuf.readBytes(data).discardSomeReadBytes();
                nextByteBridge().add(data);
            }

            if (!msgBuf.isEmpty()) {
                Object[] data = msgBuf.toArray();
                msgBuf.clear();
                nextMessageBridge().add(data);
            }
        }

        final void release() {
            byteBuf.release();
            msgBuf.release();
        }

        protected abstract Queue<Object> nextByteBridge();
        protected abstract Queue<Object> nextMessageBridge();
    }

    private final class NextInboundBridgeFeeder extends NextBridgeFeeder {
        @Override
        protected Queue<Object> nextByteBridge() {
            DefaultChannelHandlerContext ctx = next;
            for (;;) {
                if (ctx.hasInboundByteBuffer()) {
                    break;
                }
                ctx = ctx.next;
            }

            return bridge(ctx);
        }

        @Override
        protected Queue<Object> nextMessageBridge() {
            DefaultChannelHandlerContext ctx = next;
            for (;;) {
                if (ctx.hasInboundMessageBuffer()) {
                    break;
                }
                ctx = ctx.next;
            }

            return bridge(ctx);
        }

        private Queue<Object> bridge(DefaultChannelHandlerContext ctx) {
            Queue<Object> bridge = ctx.inBridge;
            if (bridge == null) {
                Queue<Object> newBridge = new ConcurrentLinkedQueue<Object>();
                if (IN_BRIDGE_UPDATER.compareAndSet(ctx, null, newBridge)) {
                    bridge = newBridge;
                } else {
                    bridge = ctx.inBridge;
                }
            }
            return bridge;
        }
    }

    private final class NextOutboundBridgeFeeder extends NextBridgeFeeder {
        @Override
        protected Queue<Object> nextByteBridge() {
            DefaultChannelHandlerContext ctx = prev;
            for (;;) {
                if (ctx.hasOutboundByteBuffer()) {
                    break;
                }
                ctx = ctx.prev;
            }

            return bridge(ctx);
        }

        @Override
        protected Queue<Object> nextMessageBridge() {
            DefaultChannelHandlerContext ctx = prev;
            for (;;) {
                if (ctx.hasOutboundMessageBuffer()) {
                    break;
                }
                ctx = ctx.prev;
            }

            return bridge(ctx);
        }

        private Queue<Object> bridge(DefaultChannelHandlerContext ctx) {
            Queue<Object> bridge = ctx.outBridge;
            if (bridge == null) {
                Queue<Object> newBridge = new ConcurrentLinkedQueue<Object>();
                if (OUT_BRIDGE_UPDATER.compareAndSet(ctx, null, newBridge)) {
                    bridge = newBridge;
                } else {
                    bridge = ctx.outBridge;
                }
            }
            return bridge;
        }
    }
}
