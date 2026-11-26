/*
 * Copyright 2026 The Netty Project
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
package io.netty.testsuite.transport;

import io.netty.channel.IoHandlerFactory;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.util.IntSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a request to suspend an event loop is never missed, no matter how it races with the event loop
 * that is about to block.
 * <p>
 * {@link io.netty.util.concurrent.SingleThreadEventExecutor#trySuspend()} only wakes up the
 * {@link io.netty.channel.IoHandler}, it does not queue a task. Some {@link io.netty.channel.IoHandler}s ignore a
 * wakeup that arrives while the event loop is awake, so this must not be the only thing that makes the event loop
 * notice the request.
 * <p>
 * Subclasses provide the {@link IoHandlerFactory} for each transport. It must use the given
 * {@link SelectStrategyFactory}.
 */
public abstract class AbstractIoHandlerSuspendTest {

    protected abstract IoHandlerFactory newIoHandlerFactory(SelectStrategyFactory selectStrategyFactory);

    @Test
    @Timeout(60)
    public void testSuspendRequestedJustBeforeBlockingIsNotMissed() throws Exception {
        final BlockingQueue<Thread> threads = new LinkedBlockingQueue<Thread>();
        final AtomicReference<SingleThreadIoEventLoop> loopRef = new AtomicReference<SingleThreadIoEventLoop>();
        final AtomicReference<Boolean> suspendResult = new AtomicReference<Boolean>();
        final AtomicBoolean armed = new AtomicBoolean();

        SelectStrategyFactory strategyFactory = new SelectStrategyFactory() {
            @Override
            public SelectStrategy newSelectStrategy() {
                return new SelectStrategy() {
                    @Override
                    public int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks) throws Exception {
                        // This is called by the event loop thread after it decided to keep running because it
                        // could not suspend yet, and before the IoHandler announces that it is about to block.
                        // Request the suspension from another thread right now.
                        if (armed.compareAndSet(true, false)) {
                            suspendResult.set(CompletableFuture.supplyAsync(() -> loopRef.get().trySuspend())
                                    .get(10, TimeUnit.SECONDS));
                        }
                        return hasTasks ? selectSupplier.get() : SelectStrategy.SELECT;
                    }
                };
            }
        };

        final SingleThreadIoEventLoop loop = new SingleThreadIoEventLoop(null, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                threads.add(thread);
                return thread;
            }
        }, newIoHandlerFactory(strategyFactory));
        loopRef.set(loop);
        try {
            // Arm from the event loop thread, so the next iteration of the loop is the one that is interrupted.
            // Nothing is submitted after that, which would be seen by the event loop and hide the problem.
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    armed.set(true);
                }
            });

            Thread thread = threads.poll(10, TimeUnit.SECONDS);
            assertNotNull(thread);
            thread.join(TimeUnit.SECONDS.toMillis(10));

            assertEquals(Boolean.TRUE, suspendResult.get());
            assertFalse(thread.isAlive(), "The event loop thread should have been suspended");
            assertTrue(loop.isSuspended());
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS);
        }
    }
}
