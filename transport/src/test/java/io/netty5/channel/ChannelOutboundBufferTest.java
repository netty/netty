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

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.concurrent.SingleThreadEventExecutor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static io.netty5.channel.ChannelOutboundBuffer.CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD;
import static org.assertj.core.api.Assertions.assertThat;
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
            try (Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII)) {
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
            try (Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII)) {
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
            try (Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII)) {
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

    @Test
    void consumingAllFlushedMustNotLeaveAnyFlushedMessagesBehind() throws Exception {
        AtomicReference<Future<Void>> ref = new AtomicReference<>();
        testChannelOutboundBuffer((buffer, executor) -> {
            Promise<Void> p1 = executor.newPromise();
            Promise<Void> p2 = executor.newPromise();
            assertThat(buffer.totalPendingWriteBytes()).isZero();
            buffer.addMessage(1, 5, p1);
            buffer.addMessage(2, 11, p2);
            buffer.addFlush();
            List<Map.Entry<Integer, Promise<Void>>> list = new ArrayList<>();
            buffer.consumeEachFlushedMessage((m, p) -> {
                assertFalse(p.isCancellable());
                list.add(Map.entry((Integer) m, p));
                return true;
            });
            long overhead = 2L * CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD; // We added 2 entries.
            assertThat(buffer.totalPendingWriteBytes()).isEqualTo(16 + overhead);
            assertThat(list).containsExactly(
                    Map.entry(1, p1),
                    Map.entry(2, p2));
            assertThat(buffer.size()).isZero();
            assertTrue(buffer.isEmpty());
            for (Map.Entry<Integer, Promise<Void>> entry : list) {
                entry.getValue().setSuccess(null);
            }
            ref.set(executor.submit(() -> {
                // Delay this check until after the future listeners get to run.
                // We rely on the executor being single-threaded and running its tasks in-order.
                assertThat(buffer.totalPendingWriteBytes()).isZero();
            }));
        });
        ref.get().asStage().sync();
    }

    @Test
    void notConsumingAnyFlushedMustLeaveAllMessageIntact() throws Exception {
        testChannelOutboundBuffer((buffer, executor) -> {
            Promise<Void> p1 = executor.newPromise();
            Promise<Void> p2 = executor.newPromise();
            assertThat(buffer.totalPendingWriteBytes()).isZero();
            buffer.addMessage(1, 5, p1);
            buffer.addMessage(2, 11, p2);
            buffer.addFlush();
            buffer.consumeEachFlushedMessage((m, p) -> false); // Don't consume anything.
            long overhead = 2L * CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD; // We added 2 entries.
            assertThat(buffer.totalPendingWriteBytes()).isEqualTo(16 + overhead);
            assertThat(buffer.size()).isEqualTo(2);
            assertFalse(buffer.isEmpty());
        });
    }

    private static void release(ChannelOutboundBuffer buffer) {
        while (!buffer.isEmpty()) {
            assertTrue(buffer.remove());
        }
        assertFalse(buffer.remove());
    }
}
