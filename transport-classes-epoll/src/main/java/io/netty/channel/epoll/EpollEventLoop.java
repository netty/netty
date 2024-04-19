/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.IoHandleEventLoopGroup;
import io.netty.channel.IoHandler;
import io.netty.channel.SingleThreadIoHandleEventLoop;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.internal.PlatformDependent;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class EpollEventLoop extends SingleThreadIoHandleEventLoop {

    private int ioRatio;

    EpollEventLoop(IoHandleEventLoopGroup parent, ThreadFactory threadFactory, IoHandler ioHandler) {
        super(parent, threadFactory, ioHandler);
    }

    EpollEventLoop(IoHandleEventLoopGroup parent, Executor executor, IoHandler ioHandler) {
        super(parent, executor, ioHandler);
    }

    EpollEventLoop(IoHandleEventLoopGroup parent, ThreadFactory threadFactory, IoHandler ioHandler,
                          int maxPendingTasks, RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, threadFactory, ioHandler, maxPendingTasks, rejectedExecutionHandler);
    }

    EpollEventLoop(IoHandleEventLoopGroup parent, Executor executor, IoHandler ioHandler,
                          int maxPendingTasks, RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, ioHandler, maxPendingTasks, rejectedExecutionHandler);
    }

    EpollEventLoop(IoHandleEventLoopGroup parent, Executor executor, IoHandler ioHandler,
                   EventLoopTaskQueueFactory taskQueueFactory,
                   EventLoopTaskQueueFactory tailTaskQueueFactory,
                          RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, ioHandler, newTaskQueue(taskQueueFactory), newTaskQueue(tailTaskQueueFactory),
                rejectedExecutionHandler);
    }

    private static Queue<Runnable> newTaskQueue(
            EventLoopTaskQueueFactory queueFactory) {
        if (queueFactory == null) {
            return newTaskQueue0(DEFAULT_MAX_PENDING_TASKS);
        }
        return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
    }

    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return newTaskQueue0(maxPendingTasks);
    }

    private static Queue<Runnable> newTaskQueue0(int maxPendingTasks) {
        // This event loop never calls takeTask()
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

    @Override
    public int registeredChannels() {
        return ((EpollHandler) ioHandler()).numRegisteredChannels();
    }

    @Override
    public Iterator<Channel> registeredChannelsIterator() {
        return ((EpollHandler) ioHandler()).registeredChannelsList().iterator();
    }

    /**
     * Returns the percentage of the desired amount of time spent for I/O in the event loop.
     */
    public int getIoRatio() {
        return ioRatio;
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the event loop. Value range from 1-100.
     * The default value is {@code 50}, which means the event loop will try to spend the same amount of time for I/O
     * as for non-I/O tasks. The lower the number the more time can be spent on non-I/O tasks. If value set to
     * {@code 100}, this feature will be disabled and event loop will not attempt to balance I/O and non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        if (ioRatio <= 0 || ioRatio > 100) {
            throw new IllegalArgumentException("ioRatio: " + ioRatio + " (expected: 0 < ioRatio <= 100)");
        }
        this.ioRatio = ioRatio;
    }
}
