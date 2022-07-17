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
package io.netty5.channel.embedded;

import io.netty5.channel.Channel;
import io.netty5.channel.EventLoop;
import io.netty5.channel.IoHandle;
import io.netty5.util.concurrent.AbstractScheduledEventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.StringUtil;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

final class EmbeddedEventLoop extends AbstractScheduledEventExecutor implements EventLoop {
    /*
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

    private final Queue<Runnable> tasks = new ArrayDeque<>(2);
    boolean running;

    private static EmbeddedChannel cast(IoHandle handle) {
        if (handle instanceof EmbeddedChannel) {
            return (EmbeddedChannel) handle;
        }
        throw new IllegalArgumentException("Channel of type " + StringUtil.simpleClassName(handle) + " not supported");
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public Future<Void> registerForIo(IoHandle handle) {
        Promise<Void> promise = newPromise();
        EmbeddedChannel channel = cast(handle);
        if (inEventLoop()) {
            registerForIO0(channel, promise);
        } else {
            execute(() -> registerForIO0(channel, promise));
        }
        return promise.asFuture();
    }

    private void registerForIO0(EmbeddedChannel channel, Promise<Void> promise) {
        assert inEventLoop();
        try {
            if (channel.isRegistered()) {
                throw new IllegalStateException("Channel already registered");
            }
            if (!channel.executor().inEventLoop()) {
                throw new IllegalStateException("Channel.executor() is not using the same Thread as this EventLoop");
            }
            channel.setActive();
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess(null);
    }
    @Override
    public Future<Void> deregisterForIo(IoHandle handle) {
        Promise<Void> promise = newPromise();
        EmbeddedChannel channel = cast(handle);
        if (inEventLoop()) {
            deregisterForIO0(channel, promise);
        } else {
            execute(() -> deregisterForIO0(channel, promise));
        }
        return promise.asFuture();
    }

    private void deregisterForIO0(Channel channel, Promise<Void> promise) {
        try {
            if (!channel.isRegistered()) {
                throw new IllegalStateException("Channel not registered");
            }
            if (!channel.executor().inEventLoop()) {
                throw new IllegalStateException("Channel.executor() is not using the same Thread as this EventLoop");
            }
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess(null);
    }

    @Override
    public void execute(Runnable task) {
        requireNonNull(task, "command");
        tasks.add(task);
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

    boolean hasPendingNormalTasks() {
        return !tasks.isEmpty();
    }

    long runScheduledTasks() {
        long time = getCurrentTimeNanos();
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
    public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> terminationFuture() {
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

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return EmbeddedChannel.class.isAssignableFrom(handleType);
    }
}
