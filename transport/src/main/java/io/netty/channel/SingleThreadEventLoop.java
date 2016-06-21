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

import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.concurrent.ThreadFactory;

/**
 * Abstract base class for {@link EventLoop}'s that execute all its submitted tasks in a single thread.
 *
 */
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    protected static final int DEFAULT_MAX_PENDING_TASKS = Math.max(16,
            SystemPropertyUtil.getInt("io.netty.eventLoop.maxPendingTasks", Integer.MAX_VALUE));

    /**
     * @see {@link SingleThreadEventExecutor#SingleThreadEventExecutor(EventExecutorGroup, ThreadFactory, boolean)}
     */
    protected SingleThreadEventLoop(EventLoopGroup parent, ThreadFactory threadFactory, boolean addTaskWakesUp) {
        this(parent, threadFactory, addTaskWakesUp, DEFAULT_MAX_PENDING_TASKS, RejectedExecutionHandlers.reject());
    }

    protected SingleThreadEventLoop(EventLoopGroup parent, ThreadFactory threadFactory,
                                    boolean addTaskWakesUp, int maxPendingTasks,
                                    RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, threadFactory, addTaskWakesUp, maxPendingTasks, rejectedExecutionHandler);
    }

    @Override
    public EventLoopGroup parent() {
        return (EventLoopGroup) super.parent();
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return register(channel, new DefaultChannelPromise(channel, this));
    }

    @Override
    public ChannelFuture register(final Channel channel, final ChannelPromise promise) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (promise == null) {
            throw new NullPointerException("promise");
        }

        channel.unsafe().register(this, promise);
        return promise;
    }

    @Override
    protected boolean wakesUpForTask(Runnable task) {
        return !(task instanceof NonWakeupRunnable);
    }

    /**
     * Marker interface for {@link Runnable} that will not trigger an {@link #wakeup(boolean)} in all cases.
     */
    interface NonWakeupRunnable extends Runnable { }
}
