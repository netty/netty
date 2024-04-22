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

    EpollEventLoop(IoHandleEventLoopGroup parent, ThreadFactory threadFactory, IoHandler ioHandler) {
        super(parent, threadFactory, ioHandler);
    }

    EpollEventLoop(IoHandleEventLoopGroup parent, Executor executor, IoHandler ioHandler) {
        super(parent, executor, ioHandler);
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
     * Returns 0.
     */
    public int getIoRatio() {
        return 0;
    }

    /**
     * This method does nothing.
     */
    public void setIoRatio(int ioRatio) {
    }
}
