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

import io.netty5.channel.EventLoop;
import io.netty5.channel.IoHandle;
import io.netty5.channel.IoHandler;
import io.netty5.channel.IoOps;
import io.netty5.channel.IoRegistration;
import io.netty5.util.concurrent.AbstractScheduledEventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.concurrent.Ticker;
import io.netty5.util.internal.StringUtil;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

final class EmbeddedEventLoop extends AbstractScheduledEventExecutor implements EventLoop {
    private final Queue<Runnable> tasks = new ArrayDeque<>(2);
    private final Ticker ticker;
    boolean running;
    // Used to detect concurrent accesses:
    private Thread holder;
    private int holderRefs;

    private static EmbeddedIoHandle cast(IoHandle handle) {
        if (handle instanceof EmbeddedIoHandle) {
            return (EmbeddedIoHandle) handle;
        }
        throw new IllegalArgumentException("IoHandle of type " + StringUtil.simpleClassName(handle) + " not supported");
    }

    EmbeddedEventLoop() {
        this(Ticker.systemTicker());
    }

    EmbeddedEventLoop(Ticker ticker) {
        this.ticker = requireNonNull(ticker, "ticker");
    }

    @Override
    protected Ticker ticker() {
        return ticker;
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public Future<IoRegistration> register(IoHandle handle) {
        Promise<IoRegistration> promise = newPromise();
        EmbeddedIoHandle channel = cast(handle);
        if (inEventLoop()) {
            registerForIO0(channel, promise);
        } else {
            execute(() -> registerForIO0(channel, promise));
        }
        return promise.asFuture();
    }

    private void registerForIO0(EmbeddedIoHandle handle, Promise<IoRegistration> promise) {
        assert inEventLoop();
        try {
            handle.setActive();
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess(new EmbeddedIoRegistration());
    }

    private static final class EmbeddedIoRegistration implements IoRegistration {

        private boolean cancelled;

        @Override
        public long submit(IoOps ops) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValid() {
            return !cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public IoHandler ioHandler() {
            return null;
        }
    }

    @Override
    public boolean isIoType(Class<? extends IoHandler> handlerType) {
        return false;
    }

    @Override
    public void execute(Runnable task) {
        requireNonNull(task, "task");
        begin();
        try {
            tasks.add(task);
            if (!running) {
                runTasks();
            }
        } finally {
            end();
        }
    }

    void runTasks() {
        begin();
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
            end();
        }
    }

    boolean hasPendingNormalTasks() {
        begin();
        try {
            return !tasks.isEmpty();
        } finally {
            end();
        }
    }

    long runScheduledTasks() {
        begin();
        long time = ticker().nanoTime();
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
            end();
        }
    }

    long nextScheduledTask() {
        begin();
        try {
            return nextScheduledTaskNano();
        } finally {
            end();
        }
    }

    void cancelScheduled() {
        begin();
        try {
            running = true;
            try {
                cancelScheduledTasks();
            } finally {
                running = false;
            }
        } finally {
            end();
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
        return isRunning();
    }

    boolean isRunning() {
        begin();
        try {
            return running;
        } finally {
            end();
        }
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return EmbeddedIoHandle.class.isAssignableFrom(handleType);
    }

    private void begin() {
        Thread thisThread = Thread.currentThread();
        Thread currThread = holder;
        if (currThread == null) {
            holder = thisThread;
            holderRefs = 1;
            return;
        }
        if (currThread == thisThread) {
            holderRefs++;
            return;
        }
        throw overlappingAccessException(thisThread, currThread);
    }

    private void end() {
        Thread thisThread = Thread.currentThread();
        Thread currThread = holder;
        int refs = holderRefs;
        if (thisThread != currThread || refs == 0) {
            throw overlappingAccessException(thisThread, currThread);
        }
        refs--;
        if (refs == 0) {
            holder = null;
        }
        holderRefs = refs;
    }

    private static IllegalStateException overlappingAccessException(Thread thisThread, Thread currThread) {
        return new IllegalStateException(
                "Concurrent access by multiple threads to the EmbeddedEventLoop is not allowed. " +
                        "This thread " + thisThread + ", and " + currThread + ", had overlapping accesses.");
    }
}
