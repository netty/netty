/*
 * Copyright 2020 The Netty Project

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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCountUtil;
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
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promise.setSuccess();
            }
        }, new ChannelHandlerAdapter() { });
        final AbstractCoalescingBufferQueue queue = new AbstractCoalescingBufferQueue(channel, 128) {
            @Override
            protected ByteBuf compose(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf next) {
                return composeIntoComposite(alloc, cumulation, next);
            }

            @Override
            protected ByteBuf removeEmptyValue() {
                return Unpooled.EMPTY_BUFFER;
            }
        };

        final byte[] bytes = new byte[128];
        queue.add(Unpooled.wrappedBuffer(bytes), new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                queue.add(Unpooled.wrappedBuffer(bytes));
                assertEquals(bytes.length, queue.readableBytes());
            }
        });

        assertEquals(bytes.length, queue.readableBytes());

        ChannelHandlerContext ctx = channel.pipeline().lastContext();
        if (write) {
            queue.writeAndRemoveAll(ctx);
        } else {
            queue.releaseAndFailAll(ctx, new ClosedChannelException());
        }
        ByteBuf buffer = queue.remove(channel.alloc(), 128, channel.newPromise());
        assertFalse(buffer.isReadable());
        buffer.release();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.readableBytes());

        assertFalse(channel.finish());
    }

    @Test
    public void testKeepStateConsistentOnError() {
        final IllegalReferenceCountException exception = new IllegalReferenceCountException();
        final EmbeddedChannel channel = new EmbeddedChannel();
        final AbstractCoalescingBufferQueue queue = new AbstractCoalescingBufferQueue(channel, 128) {
            @Override
            protected ByteBuf compose(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf next) {
                // Simulate throwing an IllegalReferenceCountException.
                throw exception;
            }

            @Override
            protected ByteBuf composeFirst(ByteBufAllocator allocator, ByteBuf first, int bufferSize) {
                // Simulate throwing an IllegalReferenceCountException.
                throw exception;
            }

            @Override
            protected ByteBuf removeEmptyValue() {
                return Unpooled.EMPTY_BUFFER;
            }
        };

        ChannelPromise promise = channel.newPromise();
        ByteBuf buffer = Unpooled.buffer().writeLong(0);
        queue.add(buffer, promise);

        ChannelPromise promise2 = channel.newPromise();
        ByteBuf buffer2 = Unpooled.buffer().writeLong(0);
        queue.add(buffer2, promise2);

        // Size should be 4 as its 2 buffers and 2 listeners.
        assertEquals(4, queue.size());
        assertEquals(16, queue.readableBytes());

        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                queue.remove(channel.alloc(), 128,  channel.newPromise());
            }
        });

        // This should result in have the first buf and first listener removed from the queue.
        assertEquals(2, queue.size());
        assertEquals(8, queue.readableBytes());
        assertSame(exception, promise.cause());
        assertEquals(0, buffer.refCnt());

        assertFalse(promise2.isDone());
        assertEquals(1, buffer2.refCnt());
        ByteBuf removed = queue.remove(channel.alloc(), 128, channel.newPromise());
        assertEquals(8, removed.readableBytes());
        removed.release();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertEquals(0, queue.readableBytes());
        ByteBuf removed2 = queue.remove(channel.alloc(), 128, channel.newPromise());
        assertEquals(0, removed2.readableBytes());
        removed2.release();
    }
}
