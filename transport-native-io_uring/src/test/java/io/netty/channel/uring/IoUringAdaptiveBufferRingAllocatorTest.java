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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IoUringAdaptiveBufferRingAllocatorTest {

    @Test
    public void rampUpWhenLargeDataPending() {
        IoUringAdaptiveBufferRingAllocator allocator = new IoUringAdaptiveBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT, 64, 512, 1024 * 1024 * 10);
        // Simulate that there is always more data when we attempt to read so we should always ramp up.
        allocReadExpected(allocator, 512);
        allocReadExpected(allocator, 8192);
        allocReadExpected(allocator, 131072);
        allocReadExpected(allocator, 2097152);
    }

    @Test
    public void lastPartialReadDoesNotRampDown() {
        IoUringAdaptiveBufferRingAllocator allocator = new IoUringAdaptiveBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT, 64, 512, 1024 * 1024 * 10);
        allocReadExpected(allocator, 512);
        // Simulate there is just 1 byte remaining which is unread. However the total bytes in the current read cycle
        // means that we should stay at the current step for the next ready cycle.
        allocRead(allocator, 8192, 1);
    }

    @Test
    public void lastPartialReadCanRampUp() {
        IoUringAdaptiveBufferRingAllocator allocator = new IoUringAdaptiveBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT, 64, 512, 1024 * 1024 * 10);
        allocReadExpected(allocator, 512);
        // We simulate there is just 1 less byte than we try to read, but because of the adaptive steps the total amount
        // of bytes read for this read cycle steps up to prepare for the next read cycle.
        allocRead(allocator, 8192, 8191);
    }

    @Test
    public void doesNotExceedMaximum() {
        IoUringAdaptiveBufferRingAllocator allocator = new IoUringAdaptiveBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT, 64, 9000, 9000);
        allocReadExpected(allocator, 8192);
    }

    @Test
    public void doesSetCorrectMinBounds() {
        IoUringAdaptiveBufferRingAllocator allocator = new IoUringAdaptiveBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT, 81, 95, 95);
        allocReadExpected(allocator, 81);
    }

    @Test
    public void throwsIfInitialIsBiggerThenMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> new IoUringAdaptiveBufferRingAllocator(UnpooledByteBufAllocator.DEFAULT, 64, 4096 , 1024));
    }

    @Test
    public void throwsIfInitialIsSmallerThenMinimum() {
        assertThrows(IllegalArgumentException.class,
                () -> new IoUringAdaptiveBufferRingAllocator(UnpooledByteBufAllocator.DEFAULT, 512, 64 , 1024));
    }

    @Test
    public void throwsIfMinimumIsBiggerThenMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> new IoUringAdaptiveBufferRingAllocator(UnpooledByteBufAllocator.DEFAULT, 2048, 64 , 1024));
    }

    private static void allocReadExpected(IoUringAdaptiveBufferRingAllocator allocator,
                                          int expectedSize) {
        allocRead(allocator, expectedSize, expectedSize);
    }

    private static void allocRead(IoUringAdaptiveBufferRingAllocator allocator,
                                  int expectedBufferSize,
                                  int lastRead) {
        ByteBuf buf = allocator.allocate();
        assertEquals(expectedBufferSize, buf.capacity());
        allocator.lastBytesRead(expectedBufferSize, lastRead);
        buf.release();
    }
}
