/*
 * Copyright 2022 The Netty Project
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

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.pool.BufferAllocatorMetric;
import io.netty5.buffer.pool.PooledBufferAllocator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class BufferAllocatorMetricTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("pooledAllocators")
    void testUsedMemory(Fixture fixture) {
        for (int power = 0; power < 8; power++) {
            int initialCapacity = 1024 << power;
            testUsedMemory(fixture, initialCapacity);
        }
    }

    private static void testUsedMemory(Fixture fixture, int initialCapacity) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            if (allocator instanceof PooledBufferAllocator) {
                PooledBufferAllocator pooledBufferAllocator = (PooledBufferAllocator) allocator;
                BufferAllocatorMetric metric = pooledBufferAllocator.metric();
                assertEquals(0, metric.usedMemory());
                assertEquals(0, metric.pinnedMemory());
                try (Buffer buffer = allocator.allocate(initialCapacity)) {
                    int capacity = buffer.capacity();
                    assertThat(metric.usedMemory()).isEqualTo(metric.chunkSize());
                    assertThat(metric.pinnedMemory())
                            .isGreaterThanOrEqualTo(capacity)
                            .isLessThanOrEqualTo(metric.usedMemory());

                    buffer.ensureWritable(capacity << 1);
                    capacity = buffer.capacity();
                    assertThat(metric.usedMemory()).isEqualTo(metric.chunkSize());
                    assertThat(metric.pinnedMemory())
                            .isGreaterThanOrEqualTo(capacity)
                            .isLessThanOrEqualTo(metric.usedMemory());
                }

                assertThat(metric.usedMemory()).isEqualTo(metric.chunkSize());
                assertThat(metric.pinnedMemory())
                        .isGreaterThanOrEqualTo(0)
                        .isLessThanOrEqualTo(metric.usedMemory());
                pooledBufferAllocator.trimCurrentThreadCache();
                assertEquals(0, metric.pinnedMemory());

                int[] capacities = new int[30];
                Random rng = new Random();
                for (int i = 0; i < capacities.length; i++) {
                    capacities[i] = initialCapacity / 4 + rng.nextInt(8 * initialCapacity);
                }
                Buffer[] buffers = new Buffer[capacities.length];
                for (int i = 0; i < 20; i++) {
                    buffers[i] = allocator.allocate(capacities[i]);
                }
                for (int i = 0; i < 10; i++) {
                    buffers[i].close();
                }
                for (int i = 20; i < 30; i++) {
                    buffers[i] = allocator.allocate(capacities[i]);
                }
                for (int i = 0; i < 10; i++) {
                    buffers[i] = allocator.allocate(capacities[i]);
                }
                for (int i = 0; i < 30; i++) {
                    buffers[i].close();
                }
                pooledBufferAllocator.trimCurrentThreadCache();
                assertEquals(0, metric.pinnedMemory());
            }
        }
    }
}
