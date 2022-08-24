/*
 * Copyright 2012 The Netty Project
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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.concurrent.SingleThreadEventExecutor;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChannelOutboundBufferTest {

    private static void testChannelOutboundBuffer(BiConsumer<ChannelOutboundBuffer, EventExecutor> testConsumer)
            throws InterruptedException {
        EventExecutor executor = new SingleThreadEventExecutor();
        try {
            ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(executor);
            executor.submit(() -> {
                    try {
                        testConsumer.accept(buffer, executor);
                    } finally {
                        release(buffer);
                    }
                }).asStage().sync();
        } finally {
            executor.shutdownGracefully();
        }
    }

    @Test
    public void cancelFirst() throws InterruptedException {
        testChannelOutboundBuffer((buffer, executor) -> {
            try (Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1", CharsetUtil.US_ASCII)) {
                int size = buf.readableBytes();
                Promise<Void> promise = executor.newPromise();
                buffer.addMessage(buf.copy(), size, promise);
                buffer.addMessage(buf.copy(), size, executor.newPromise());

                assertTrue(promise.cancel());
                buffer.addFlush();
                // Should have 1 entries.
                assertNotNull(buffer.current());
                assertTrue(buffer.remove());

                assertNull(buffer.current());
                assertTrue(buffer.isEmpty());
                assertFalse(buffer.remove());
            }
        });
    }

    @Test
    public void cancelLast() throws InterruptedException {
        testChannelOutboundBuffer((buffer, executor) -> {
            try (Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1", CharsetUtil.US_ASCII)) {
                int size = buf.readableBytes();
                Promise<Void> promise = executor.newPromise();
                buffer.addMessage(buf.copy(), size, executor.newPromise());
                buffer.addMessage(buf.copy(), size, promise);

                assertTrue(promise.cancel());
                buffer.addFlush();
                // Should have 1 entries.
                assertNotNull(buffer.current());
                assertTrue(buffer.remove());

                assertNull(buffer.current());
                assertTrue(buffer.isEmpty());
                assertFalse(buffer.remove());
            }
        });
    }

    @Test
    public void cancelInBetween() throws InterruptedException {
        testChannelOutboundBuffer((buffer, executor) -> {
            try (Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1", CharsetUtil.US_ASCII)) {
                int size = buf.readableBytes();
                buffer.addMessage(buf.copy(), size, executor.newPromise());
                Promise<Void> promise = executor.newPromise();
                buffer.addMessage(buf.copy(), size, promise);
                buffer.addMessage(buf.copy(), size, executor.newPromise());
                assertTrue(promise.cancel());
                buffer.addFlush();

                // Should have two entries.
                assertNotNull(buffer.current());
                assertTrue(buffer.remove());
                assertNotNull(buffer.current());
                assertTrue(buffer.remove());

                assertNull(buffer.current());
                assertTrue(buffer.isEmpty());
                assertFalse(buffer.remove());
            }
        });
    }

    private static void release(ChannelOutboundBuffer buffer) {
        while (!buffer.isEmpty()) {
            assertTrue(buffer.remove());
        }
        assertFalse(buffer.remove());
    }
}
