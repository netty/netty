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
package io.netty.channel.kqueue;

import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class KQueueIoHandlerWakeupTest {

    private ExecutorService loopExecutor;
    private volatile Thread loopThread;
    private KQueueIoHandler handler;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(KQueue.isAvailable());
    }

    @BeforeEach
    public void setUp() throws Exception {
        loopExecutor = Executors.newSingleThreadExecutor();
        // Pin the "event loop thread" before creating the handler, so wakeup() calls made from any other thread
        // are treated as coming from outside the event loop.
        loopThread = loopExecutor.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
        ThreadAwareExecutor executor = new ThreadAwareExecutor() {
            @Override
            public boolean isExecutorThread(Thread thread) {
                return thread == loopThread;
            }

            @Override
            public void execute(Runnable command) {
                throw new UnsupportedOperationException();
            }
        };
        IoHandler h = KQueueIoHandler.newFactory().newHandler(executor);
        handler = (KQueueIoHandler) h;
    }

    @AfterEach
    public void tearDown() throws Exception {
        try {
            loopExecutor.submit(() -> handler.destroy()).get(5, TimeUnit.SECONDS);
        } finally {
            loopExecutor.shutdownNow();
        }
    }

    @Test
    @Timeout(30)
    public void testWakeupWhileAwakeDoesNotTriggerUserEvent() throws Exception {
        // The event loop is not blocked in kevent(...) so there is nothing to wake up. It will find the work that
        // comes with a wakeup the next time it is about to block, so the (expensive) user event must not be
        // triggered.
        fromAnotherThread(handler::wakeup);
        assertThat(pendingEvents()).isZero();

        // The remembered request must not make us block forever, it is consumed by the next run.
        assertThat(runOnLoopThread(blockingContext(null))).succeedsWithin(10, TimeUnit.SECONDS);
        assertThat(pendingEvents()).isZero();
    }

    @Test
    @Timeout(30)
    public void testWakeupWhileSelectingTriggersUserEvent() throws Exception {
        // Request the wakeup after the event loop decided that it is going to block. Nothing else will wake it up
        // as nothing is scheduled and there is no I/O, so run() only returns if the user event was triggered.
        assertThat(runOnLoopThread(blockingContext(() -> fromAnotherThread(handler::wakeup))))
                .succeedsWithin(10, TimeUnit.SECONDS);
        assertThat(pendingEvents()).isZero();
    }

    @Test
    @Timeout(30)
    public void testWakeupWithoutTaskIsNeverLost() throws Exception {
        // A wakeup is not always accompanied by a task (for example, suspension). Whatever the state of the event
        // loop is, it needs to notice the request and may not block forever.
        final int iterations = 20_000;
        final AtomicLong request = new AtomicLong();
        final AtomicLong seen = new AtomicLong();
        final IoHandlerContext blocking = blockingContext(null);

        CompletableFuture<Void> loop = runOnLoopThread(() -> {
            while (seen.get() < iterations) {
                handler.run(blocking);
                seen.set(request.get());
            }
        });

        for (long i = 1; i <= iterations; i++) {
            request.set(i);
            handler.wakeup();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (seen.get() < i) {
                assertThat(System.nanoTime() - deadline).as("wakeup %s was lost", i).isNegative();
                Thread.yield();
            }
        }
        assertThat(loop).succeedsWithin(10, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(60)
    public void testTasksFromMultipleThreadsAreNeverLost() throws Exception {
        final int producers = 4;
        final int tasksPerProducer = 50_000;
        final Queue<Object> queue = new ConcurrentLinkedQueue<>();
        final IoHandlerContext context = context(queue::isEmpty, null);

        CompletableFuture<Void> loop = runOnLoopThread(() -> {
            int processed = 0;
            while (processed < producers * tasksPerProducer) {
                handler.run(context);
                while (queue.poll() != null) {
                    processed++;
                }
            }
        });

        ExecutorService producerExecutor = Executors.newFixedThreadPool(producers);
        try {
            for (int p = 0; p < producers; p++) {
                producerExecutor.execute(() -> {
                    for (int i = 0; i < tasksPerProducer; i++) {
                        queue.add(this);
                        handler.wakeup();
                    }
                });
            }
            assertThat(loop).succeedsWithin(30, TimeUnit.SECONDS);
        } finally {
            producerExecutor.shutdownNow();
        }
    }

    /**
     * Returns the number of events that are ready in the kqueue of the handler, which consumes them.
     */
    private int pendingEvents() throws Exception {
        Field field = KQueueIoHandler.class.getDeclaredField("kqueueFd");
        field.setAccessible(true);
        int kqueueFd = ((FileDescriptor) field.get(handler)).intValue();
        KQueueEventArray changeList = new KQueueEventArray(4);
        KQueueEventArray eventList = new KQueueEventArray(4);
        try {
            return Native.keventWait(kqueueFd, changeList, eventList, 0, 0);
        } finally {
            changeList.free();
            eventList.free();
        }
    }

    private CompletableFuture<Void> runOnLoopThread(IoHandlerContext context) {
        return runOnLoopThread(() -> handler.run(context));
    }

    private CompletableFuture<Void> runOnLoopThread(Runnable task) {
        return CompletableFuture.runAsync(task, loopExecutor);
    }

    private static void fromAnotherThread(Runnable task) {
        assertThat(CompletableFuture.runAsync(task)).succeedsWithin(10, TimeUnit.SECONDS);
    }

    private static IoHandlerContext blockingContext(Runnable onDelayNanos) {
        return context(() -> true, onDelayNanos);
    }

    private static IoHandlerContext context(BooleanSupplier canBlock, Runnable onDelayNanos) {
        return new IoHandlerContext() {
            @Override
            public boolean canBlock() {
                return canBlock.getAsBoolean();
            }

            @Override
            public long delayNanos(long currentTimeNanos) {
                // Called after the event loop decided to block.
                if (onDelayNanos != null) {
                    onDelayNanos.run();
                }
                return Long.MAX_VALUE;
            }

            @Override
            public long deadlineNanos() {
                return -1;
            }
        };
    }
}
