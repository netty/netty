/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;

import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PendingWriteQueueTest {

    @Test
    public void testRemoveAndWrite() {
        assertWrite(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) {
                queue.removeAndTransfer(ctx::write);
                assertQueueEmpty(queue);
                super.flush(ctx);
            }
        }, 1);
    }

    @Test
    public void testRemoveAndWriteAll() {
        assertWrite(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) {
                queue.removeAndTransferAll(ctx::write);
                assertQueueEmpty(queue);
                super.flush(ctx);
            }
        }, 3);
    }

    @Test
    public void testRemoveAndFail() {
        assertWriteFails(new TestHandler() {

            @Override
            public void flush(ChannelHandlerContext ctx) {
                queue.removeAndFail(new TestException());
                super.flush(ctx);
            }
        }, 1);
    }

    @Test
    public void testRemoveAndFailAll() {
        assertWriteFails(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) {
                queue.removeAndFailAll(new TestException());
                super.flush(ctx);
            }
        }, 3);
    }

    private static void assertWrite(ChannelHandler handler, int count) {
        final ByteBuf buffer = Unpooled.copiedBuffer("Test", CharsetUtil.US_ASCII);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(1, 3));

        ByteBuf[] buffers = new ByteBuf[count];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = buffer.retainedDuplicate();
        }
        assertTrue(channel.writeOutbound(buffers));
        assertTrue(channel.finish());
        channel.closeFuture().syncUninterruptibly();

        for (int i = 0; i < buffers.length; i++) {
            assertBuffer(channel, buffer);
        }
        buffer.release();
        assertNull(channel.readOutbound());
    }

    private static void assertBuffer(EmbeddedChannel channel, ByteBuf buffer) {
        ByteBuf written = channel.readOutbound();
        assertEquals(buffer, written);
        written.release();
    }

    private static void assertQueueEmpty(PendingWriteQueue queue) {
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertEquals(0, queue.bytes());
        assertNull(queue.current());
    }

    private static void assertWriteFails(ChannelHandler handler, int count) {
        final ByteBuf buffer = Unpooled.copiedBuffer("Test", CharsetUtil.US_ASCII);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        ByteBuf[] buffers = new ByteBuf[count];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = buffer.retainedDuplicate();
        }
        Throwable cause = assertThrows(CompletionException.class, () -> channel.writeOutbound(buffers));
        assertInstanceOf(TestException.class, cause.getCause());
        assertFalse(channel.finish());
        channel.closeFuture().syncUninterruptibly();

        buffer.release();
        assertNull(channel.readOutbound());
    }

    private static EmbeddedChannel newChannel() {
        // Add a handler so we can access a ChannelHandlerContext via the ChannelPipeline.
        return new EmbeddedChannel(new ChannelHandler() { });
    }

    @Test
    public void testRemoveAndFailAllReentrantFailAll() {
        EmbeddedChannel channel = newChannel();
        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().firstContext());

        Promise<Void> promise = channel.newPromise();
        promise.addListener(future -> queue.removeAndFailAll(new IllegalStateException()));
        queue.add(1L, promise);

        Promise<Void> promise2 = channel.newPromise();
        queue.add(2L, promise2);
        queue.removeAndFailAll(new Exception());
        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
        assertTrue(promise2.isDone());
        assertFalse(promise2.isSuccess());
        assertFalse(channel.finish());
    }

    @Test
    public void testRemoveAndWriteAllReentrantWrite() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
                // Convert to writeAndFlush(...) so the promise will be notified by the transport.
                ctx.writeAndFlush(msg, handler);
            }
        }, new ChannelOutboundHandler() { });

        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().lastContext());

        Promise<Void> promise = channel.newPromise();
        final Promise<Void> promise3 = channel.newPromise();
        promise.addListener(future -> queue.add(3L, promise3));
        queue.add(1L, promise);
        Promise<Void> promise2 = channel.newPromise();
        queue.add(2L, promise2);
        queue.removeAndTransferAll(channel::write);

        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
        assertTrue(promise2.isDone());
        assertTrue(promise2.isSuccess());
        assertTrue(promise3.isDone());
        assertTrue(promise3.isSuccess());
        assertTrue(channel.finish());
        assertEquals(1L, (Long) channel.readOutbound());
        assertEquals(2L, (Long) channel.readOutbound());
        assertEquals(3L, (Long) channel.readOutbound());
    }

    @Test
    public void testRemoveAndFailAllReentrantWrite() {
        final List<Integer> failOrder = Collections.synchronizedList(new ArrayList<Integer>());
        EmbeddedChannel channel = newChannel();
        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().firstContext());

        Promise<Void> promise = channel.newPromise();
        final Promise<Void> promise3 = channel.newPromise();
        promise3.addListener(future -> failOrder.add(3));
        promise.addListener(future -> {
            failOrder.add(1);
            queue.add(3L, promise3);
        });
        queue.add(1L, promise);

        Promise<Void> promise2 = channel.newPromise();
        promise2.addListener(future -> failOrder.add(2));
        queue.add(2L, promise2);
        queue.removeAndFailAll(new Exception());
        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
        assertTrue(promise2.isDone());
        assertFalse(promise2.isSuccess());
        assertTrue(promise3.isDone());
        assertFalse(promise3.isSuccess());
        assertFalse(channel.finish());
        assertEquals(1, (int) failOrder.get(0));
        assertEquals(2, (int) failOrder.get(1));
        assertEquals(3, (int) failOrder.get(2));
    }

    @Test
    public void testRemoveAndWriteAllReentrance() {
        EmbeddedChannel channel = newChannel();
        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().firstContext());

        Promise<Void> promise = channel.newPromise();
        promise.addListener(future -> queue.removeAndTransferAll(channel::write));
        queue.add(1L, promise);

        Promise<Void> promise2 = channel.newPromise();
        queue.add(2L, promise2);
        queue.removeAndTransferAll(channel::write);
        channel.flush();
        assertTrue(promise.isSuccess());
        assertTrue(promise2.isSuccess());
        assertTrue(channel.finish());

        assertEquals(1L, (Long) channel.readOutbound());
        assertEquals(2L, (Long) channel.readOutbound());
        assertNull(channel.readOutbound());
        assertNull(channel.readInbound());
    }

    // See https://github.com/netty/netty/issues/3967
    @Test
    public void testCloseChannelOnCreation() {
        EmbeddedChannel channel = newChannel();
        ChannelHandlerContext context = channel.pipeline().firstContext();
        channel.close().syncUninterruptibly();

        final PendingWriteQueue queue = new PendingWriteQueue(context);

        IllegalStateException ex = new IllegalStateException();
        Promise<Void> promise = channel.newPromise();
        queue.add(1L, promise);
        queue.removeAndFailAll(ex);
        assertSame(ex, promise.cause());
    }

    private static class TestHandler implements  ChannelInboundHandler, ChannelOutboundHandler {
        protected PendingWriteQueue queue;
        private int expectedSize;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelActive();
            assertQueueEmpty(queue);
            assertTrue(ctx.channel().isWritable(), "Should be writable");
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
            queue.add(msg, handler);
            assertFalse(queue.isEmpty());
            assertEquals(++expectedSize, queue.size());
            assertNotNull(queue.current());
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            queue = new PendingWriteQueue(ctx);
        }
    }

    private static final class TestException extends Exception {
        private static final long serialVersionUID = -9018570103039458401L;
    }
}
