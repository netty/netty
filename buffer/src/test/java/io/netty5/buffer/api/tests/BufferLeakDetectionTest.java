/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.LeakInfo;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.Send;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationListener;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("StringOperationCanBeSimplified")
public class BufferLeakDetectionTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferMustNotLeakWhenClosedProperly(Fixture fixture, TestInfo testInfo) throws Exception {
        Object hint = makeHint(testInfo);
        Consumer<Buffer> closeBuffer = buffer -> buffer.close();
        AtomicInteger counter = new AtomicInteger();
        Semaphore gcEvents = new Semaphore(0);
        Consumer<LeakInfo> callback = forHint(hint, leak -> counter.incrementAndGet());
        try (var ignore1 = MemoryManager.onLeakDetected(callback);
             var ignore2 = installGcEventListener(() -> gcEvents.release());
             BufferAllocator allocator = fixture.createAllocator()) {
            var runnable = new CreateAndUseBuffers(allocator, hint, closeBuffer);
            var thread = new Thread(runnable);
            thread.start();
            gcEvents.acquire(); // Wait for a GC event to happen.
            thread.interrupt();
            thread.join();
            assertThat(counter.get()).as("Unexpected leak in " + testInfo.getDisplayName()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferLeakMustBeDetectedWhenNotClosedProperly(Fixture fixture, TestInfo testInfo) throws Exception {
        Object hint = makeHint(testInfo);
        Consumer<Buffer> leakBuffer = buffer -> { };
        LinkedBlockingQueue<LeakInfo> leakQueue = new LinkedBlockingQueue<>();
        Consumer<LeakInfo> callback = forHint(hint, leak -> leakQueue.offer(leak));
        CreateAndUseBuffers runnable;
        Thread thread;
        LeakInfo leakInfo;
        try (var ignore1 = MemoryManager.onLeakDetected(callback);
             BufferAllocator allocator = fixture.createAllocator()) {
            runnable = new CreateAndUseBuffers(allocator, hint, leakBuffer);
            thread = new Thread(runnable);
            thread.start();
            leakInfo = leakQueue.poll(20, TimeUnit.SECONDS);
            thread.interrupt();
            thread.join();
        }
        assertThat(leakInfo)
                .as("No leak detected in 20 seconds for \"" + testInfo.getDisplayName() + "\".")
                .isNotNull();
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferMustNotLeakWhenClosedAfterSend(Fixture fixture, TestInfo testInfo) throws Exception {
        Object hint = makeHint(testInfo);
        Consumer<Buffer> sendThenClose = buffer -> {
            Send<Buffer> send = buffer.send();
            send.receive().close();
        };
        AtomicInteger counter = new AtomicInteger();
        Semaphore gcEvents = new Semaphore(0);
        Consumer<LeakInfo> callback = forHint(hint, leak -> counter.incrementAndGet());
        try (var ignore1 = MemoryManager.onLeakDetected(callback);
             var ignore2 = installGcEventListener(() -> gcEvents.release());
             BufferAllocator allocator = fixture.createAllocator()) {
            var runnable = new CreateAndUseBuffers(allocator, hint, sendThenClose);
            var thread = new Thread(runnable);
            thread.start();
            gcEvents.acquire(); // Wait for a GC event to happen.
            thread.interrupt();
            thread.join();
            assertThat(counter.get()).as("Unexpected leak in " + testInfo.getDisplayName()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferLeakMustBeDetectedWhenNotClosedAfterSend(Fixture fixture, TestInfo testInfo) throws Exception {
        Object leakingHint = makeHint(testInfo);
        Object nonLeakingHint = new String(leakingHint + " (non-leaking hint)");
        Consumer<Buffer> sendThenLeakBuffer = buffer -> {
            buffer.send().receive().touch(leakingHint); // Buffer is received from send, but then leaks.
        };
        LinkedBlockingQueue<LeakInfo> leakQueue = new LinkedBlockingQueue<>();
        AtomicReference<LeakInfo> nonLeakAsserts = new AtomicReference<>();
        Consumer<LeakInfo> callback = forHint(leakingHint, leak -> leakQueue.offer(leak));
        Consumer<LeakInfo> assertNoNonLeakingHints = forHint(nonLeakingHint, leak -> nonLeakAsserts.set(leak));
        CreateAndUseBuffers runnable;
        Thread thread;
        LeakInfo leakInfo;
        try (var ignore1 = MemoryManager.onLeakDetected(callback);
             var ignore2 = MemoryManager.onLeakDetected(assertNoNonLeakingHints);
             BufferAllocator allocator = fixture.createAllocator()) {
            runnable = new CreateAndUseBuffers(allocator, nonLeakingHint, sendThenLeakBuffer);
            thread = new Thread(runnable);
            thread.start();
            leakInfo = leakQueue.poll(20, TimeUnit.SECONDS);
            thread.interrupt();
            thread.join();
        }
        assertThat(leakInfo)
                .as("No leak detected in 20 seconds for \"" + testInfo.getDisplayName() + "\".")
                .isNotNull();
        if (nonLeakAsserts.get() != null) {
            LeakInfo info = nonLeakAsserts.get();
            AssertionError error = new AssertionError(
                    "Buffers that were sent and properly received should not leak, in " + testInfo.getDisplayName());
            info.forEach(tracePoint -> error.addSuppressed(tracePoint.traceback()));
            throw error;
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferLeakMustBeDetectedWhenSendObjectLeaks(Fixture fixture, TestInfo testInfo) throws Exception {
        Object hint = makeHint(testInfo);
        Consumer<Buffer> sendThenLeakBuffer = buffer -> {
            buffer.send(); // Send object itself leaks.
        };
        LinkedBlockingQueue<LeakInfo> leakQueue = new LinkedBlockingQueue<>();
        Consumer<LeakInfo> callback = forHint(hint, leak -> leakQueue.offer(leak));
        CreateAndUseBuffers runnable;
        Thread thread;
        LeakInfo leakInfo;
        try (var ignore1 = MemoryManager.onLeakDetected(callback);
             BufferAllocator allocator = fixture.createAllocator()) {
            runnable = new CreateAndUseBuffers(allocator, hint, sendThenLeakBuffer);
            thread = new Thread(runnable);
            thread.start();
            leakInfo = leakQueue.poll(20, TimeUnit.SECONDS);
            thread.interrupt();
            thread.join();
        }
        assertThat(leakInfo)
                .as("No leak detected in 20 seconds for \"" + testInfo.getDisplayName() + "\".")
                .isNotNull();
    }

    private static String makeHint(TestInfo testInfo) {
        return new String("for test \"" + testInfo.getDisplayName() + '"');
    }

    private static AutoCloseable installGcEventListener(Runnable callback) {
        CallbackListener listener = null;
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean bean : garbageCollectorMXBeans) {
            if (bean instanceof NotificationBroadcaster) {
                NotificationBroadcaster broadcaster = (NotificationBroadcaster) bean;
                if (listener == null) {
                    listener = new CallbackListener(callback);
                }
                listener.install(broadcaster);
            }
        }
        if (listener != null) {
            return listener;
        }

        // Alternative callback mechanism based on counting the number of GCs that happen.
        CollectionCounter counter = new CollectionCounter(callback, garbageCollectorMXBeans);
        counter.start();
        return counter;
    }

    private static Consumer<LeakInfo> forHint(Object hint, Consumer<LeakInfo> consumer) {
        return leak -> {
            boolean foundIntendedLeak = leak.stream().anyMatch(tracePoint -> tracePoint.hint() == hint);
            if (foundIntendedLeak) {
                consumer.accept(leak);
            } else {
                InternalLogger logger = InternalLoggerFactory.getInstance(BufferLeakDetectionTest.class);
                logger.warn("Found leaked object \"{}\" that did not match hint \"{}\".",
                            leak.objectDescription(), hint);
            }
        };
    }

    private static class CreateAndUseBuffers implements Runnable {
        private static final AtomicLong resultCaptor = new AtomicLong();
        private static final int N_THREADS = 4;
        private final BufferAllocator allocator;
        private final Object hint;
        private final Consumer<Buffer> consumer;
        private final ExecutorService executor;

        CreateAndUseBuffers(BufferAllocator allocator, Object hint, Consumer<Buffer> consumer) {
            requireNonNull(allocator, "allocator");
            requireNonNull(hint, "hint");
            requireNonNull(consumer, "consumer");
            this.allocator = allocator;
            this.hint = hint;
            this.consumer = consumer;
            executor = Executors.newFixedThreadPool(N_THREADS);
        }

        @Override
        public void run() {
            allocateAndProcessBuffer();
            while (!Thread.interrupted()) {
                produceGarbage();
            }
            executor.shutdown();
            try {
                //noinspection ResultOfMethodCallIgnored
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void allocateAndProcessBuffer() {
            Buffer buffer = allocator.allocate(128);
            buffer.touch(hint);
            consumer.accept(buffer);
        }

        private void produceGarbage() {
            Semaphore semaphore = new Semaphore(0);
            AtomicInteger trigger = new AtomicInteger();
            Runnable gcCallback = () -> {
                trigger.incrementAndGet();
                semaphore.release();
            };
            Runnable gcProducer = () -> {
                while (trigger.get() < 1) {
                    resultCaptor.set(System.identityHashCode(new int[1024]));
                }
            };

            try (AutoCloseable ignore = installGcEventListener(gcCallback)) {
                for (int i = 0; i < N_THREADS; i++) {
                    executor.execute(gcProducer);
                }
                semaphore.acquireUninterruptibly();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class CallbackListener implements NotificationListener, AutoCloseable {
        private final Runnable callback;
        private final List<NotificationBroadcaster> installedBroadcasters;

        CallbackListener(Runnable callback) {
            this.callback = callback;
            installedBroadcasters = new ArrayList<>();
        }

        public void install(NotificationBroadcaster broadcaster) {
            broadcaster.addNotificationListener(this, null, null);
            installedBroadcasters.add(broadcaster);
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            callback.run();
        }

        @Override
        public void close() throws Exception {
            for (NotificationBroadcaster broadcaster : installedBroadcasters) {
                broadcaster.removeNotificationListener(this);
            }
        }
    }

    private static class CollectionCounter extends Thread implements AutoCloseable {
        private final Runnable callback;
        private final List<GarbageCollectorMXBean> gcBeans;

        CollectionCounter(Runnable callback,
                          List<GarbageCollectorMXBean> gcBeans) {
            super("Garbage Collection Counter");
            this.callback = callback;
            this.gcBeans = gcBeans;
        }

        @Override
        public void run() {
            long prevSum = sum();
            boolean interrupted = false;
            do {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
                long newSum = sum();
                if (newSum > prevSum) {
                    callback.run();
                    prevSum = newSum;
                }
            } while (!interrupted);
        }

        private long sum() {
            long sum = 0;
            for (GarbageCollectorMXBean bean : gcBeans) {
                long count = bean.getCollectionCount();
                if (count > 0) { // The 'count' is allowed to be -1.
                    sum += count;
                }
            }
            return sum;
        }

        @Override
        public void close() throws Exception {
            interrupt();
            join(10_000);
        }
    }
}
