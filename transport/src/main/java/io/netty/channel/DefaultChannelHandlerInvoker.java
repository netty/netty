/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.Recycler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.OneTimeTask;

import java.net.SocketAddress;

import static io.netty.channel.ChannelHandlerInvokerUtil.*;
import static io.netty.channel.DefaultChannelPipeline.*;

public class DefaultChannelHandlerInvoker implements ChannelHandlerInvoker {

    private final EventExecutor executor;

    public DefaultChannelHandlerInvoker(EventExecutor executor) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }

        this.executor = executor;
    }

    @Override
    public EventExecutor executor() {
        return executor;
    }

    @Override
    public void invokeChannelRegistered(final ChannelHandlerContext ctx) {
        if (executor.inEventLoop()) {
            invokeChannelRegisteredNow(ctx);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeChannelRegisteredNow(ctx);
                }
            });
        }
    }

    @Override
    public void invokeChannelUnregistered(final ChannelHandlerContext ctx) {
        if (executor.inEventLoop()) {
            invokeChannelUnregisteredNow(ctx);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeChannelUnregisteredNow(ctx);
                }
            });
        }
    }

    @Override
    public void invokeChannelActive(final ChannelHandlerContext ctx) {
        if (executor.inEventLoop()) {
            invokeChannelActiveNow(ctx);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeChannelActiveNow(ctx);
                }
            });
        }
    }

    @Override
    public void invokeChannelInactive(final ChannelHandlerContext ctx) {
        if (executor.inEventLoop()) {
            invokeChannelInactiveNow(ctx);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeChannelInactiveNow(ctx);
                }
            });
        }
    }

    @Override
    public void invokeExceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        if (executor.inEventLoop()) {
            invokeExceptionCaughtNow(ctx, cause);
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeExceptionCaughtNow(ctx, cause);
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

    @Override
    public void invokeUserEventTriggered(final ChannelHandlerContext ctx, final Object event) {
        if (event == null) {
            throw new NullPointerException("event");
        }

        if (executor.inEventLoop()) {
            invokeUserEventTriggeredNow(ctx, event);
        } else {
            safeExecuteInbound(new Runnable() {
                @Override
                public void run() {
                    invokeUserEventTriggeredNow(ctx, event);
                }
            }, event);
        }
    }

    @Override
    public void invokeChannelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        if (executor.inEventLoop()) {
            invokeChannelReadNow(ctx, msg);
        } else {
            safeExecuteInbound(new Runnable() {
                @Override
                public void run() {
                    invokeChannelReadNow(ctx, msg);
                }
            }, msg);
        }
    }

    @Override
    public void invokeChannelReadComplete(final ChannelHandlerContext ctx) {
        if (executor.inEventLoop()) {
            invokeChannelReadCompleteNow(ctx);
        } else {
            DefaultChannelHandlerContext dctx = (DefaultChannelHandlerContext) ctx;
            Runnable task = dctx.invokeChannelReadCompleteTask;
            if (task == null) {
                dctx.invokeChannelReadCompleteTask = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeChannelReadCompleteNow(ctx);
                    }
                };
            }
            executor.execute(task);
        }
    }

    @Override
    public void invokeChannelWritabilityChanged(final ChannelHandlerContext ctx) {
        if (executor.inEventLoop()) {
            invokeChannelWritabilityChangedNow(ctx);
        } else {
            DefaultChannelHandlerContext dctx = (DefaultChannelHandlerContext) ctx;
            Runnable task = dctx.invokeChannelWritableStateChangedTask;
            if (task == null) {
                dctx.invokeChannelWritableStateChangedTask = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeChannelWritabilityChangedNow(ctx);
                    }
                };
            }
            executor.execute(task);
        }
    }

    @Override
    public void invokeBind(
            final ChannelHandlerContext ctx, final SocketAddress localAddress, final ChannelPromise promise) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        if (!validatePromise(ctx, promise, false)) {
            // promise cancelled
            return;
        }

        if (executor.inEventLoop()) {
            invokeBindNow(ctx, localAddress, promise);
        } else {
            safeExecuteOutbound(new Runnable() {
                @Override
                public void run() {
                    invokeBindNow(ctx, localAddress, promise);
                }
            }, promise);
        }
    }

    @Override
    public void invokeConnect(
            final ChannelHandlerContext ctx,
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        if (!validatePromise(ctx, promise, false)) {
            // promise cancelled
            return;
        }

        if (executor.inEventLoop()) {
            invokeConnectNow(ctx, remoteAddress, localAddress, promise);
        } else {
            safeExecuteOutbound(new Runnable() {
                @Override
                public void run() {
                    invokeConnectNow(ctx, remoteAddress, localAddress, promise);
                }
            }, promise);
        }
    }

    @Override
    public void invokeDisconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        if (!validatePromise(ctx, promise, false)) {
            // promise cancelled
            return;
        }

        if (executor.inEventLoop()) {
            invokeDisconnectNow(ctx, promise);
        } else {
            safeExecuteOutbound(new Runnable() {
                @Override
                public void run() {
                    invokeDisconnectNow(ctx, promise);
                }
            }, promise);
        }
    }

    @Override
    public void invokeClose(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        if (!validatePromise(ctx, promise, false)) {
            // promise cancelled
            return;
        }

        if (executor.inEventLoop()) {
            invokeCloseNow(ctx, promise);
        } else {
            safeExecuteOutbound(new Runnable() {
                @Override
                public void run() {
                    invokeCloseNow(ctx, promise);
                }
            }, promise);
        }
    }

    @Override
    public void invokeDeregister(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        if (!validatePromise(ctx, promise, false)) {
            // promise cancelled
            return;
        }

        if (executor.inEventLoop()) {
            invokeDeregisterNow(ctx, promise);
        } else {
            safeExecuteOutbound(new Runnable() {
                @Override
                public void run() {
                    invokeDeregisterNow(ctx, promise);
                }
            }, promise);
        }
    }

    @Override
    public void invokeRead(final ChannelHandlerContext ctx) {
        if (executor.inEventLoop()) {
            invokeReadNow(ctx);
        } else {
            DefaultChannelHandlerContext dctx = (DefaultChannelHandlerContext) ctx;
            Runnable task = dctx.invokeReadTask;
            if (task == null) {
                dctx.invokeReadTask = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeReadNow(ctx);
                    }
                };
            }
            executor.execute(task);
        }
    }

    @Override
    public void invokeWrite(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        if (!validatePromise(ctx, promise, true)) {
            // promise cancelled
            ReferenceCountUtil.release(msg);
            return;
        }

        invokeWrite(ctx, msg, false, promise);
    }

    private void invokeWrite(ChannelHandlerContext ctx, Object msg, boolean flush, ChannelPromise promise) {
        if (executor.inEventLoop()) {
            invokeWriteNow(ctx, msg, promise);
            if (flush) {
                invokeFlushNow(ctx);
            }
        } else {
            AbstractChannel channel = (AbstractChannel) ctx.channel();
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
                task = WriteAndFlushTask.newInstance(ctx, msg, size, promise);
            }  else {
                task = WriteTask.newInstance(ctx, msg, size, promise);
            }
            safeExecuteOutbound(task, promise, msg);
        }
    }

    @Override
    public void invokeFlush(final ChannelHandlerContext ctx) {
        if (executor.inEventLoop()) {
            invokeFlushNow(ctx);
        } else {
            DefaultChannelHandlerContext dctx = (DefaultChannelHandlerContext) ctx;
            Runnable task = dctx.invokeFlushTask;
            if (task == null) {
                dctx.invokeFlushTask = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeFlushNow(ctx);
                    }
                };
            }
            executor.execute(task);
        }
    }

    @Override
    public void invokeWriteAndFlush(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        if (!validatePromise(ctx, promise, true)) {
            // promise cancelled
            ReferenceCountUtil.release(msg);
            return;
        }

        invokeWrite(ctx, msg, true, promise);
    }

    private void safeExecuteInbound(Runnable task, Object msg) {
        boolean success = false;
        try {
            executor.execute(task);
            success = true;
        } finally {
            if (!success) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    private void safeExecuteOutbound(Runnable task, ChannelPromise promise) {
        try {
            executor.execute(task);
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
    }
    private void safeExecuteOutbound(Runnable task, ChannelPromise promise, Object msg) {
        try {
            executor.execute(task);
        } catch (Throwable cause) {
            try {
                promise.setFailure(cause);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    abstract static class AbstractWriteTask<T> extends OneTimeTask {
        private final Recycler.Handle<T> handle;

        private ChannelHandlerContext ctx;
        private Object msg;
        private ChannelPromise promise;
        private int size;

        protected AbstractWriteTask(Recycler.Handle<T> handle) {
            this.handle = handle;
        }

        protected static void init(
                AbstractWriteTask<?> task, ChannelHandlerContext ctx, Object msg, int size, ChannelPromise promise) {
            task.ctx = ctx;
            task.msg = msg;
            task.promise = promise;
            task.size = size;
        }

        @Override
        public final void run() {
            try {
                if (size > 0) {
                    ChannelOutboundBuffer buffer = ctx.channel().unsafe().outboundBuffer();
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

        protected void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            invokeWriteNow(ctx, msg, promise);
        }

        protected abstract void recycle(Recycler.Handle<T> handle);
    }

    static final class WriteTask
            extends AbstractWriteTask<WriteTask> implements SingleThreadEventLoop.NonWakeupRunnable {

        private static final Recycler<WriteTask> RECYCLER = new Recycler<WriteTask>() {
            @Override
            protected WriteTask newObject(Handle<WriteTask> handle) {
                return new WriteTask(handle);
            }
        };

        static WriteTask newInstance(ChannelHandlerContext ctx, Object msg, int size, ChannelPromise promise) {
            WriteTask task = RECYCLER.get();
            init(task, ctx, msg, size, promise);
            return task;
        }

        private WriteTask(Recycler.Handle<WriteTask> handle) {
            super(handle);
        }

        @Override
        protected void recycle(Recycler.Handle<WriteTask> handle) {
            RECYCLER.recycle(this, handle);
        }
    }

    static final class WriteAndFlushTask extends AbstractWriteTask<WriteAndFlushTask> {

        private static final Recycler<WriteAndFlushTask> RECYCLER = new Recycler<WriteAndFlushTask>() {
            @Override
            protected WriteAndFlushTask newObject(Handle<WriteAndFlushTask> handle) {
                return new WriteAndFlushTask(handle);
            }
        };

        static WriteAndFlushTask newInstance(
                ChannelHandlerContext ctx, Object msg, int size, ChannelPromise promise) {
            WriteAndFlushTask task = RECYCLER.get();
            init(task, ctx, msg, size, promise);
            return task;
        }

        private WriteAndFlushTask(Recycler.Handle<WriteAndFlushTask> handle) {
            super(handle);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            super.write(ctx, msg, promise);
            invokeFlushNow(ctx);
        }

        @Override
        protected void recycle(Recycler.Handle<WriteAndFlushTask> handle) {
            RECYCLER.recycle(this, handle);
        }
    }
}
