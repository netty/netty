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

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.util.IllegalReferenceCountException;
import io.netty5.util.Resource;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.concurrent.Future;

import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.channels.ClosedChannelException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    public void testKeepStateConsistentOnError() {
        final IllegalReferenceCountException exception = new IllegalReferenceCountException();
        final EmbeddedChannel channel = new EmbeddedChannel();
        final AbstractCoalescingBufferQueue queue = new AbstractCoalescingBufferQueue(128) {
            @Override
            protected Buffer compose(BufferAllocator alloc, Buffer cumulation, Buffer next) {
                // Simulate throwing an IllegalReferenceCountException.
                throw exception;
            }

            @Override
            protected Buffer composeFirst(BufferAllocator allocator, Buffer first) {
                // Simulate throwing an IllegalReferenceCountException.
                throw exception;
            }

            @Override
            protected Buffer removeEmptyValue() {
                return channel.bufferAllocator().allocate(0);
            }
        };

        Promise<Void> promise = channel.newPromise();
        Buffer buffer = channel.bufferAllocator().allocate(8).writeLong(0);
        queue.add(buffer, promise);

        Promise<Void> promise2 = channel.newPromise();
        Buffer buffer2 = channel.bufferAllocator().allocate(8).writeLong(0);
        queue.add(buffer2, promise2);

        // Size should be 4 as its 2 buffers and 2 listeners.
        assertEquals(4, queue.size());
        assertEquals(16, queue.readableBytes());

        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                queue.remove(channel.bufferAllocator(), 128,  channel.newPromise());
            }
        });

        // This should result in have the first buf and first listener removed from the queue.
        assertEquals(2, queue.size());
        assertEquals(8, queue.readableBytes());
        assertSame(exception, promise.cause());
        assertFalse(buffer.isAccessible());

        assertFalse(promise2.isDone());
        assertTrue(buffer2.isAccessible());
        Buffer removed = queue.remove(channel.bufferAllocator(), 128, channel.newPromise());
        assertEquals(8, removed.readableBytes());
        removed.close();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertEquals(0, queue.readableBytes());
        Buffer removed2 = queue.remove(channel.bufferAllocator(), 128, channel.newPromise());
        assertEquals(0, removed2.readableBytes());
        removed2.close();
    }
}
