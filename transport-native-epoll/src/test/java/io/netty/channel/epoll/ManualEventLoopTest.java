/*
 * Copyright 2025 The Netty Project
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

import io.netty.channel.IoEventLoop;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ManualIoEventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Timeout(5)
public class ManualEventLoopTest {

    @Test
    void firstRacySubmissionMissWakeupEpoll() throws Exception {
        racySubmissionMissWakeup(EpollIoHandler.newFactory(), 1);
    }

    @Test
    void secondRacySubmissionMissWakeupEpoll() throws Exception {
        racySubmissionMissWakeup(EpollIoHandler.newFactory(), 2);
    }

    @Test
    void firstRacyOtherSubmissionMissWakeupEpoll() throws Exception {
        racyOtherSubmissionMissWakeup(EpollIoHandler.newFactory(), 1);
    }

    @Test
    void secondRacyOtherSubmissionMissWakeupEpoll() throws Exception {
        racyOtherSubmissionMissWakeup(EpollIoHandler.newFactory(), 2);
    }

    private void racySubmissionMissWakeup(IoHandlerFactory handlerFactory, long canBlockAttempt)
            throws Exception {
        CyclicBarrier waitBeforeSubmittingTask = new CyclicBarrier(2);
        CountDownLatch taskSubmitted = new CountDownLatch(1);
        AtomicLong canBlock = new AtomicLong(0);
        ManualMultithreadedIoEventLoopGroup group = new ManualMultithreadedIoEventLoopGroup(handlerFactory) {
            @Override
            protected void beforeCanBlock(Executor executor) {
                if (canBlock.incrementAndGet() == canBlockAttempt) {
                    try {
                        waitBeforeSubmittingTask.await();
                    } catch (Throwable ignore) {
                        //
                    }
                    try {
                        taskSubmitted.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
        waitBeforeSubmittingTask.await();
        // depending on canBlockAttempt this submission could observe an AWAKE event loop
        // or with a setup NONE deadline (e.g. Long.MAX_VALUE).
        // In the latter case, it can be already asleep or ready to do it.
        Future<?> submitted = group.submit(() -> {
        });
        // unblock canBlock
        taskSubmitted.countDown();
        submitted.get();
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).get();
    }

    private void racyOtherSubmissionMissWakeup(IoHandlerFactory handlerFactory, long canBlockAttempt)
            throws Exception {
        CyclicBarrier waitBeforeSubmittingTask = new CyclicBarrier(2);
        CountDownLatch taskSubmitted = new CountDownLatch(1);
        AtomicLong canBlock = new AtomicLong(0);
        ManualMultithreadedIoEventLoopGroup group = new ManualMultithreadedIoEventLoopGroup(handlerFactory) {
            @Override
            protected void beforeCanBlock(Executor executor) {
                // this should be called when canBlock is called after setting the wakeup flag!
                if (canBlock.incrementAndGet() == canBlockAttempt) {
                    try {
                        waitBeforeSubmittingTask.await();
                    } catch (Throwable ignore) {
                        //
                    }
                    try {
                        taskSubmitted.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
        waitBeforeSubmittingTask.await();
        CountDownLatch completed = new CountDownLatch(1);
        // depending on canBlockAttempt this submission could observe an AWAKE event loop
        // or with a setup NONE deadline (e.g. Long.MAX_VALUE).
        // In the latter case, it can be already asleep or ready to do it.
        group.ioEventLoopRunner.execute(completed::countDown);
        taskSubmitted.countDown();
        completed.await();
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).get();
    }

    @RepeatedTest(value = 100, failureThreshold = 1)
    void testTightShutodownEpoll() throws InterruptedException, ExecutionException {
        ManualMultithreadedIoEventLoopGroup group = new ManualMultithreadedIoEventLoopGroup(
                EpollIoHandler.newFactory());
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).get();
    }

    public static class ManualMultithreadedIoEventLoopGroup extends MultiThreadIoEventLoopGroup {

        private ManualIoEventLoopRunner ioEventLoopRunner;
        private Consumer<ManualIoEventLoopRunner> beforeCanBlock;

        public ManualMultithreadedIoEventLoopGroup(IoHandlerFactory ioHandlerFactory) {
            super(1, ioHandlerFactory);
        }

        @Override
        protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
            this.ioEventLoopRunner = new ManualIoEventLoopRunner(this, ioHandlerFactory,
                    executor, this::beforeCanBlock);
            return ioEventLoopRunner.ioEventLoop;
        }

        protected void beforeCanBlock(Executor executor) {
        }

        private static class ManualIoEventLoopRunner implements Executor {

            private final ManualIoEventLoop ioEventLoop;
            private final Queue<Runnable> otherTasks = new ConcurrentLinkedQueue<>();

            ManualIoEventLoopRunner(IoEventLoopGroup parent, IoHandlerFactory factory,
                                    Executor executor, Consumer<Executor> beforeCanBlock) {
                this.ioEventLoop = new ManualIoEventLoop(parent, null, factory) {
                    @Override
                    protected boolean canBlock() {
                        if (beforeCanBlock != null) {
                            beforeCanBlock.accept(ManualIoEventLoopRunner.this);
                        }
                        return otherTasks.isEmpty();
                    }
                };
                CountDownLatch started = new CountDownLatch(1);
                executor.execute(() -> {
                    ioEventLoop.setOwningThread(Thread.currentThread());
                    // it would force a first init
                    ioEventLoop.runNow();
                    started.countDown();
                    mainLoop();
                });
                try {
                    started.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }

            private void mainLoop() {
                while (!ioEventLoop.isShuttingDown()) {
                    ioEventLoop.run(0);
                    Runnable otherTask = otherTasks.poll();
                    if (otherTask != null) {
                        safeExecute(otherTask);
                    }
                }
                while (!ioEventLoop.isTerminated() || !otherTasks.isEmpty()) {
                    ioEventLoop.runNow();
                    Runnable otherTask = otherTasks.poll();
                    if (otherTask != null) {
                        safeExecute(otherTask);
                    }
                }
            }

            private void safeExecute(Runnable task) {
                try {
                    task.run();
                } catch (Throwable ignore) {
                    //
                }
            }

            public void execute(Runnable otherTask) {
                otherTasks.add(otherTask);
                if (ioEventLoop.isShutdown()) {
                    if (otherTasks.remove(otherTask)) {
                        throw new RejectedExecutionException("Event loop shut down");
                    }
                }
                ioEventLoop.wakeup();
            }
        }
    }
}
