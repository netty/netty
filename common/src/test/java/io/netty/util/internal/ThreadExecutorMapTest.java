/*
 * Copyright 2019 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.ImmediateExecutor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ThreadExecutorMapTest {
    private static final EventExecutor EVENT_EXECUTOR = new AbstractEventExecutor() {
        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return false;
        }

        @Override
        public boolean isShuttingDown() {
            return false;
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
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) {
            return false;
        }

        @Override
        public void execute(@NotNull Runnable command) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    public void testOldExecutorIsRestored() {
        Executor executor = ThreadExecutorMap.apply(ImmediateExecutor.INSTANCE, ImmediateEventExecutor.INSTANCE);
        Executor executor2 = ThreadExecutorMap.apply(ImmediateExecutor.INSTANCE, EVENT_EXECUTOR);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                executor2.execute(new Runnable() {
                    @Override
                    public void run() {
                        assertSame(EVENT_EXECUTOR, ThreadExecutorMap.currentExecutor());
                    }
                });
                assertSame(ImmediateEventExecutor.INSTANCE, ThreadExecutorMap.currentExecutor());
            }
        });
    }

    @Test
    public void testDecorateExecutor() {
        Executor executor = ThreadExecutorMap.apply(ImmediateExecutor.INSTANCE, ImmediateEventExecutor.INSTANCE);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                assertSame(ImmediateEventExecutor.INSTANCE, ThreadExecutorMap.currentExecutor());
            }
        });
    }

    @Test
    public void testDecorateRunnable() {
        ThreadExecutorMap.apply(new Runnable() {
            @Override
            public void run() {
                assertSame(ImmediateEventExecutor.INSTANCE,
                        ThreadExecutorMap.currentExecutor());
            }
        }, ImmediateEventExecutor.INSTANCE).run();
    }

    @Test
    public void testDecorateThreadFactory() throws InterruptedException {
        ThreadFactory threadFactory =
                ThreadExecutorMap.apply(Executors.defaultThreadFactory(), ImmediateEventExecutor.INSTANCE);
        Thread thread = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                assertSame(ImmediateEventExecutor.INSTANCE, ThreadExecutorMap.currentExecutor());
            }
        });
        thread.start();
        thread.join();
    }
}
