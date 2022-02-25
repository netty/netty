/*
 * Copyright 2017 The Netty Project
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
package io.netty5.channel;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.RecvBufferAllocator.Handle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdaptiveRecvBufferAllocatorTest {
    @Mock
    private ChannelConfig config;
    private final ByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
    private Handle handle;

    @BeforeEach
    public void setup() {
        config = mock(ChannelConfig.class);
        when(config.isAutoRead()).thenReturn(true);
        AdaptiveRecvBufferAllocator recvBufferAllocator = new AdaptiveRecvBufferAllocator(64, 512, 1024 * 1024 * 10);
        handle = recvBufferAllocator.newHandle();
        handle.reset(config);
    }

    @Test
    public void rampUpBeforeReadCompleteWhenLargeDataPending() {
        // Simulate that there is always more data when we attempt to read, so we should always ramp up.
        allocReadExpected(handle, alloc, 512);
        allocReadExpected(handle, alloc, 8192);
        allocReadExpected(handle, alloc, 131072);
        allocReadExpected(handle, alloc, 2097152);
        handle.readComplete();

        handle.reset(config);
        allocReadExpected(handle, alloc, 8388608);
    }

    @Test
    public void rampUpBeforeReadCompleteWhenLargeDataPendingBuffer() {
        // Simulate that there is always more data when we attempt to read, so we should always ramp up.
        try (BufferAllocator alloc = BufferAllocator.onHeapUnpooled()) {
            allocReadExpected(handle, alloc, 512);
            allocReadExpected(handle, alloc, 8192);
            allocReadExpected(handle, alloc, 131072);
            allocReadExpected(handle, alloc, 2097152);
            handle.readComplete();

            handle.reset(config);
            allocReadExpected(handle, alloc, 8388608);
        }
    }

    @Test
    public void memoryAllocationIntervalsTest() {
        computingNext(512, 512);
        computingNext(8192, 1110);
        computingNext(8192, 1200);
        computingNext(4096, 1300);
        computingNext(4096, 1500);
        computingNext(2048, 1700);
        computingNext(2048, 1550);
        computingNext(2048, 2000);
        computingNext(2048, 1900);
    }

    private void computingNext(long expectedSize, int actualReadBytes) {
        assertEquals(expectedSize, handle.guess());
        handle.reset(config);
        handle.lastBytesRead(actualReadBytes);
        handle.readComplete();
    }

    @Test
    public void lastPartialReadDoesNotRampDown() {
        allocReadExpected(handle, alloc, 512);
        // Simulate there is just 1 byte remaining which is unread. However, the total bytes in the current read cycle
        // means that we should stay at the current step for the next ready cycle.
        allocRead(handle, alloc, 8192, 1);
        handle.readComplete();

        handle.reset(config);
        allocReadExpected(handle, alloc, 8192);
    }

    @Test
    public void lastPartialReadDoesNotRampDownBuffer() {
        try (BufferAllocator alloc = BufferAllocator.onHeapUnpooled()) {
            allocReadExpected(handle, alloc, 512);
            // Simulate there is just 1 byte remaining which is unread. However, the total bytes in the current read
            // cycle means that we should stay at the current step for the next ready cycle.
            allocRead(handle, alloc, 8192, 1);
            handle.readComplete();

            handle.reset(config);
            allocReadExpected(handle, alloc, 8192);
        }
    }

    @Test
    public void lastPartialReadCanRampUp() {
        allocReadExpected(handle, alloc, 512);
        // We simulate there is just 1 less byte than we try to read, but because of the adaptive steps the total amount
        // of bytes read for this read cycle steps up to prepare for the next read cycle.
        allocRead(handle, alloc, 8192, 8191);
        handle.readComplete();

        handle.reset(config);
        allocReadExpected(handle, alloc, 131072);
    }

    @Test
    public void lastPartialReadCanRampUpBuffer() {
        try (BufferAllocator alloc = BufferAllocator.onHeapUnpooled()) {
            allocReadExpected(handle, alloc, 512);
            // We simulate there is just 1 less byte than we try to read, but because of the adaptive steps the total
            // amount of bytes read for this read cycle steps up to prepare for the next read cycle.
            allocRead(handle, alloc, 8192, 8191);
            handle.readComplete();

            handle.reset(config);
            allocReadExpected(handle, alloc, 131072);
        }
    }

    private static void allocReadExpected(Handle handle, ByteBufAllocator alloc, int expectedSize) {
        allocRead(handle, alloc, expectedSize, expectedSize);
    }

    private static void allocRead(Handle handle, ByteBufAllocator alloc, int expectedBufferSize, int lastRead) {
        ByteBuf buf = handle.allocate(alloc);
        assertEquals(expectedBufferSize, buf.capacity());
        handle.attemptedBytesRead(expectedBufferSize);
        handle.lastBytesRead(lastRead);
        handle.incMessagesRead(1);
        buf.release();
    }

    private static void allocReadExpected(Handle handle, BufferAllocator alloc, int expectedSize) {
        allocRead(handle, alloc, expectedSize, expectedSize);
    }

    private static void allocRead(Handle handle, BufferAllocator alloc, int expectedBufferSize, int lastRead) {
        try (Buffer buf = handle.allocate(alloc)) {
            assertEquals(expectedBufferSize, buf.capacity());
            handle.attemptedBytesRead(expectedBufferSize);
            handle.lastBytesRead(lastRead);
            handle.incMessagesRead(1);
        }
    }
}
