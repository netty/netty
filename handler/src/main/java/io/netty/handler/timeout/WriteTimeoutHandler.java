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
package io.netty.handler.timeout;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.ObjectUtil;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 写超时本身是否超时了？和读超时及空闲检查不是一回事
 *
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
 * public class MyHandler extends {@link ChannelDuplexHandler} {
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
public class WriteTimeoutHandler extends ChannelOutboundHandlerAdapter {
    private static final long MIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    /**
     * 超时时间，单位：纳秒
     */
    private final long timeoutNanos;

    /**
     *  WriteTimeoutTask 双向链表。
     *  lastTask 为链表的尾节点
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
        ObjectUtil.checkNotNull(unit, "unit");

        if (timeout <= 0) {
            timeoutNanos = 0;
        } else {
            timeoutNanos = Math.max(unit.toNanos(timeout), MIN_TIMEOUT_NANOS);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (timeoutNanos > 0) {
            promise = promise.unvoid();
            // 触发些操作时
            scheduleTimeout(ctx, promise);
        }
        ctx.write(msg, promise);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        assert ctx.executor().inEventLoop();
        WriteTimeoutTask task = lastTask;
        lastTask = null;
        // 遍历双向链表，并取消调度任务
        while (task != null) {
            assert task.ctx.executor().inEventLoop();
            task.scheduledFuture.cancel(false);
            WriteTimeoutTask prev = task.prev;
            task.prev = null;
            task.next = null;
            task = prev;
        }
    }

    private void scheduleTimeout(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        // Schedule a timeout.
        final WriteTimeoutTask task = new WriteTimeoutTask(ctx, promise);
        // 添加一个写任务的调度
        task.scheduledFuture = ctx.executor().schedule(task, timeoutNanos, TimeUnit.NANOSECONDS);

        if (!task.scheduledFuture.isDone()) {
            addWriteTimeoutTask(task);

            // Cancel the scheduled timeout if the flush promise is complete.
            // 添加监听
            promise.addListener(task);
        }
    }

    private void addWriteTimeoutTask(WriteTimeoutTask task) {
        assert task.ctx.executor().inEventLoop();
        // 当前任务添加到双向链表尾部
        if (lastTask != null) {
            lastTask.next = task;
            task.prev = lastTask;
        }
        lastTask = task;
    }

    /**
     * 写操作未超时，从双向链表中将当前 task 移除
     */
    private void removeWriteTimeoutTask(WriteTimeoutTask task) {
        assert task.ctx.executor().inEventLoop();
        // 作为尾结点
        if (task == lastTask) {
            // task is the tail of list
            assert task.next == null;
            lastTask = lastTask.prev;
            if (lastTask != null) {
                lastTask.next = null;
            }
        } else if (task.prev == null && task.next == null) {
            // 就 task 一个任务（已经被移除了或未添加）
            // Since task is not lastTask, then it has been removed or not been added.
            return;
        } else if (task.prev == null) {
            // 作为头结点
            // task is the head of list and the list has at least 2 nodes
            task.next.prev = null;
        } else {
            // 正常的中间节点
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
        // 写超时了，广播写异常事件，并关闭 channel
        if (!closed) {
            ctx.fireExceptionCaught(WriteTimeoutException.INSTANCE);
            ctx.close();
            closed = true;
        }
    }

    private final class WriteTimeoutTask implements Runnable, ChannelFutureListener {

        private final ChannelHandlerContext ctx;
        private final ChannelPromise promise;

        // WriteTimeoutTask is also a node of a doubly-linked list
        WriteTimeoutTask prev;
        WriteTimeoutTask next;

        ScheduledFuture<?> scheduledFuture;

        WriteTimeoutTask(ChannelHandlerContext ctx, ChannelPromise promise) {
            this.ctx = ctx;
            this.promise = promise;
        }

        @Override
        public void run() {
            // Was not written yet so issue a write timeout
            // The promise itself will be failed with a ClosedChannelException once the close() was issued
            // See https://github.com/netty/netty/issues/2159
            // 写操作未完成，说明写超时了
            if (!promise.isDone()) {
                try {
                    writeTimedOut(ctx);
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            }

            removeWriteTimeoutTask(this);
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            // scheduledFuture has already be set when reaching here
            // 取消定时任务
            scheduledFuture.cancel(false);

            // Check if its safe to modify the "doubly-linked-list" that we maintain. If its not we will schedule the
            // modification so its picked up by the executor..
            if (ctx.executor().inEventLoop()) {
                // 从双向链表中移除任务
                removeWriteTimeoutTask(this);
            } else {
                // So let's just pass outself to the executor which will then take care of remove this task
                // from the doubly-linked list. Schedule ourself is fine as the promise itself is done.
                //
                // This fixes https://github.com/netty/netty/issues/11053
                assert promise.isDone();
                ctx.executor().execute(this);
            }
        }
    }
}
