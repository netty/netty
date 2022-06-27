/*
 * Copyright 2021 The Netty Project

 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:

 * https://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.util.Resource;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractCoalescingBufferQueueTest {

    // See https://github.com/netty/netty/issues/10286
    @Test
    public void testDecrementAllWhenWriteAndRemoveAll() {
        testDecrementAll(true);
    }

    // See https://github.com/netty/netty/issues/10286
    @Test
    public void testDecrementAllWhenReleaseAndFailAll() {
        testDecrementAll(false);
    }

    private static void testDecrementAll(boolean write) {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                Resource.dispose(msg);
                return ctx.newSucceededFuture();
            }
        }, new ChannelHandlerAdapter() { });
        final AbstractCoalescingBufferQueue queue = new AbstractCoalescingBufferQueue(128) {
            @Override
            protected Buffer compose(BufferAllocator alloc, Buffer cumulation, Buffer next) {
                return composeIntoComposite(alloc, cumulation, next);
            }

            @Override
            protected Buffer removeEmptyValue() {
                return BufferAllocator.offHeapUnpooled().allocate(0);
            }
        };

        final byte[] bytes = new byte[128];
        queue.add(BufferAllocator.offHeapUnpooled().copyOf(bytes), future -> {
            queue.add(BufferAllocator.offHeapUnpooled().copyOf(bytes));
            assertEquals(bytes.length, queue.readableBytes());
        });

        assertEquals(bytes.length, queue.readableBytes());

        ChannelHandlerContext ctx = channel.pipeline().lastContext();
        if (write) {
            queue.writeAndRemoveAll(ctx);
        } else {
            queue.releaseAndFailAll(ctx, new ClosedChannelException());
        }
        Buffer buffer = queue.remove(channel.bufferAllocator(), 128, channel.newPromise());
        assertFalse(buffer.readableBytes() > 0);
        buffer.close();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.readableBytes());

        assertFalse(channel.finish());
    }
}
