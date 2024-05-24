/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SslHandlerCoalescingBufferQueueTest {

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testCumulation(boolean readOnlyAndDuplicate) {
        EmbeddedChannel channel = new EmbeddedChannel();
        SslHandlerCoalescingBufferQueue queue = new SslHandlerCoalescingBufferQueue(channel, 16, false, false) {
            @Override
            protected int wrapDataSize() {
                return 128;
            }
        };

        ByteBuffer nioBuffer = ByteBuffer.allocateDirect(128);
        nioBuffer.putLong(0);
        nioBuffer.putLong(0);
        nioBuffer.putLong(0);
        nioBuffer.flip();
        ByteBuf first;
        if (readOnlyAndDuplicate) {
            first = Unpooled.wrappedBuffer(nioBuffer.asReadOnlyBuffer()).duplicate();
            first.writerIndex(8);
        } else {
            first = Unpooled.wrappedBuffer(nioBuffer);
            first.writerIndex(8);
        }
        queue.add(first);
        ByteBuf second = Unpooled.copyLong(1);
        queue.add(second);

        ChannelPromise promise = channel.newPromise();
        assertFalse(queue.isEmpty());
        ByteBuf buffer = queue.remove(UnpooledByteBufAllocator.DEFAULT, 128, promise);
        try {
            assertEquals(16, buffer.readableBytes());
            assertEquals(0, buffer.readLong());
            assertEquals(1, buffer.readLong());
        } finally {
            buffer.release();
        }
        assertTrue(queue.isEmpty());
        assertEquals(0, first.refCnt());
        assertEquals(0, second.refCnt());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testSmallerBufferThanRequestedBytesGetsCopiedWhenWantsMutableBuffer(boolean wantsMutableBuffer) {
        EmbeddedChannel channel = new EmbeddedChannel();
        SslHandlerCoalescingBufferQueue queue =
                new SslHandlerCoalescingBufferQueue(channel, 16, false, wantsMutableBuffer) {
                    @Override
                    protected int wrapDataSize() {
                        return 128;
                    }
                };

        ByteBuf buf = Unpooled.buffer(64);
        buf.writeZero(64);
        buf = buf.asReadOnly();
        queue.add(buf);

        ChannelPromise promise = channel.newPromise();
        ByteBuf buffer = queue.remove(UnpooledByteBufAllocator.DEFAULT, 128, promise);
        try {
            assertEquals(64, buffer.readableBytes());
            // Ensure that the buffer is a copy if we want a mutable buffer.
            assertEquals(wantsMutableBuffer, buf != buffer);
            // Ensure that the buffer is not read-only if we want a mutable buffer.
            assertEquals(wantsMutableBuffer, !buffer.isReadOnly());
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testLargerBufferThanRequestedBytesGetsCopiedWhenWantsMutableBuffer(boolean wantsMutableBuffer) {
        EmbeddedChannel channel = new EmbeddedChannel();
        SslHandlerCoalescingBufferQueue queue =
                new SslHandlerCoalescingBufferQueue(channel, 16, false, wantsMutableBuffer) {
                    @Override
                    protected int wrapDataSize() {
                        return 128;
                    }
                };

        ByteBuf buf = Unpooled.buffer(256);
        buf.writeZero(256);
        buf = buf.asReadOnly();
        queue.add(buf);

        ChannelPromise promise = channel.newPromise();
        for (;;) {
            ByteBuf buffer = queue.remove(UnpooledByteBufAllocator.DEFAULT, 128, promise);
            if (buffer == null) {
                break;
            }
            try {
                assertEquals(128, buffer.readableBytes());
                // Ensure that the buffer is not read-only if we want a mutable buffer.
                assertEquals(wantsMutableBuffer, !buffer.isReadOnly());
            } finally {
                buffer.release();
            }
        }
        assertTrue(queue.isEmpty());
    }
}
