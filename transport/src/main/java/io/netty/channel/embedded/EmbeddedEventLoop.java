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
package io.netty.channel.embedded;

import static java.util.Objects.requireNonNull;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.AbstractScheduledEventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.StringUtil;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

final class EmbeddedEventLoop extends AbstractScheduledEventExecutor implements EventLoop {

    private final Queue<Runnable> tasks = new ArrayDeque<>(2);
    private boolean running;

    private static EmbeddedChannel cast(Channel channel) {
        if (channel instanceof EmbeddedChannel) {
            return (EmbeddedChannel) channel;
        }
        throw new IllegalArgumentException("Channel of type " + StringUtil.simpleClassName(channel) + " not supported");
    }

    private final Unsafe unsafe = new Unsafe() {
        @Override
        public void register(Channel channel) {
            assert inEventLoop();
            cast(channel).setActive();
        }

        @Override
        public void deregister(Channel channel) {
            assert inEventLoop();
        }
    };

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public void execute(Runnable command) {
        requireNonNull(command, "command");
        tasks.add(command);
        if (!running) {
            runTasks();
        }
    }

    void runTasks() {
        boolean wasRunning = running;
        try {
            for (;;) {
                running = true;
                Runnable task = tasks.poll();
                if (task == null) {
                    break;
                }

                task.run();
            }
        } finally {
            if (!wasRunning) {
                running = false;
            }
        }
    }

    long runScheduledTasks() {
        long time = AbstractScheduledEventExecutor.nanoTime();
        boolean wasRunning = running;
        try {
            for (;;) {
                running = true;
                Runnable task = pollScheduledTask(time);
                if (task == null) {
                    return nextScheduledTaskNano();
                }

                task.run();
            }
        } finally {
            if (!wasRunning) {
                running = false;
            }
        }
    }

    long nextScheduledTask() {
        return nextScheduledTaskNano();
    }

    void cancelScheduled() {
        running = true;
        try {
            cancelScheduledTasks();
        } finally {
            running = false;
        }
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<?> terminationFuture() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShuttingDown() {
        return false;
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return running;
    }
}
