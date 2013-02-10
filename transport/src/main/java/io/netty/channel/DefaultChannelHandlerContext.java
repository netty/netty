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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.DefaultAttributeMap;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.netty.channel.DefaultChannelPipeline.*;

final class DefaultChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext {

    private static final EnumSet<ChannelHandlerType> EMPTY_TYPE = EnumSet.noneOf(ChannelHandlerType.class);

    volatile DefaultChannelHandlerContext next;
    volatile DefaultChannelHandlerContext prev;

    private final Channel channel;
    private final DefaultChannelPipeline pipeline;
    private final String name;
    private final Set<ChannelHandlerType> type;
    private final ChannelHandler handler;
    private boolean needsLazyBufInit;

    // Will be set to null if no child executor should be used, otherwise it will be set to the
    // child executor.
    final EventExecutor executor;

    private MessageBuf<Object> inMsgBuf;
    private ByteBuf inByteBuf;
    private MessageBuf<Object> outMsgBuf;
    private ByteBuf outByteBuf;

    // When the two handlers run in a different thread and they are next to each other,
    // each other's buffers can be accessed at the same time resulting in a race condition.
    // To avoid such situation, we lazily creates an additional thread-safe buffer called
    // 'bridge' so that the two handlers access each other's buffer only via the bridges.
    // The content written into a bridge is flushed into the actual buffer by flushBridge().
    //
    // Note we use an AtomicReferenceFieldUpdater for atomic operations on these to safe memory. This will safe us
    // 64 bytes per Bridge.
    @SuppressWarnings("UnusedDeclaration")
    private volatile MessageBridge inMsgBridge;
    @SuppressWarnings("UnusedDeclaration")
    private volatile MessageBridge outMsgBridge;
    @SuppressWarnings("UnusedDeclaration")
    private volatile ByteBridge inByteBridge;
    @SuppressWarnings("UnusedDeclaration")
    private volatile ByteBridge outByteBridge;

    private static final AtomicReferenceFieldUpdater<DefaultChannelHandlerContext, MessageBridge> IN_MSG_BRIDGE_UPDATER
            = AtomicReferenceFieldUpdater.newUpdater(DefaultChannelHandlerContext.class,
                MessageBridge.class, "inMsgBridge");

    private static final AtomicReferenceFieldUpdater<DefaultChannelHandlerContext, MessageBridge> OUT_MSG_BRIDGE_UPDATER
            = AtomicReferenceFieldUpdater.newUpdater(DefaultChannelHandlerContext.class,
                MessageBridge.class, "outMsgBridge");

    private static final AtomicReferenceFieldUpdater<DefaultChannelHandlerContext, ByteBridge> IN_BYTE_BRIDGE_UPDATER
            =  AtomicReferenceFieldUpdater.newUpdater(DefaultChannelHandlerContext.class,
                ByteBridge.class, "inByteBridge");
    private static final AtomicReferenceFieldUpdater<DefaultChannelHandlerContext, ByteBridge> OUT_BYTE_BRIDGE_UPDATER
            = AtomicReferenceFieldUpdater.newUpdater(DefaultChannelHandlerContext.class,
                ByteBridge.class, "outByteBridge");

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    private Runnable invokeChannelRegisteredTask;
    private Runnable invokeChannelUnregisteredTask;
    private Runnable invokeChannelActiveTask;
    private Runnable invokeChannelInactiveTask;
    private Runnable invokeInboundBufferUpdatedTask;
    private Runnable fireInboundBufferUpdated0Task;
    private Runnable invokeChannelReadSuspendedTask;
    private Runnable invokeFreeInboundBuffer0Task;
    private Runnable invokeFreeOutboundBuffer0Task;
    private Runnable invokeRead0Task;
    volatile boolean removed;

    @SuppressWarnings("unchecked")
    DefaultChannelHandlerContext(
            DefaultChannelPipeline pipeline, EventExecutorGroup group, String name, ChannelHandler handler) {

        if (name == null) {
            throw new NullPointerException("name");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        // Determine the type of the specified handler.
        EnumSet<ChannelHandlerType> type = EMPTY_TYPE.clone();
        if (handler instanceof ChannelStateHandler) {
            type.add(ChannelHandlerType.STATE);
            if (handler instanceof ChannelInboundHandler) {
                type.add(ChannelHandlerType.INBOUND);
            }
        }
        if (handler instanceof ChannelOperationHandler) {
            type.add(ChannelHandlerType.OPERATION);
            if (handler instanceof ChannelOutboundHandler) {
                type.add(ChannelHandlerType.OUTBOUND);
            }
        }
        this.type = Collections.unmodifiableSet(type);

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
            } else if (buf instanceof MessageBuf) {
                inMsgBuf = (MessageBuf<Object>) buf;
            } else {
                throw new ChannelPipelineException(
                        handler.getClass().getSimpleName() + ".newInboundBuffer() returned neither " +
                        ByteBuf.class.getSimpleName() + " nor " + MessageBuf.class.getSimpleName() + ": " + buf);
            }
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
        type = null;
        channel = pipeline.channel;
        this.pipeline = pipeline;
        this.name = name;
        this.handler = handler;
        executor = null;
        needsLazyBufInit = true;
    }

    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, String name, TailHandler handler) {
        type = null;
        channel = pipeline.channel;
        this.pipeline = pipeline;
        this.name = name;
        this.handler = handler;
        executor = null;
        inByteBuf = handler.byteSink;
        inMsgBuf = handler.msgSink;
    }

    void forwardBufferContent() {
        if (hasOutboundByteBuffer() && outboundByteBuffer().isReadable()) {
            nextOutboundByteBuffer().writeBytes(outboundByteBuffer());
            flush();
        }
        if (hasOutboundMessageBuffer() && !outboundMessageBuffer().isEmpty()) {
            if (outboundMessageBuffer().drainTo(nextOutboundMessageBuffer()) > 0) {
                flush();
            }
        }
        if (hasInboundByteBuffer() && inboundByteBuffer().isReadable()) {
            nextInboundByteBuffer().writeBytes(inboundByteBuffer());
            fireInboundBufferUpdated();
        }
        if (hasInboundMessageBuffer() && !inboundMessageBuffer().isEmpty()) {
            if (inboundMessageBuffer().drainTo(nextInboundMessageBuffer()) > 0) {
                fireInboundBufferUpdated();
            }
        }
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

    private void lazyInitHeadHandler() {
        if (needsLazyBufInit) {
            EventExecutor exec = executor();
            if (exec.inEventLoop()) {
                if (needsLazyBufInit) {
                    needsLazyBufInit = false;
                    HeadHandler headHandler = (HeadHandler) handler;
                    headHandler.init(this);
                    outByteBuf = headHandler.byteSink;
                    outMsgBuf = headHandler.msgSink;
                }
            } else {
                try {
                    getFromFuture(exec.submit(new Runnable() {
                        @Override
                        public void run() {
                            lazyInitHeadHandler();
                        }
                    }));
                } catch (Exception e) {
                    throw new ChannelPipelineException("failed to initialize an outbound buffer lazily", e);
                }
            }
        }
    }

    private void fillBridge() {
        if (inMsgBridge != null) {
            MessageBridge bridge = inMsgBridge;
            if (bridge != null) {
                bridge.fill();
            }
        } else if (inByteBridge != null) {
            ByteBridge bridge = inByteBridge;
            if (bridge != null) {
                bridge.fill();
            }
        }

        if (outMsgBridge != null) {
            MessageBridge bridge = outMsgBridge;
            if (bridge != null) {
                bridge.fill();
            }
        } else if (outByteBridge != null) {
            ByteBridge bridge = outByteBridge;
            if (bridge != null) {
                bridge.fill();
            }
        }
    }

    private void flushBridge() {
        if (inMsgBridge != null) {
            MessageBridge bridge = inMsgBridge;
            if (bridge != null) {
                bridge.flush(inMsgBuf);
            }
        } else if (inByteBridge != null) {
            ByteBridge bridge = inByteBridge;
            if (bridge != null) {
                bridge.flush(inByteBuf);
            }
        }

        if (outMsgBridge != null) {
            MessageBridge bridge = outMsgBridge;
            if (bridge != null) {
                bridge.flush(outMsgBuf);
            }
        } else if (outByteBridge != null) {
            ByteBridge bridge = outByteBridge;
            if (bridge != null) {
                bridge.flush(outByteBuf);
            }
        }
    }

    void freeHandlerBuffersAfterRemoval() {
        if (!removed) {
            return;
        }
        final ChannelHandler handler = handler();

        if (handler instanceof ChannelInboundHandler) {
            try {
                ((ChannelInboundHandler) handler).freeInboundBuffer(this);
            } catch (Exception e) {
                pipeline.notifyHandlerException(e);
            }
        }
        if (handler instanceof ChannelOutboundHandler) {
            try {
                ((ChannelOutboundHandler) handler).freeOutboundBuffer(this);
            } catch (Exception e) {
                pipeline.notifyHandlerException(e);
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
    public Set<ChannelHandlerType> types() {
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

    /**
     * Executes a task on the event loop and waits for it to finish.  If the task is interrupted, then the
     * current thread will be interrupted and this will return {@code null}.  It is expected that the task
     * performs any appropriate locking.
     * <p>
     * If the {@link Callable#call()} call throws a {@link Throwable}, but it is not an instance of
     * {@link Error}, {@link RuntimeException}, or {@link Exception}, then it is wrapped inside an
     * {@link AssertionError} and that is thrown instead.</p>
     *
     * @param c execute this callable and return its value
     * @param <T> the return value type
     * @return the task's return value, or {@code null} if the task was interrupted.
     * @see Callable#call()
     * @see Future#get()
     * @throws Error if the task threw this.
     * @throws RuntimeException if the task threw this.
     * @throws Exception if the task threw this.
     * @throws ChannelPipelineException with a {@link Throwable} as a cause, if the task threw another type of
     *         {@link Throwable}.
     */
    private <T> T executeOnEventLoop(Callable<T> c) throws Exception {
        return getFromFuture(executor().submit(c));
    }

    /**
     * Executes a task on the event loop and waits for it to finish.  If the task is interrupted, then the
     * current thread will be interrupted.  It is expected that the task performs any appropriate locking.
     * <p>
     * If the {@link Runnable#run()} call throws a {@link Throwable}, but it is not an instance of
     * {@link Error} or {@link RuntimeException}, then it is wrapped inside a
     * {@link ChannelPipelineException} and that is thrown instead.</p>
     *
     * @param r execute this runnable
     * @see Runnable#run()
     * @see Future#get()
     * @throws Error if the task threw this.
     * @throws RuntimeException if the task threw this.
     * @throws ChannelPipelineException with a {@link Throwable} as a cause, if the task threw another type of
     *         {@link Throwable}.
     */
    void executeOnEventLoop(Runnable r) {
        waitForFuture(executor().submit(r));
    }

    /**
     * Waits for a future to finish and gets the result.  If the task is interrupted, then the current thread
     * will be interrupted and this will return {@code null}. It is expected that the task performs any
     * appropriate locking.
     * <p>
     * If the internal call throws a {@link Throwable}, but it is not an instance of {@link Error},
     * {@link RuntimeException}, or {@link Exception}, then it is wrapped inside an {@link AssertionError}
     * and that is thrown instead.</p>
     *
     * @param future wait for this future
     * @param <T> the return value type
     * @return the task's return value, or {@code null} if the task was interrupted.
     * @see Future#get()
     * @throws Error if the task threw this.
     * @throws RuntimeException if the task threw this.
     * @throws Exception if the task threw this.
     * @throws ChannelPipelineException with a {@link Throwable} as a cause, if the task threw another type of
     *         {@link Throwable}.
     */
    private static <T> T getFromFuture(Future<T> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            // In the arbitrary case, we can throw Error, RuntimeException, and Exception

            Throwable t = ex.getCause();
            if (t instanceof Error) { throw (Error) t; }
            if (t instanceof RuntimeException) { throw (RuntimeException) t; }
            if (t instanceof Exception) { throw (Exception) t; }
            throw new ChannelPipelineException(t);
        } catch (InterruptedException ex) {
            // Interrupt the calling thread (note that this method is not called from the event loop)

            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Waits for a future to finish.  If the task is interrupted, then the current thread will be interrupted.
     * It is expected that the task performs any appropriate locking.
     * <p>
     * If the internal call throws a {@link Throwable}, but it is not an instance of {@link Error} or
     * {@link RuntimeException}, then it is wrapped inside a {@link ChannelPipelineException} and that is
     * thrown instead.</p>
     *
     * @param future wait for this future
     * @see Future#get()
     * @throws Error if the task threw this.
     * @throws RuntimeException if the task threw this.
     * @throws ChannelPipelineException with a {@link Throwable} as a cause, if the task threw another type of
     *         {@link Throwable}.
     */
    static void waitForFuture(Future<?> future) {
        try {
            future.get();
        } catch (ExecutionException ex) {
            // In the arbitrary case, we can throw Error, RuntimeException, and Exception

            Throwable t = ex.getCause();
            if (t instanceof Error) { throw (Error) t; }
            if (t instanceof RuntimeException) { throw (RuntimeException) t; }
            throw new ChannelPipelineException(t);
        } catch (InterruptedException ex) {
            // Interrupt the calling thread (note that this method is not called from the event loop)

            Thread.currentThread().interrupt();
        }
    }

    @Override
    public ByteBuf replaceInboundByteBuffer(final ByteBuf newInboundByteBuf) {
        if (newInboundByteBuf == null) {
            throw new NullPointerException("newInboundByteBuf");
        }

        if (!executor().inEventLoop()) {
            try {
                return executeOnEventLoop(new Callable<ByteBuf>() {
                        @Override
                        public ByteBuf call() {
                            return replaceInboundByteBuffer(newInboundByteBuf);
                        }
                    });
            } catch (Exception ex) {
                throw new ChannelPipelineException("failed to replace an inbound byte buffer", ex);
            }
        }

        ByteBuf currentInboundByteBuf = inboundByteBuffer();

        inByteBuf = newInboundByteBuf;
        return currentInboundByteBuf;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> replaceInboundMessageBuffer(final MessageBuf<T> newInboundMsgBuf) {
        if (newInboundMsgBuf == null) {
            throw new NullPointerException("newInboundMsgBuf");
        }

        if (!executor().inEventLoop()) {
            try {
                return executeOnEventLoop(new Callable<MessageBuf<T>>() {
                    @Override
                    public MessageBuf<T> call() {
                        return replaceInboundMessageBuffer(newInboundMsgBuf);
                    }
                });
            } catch (Exception ex) {
                throw new ChannelPipelineException("failed to replace an inbound message buffer", ex);
            }
        }

        MessageBuf<T> currentInboundMsgBuf = inboundMessageBuffer();

        inMsgBuf = (MessageBuf<Object>) newInboundMsgBuf;
        return currentInboundMsgBuf;
    }

    @Override
    public ByteBuf replaceOutboundByteBuffer(final ByteBuf newOutboundByteBuf) {
        if (newOutboundByteBuf == null) {
            throw new NullPointerException("newOutboundByteBuf");
        }

        if (!executor().inEventLoop()) {
            try {
                return executeOnEventLoop(new Callable<ByteBuf>() {
                    @Override
                    public ByteBuf call() {
                        return replaceOutboundByteBuffer(newOutboundByteBuf);
                    }
                });
            } catch (Exception ex) {
                throw new ChannelPipelineException("failed to replace an outbound byte buffer", ex);
            }
        }

        ByteBuf currentOutboundByteBuf = outboundByteBuffer();

        outByteBuf = newOutboundByteBuf;
        return currentOutboundByteBuf;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> replaceOutboundMessageBuffer(final MessageBuf<T> newOutboundMsgBuf) {
        if (newOutboundMsgBuf == null) {
            throw new NullPointerException("newOutboundMsgBuf");
        }

        if (!executor().inEventLoop()) {
            try {
                return executeOnEventLoop(new Callable<MessageBuf<T>>() {
                    @Override
                    public MessageBuf<T> call() {
                        return replaceOutboundMessageBuffer(newOutboundMsgBuf);
                    }
                });
            } catch (Exception ex) {
                throw new ChannelPipelineException("failed to replace an outbound message buffer", ex);
            }
        }

        MessageBuf<T> currentOutboundMsgBuf = outboundMessageBuffer();

        outMsgBuf = (MessageBuf<Object>) newOutboundMsgBuf;
        return currentOutboundMsgBuf;
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
                    ByteBridge bridge = ctx.inByteBridge;
                    if (bridge == null) {
                        bridge = new ByteBridge(ctx);
                        if (!IN_BYTE_BRIDGE_UPDATER.compareAndSet(ctx, null, bridge)) {
                            bridge = ctx.inByteBridge;
                        }
                    }
                    return bridge.byteBuf;
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
                    MessageBridge bridge = ctx.inMsgBridge;
                    if (bridge == null) {
                        bridge = new MessageBridge();
                        if (!IN_MSG_BRIDGE_UPDATER.compareAndSet(ctx, null, bridge)) {
                            bridge = ctx.inMsgBridge;
                        }
                    }
                    return bridge.msgBuf;
                }
                throw new IllegalStateException("nextInboundMessageBuffer() called from outside the eventLoop");
            }
            ctx = ctx.next;
        }
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
                    ByteBridge bridge = ctx.outByteBridge;
                    if (bridge == null) {
                        bridge = new ByteBridge(ctx);
                        if (!OUT_BYTE_BRIDGE_UPDATER.compareAndSet(ctx, null, bridge)) {
                            bridge = ctx.outByteBridge;
                        }
                    }
                    return bridge.byteBuf;
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
                    MessageBridge bridge = ctx.outMsgBridge;
                    if (bridge == null) {
                        bridge = new MessageBridge();
                        if (!OUT_MSG_BRIDGE_UPDATER.compareAndSet(ctx, null, bridge)) {
                            bridge = ctx.outMsgBridge;
                        }
                    }
                    return bridge.msgBuf;
                }
                throw new IllegalStateException("nextOutboundMessageBuffer() called from outside the eventLoop");
            }
            ctx = ctx.prev;
        }
    }

    @Override
    public void fireChannelRegistered() {
        lazyInitHeadHandler();
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelRegistered();
        } else {
            Runnable task = next.invokeChannelRegisteredTask;
            if (task == null) {
                next.invokeChannelRegisteredTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeChannelRegistered();
                    }
                };
            }
            executor.execute(task);
        }
    }

    private void invokeChannelRegistered() {
        try {
            ((ChannelStateHandler) handler()).channelRegistered(this);
        } catch (Throwable t) {
            pipeline.notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public void fireChannelUnregistered() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (prev != null && executor.inEventLoop()) {
            next.invokeChannelUnregistered();
        } else {
            Runnable task = next.invokeChannelUnregisteredTask;
            if (task == null) {
                next.invokeChannelUnregisteredTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeChannelUnregistered();
                    }
                };
            }
            executor.execute(task);
        }
    }

    private void invokeChannelUnregistered() {
        try {
            ((ChannelStateHandler) handler()).channelUnregistered(this);
        } catch (Throwable t) {
            pipeline.notifyHandlerException(t);
        }
    }

    @Override
    public void fireChannelActive() {
        lazyInitHeadHandler();
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelActive();
        } else {
            Runnable task = next.invokeChannelActiveTask;
            if (task == null) {
                next.invokeChannelActiveTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeChannelActive();
                    }
                };
            }
            executor.execute(task);
        }
    }

    private void invokeChannelActive() {
        try {
            ((ChannelStateHandler) handler()).channelActive(this);
        } catch (Throwable t) {
            pipeline.notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public void fireChannelInactive() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (prev != null && executor.inEventLoop()) {
            next.invokeChannelInactive();
        } else {
            Runnable task = next.invokeChannelInactiveTask;
            if (task == null) {
                next.invokeChannelInactiveTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeChannelInactive();
                    }
                };
            }
            executor.execute(task);
        }
    }

    private void invokeChannelInactive() {
        try {
            ((ChannelStateHandler) handler()).channelInactive(this);
        } catch (Throwable t) {
            pipeline.notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public void fireExceptionCaught(final Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        final DefaultChannelHandlerContext next = this.next;
        EventExecutor executor = next.executor();
        if (prev != null && executor.inEventLoop()) {
            next.invokeExceptionCaught(cause);
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        next.invokeExceptionCaught(cause);
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

    private void invokeExceptionCaught(Throwable cause) {
        try {
            handler().exceptionCaught(this, cause);
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
    public void fireUserEventTriggered(final Object event) {
        if (event == null) {
            throw new NullPointerException("event");
        }

        final DefaultChannelHandlerContext next = this.next;
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
    }

    private void invokeUserEventTriggered(Object event) {
        try {
            handler().userEventTriggered(this, event);
        } catch (Throwable t) {
            pipeline.notifyHandlerException(t);
        } finally {
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public void fireInboundBufferUpdated() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            fireInboundBufferUpdated0();
        } else {
            Runnable task = fireInboundBufferUpdated0Task;
            if (task == null) {
                fireInboundBufferUpdated0Task = task = new Runnable() {
                    @Override
                    public void run() {
                        fireInboundBufferUpdated0();
                    }
                };
            }
            executor.execute(task);
        }
    }

    private void fireInboundBufferUpdated0() {
        final DefaultChannelHandlerContext next = findContextInbound();
        if (!next.isInboundBufferFreed()) {
            next.fillBridge();
            // This comparison is safe because this method is always executed from the executor.
            if (next.executor == executor) {
                next.invokeInboundBufferUpdated();
            } else {
                Runnable task = next.invokeInboundBufferUpdatedTask;
                if (task == null) {
                    next.invokeInboundBufferUpdatedTask = task = new Runnable() {
                        @Override
                        public void run() {
                            if (!next.isInboundBufferFreed()) {
                                next.invokeInboundBufferUpdated();
                            }
                        }
                    };
                }
                next.executor().execute(task);
            }
        }
    }

    private void invokeInboundBufferUpdated() {
        ChannelStateHandler handler = (ChannelStateHandler) handler();
        flushBridge();
        try {
            handler.inboundBufferUpdated(this);
        } catch (Throwable t) {
            pipeline.notifyHandlerException(t);
        } finally {
            if (handler instanceof ChannelInboundByteHandler && !isInboundBufferFreed()) {
                try {
                    ((ChannelInboundByteHandler) handler).discardInboundReadBytes(this);
                } catch (Throwable t) {
                    pipeline.notifyHandlerException(t);
                }
            }
            freeHandlerBuffersAfterRemoval();
        }
    }

    @Override
    public void fireChannelReadSuspended() {
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
    }

    private void invokeChannelReadSuspended() {
        try {
            ((ChannelStateHandler) handler()).channelReadSuspended(this);
        } catch (Throwable t) {
            pipeline.notifyHandlerException(t);
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
            pipeline.notifyHandlerException(t);
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
            pipeline.notifyHandlerException(t);
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
            pipeline.notifyHandlerException(t);
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
            pipeline.notifyHandlerException(t);
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
            pipeline.notifyHandlerException(t);
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
            pipeline.notifyHandlerException(t);
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
            invokePrevFlush(promise, currentThread);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokePrevFlush(promise, Thread.currentThread());
                }
            });
        }

        return promise;
    }

    private void invokePrevFlush(ChannelPromise promise, Thread currentThread) {
        DefaultChannelHandlerContext prev = findContextOutbound();
        if (prev.isOutboundBufferFreed()) {
            promise.setFailure(new ChannelPipelineException(
                    "Unable to flush as outbound buffer of next handler was freed already"));
            return;
        }
        prev.fillBridge();
        prev.invokeFlush(promise, currentThread);
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
        Channel channel = channel();
        if (!channel.isRegistered() && !channel.isActive()) {
            promise.setFailure(new ClosedChannelException());
            return;
        }

        ChannelOperationHandler handler = (ChannelOperationHandler) handler();
        try {
            flushBridge();
            handler.flush(this, promise);
        } catch (Throwable t) {
            pipeline.notifyHandlerException(t);
        } finally {
            if (handler instanceof ChannelOutboundByteHandler && !isOutboundBufferFreed()) {
                try {
                    ((ChannelOutboundByteHandler) handler).discardOutboundReadBytes(this);
                } catch (Throwable t) {
                    pipeline.notifyHandlerException(t);
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
        try {
            flushBridge();
            ((ChannelOperationHandler) handler()).sendFile(this, region, promise);
        } catch (Throwable t) {
            pipeline.notifyHandlerException(t);
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

        if (isOutboundBufferFreed()) {
            promise.setFailure(new ChannelPipelineException(
                    "Unable to write as outbound buffer of next handler was freed already"));
            return;
        }
        if (msgBuf) {
            outboundMessageBuffer().add(message);
        } else {
            ByteBuf buf = (ByteBuf) message;
            outboundByteBuffer().writeBytes(buf, buf.readerIndex(), buf.readableBytes());
        }
        invokeFlush0(promise);
    }

    void invokeFreeInboundBuffer() {
        pipeline.inboundBufferFreed = true;
        EventExecutor executor = executor();
        if (prev != null && executor.inEventLoop()) {
            invokeFreeInboundBuffer0();
        } else {
            Runnable task = invokeFreeInboundBuffer0Task;
            if (task == null) {
                invokeFreeInboundBuffer0Task = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeFreeInboundBuffer0();
                    }
                };
            }
            executor.execute(task);
        }
    }

    private void invokeFreeInboundBuffer0() {
        ChannelHandler handler = handler();
        if (handler instanceof ChannelInboundHandler) {
            ChannelInboundHandler h = (ChannelInboundHandler) handler;
            try {
                h.freeInboundBuffer(this);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
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
        pipeline.outboundBufferFreed = true;
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeFreeOutboundBuffer0();
        } else {
            Runnable task = invokeFreeOutboundBuffer0Task;
            if (task == null) {
                invokeFreeOutboundBuffer0Task = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeFreeOutboundBuffer0();
                    }
                };
            }
            executor.execute(task);
        }
    }

    private void invokeFreeOutboundBuffer0() {
        ChannelHandler handler = handler();
        if (handler instanceof ChannelOutboundHandler) {
            ChannelOutboundHandler h = (ChannelOutboundHandler) handler;
            try {
                h.freeOutboundBuffer(this);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }

        if (prev != null) {
            findContextOutbound().invokeFreeOutboundBuffer();
        }
    }

    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel());
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return channel().newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return channel().newFailedFuture(cause);
    }

    private boolean isInboundBufferFreed() {
        return pipeline.inboundBufferFreed;
    }

    private boolean isOutboundBufferFreed() {
        return pipeline.outboundBufferFreed;
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

    private DefaultChannelHandlerContext findContextOutbound() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while (!(ctx.handler() instanceof ChannelOperationHandler));
        return ctx;
    }

    private static final class MessageBridge {
        private final MessageBuf<Object> msgBuf = Unpooled.messageBuffer();

        private final Queue<Object[]> exchangeBuf = new ConcurrentLinkedQueue<Object[]>();

        private void fill() {
            if (msgBuf.isEmpty()) {
                return;
            }
            Object[] data = msgBuf.toArray();
            msgBuf.clear();
            exchangeBuf.add(data);
        }

        private void flush(MessageBuf<Object> out) {
            for (;;) {
                Object[] data = exchangeBuf.poll();
                if (data == null) {
                    break;
                }

                Collections.addAll(out, data);
            }
        }
    }

    private static final class ByteBridge {
        private final ByteBuf byteBuf;

        private final Queue<ByteBuf> exchangeBuf = new ConcurrentLinkedQueue<ByteBuf>();
        private final ChannelHandlerContext ctx;

        ByteBridge(ChannelHandlerContext ctx) {
            this.ctx = ctx;
            // TODO Choose whether to use heap or direct buffer depending on the context's buffer type.
            byteBuf = ctx.alloc().buffer();
        }

        private void fill() {
            if (!byteBuf.isReadable()) {
                return;
            }

            int dataLen = byteBuf.readableBytes();
            ByteBuf data;
            if (byteBuf.isDirect()) {
                data = ctx.alloc().directBuffer(dataLen, dataLen);
            } else {
                data = ctx.alloc().buffer(dataLen, dataLen);
            }

            byteBuf.readBytes(data).discardSomeReadBytes();

            exchangeBuf.add(data);
        }

        private void flush(ByteBuf out) {
            while (out.isWritable()) {
                ByteBuf data = exchangeBuf.peek();
                if (data == null) {
                    break;
                }

                if (out.writerIndex() > out.maxCapacity() - data.readableBytes()) {
                    // The target buffer is not going to be able to accept all data in the bridge.
                    out.capacity(out.maxCapacity());
                    out.writeBytes(data, out.writableBytes());
                } else {
                    exchangeBuf.remove();
                    try {
                        out.writeBytes(data);
                    } finally {
                        data.release();
                    }
                }
            }
        }
    }
}
