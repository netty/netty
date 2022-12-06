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
package io.netty5.buffer.tests;

import io.netty5.buffer.AllocationType;
import io.netty5.buffer.AllocatorControl;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.Drop;
import io.netty5.buffer.MemoryManager;
import io.netty5.buffer.SensitiveBufferAllocator;
import io.netty5.buffer.internal.LeakDetection;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ServiceConfigurationError;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 10, unit = TimeUnit.MINUTES)
@Isolated
public class CleanerDropTest {
    static Stream<MemoryManager> managers() {
        return MemoryManager.availableManagers().flatMap(provider -> {
            try {
                return Stream.of(provider.get());
            } catch (ServiceConfigurationError | Exception ignore) {
                // Provider.get() may throw for unavailable implementations.
                return Stream.empty();
            }
        });
    }

    static Stream<Supplier<BufferAllocator>> allocators() {
        return Stream.of(
                supplier("onHeapUnpooled", BufferAllocator::onHeapUnpooled),
                supplier("offHeapUnpooled", BufferAllocator::offHeapUnpooled),
                supplier("onHeapPooled", BufferAllocator::onHeapPooled),
                supplier("offHeapPooled", BufferAllocator::offHeapPooled),
                supplier("sensitive", SensitiveBufferAllocator::sensitiveOffHeapAllocator));
    }

    static <T> Supplier<T> supplier(String name, Supplier<T> supplier) {
        return new Supplier<T>() {
            @Override
            public T get() {
                return supplier.get();
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    static Stream<Arguments> parameters() {
        return managers().flatMap(manager -> allocators().map(allocator -> Arguments.of(manager, allocator)));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void leakedBufferMustBeDroppedByCleanerWhenLeakDetectionIsEnabled(
            MemoryManager manager, Supplier<BufferAllocator> allocatorSupplier) throws Exception {
        Semaphore leakDetectorSemaphore = new Semaphore(0);
        try (var ignore = LeakDetection.onLeakDetected(ignore1 -> leakDetectorSemaphore.release())) {
            Semaphore leakSemaphore = new Semaphore(0);
            MemoryManager.using(new LeakTrappingMemoryManager(leakSemaphore, manager), () -> {
                int counter = 0;
                try (BufferAllocator allocator = allocatorSupplier.get()) {
                    leakBuffer(allocator);
                    do {
                        produceGarbage();
                        counter++;
                        assertThat(counter).isLessThan(5000);
                    } while (!tryAcquireForOneMillisecond(leakSemaphore));
                    return null;
                }
            });
            // Make sure we capture all the buffers we create in this test.
            assertTrue(leakDetectorSemaphore.tryAcquire(1, 5, TimeUnit.MINUTES),
                    "Not all leaked objects were captured by the leak detector.");
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void constBufferSuppliersAreClosedByCleaner(
            MemoryManager manager, Supplier<BufferAllocator> allocatorSupplier) throws Exception {
        Semaphore leakSemaphore = new Semaphore(0);
        MemoryManager.using(new LeakTrappingMemoryManager(leakSemaphore, manager), () -> {
            int counter = 0;
            try (BufferAllocator allocator = allocatorSupplier.get()) {
                leakConstBufferSupplier(allocator);
                do {
                    produceGarbage();
                    counter++;
                    assertThat(counter).isLessThan(5000);
                } while (!tryAcquireForOneMillisecond(leakSemaphore));
                return null;
            }
        });
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void constBufferSuppliersAreClosedByCleanerButDoNotLeak(
            MemoryManager manager, Supplier<BufferAllocator> allocatorSupplier) throws Exception {
        AtomicInteger leakCounter = new AtomicInteger();
        try (var ignore = LeakDetection.onLeakDetected(ignore1 -> leakCounter.incrementAndGet())) {
            Semaphore leakSemaphore = new Semaphore(0);
            MemoryManager.using(new LeakTrappingMemoryManager(leakSemaphore, manager), () -> {
                int counter = 0;
                try (BufferAllocator allocator = allocatorSupplier.get()) {
                    leakConstBufferSupplier(allocator);
                    do {
                        produceGarbage();
                        counter++;
                        assertThat(counter).isLessThan(5000);
                    } while (!tryAcquireForOneMillisecond(leakSemaphore));
                    return null;
                }
            });
            assertThat(leakCounter).hasValue(0);
        }
    }

    private static void leakBuffer(BufferAllocator allocator) {
        allocator.allocate(128);
    }

    private static void leakConstBufferSupplier(BufferAllocator allocator) {
        // The allocation of the supplier itself will likely require an internal buffer to be allocated,
        // to capture a snapshot of the input array, and for use in structural sharing.
        // This buffer cannot be closed, so we cannot reasonably consider it to be leaked.
        // Buffers allocated from the supplier, however, could leak. Hence, we only allocate the supplier.
        allocator.constBufferSupplier(new byte[128]);
    }

    private static boolean tryAcquireForOneMillisecond(Semaphore leakSemaphore) {
        try {
            return leakSemaphore.tryAcquire(1, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignore) {
            return false;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void produceGarbage() {
        ThreadLocalRandom.current().ints(256).mapToObj(String::valueOf).collect(Collectors.toList());
        System.gc();
    }

    private static class LeakTrappingMemoryManager implements MemoryManager {
        private final Semaphore leakSemaphore;
        private final MemoryManager manager;

        LeakTrappingMemoryManager(Semaphore leakSemaphore, MemoryManager manager) {
            this.leakSemaphore = leakSemaphore;
            this.manager = manager;
        }

        @Override
        public Buffer allocateShared(AllocatorControl allocatorControl, long size,
                                     Function<Drop<Buffer>, Drop<Buffer>> dropDecorator,
                                     AllocationType allocationType) {
            return manager.allocateShared(allocatorControl, size, dropDecorator, allocationType);
        }

        @Override
        public Buffer allocateConstChild(Buffer readOnlyConstParent) {
            return manager.allocateConstChild(readOnlyConstParent);
        }

        @Override
        public Object unwrapRecoverableMemory(Buffer buf) {
            return manager.unwrapRecoverableMemory(buf);
        }

        @Override
        public Buffer recoverMemory(AllocatorControl allocatorControl,
                                    Object recoverableMemory,
                                    Drop<Buffer> drop) {
            leakSemaphore.release(); // Signal that we caught a leak!
            return manager.recoverMemory(allocatorControl, recoverableMemory, drop);
        }

        @Override
        public Object sliceMemory(Object memory, int offset, int length) {
            return manager.sliceMemory(memory, offset, length);
        }

        @Override
        public void clearMemory(Object memory) {
            manager.clearMemory(memory);
        }

        @Override
        public int sizeOf(Object memory) {
            return manager.sizeOf(memory);
        }

        @Override
        public String implementationName() {
            return "Leak Tracking " + manager.implementationName();
        }
    }
}
