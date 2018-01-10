/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdaptiveRecvByteBufAllocatorTest {
    @Mock
    private ChannelConfig config;
    private ByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
    private RecvByteBufAllocator.ExtendedHandle handle;

    @Before
    public void setup() {
        config = mock(ChannelConfig.class);
        when(config.isAutoRead()).thenReturn(true);
        AdaptiveRecvByteBufAllocator recvByteBufAllocator = new AdaptiveRecvByteBufAllocator(64, 512, 1024 * 1024 * 10);
        handle = (RecvByteBufAllocator.ExtendedHandle) recvByteBufAllocator.newHandle();
        handle.reset(config);
    }

    @Test
    public void rampUpBeforeReadCompleteWhenLargeDataPending() {
        // Simulate that there is always more data when we attempt to read so we should always ramp up.
        allocReadExpected(handle, alloc, 512);
        allocReadExpected(handle, alloc, 8192);
        allocReadExpected(handle, alloc, 131072);
        allocReadExpected(handle, alloc, 2097152);
        handle.readComplete();

        handle.reset(config);
        allocReadExpected(handle, alloc, 8388608);
    }

    @Test
    public void lastPartialReadDoesNotRampDown() {
        allocReadExpected(handle, alloc, 512);
        // Simulate there is just 1 byte remaining which is unread. However the total bytes in the current read cycle
        // means that we should stay at the current step for the next ready cycle.
        allocRead(handle, alloc, 8192, 1);
        handle.readComplete();

        handle.reset(config);
        allocReadExpected(handle, alloc, 8192);
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

    private static void allocReadExpected(RecvByteBufAllocator.ExtendedHandle handle,
                                          ByteBufAllocator alloc,
                                          int expectedSize) {
        allocRead(handle, alloc, expectedSize, expectedSize);
    }

    private static void allocRead(RecvByteBufAllocator.ExtendedHandle handle,
                                  ByteBufAllocator alloc,
                                  int expectedBufferSize,
                                  int lastRead) {
        ByteBuf buf = handle.allocate(alloc);
        assertEquals(expectedBufferSize, buf.capacity());
        handle.attemptedBytesRead(expectedBufferSize);
        handle.lastBytesRead(lastRead);
        handle.incMessagesRead(1);
        buf.release();
    }
}
