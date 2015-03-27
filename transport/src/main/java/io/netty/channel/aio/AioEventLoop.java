/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.aio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SingleThreadEventLoop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;

/**
 * {@link SingleThreadEventLoop} implementations which will handle AIO {@link Channel}s.
 */
final class AioEventLoop extends SingleThreadEventLoop {

    private final Set<Channel> channels = Collections.newSetFromMap(new IdentityHashMap<Channel, Boolean>());
    private final ChannelFutureListener deregistrationListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            channels.remove(future.channel());
        }
    };
    private final ChannelFutureListener registrationListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess()) {
                return;
            }

            Channel ch = future.channel();
            channels.add(ch);
            ch.closeFuture().addListener(deregistrationListener);
        }
    };
    private LinkedBlockingDeque<Runnable> taskQueue;

    AioEventLoop(AioEventLoopGroup parent, ThreadFactory threadFactory) {
        super(parent, threadFactory, true);
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return super.register(channel).addListener(registrationListener);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise future) {
        return super.register(channel, future).addListener(registrationListener);
    }

    @Override
    protected void run() {
        for (;;) {
            Runnable task = takeTask();
            if (task != null) {
                task.run();
                updateLastExecutionTime();
            }

            if (isShuttingDown()) {
                closeAll();
                if (confirmShutdown()) {
                    break;
                }
            }
        }
    }

    private void closeAll() {
        Collection<Channel> channels = new ArrayList<Channel>(this.channels.size());
        for (Channel ch : this.channels) {
            channels.add(ch);
        }

        for (Channel ch : channels) {
            ch.unsafe().close(ch.voidPromise());
        }
    }

    @Override
    protected Queue<Runnable> newTaskQueue() {
        // use a Deque as we need to be able to also add tasks on the first position.
        taskQueue = new LinkedBlockingDeque<Runnable>();
        return taskQueue;
    }

    @Override
    protected void addTask(Runnable task) {
        if (task instanceof RecursionBreakingRunnable) {
            if (task == null) {
                throw new NullPointerException("task");
            }
            if (isTerminated()) {
                reject();
            }
            // put the task at the first postion of the queue as we just schedule it to
            // break the recursive operation
            taskQueue.addFirst(task);
        } else {
            super.addTask(task);
        }
    }

    /**
     * Special Runnable which is used by {@link AioCompletionHandler} to break a recursive call and so prevent from
     * StackOverFlowError. When a task is executed that implement it needs to put on the first position of the queue to
     * guarantee execution order and break the recursive call.
     */
    interface RecursionBreakingRunnable extends Runnable {
        // Marker interface
    }
}
