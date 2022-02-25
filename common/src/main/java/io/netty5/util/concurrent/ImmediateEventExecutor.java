/*
 * Copyright 2013 The Netty Project
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
package io.netty5.util.concurrent;

import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Executes {@link Runnable} objects in the caller's thread. If the {@link #execute(Runnable)} is reentrant it will be
 * queued until the original {@link Runnable} finishes execution.
 * <p>
 * All {@link Throwable} objects thrown from {@link #execute(Runnable)} will be swallowed and logged. This is to ensure
 * that all queued {@link Runnable} objects have the chance to be run.
 */
public final class ImmediateEventExecutor extends AbstractEventExecutor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ImmediateEventExecutor.class);
    public static final ImmediateEventExecutor INSTANCE = new ImmediateEventExecutor();
    /**
     * A Runnable will be queued if we are executing a Runnable. This is to prevent a {@link StackOverflowError}.
     */
    private static final FastThreadLocal<Queue<Runnable>> DELAYED_RUNNABLES = new FastThreadLocal<Queue<Runnable>>() {
        @Override
        protected Queue<Runnable> initialValue() throws Exception {
            return new ArrayDeque<>();
        }
    };
    /**
     * Set to {@code true} if we are executing a runnable.
     */
    private static final FastThreadLocal<Boolean> RUNNING = new FastThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() throws Exception {
            return false;
        }
    };

    private final Future<Void> terminationFuture = DefaultPromise.<Void>newFailedPromise(
            GlobalEventExecutor.INSTANCE, new UnsupportedOperationException()).asFuture();

    private ImmediateEventExecutor() { }

    @Override
    public boolean inEventLoop(Thread thread) {
        return true;
    }

    @Override
    public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return terminationFuture();
    }

    @Override
    public Future<Void> terminationFuture() {
        return terminationFuture;
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
    public void execute(Runnable task) {
        requireNonNull(task, "command");
        if (!RUNNING.get()) {
            RUNNING.set(true);
            try {
                task.run();
            } catch (Throwable cause) {
                logger.info("Throwable caught while executing Runnable {}", task, cause);
            } finally {
                Queue<Runnable> delayedRunnables = DELAYED_RUNNABLES.get();
                Runnable runnable;
                while ((runnable = delayedRunnables.poll()) != null) {
                    try {
                        runnable.run();
                    } catch (Throwable cause) {
                        logger.info("Throwable caught while executing Runnable {}", runnable, cause);
                    }
                }
                RUNNING.set(false);
            }
        } else {
            DELAYED_RUNNABLES.get().add(task);
        }
    }

    @Override
    public <V> Promise<V> newPromise() {
        return new ImmediatePromise<>(this);
    }

    @Override
    public Future<Void> schedule(Runnable task, long delay,
                                 TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    static class ImmediatePromise<V> extends DefaultPromise<V> {
        ImmediatePromise(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void checkDeadLock() {
            // No check
        }
    }
}
