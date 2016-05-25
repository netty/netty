/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * {@link AbstractEventExecutor} which execute tasks in the caller's thread.
 * <p>
 * This class does not provide any protection against re-entry or {@link StackOverflowError}.
 * Use {@link ReentrantImmediateEventExecutor} if these protections are necessary.
 */
public class ImmediateEventExecutor extends AbstractEventExecutor {

    public static final ImmediateEventExecutor INSTANCE = new ImmediateEventExecutor();

    private final Future<?> terminationFuture = new FailedFuture<Object>(
            GlobalEventExecutor.INSTANCE, new UnsupportedOperationException());

    ImmediateEventExecutor() { }

    @Override
    public final boolean inEventLoop() {
        return true;
    }

    @Override
    public final boolean inEventLoop(Thread thread) {
        return true;
    }

    @Override
    public final Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return terminationFuture();
    }

    @Override
    public final Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public final void shutdown() { }

    @Override
    public final boolean isShuttingDown() {
        return false;
    }

    @Override
    public final boolean isShutdown() {
        return false;
    }

    @Override
    public final boolean isTerminated() {
        return false;
    }

    @Override
    public final boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        command.run();
    }

    @Override
    public final <V> Promise<V> newPromise() {
        return new ImmediatePromise<V>(this);
    }

    @Override
    public final <V> ProgressivePromise<V> newProgressivePromise() {
        return new ImmediateProgressivePromise<V>(this);
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

    static class ImmediateProgressivePromise<V> extends DefaultProgressivePromise<V> {
        ImmediateProgressivePromise(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void checkDeadLock() {
            // No check
        }
    }
}
