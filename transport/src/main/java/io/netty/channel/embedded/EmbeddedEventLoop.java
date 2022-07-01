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
package io.netty.channel.embedded;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.AbstractScheduledEventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.ObjectUtil;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

final class EmbeddedEventLoop extends AbstractScheduledEventExecutor implements EventLoop {
    /**
     * When time is not {@link #timeFrozen frozen}, the base time to subtract from {@link System#nanoTime()}. When time
     * is frozen, this variable is unused.
     *
     * Initialized to {@link #initialNanoTime()} so that until one of the time mutator methods is called,
     * {@link #getCurrentTimeNanos()} matches the default behavior.
     */
    private long startTime = initialNanoTime();
    /**
     * When time is frozen, the timestamp returned by {@link #getCurrentTimeNanos()}. When unfrozen, this is unused.
     */
    private long frozenTimestamp;
    /**
     * Whether time is currently frozen.
     */
    private boolean timeFrozen;

    private final Queue<Runnable> tasks = new ArrayDeque<Runnable>(2);

    @Override
    public EventLoopGroup parent() {
        return (EventLoopGroup) super.parent();
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public void execute(Runnable command) {
        tasks.add(ObjectUtil.checkNotNull(command, "command"));
    }

    void runTasks() {
        for (;;) {
            Runnable task = tasks.poll();
            if (task == null) {
                break;
            }

            task.run();
        }
    }

    boolean hasPendingNormalTasks() {
        return !tasks.isEmpty();
    }

    long runScheduledTasks() {
        long time = getCurrentTimeNanos();
        for (;;) {
            Runnable task = pollScheduledTask(time);
            if (task == null) {
                return nextScheduledTaskNano();
            }

            task.run();
        }
    }

    long nextScheduledTask() {
        return nextScheduledTaskNano();
    }

    @Override
    protected long getCurrentTimeNanos() {
        if (timeFrozen) {
            return frozenTimestamp;
        }
        return System.nanoTime() - startTime;
    }

    void advanceTimeBy(long nanos) {
        if (timeFrozen) {
            frozenTimestamp += nanos;
        } else {
            // startTime is subtracted from nanoTime, so increasing the startTime will advance getCurrentTimeNanos
            startTime -= nanos;
        }
    }

    void freezeTime() {
        if (!timeFrozen) {
            frozenTimestamp = getCurrentTimeNanos();
            timeFrozen = true;
        }
    }

    void unfreezeTime() {
        if (timeFrozen) {
            // we want getCurrentTimeNanos to continue right where frozenTimestamp left off:
            // getCurrentTimeNanos = nanoTime - startTime = frozenTimestamp
            // then solve for startTime
            startTime = System.nanoTime() - frozenTimestamp;
            timeFrozen = false;
        }
    }

    @Override
    protected void cancelScheduledTasks() {
        super.cancelScheduledTasks();
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
    public ChannelFuture register(Channel channel) {
        return register(new DefaultChannelPromise(channel, this));
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }

    @Deprecated
    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        channel.unsafe().register(this, promise);
        return promise;
    }

    @Override
    public boolean inEventLoop() {
        return true;
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return true;
    }
}
