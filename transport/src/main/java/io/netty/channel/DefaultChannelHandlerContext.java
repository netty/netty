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
import java.util.Collections;
import java.util.EnumSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.DefaultChannelPipeline.*;

final class DefaultChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext {

    private static final EnumSet<ChannelHandlerType> EMPTY_TYPE = EnumSet.noneOf(ChannelHandlerType.class);

    private static final int FLAG_STATE_HANDLER = 1;
    static final int FLAG_OPERATION_HANDLER = 2;
    static final int FLAG_INBOUND_HANDLER = 4;
    private static final int FLAG_OUTBOUND_HANDLER = 8;
    private static final int FLAG_NEEDS_LAZY_INIT = 16;

    volatile DefaultChannelHandlerContext next;
    volatile DefaultChannelHandlerContext prev;

    private final Channel channel;
    private final DefaultChannelPipeline pipeline;
    private final String name;
    private final Set<ChannelHandlerType> type;
    private final ChannelHandler handler;
    final int flags;

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
    private final AtomicReference<MessageBridge> inMsgBridge;
    AtomicReference<MessageBridge> outMsgBridge;
    private final AtomicReference<ByteBridge> inByteBridge;
    AtomicReference<ByteBridge> outByteBridge;

    // Runnables that calls handlers
    private final Runnable fireChannelRegisteredTask = new Runnable() {
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
    private final Runnable fireChannelUnregisteredTask = new Runnable() {
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
    private final Runnable fireChannelActiveTask = new Runnable() {
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
    private final Runnable fireChannelInactiveTask = new Runnable() {
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
    private final Runnable curCtxFireInboundBufferUpdatedTask = new Runnable() {
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
                    DefaultChannelHandlerContext.this.next, FLAG_STATE_HANDLER);
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
    private final Runnable fireInboundBufferSuspendedTask = new Runnable() {
        @Override
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            try {
                ((ChannelStateHandler) ctx.handler).inboundBufferSuspended(ctx);
            } catch (Throwable t) {
                pipeline.notifyHandlerException(t);
            }
        }
    };
    private final Runnable freeInboundBufferTask = new Runnable() {
        @Override
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            if (ctx.handler instanceof ChannelInboundHandler) {
                ChannelInboundHandler h = (ChannelInboundHandler) ctx.handler;
                try {
                    if (ctx.hasInboundByteBuffer()) {
                        if (ctx.inByteBuf != null) {
                            h.freeInboundBuffer(ctx, ctx.inByteBuf);
                        }
                    } else {
                        if (ctx.inMsgBuf != null) {
                            h.freeInboundBuffer(ctx, ctx.inMsgBuf);
                        }
                    }
                } catch (Throwable t) {
                    pipeline.notifyHandlerException(t);
                }
            }

            DefaultChannelHandlerContext nextCtx = nextContext(ctx.next, FLAG_STATE_HANDLER);
            if (nextCtx != null) {
                nextCtx.callFreeInboundBuffer();
            } else {
                // Freed all inbound buffers. Free all outbound buffers in a reverse order.
                pipeline.lastContext(FLAG_OPERATION_HANDLER).callFreeOutboundBuffer();
            }
        }
    };
    private final Runnable freeOutboundBufferTask = new Runnable() {
        @Override
        public void run() {
            DefaultChannelHandlerContext ctx = DefaultChannelHandlerContext.this;
            if (ctx.handler instanceof ChannelOutboundHandler) {
                ChannelOutboundHandler h = (ChannelOutboundHandler) ctx.handler;
                try {
                    if (ctx.hasOutboundByteBuffer()) {
                        if (ctx.outByteBuf != null) {
                            h.freeOutboundBuffer(ctx, ctx.outByteBuf);
                        }
                    } else {
                        if (ctx.outMsgBuf != null) {
                            h.freeOutboundBuffer(ctx, ctx.outMsgBuf);
                        }
                    }
                } catch (Throwable t) {
                    pipeline.notifyHandlerException(t);
                }
            }

            DefaultChannelHandlerContext nextCtx = prevContext(ctx.prev, FLAG_OPERATION_HANDLER);
            if (nextCtx != null) {
                nextCtx.callFreeOutboundBuffer();
            }
        }
    };

    final Runnable read0Task = new Runnable() {
        @Override
        public void run() {
            pipeline.read0(DefaultChannelHandlerContext.this);
        }
    };

    @SuppressWarnings("unchecked")
    DefaultChannelHandlerContext(
            DefaultChannelPipeline pipeline, EventExecutorGroup group,
            DefaultChannelHandlerContext prev, DefaultChannelHandlerContext next,
            String name, ChannelHandler handler) {

        if (name == null) {
            throw new NullPointerException("name");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        int flags = 0;

        // Determine the type of the specified handler.
        EnumSet<ChannelHandlerType> type = EMPTY_TYPE.clone();
        if (handler instanceof ChannelStateHandler) {
            type.add(ChannelHandlerType.STATE);
            flags |= FLAG_STATE_HANDLER;
            if (handler instanceof ChannelInboundHandler) {
                type.add(ChannelHandlerType.INBOUND);
                flags |= FLAG_INBOUND_HANDLER;
            }
        }
        if (handler instanceof ChannelOperationHandler) {
            type.add(ChannelHandlerType.OPERATION);
            flags |= FLAG_OPERATION_HANDLER;
            if (handler instanceof ChannelOutboundHandler) {
                type.add(ChannelHandlerType.OUTBOUND);
                flags |= FLAG_OUTBOUND_HANDLER;
            }
        }
        this.type = Collections.unmodifiableSet(type);

        this.prev = prev;
        this.next = next;

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

        if ((flags & FLAG_INBOUND_HANDLER) != 0) {
            Buf buf;
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

        if ((flags & FLAG_OUTBOUND_HANDLER) != 0) {
            if (prev == null) {
                // Special case: if pref == null, it means this context for HeadHandler.
                // HeadHandler is an outbound handler instantiated by the constructor of DefaultChannelPipeline.
                // Because Channel is not really fully initialized at this point, we should not call
                // newOutboundBuffer() yet because it will usually lead to NPE.
                // To work around this problem, we lazily initialize the outbound buffer for this special case.
                flags |= FLAG_NEEDS_LAZY_INIT;
            } else {
                initOutboundBuffer();
            }
        } else {
            outByteBuf = null;
            outByteBridge = null;
            outMsgBuf = null;
            outMsgBridge = null;
        }

        this.flags = flags;
    }

    private void lazyInitOutboundBuffer() {
        if ((flags & FLAG_NEEDS_LAZY_INIT) != 0) {
            if (outByteBuf == null && outMsgBuf == null) {
                EventExecutor exec = executor();
                if (exec.inEventLoop()) {
                    initOutboundBuffer();
                } else {
                    try {
                        getFromFuture(exec.submit(new Runnable() {
                            @Override
                            public void run() {
                                lazyInitOutboundBuffer();
                            }
                        }));
                    } catch (Exception e) {
                        throw new ChannelPipelineException("failed to initialize an outbound buffer lazily", e);
                    }
                }
            }
        }
    }

    private void initOutboundBuffer() {
        Buf buf;
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
            @SuppressWarnings("unchecked")
            MessageBuf<Object> msgBuf = (MessageBuf<Object>) buf;
            outMsgBuf = msgBuf;
            outMsgBridge = new AtomicReference<MessageBridge>();
        } else {
            throw new Error();
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

        lazyInitOutboundBuffer();
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
    public ByteBufAllocator alloc() {
        return channel.config().getAllocator();
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
            if (handler instanceof ChannelInboundHandler) {
                throw new NoSuchBufferException(String.format(
                        "the handler '%s' has no inbound byte buffer; it implements %s, but " +
                        "its newInboundBuffer() method created a %s.",
                        name, ChannelInboundHandler.class.getSimpleName(),
                        MessageBuf.class.getSimpleName()));
            } else {
                throw new NoSuchBufferException(String.format(
                        "the handler '%s' has no inbound byte buffer; it does not implement %s.",
                        name, ChannelInboundHandler.class.getSimpleName()));
            }
        }
        return inByteBuf;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> inboundMessageBuffer() {
        if (inMsgBuf == null) {
            if (handler instanceof ChannelInboundHandler) {
                throw new NoSuchBufferException(String.format(
                        "the handler '%s' has no inbound message buffer; it implements %s, but " +
                        "its newInboundBuffer() method created a %s.",
                        name, ChannelInboundHandler.class.getSimpleName(),
                        ByteBuf.class.getSimpleName()));
            } else {
                throw new NoSuchBufferException(String.format(
                        "the handler '%s' has no inbound message buffer; it does not implement %s.",
                        name, ChannelInboundHandler.class.getSimpleName()));
            }
        }
        return (MessageBuf<T>) inMsgBuf;
    }

    @Override
    public boolean hasOutboundByteBuffer() {
        lazyInitOutboundBuffer();
        return outByteBuf != null;
    }

    @Override
    public boolean hasOutboundMessageBuffer() {
        lazyInitOutboundBuffer();
        return outMsgBuf != null;
    }

    @Override
    public ByteBuf outboundByteBuffer() {
        if (outMsgBuf == null) {
            lazyInitOutboundBuffer();
        }

        if (outByteBuf == null) {
            if (handler instanceof ChannelOutboundHandler) {
                throw new NoSuchBufferException(String.format(
                        "the handler '%s' has no outbound byte buffer; it implements %s, but " +
                        "its newOutboundBuffer() method created a %s.",
                        name, ChannelOutboundHandler.class.getSimpleName(),
                        MessageBuf.class.getSimpleName()));
            } else {
                throw new NoSuchBufferException(String.format(
                        "the handler '%s' has no outbound byte buffer; it does not implement %s.",
                        name, ChannelOutboundHandler.class.getSimpleName()));
            }
        }
        return outByteBuf;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> outboundMessageBuffer() {
        if (outMsgBuf == null) {
            initOutboundBuffer();
        }

        if (outMsgBuf == null) {
            if (handler instanceof ChannelOutboundHandler) {
                throw new NoSuchBufferException(String.format(
                        "the handler '%s' has no outbound message buffer; it implements %s, but " +
                        "its newOutboundBuffer() method created a %s.",
                        name, ChannelOutboundHandler.class.getSimpleName(),
                        ByteBuf.class.getSimpleName()));
            } else {
                throw new NoSuchBufferException(String.format(
                        "the handler '%s' has no outbound message buffer; it does not implement %s.",
                        name, ChannelOutboundHandler.class.getSimpleName()));
            }
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
        DefaultChannelHandlerContext ctx = prev;
        for (;;) {
            if (ctx == null) {
                return false;
            }

            ctx.lazyInitOutboundBuffer();

            if (ctx.outByteBridge != null) {
                return true;
            }
            ctx = ctx.prev;
        }
    }

    @Override
    public boolean hasNextOutboundMessageBuffer() {
        DefaultChannelHandlerContext ctx = prev;
        for (;;) {
            if (ctx == null) {
                return false;
            }

            ctx.lazyInitOutboundBuffer();

            if (ctx.outMsgBridge != null) {
                return true;
            }
            ctx = ctx.prev;
        }
    }

    @Override
    public ByteBuf nextInboundByteBuffer() {
        DefaultChannelHandlerContext ctx = next;
        final Thread currentThread = Thread.currentThread();
        for (;;) {
            if (ctx == null) {
                if (prev != null) {
                    throw new NoSuchBufferException(String.format(
                            "the handler '%s' could not find a %s whose inbound buffer is %s.",
                            name, ChannelInboundHandler.class.getSimpleName(),
                            ByteBuf.class.getSimpleName()));
                } else {
                    throw new NoSuchBufferException(String.format(
                            "the pipeline does not contain a %s whose inbound buffer is %s.",
                            ChannelInboundHandler.class.getSimpleName(),
                            ByteBuf.class.getSimpleName()));
                }
            }
            if (ctx.inByteBuf != null) {
                if (ctx.executor().inEventLoop(currentThread)) {
                    return ctx.inByteBuf;
                } else {
                    ByteBridge bridge = ctx.inByteBridge.get();
                    if (bridge == null) {
                        bridge = new ByteBridge(ctx);
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
                if (prev != null) {
                    throw new NoSuchBufferException(String.format(
                            "the handler '%s' could not find a %s whose inbound buffer is %s.",
                            name, ChannelInboundHandler.class.getSimpleName(),
                            MessageBuf.class.getSimpleName()));
                } else {
                    throw new NoSuchBufferException(String.format(
                            "the pipeline does not contain a %s whose inbound buffer is %s.",
                            ChannelInboundHandler.class.getSimpleName(),
                            MessageBuf.class.getSimpleName()));
                }
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
        DefaultChannelHandlerContext next = nextContext(this.next, FLAG_STATE_HANDLER);
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
        DefaultChannelHandlerContext next = nextContext(this.next, FLAG_STATE_HANDLER);
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop() && prev != null) {
                next.fireChannelUnregisteredTask.run();
            } else {
                executor.execute(next.fireChannelUnregisteredTask);
            }
        }
    }

    @Override
    public void fireChannelActive() {
        DefaultChannelHandlerContext next = nextContext(this.next, FLAG_STATE_HANDLER);
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
        DefaultChannelHandlerContext next = nextContext(this.next, FLAG_STATE_HANDLER);
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop() && prev != null) {
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

        final DefaultChannelHandlerContext next = this.next;
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop() && prev != null) {
                fireExceptionCaught0(next, cause);
            } else {
                try {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            fireExceptionCaught0(next, cause);
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

    private static void fireExceptionCaught0(DefaultChannelHandlerContext next, Throwable cause) {
        try {
            next.handler().exceptionCaught(next, cause);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by a user handler's " +
                        "exceptionCaught() method while handling the following exception:", cause);
            }
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
    public void fireInboundBufferSuspended() {
        DefaultChannelHandlerContext next = nextContext(this.next, FLAG_STATE_HANDLER);
        if (next != null) {
            EventExecutor executor = next.executor();
            if (executor.inEventLoop() && prev != null) {
                next.fireInboundBufferSuspendedTask.run();
            } else {
                executor.execute(next.fireInboundBufferSuspendedTask);
            }
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
        return pipeline.bind(prevContext(prev, FLAG_OPERATION_HANDLER), localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.connect(prevContext(prev, FLAG_OPERATION_HANDLER), remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline.disconnect(prevContext(prev, FLAG_OPERATION_HANDLER), promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return pipeline.close(prevContext(prev, FLAG_OPERATION_HANDLER), promise);
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return pipeline.deregister(prevContext(prev, FLAG_OPERATION_HANDLER), promise);
    }

    @Override
    public void read() {
        pipeline.read(prevContext(prev, FLAG_OPERATION_HANDLER));
    }

    @Override
    public ChannelFuture flush(final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext prev = prevContext(this.prev, FLAG_OPERATION_HANDLER);
            prev.fillBridge();
            pipeline.flush(prev, promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    flush(promise);
                }
            });
        }

        return promise;
    }

    @Override
    public ChannelFuture write(Object message, ChannelPromise promise) {
        return pipeline.write(prev, message, promise);
    }

    void callFreeInboundBuffer() {
        EventExecutor executor = executor();
        if (executor.inEventLoop() && prev != null) {
            freeInboundBufferTask.run();
        } else {
            executor.execute(freeInboundBufferTask);
        }
    }

    /** Invocation initiated by {@link #freeInboundBufferTask} after freeing all inbound buffers. */
    private void callFreeOutboundBuffer() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            freeOutboundBufferTask.run();
        } else {
            executor.execute(freeOutboundBufferTask);
        }
    }

    @Override
    public ChannelPromise newPromise() {
        return channel.newPromise();
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

    static final class ByteBridge {
        final ByteBuf byteBuf;

        private final Queue<ByteBuf> exchangeBuf = new ConcurrentLinkedQueue<ByteBuf>();
        private final ChannelHandlerContext ctx;

        ByteBridge(ChannelHandlerContext ctx) {
            this.ctx = ctx;
            // TODO Choose whether to use heap or direct buffer depending on the context's buffer type.
            byteBuf = ctx.alloc().buffer();
        }

        private void fill() {
            if (!byteBuf.readable()) {
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
            while (out.writable()) {
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
                        data.free();
                    }
                }
            }
        }
    }

    @Override
    public ChannelFuture sendFile(FileRegion region) {
        return pipeline.sendFile(prevContext(prev, FLAG_OPERATION_HANDLER), region, newPromise());
    }

    @Override
    public ChannelFuture sendFile(FileRegion region, ChannelPromise promise) {
        return pipeline.sendFile(prevContext(prev, FLAG_OPERATION_HANDLER), region, promise);
    }
}
