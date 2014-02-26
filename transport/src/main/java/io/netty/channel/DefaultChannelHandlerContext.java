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

import static io.netty.channel.DefaultChannelPipeline.logger;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.Recycler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;

final class DefaultChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext {

    volatile DefaultChannelHandlerContext next;
    volatile DefaultChannelHandlerContext prev;

    private final boolean inbound;
    private final boolean outbound;
    private final AbstractChannel channel;
    private final DefaultChannelPipeline pipeline;
    private final String name;
    private final ChannelHandler handler;
    private boolean removed;

    // Will be set to null if no child executor should be used, otherwise it will be set to the
    // child executor.
    final EventExecutor executor;
    private ChannelFuture succeededFuture;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // These needs to be volatile as otherwise an other Thread may see an half initialized instance.
    // See the JMM for more details
    private volatile Runnable invokeChannelReadCompleteTask;
    private volatile Runnable invokeReadTask;
    private volatile Runnable invokeChannelWritableStateChangedTask;
    private volatile Runnable invokeFlushTask;

    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, EventExecutorGroup group, String name,
            ChannelHandler handler) {

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

        inbound = handler instanceof ChannelInboundHandler;
        outbound = handler instanceof ChannelOutboundHandler;
    }

    /** Invocation initiated by {@link DefaultChannelPipeline#teardownAll()}}. */
    void teardown() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            teardown0();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    teardown0();
                }
            });
        }
    }

    private void teardown0() {
        DefaultChannelHandlerContext prev = this.prev;
        if (prev != null) {
            synchronized (pipeline) {
                pipeline.remove0(this);
            }
            prev.teardown();
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
    public ChannelHandlerContext fireChannelRegistered() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelRegistered();
        } else {
            executor.execute(new OneTimeTask() {
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
            ((ChannelInboundHandler) handler).channelRegistered(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelUnregistered();
        } else {
            executor.execute(new OneTimeTask() {
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
            ((ChannelInboundHandler) handler).channelUnregistered(this);
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
            executor.execute(new OneTimeTask() {
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
            ((ChannelInboundHandler) handler).channelActive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelInactive();
        } else {
            executor.execute(new OneTimeTask() {
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
            ((ChannelInboundHandler) handler).channelInactive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        final DefaultChannelHandlerContext next = this.next;

        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeExceptionCaught(cause);
        } else {
            try {
                executor.execute(new OneTimeTask() {
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

        return this;
    }

    private void invokeExceptionCaught(final Throwable cause) {
        try {
            handler.exceptionCaught(this, cause);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by a user handler's " +
                        "exceptionCaught() method while handling the following exception:", cause);
            }
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
            executor.execute(new OneTimeTask() {
                @Override
                public void run() {
                    next.invokeUserEventTriggered(event);
                }
            });
        }
        return this;
    }

    private void invokeUserEventTriggered(Object event) {
        try {
            ((ChannelInboundHandler) handler).userEventTriggered(this, event);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelRead(msg);
        } else {
            executor.execute(new OneTimeTask() {
                @Override
                public void run() {
                    next.invokeChannelRead(msg);
                }
            });
        }
        return this;
    }

    private void invokeChannelRead(Object msg) {
        try {
            ((ChannelInboundHandler) handler).channelRead(this, msg);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelReadComplete();
        } else {
            Runnable task = next.invokeChannelReadCompleteTask;
            if (task == null) {
                next.invokeChannelReadCompleteTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeChannelReadComplete();
                    }
                };
            }
            executor.execute(task);
        }
        return this;
    }

    private void invokeChannelReadComplete() {
        try {
            ((ChannelInboundHandler) handler).channelReadComplete(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelWritabilityChanged();
        } else {
            Runnable task = next.invokeChannelWritableStateChangedTask;
            if (task == null) {
                next.invokeChannelWritableStateChangedTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeChannelWritabilityChanged();
                    }
                };
            }
            executor.execute(task);
        }
        return this;
    }

    private void invokeChannelWritabilityChanged() {
        try {
            ((ChannelInboundHandler) handler).channelWritabilityChanged(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
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
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        validatePromise(promise, false);

        final DefaultChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeBind(localAddress, promise);
        } else {
            safeExecute(executor, new OneTimeTask() {
                @Override
                public void run() {
                    next.invokeBind(localAddress, promise);
                }
            }, promise, null);
        }

        return promise;
    }

    private void invokeBind(SocketAddress localAddress, ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler).bind(this, localAddress, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {

        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validatePromise(promise, false);

        final DefaultChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeConnect(remoteAddress, localAddress, promise);
        } else {
            safeExecute(executor, new OneTimeTask() {
                @Override
                public void run() {
                    next.invokeConnect(remoteAddress, localAddress, promise);
                }
            }, promise, null);
        }

        return promise;
    }

    private void invokeConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler).connect(this, remoteAddress, localAddress, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelFuture disconnect(final ChannelPromise promise) {
        validatePromise(promise, false);

        final DefaultChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
            // So far, UDP/IP is the only transport that has such behavior.
            if (!channel().metadata().hasDisconnect()) {
                next.invokeClose(promise);
            } else {
                next.invokeDisconnect(promise);
            }
        } else {
            safeExecute(executor, new OneTimeTask() {
                @Override
                public void run() {
                    if (!channel().metadata().hasDisconnect()) {
                        next.invokeClose(promise);
                    } else {
                        next.invokeDisconnect(promise);
                    }
                }
            }, promise, null);
        }

        return promise;
    }

    private void invokeDisconnect(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler).disconnect(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelFuture close(final ChannelPromise promise) {
        validatePromise(promise, false);

        final DefaultChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeClose(promise);
        } else {
            safeExecute(executor, new OneTimeTask() {
                @Override
                public void run() {
                    next.invokeClose(promise);
                }
            }, promise, null);
        }

        return promise;
    }

    private void invokeClose(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler).close(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        validatePromise(promise, false);

        final DefaultChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeDeregister(promise);
        } else {
            safeExecute(executor, new OneTimeTask() {
                @Override
                public void run() {
                    next.invokeDeregister(promise);
                }
            }, promise, null);
        }

        return promise;
    }

    private void invokeDeregister(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler).deregister(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelHandlerContext read() {
        final DefaultChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeRead();
        } else {
            Runnable task = next.invokeReadTask;
            if (task == null) {
                next.invokeReadTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeRead();
                    }
                };
            }
            executor.execute(task);
        }

        return this;
    }

    private void invokeRead() {
        try {
            ((ChannelOutboundHandler) handler).read(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelFuture write(Object msg) {
        return write(msg, newPromise());
    }

    @Override
    public ChannelFuture write(final Object msg, final ChannelPromise promise) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        validatePromise(promise, true);

        write(msg, false, promise);

        return promise;
    }

    private void invokeWrite(Object msg, ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler).write(this, msg, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelHandlerContext flush() {
        final DefaultChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeFlush();
        } else {
            Runnable task = next.invokeFlushTask;
            if (task == null) {
                next.invokeFlushTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeFlush();
                    }
                };
            }
            safeExecute(executor, task, channel.voidPromise(), null);
        }

        return this;
    }

    private void invokeFlush() {
        try {
            ((ChannelOutboundHandler) handler).flush(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        validatePromise(promise, true);

        write(msg, true, promise);

        return promise;
    }

    private void write(Object msg, boolean flush, ChannelPromise promise) {

        DefaultChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeWrite(msg, promise);
            if (flush) {
                next.invokeFlush();
            }
        } else {
            int size = channel.estimatorHandle().size(msg);
            if (size > 0) {
                ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
                // Check for null as it may be set to null if the channel is closed already
                if (buffer != null) {
                    buffer.incrementPendingOutboundBytes(size);
                }
            }
            Runnable task;
            if (flush) {
                task = WriteAndFlushTask.newInstance(next, msg, size, promise);
            }  else {
                task = WriteTask.newInstance(next, msg, size, promise);
            }
            safeExecute(executor, task, promise, msg);
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return writeAndFlush(msg, newPromise());
    }

    private static void notifyOutboundHandlerException(Throwable cause, ChannelPromise promise) {
        // only try to fail the promise if its not a VoidChannelPromise, as
        // the VoidChannelPromise would also fire the cause through the pipeline
        if (promise instanceof VoidChannelPromise) {
            return;
        }

        if (!promise.tryFailure(cause)) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to fail the promise because it's done already: {}", promise, cause);
            }
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

    private void validatePromise(ChannelPromise promise, boolean allowVoidPromise) {
        if (promise == null) {
            throw new NullPointerException("promise");
        }

        if (promise.isDone()) {
            throw new IllegalArgumentException("promise already done: " + promise);
        }

        if (promise.channel() != channel()) {
            throw new IllegalArgumentException(String.format(
                    "promise.channel does not match: %s (expected: %s)", promise.channel(), channel()));
        }

        if (promise.getClass() == DefaultChannelPromise.class) {
            return;
        }

        if (!allowVoidPromise && promise instanceof VoidChannelPromise) {
            throw new IllegalArgumentException(
                    StringUtil.simpleClassName(VoidChannelPromise.class) + " not allowed for this operation");
        }

        if (promise instanceof AbstractChannel.CloseFuture) {
            throw new IllegalArgumentException(
                    StringUtil.simpleClassName(AbstractChannel.CloseFuture.class) + " not allowed in a pipeline");
        }
    }

    private DefaultChannelHandlerContext findContextInbound() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while (!ctx.inbound);
        return ctx;
    }

    private DefaultChannelHandlerContext findContextOutbound() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while (!ctx.outbound);
        return ctx;
    }

    @Override
    public ChannelPromise voidPromise() {
        return channel.voidPromise();
    }

    void setRemoved() {
        removed = true;
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    private static void safeExecute(EventExecutor executor, Runnable runnable, ChannelPromise promise, Object msg) {
        try {
            executor.execute(runnable);
        } catch (Throwable cause) {
            try {
                promise.setFailure(cause);
            } finally {
                if (msg != null) {
                    ReferenceCountUtil.release(msg);
                }
            }
        }
    }

    abstract static class AbstractWriteTask extends OneTimeTask {
        private final Recycler.Handle handle;

        private DefaultChannelHandlerContext ctx;
        private Object msg;
        private ChannelPromise promise;
        private int size;

        private AbstractWriteTask(Recycler.Handle handle) {
            this.handle = handle;
        }

        protected static void init(AbstractWriteTask task, DefaultChannelHandlerContext ctx,
                                   Object msg, int size, ChannelPromise promise) {
            task.ctx = ctx;
            task.msg = msg;
            task.promise = promise;
            task.size = size;
        }

        @Override
        public final void run() {
            try {
                if (size > 0) {
                    ChannelOutboundBuffer buffer = ctx.channel.unsafe().outboundBuffer();
                    // Check for null as it may be set to null if the channel is closed already
                    if (buffer != null) {
                        buffer.decrementPendingOutboundBytes(size);
                    }
                }
                write(ctx, msg, promise);
            } finally {
                // Set to null so the GC can collect them directly
                ctx = null;
                msg = null;
                promise = null;
                recycle(handle);
            }
        }

        protected void write(DefaultChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ctx.invokeWrite(msg, promise);
        }

        protected abstract void recycle(Recycler.Handle handle);
    }

    static final class WriteTask extends AbstractWriteTask implements SingleThreadEventLoop.NonWakeupRunnable {

        private static final Recycler<WriteTask> RECYCLER = new Recycler<WriteTask>() {
            @Override
            protected WriteTask newObject(Handle handle) {
                return new WriteTask(handle);
            }
        };

        private static WriteTask newInstance(
                DefaultChannelHandlerContext ctx, Object msg, int size, ChannelPromise promise) {
            WriteTask task = RECYCLER.get();
            init(task, ctx, msg, size, promise);
            return task;
        }

        private WriteTask(Recycler.Handle handle) {
            super(handle);
        }

        @Override
        protected void recycle(Recycler.Handle handle) {
            RECYCLER.recycle(this, handle);
        }
    }

    static final class WriteAndFlushTask extends AbstractWriteTask {

        private static final Recycler<WriteAndFlushTask> RECYCLER = new Recycler<WriteAndFlushTask>() {
            @Override
            protected WriteAndFlushTask newObject(Handle handle) {
                return new WriteAndFlushTask(handle);
            }
        };

        private static WriteAndFlushTask newInstance(
                DefaultChannelHandlerContext ctx, Object msg, int size, ChannelPromise promise) {
            WriteAndFlushTask task = RECYCLER.get();
            init(task, ctx, msg, size, promise);
            return task;
        }

        private WriteAndFlushTask(Recycler.Handle handle) {
            super(handle);
        }

        @Override
        public void write(DefaultChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            super.write(ctx, msg, promise);
            ctx.invokeFlush();
        }

        @Override
        protected void recycle(Recycler.Handle handle) {
            RECYCLER.recycle(this, handle);
        }
    }
}
