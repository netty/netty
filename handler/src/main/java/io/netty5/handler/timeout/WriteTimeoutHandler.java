/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.timeout;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Raises a {@link WriteTimeoutException} when a write operation cannot finish in a certain period of time.
 *
 * <pre>
 * // The connection is closed when a write operation cannot finish in 30 seconds.
 *
 * public class MyChannelInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("writeTimeoutHandler", new {@link WriteTimeoutHandler}(30);
 *         channel.pipeline().addLast("myHandler", new MyHandler());
 *     }
 * }
 *
 * // Handler should handle the {@link WriteTimeoutException}.
 * public class MyHandler implements {@link ChannelHandler} {
 *     {@code @Override}
 *     public void exceptionCaught({@link ChannelHandlerContext} ctx, {@link Throwable} cause)
 *             throws {@link Exception} {
 *         if (cause instanceof {@link WriteTimeoutException}) {
 *             // do something
 *         } else {
 *             super.exceptionCaught(ctx, cause);
 *         }
 *     }
 * }
 *
 * {@link ServerBootstrap} bootstrap = ...;
 * ...
 * bootstrap.childHandler(new MyChannelInitializer());
 * ...
 * </pre>
 * @see ReadTimeoutHandler
 * @see IdleStateHandler
 */
public class WriteTimeoutHandler implements ChannelHandler {
    private static final long MIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    private final long timeoutNanos;

    /**
     * A doubly-linked list to track all WriteTimeoutTasks
     */
    private WriteTimeoutTask lastTask;

    private boolean closed;

    /**
     * Creates a new instance.
     *
     * @param timeoutSeconds
     *        write timeout in seconds
     */
    public WriteTimeoutHandler(int timeoutSeconds) {
        this(timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Creates a new instance.
     *
     * @param timeout
     *        write timeout
     * @param unit
     *        the {@link TimeUnit} of {@code timeout}
     */
    public WriteTimeoutHandler(long timeout, TimeUnit unit) {
        requireNonNull(unit, "unit");

        if (timeout <= 0) {
            timeoutNanos = 0;
        } else {
            timeoutNanos = Math.max(unit.toNanos(timeout), MIN_TIMEOUT_NANOS);
        }
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        Future<Void> f = ctx.write(msg);
        if (timeoutNanos > 0) {
            scheduleTimeout(ctx, f);
        }
        return f;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        assert ctx.executor().inEventLoop();
        WriteTimeoutTask task = lastTask;
        lastTask = null;
        while (task != null) {
            assert task.ctx.executor().inEventLoop();
            task.scheduledFuture.cancel();
            WriteTimeoutTask prev = task.prev;
            task.prev = null;
            task.next = null;
            task = prev;
        }
    }

    private void scheduleTimeout(final ChannelHandlerContext ctx, final Future<Void> future) {
        // Schedule a timeout.
        final WriteTimeoutTask task = new WriteTimeoutTask(ctx, future);
        task.scheduledFuture = ctx.executor().schedule(task, timeoutNanos, TimeUnit.NANOSECONDS);

        if (!task.scheduledFuture.isDone()) {
            addWriteTimeoutTask(task);

            // Cancel the scheduled timeout if the flush promise is complete.
            future.addListener(task);
        }
    }

    private void addWriteTimeoutTask(WriteTimeoutTask task) {
        assert task.ctx.executor().inEventLoop();
        if (lastTask != null) {
            lastTask.next = task;
            task.prev = lastTask;
        }
        lastTask = task;
    }

    private void removeWriteTimeoutTask(WriteTimeoutTask task) {
        assert task.ctx.executor().inEventLoop();
        if (task == lastTask) {
            // task is the tail of list
            assert task.next == null;
            lastTask = lastTask.prev;
            if (lastTask != null) {
                lastTask.next = null;
            }
        } else if (task.prev == null && task.next == null) {
            // Since task is not lastTask, then it has been removed or not been added.
            return;
        } else if (task.prev == null) {
            // task is the head of list and the list has at least 2 nodes
            task.next.prev = null;
        } else {
            task.prev.next = task.next;
            task.next.prev = task.prev;
        }
        task.prev = null;
        task.next = null;
    }

    /**
     * Is called when a write timeout was detected
     */
    protected void writeTimedOut(ChannelHandlerContext ctx) throws Exception {
        if (!closed) {
            ctx.fireExceptionCaught(WriteTimeoutException.INSTANCE);
            ctx.close();
            closed = true;
        }
    }

    private final class WriteTimeoutTask implements Runnable, FutureListener<Void> {

        private final ChannelHandlerContext ctx;
        private final Future<Void> future;

        // WriteTimeoutTask is also a node of a doubly-linked list
        WriteTimeoutTask prev;
        WriteTimeoutTask next;

        Future<?> scheduledFuture;
        WriteTimeoutTask(ChannelHandlerContext ctx, Future<Void> future) {
            this.ctx = ctx;
            this.future = future;
        }

        @Override
        public void run() {
            // Was not written yet so issue a write timeout
            // The promise itself will be failed with a ClosedChannelException once the close() was issued
            // See https://github.com/netty/netty/issues/2159
            if (!future.isDone()) {
                try {
                    writeTimedOut(ctx);
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            }
            removeWriteTimeoutTask(this);
        }

        @Override
        public void operationComplete(Future<? extends Void> future) throws Exception {
            // scheduledFuture has already be set when reaching here
            scheduledFuture.cancel();

            // Check if its safe to modify the "doubly-linked-list" that we maintain. If its not we will schedule the
            // modification so its picked up by the executor..
            if (ctx.executor().inEventLoop()) {
                removeWriteTimeoutTask(this);
            } else {
                // So let's just pass outself to the executor which will then take care of remove this task
                // from the doubly-linked list. Schedule ourself is fine as the promise itself is done.
                //
                // This fixes https://github.com/netty/netty/issues/11053
                assert future.isDone();
                ctx.executor().execute(this);
            }
        }
    }
}
