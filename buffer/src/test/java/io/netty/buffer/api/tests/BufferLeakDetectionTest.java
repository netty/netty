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
package io.netty.buffer.api.tests;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.LeakInfo;
import io.netty.buffer.api.MemoryManager;
import io.netty.buffer.api.Send;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationListener;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class BufferLeakDetectionTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferMustNotLeakWhenClosedProperly(Fixture fixture) throws Exception {
        Object hint = new Object();
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
            assertThat(counter.get()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferLeakMustBeDetectedWhenNotClosedProperty(Fixture fixture) throws Exception {
        Object hint = new Object();
        Consumer<Buffer> leakBuffer = buffer -> { };
        LinkedBlockingQueue<LeakInfo> leakQueue = new LinkedBlockingQueue<>();
        Consumer<LeakInfo> callback = forHint(hint, leak -> leakQueue.offer(leak));
        CreateAndUseBuffers runnable;
        Thread thread;
        LeakInfo leakInfo;
        try (var ignore1 = MemoryManager.onLeakDetected(callback);
             var ignore2 = installGcEventListener(() -> { });
             BufferAllocator allocator = fixture.createAllocator()) {
            runnable = new CreateAndUseBuffers(allocator, hint, leakBuffer);
            thread = new Thread(runnable);
            thread.start();
            leakInfo = leakQueue.poll(10, TimeUnit.SECONDS);
            thread.interrupt();
        }
        thread.join();
        assertThat(leakInfo).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferMustNotLeakWhenClosedAfterSend(Fixture fixture) throws Exception {
        Object hint = new Object();
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
            assertThat(counter.get()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferLeakMustBeDetectedWhenNotClosedAfterSend(Fixture fixture) throws Exception {
        Object leakingHint = new Object();
        Object nonLeakingHint = new Object();
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
             var ignore2 = installGcEventListener(() -> { });
             var ignore3 = MemoryManager.onLeakDetected(assertNoNonLeakingHints);
             BufferAllocator allocator = fixture.createAllocator()) {
            runnable = new CreateAndUseBuffers(allocator, nonLeakingHint, sendThenLeakBuffer);
            thread = new Thread(runnable);
            thread.start();
            leakInfo = leakQueue.poll(10, TimeUnit.SECONDS);
            thread.interrupt();
        }
        thread.join();
        assertThat(leakInfo).isNotNull();
        if (nonLeakAsserts.get() != null) {
            LeakInfo info = nonLeakAsserts.get();
            AssertionError error = new AssertionError("Buffers that were sent and properly received should not leak");
            info.forEach(tracePoint -> error.addSuppressed(tracePoint.traceback()));
            throw error;
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bufferLeakMustBeDetectedWhenSendObjectLeaks(Fixture fixture) throws Exception {
        Object hint = new Object();
        Consumer<Buffer> sendThenLeakBuffer = buffer -> {
            buffer.send(); // Send object itself leaks.
        };
        LinkedBlockingQueue<LeakInfo> leakQueue = new LinkedBlockingQueue<>();
        Consumer<LeakInfo> callback = forHint(hint, leak -> leakQueue.offer(leak));
        CreateAndUseBuffers runnable;
        Thread thread;
        LeakInfo leakInfo;
        try (var ignore1 = MemoryManager.onLeakDetected(callback);
             var ignore2 = installGcEventListener(() -> { });
             BufferAllocator allocator = fixture.createAllocator()) {
            runnable = new CreateAndUseBuffers(allocator, hint, sendThenLeakBuffer);
            thread = new Thread(runnable);
            thread.start();
            leakInfo = leakQueue.poll(10, TimeUnit.SECONDS);
            thread.interrupt();
        }
        thread.join();
        assertThat(leakInfo).isNotNull();
    }

    private static AutoCloseable installGcEventListener(Runnable callback) {
        CallbackListener listener = new CallbackListener(callback);
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean bean : garbageCollectorMXBeans) {
            if (bean instanceof NotificationBroadcaster) {
                NotificationBroadcaster broadcaster = (NotificationBroadcaster) bean;
                listener.install(broadcaster);
            }
        }
        return listener;
    }

    private static Consumer<LeakInfo> forHint(Object hint, Consumer<LeakInfo> consumer) {
        return leak -> {
            if (leak.stream().anyMatch(tracePoint -> tracePoint.hint() == hint)) {
                consumer.accept(leak);
            }
        };
    }

    private static class CreateAndUseBuffers implements Runnable {
        private final BufferAllocator allocator;
        private final Object hint;
        private final Consumer<Buffer> consumer;

        CreateAndUseBuffers(BufferAllocator allocator, Object hint, Consumer<Buffer> consumer) {
            this.allocator = allocator;
            this.hint = hint;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                Buffer buffer = allocator.allocate(128);
                if (hint != null) {
                    buffer.touch(hint);
                }
                consumer.accept(buffer);
                produceGarbage();
            }
        }

        private static void produceGarbage() {
            AtomicBoolean trigger = new AtomicBoolean();
            try (AutoCloseable ignore = installGcEventListener(() -> trigger.set(true))) {
                while (!trigger.get()) {
                    //noinspection ResultOfMethodCallIgnored
                    Arrays.stream(new int[1024]).mapToObj(String::valueOf).count();
                }
            } catch (Exception ignore) {
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
}
