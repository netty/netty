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
package io.netty5.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PendingWriteQueueTest {

    @Test
    public void testRemoveAndWrite() throws Exception {
        assertWrite(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) {
                Future<Void> future = queue.removeAndTransferAll(ctx::write);
                future.addListener(future1 -> assertQueueEmpty(queue));
                super.flush(ctx);
            }
        }, 1);
    }

    @Test
    public void testRemoveAndWriteAll() throws Exception {
        assertWrite(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) {
                Future<Void> future = queue.removeAndTransferAll(ctx::write);
                future.addListener(future1 -> assertQueueEmpty(queue));
                super.flush(ctx);
            }
        }, 3);
    }

    @Test
    public void testRemoveAndFail() throws Exception {
        assertWriteFails(new TestHandler() {

            @Override
            public void flush(ChannelHandlerContext ctx) {
                queue.removeAndFail(new TestException());
                super.flush(ctx);
            }
        }, 1);
    }

    @Test
    public void testRemoveAndFailAll() throws Exception {
        assertWriteFails(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) {
                queue.removeAndFailAll(new TestException());
                super.flush(ctx);
            }
        }, 3);
    }

    private static void assertWrite(ChannelHandler handler, int count) throws Exception {
        try (Buffer buffer = preferredAllocator().copyOf("Test", CharsetUtil.US_ASCII)) {
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            channel.setOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1, 3));

            Buffer[] buffers = new Buffer[count];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = buffer.copy();
            }
            assertTrue(channel.writeOutbound(buffers));
            assertTrue(channel.finish());
            channel.closeFuture().asStage().sync();

            for (int i = 0; i < buffers.length; i++) {
                assertBuffer(channel, buffer);
            }
            assertNull(channel.readOutbound());
        }
    }

    private static void assertBuffer(EmbeddedChannel channel, Buffer buffer) {
        try (Buffer written = channel.readOutbound()) {
            assertEquals(buffer, written);
        }
    }

    private static void assertQueueEmpty(PendingWriteQueue queue) {
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertEquals(0, queue.bytes());
        assertNull(queue.current());
    }

    private static void assertWriteFails(ChannelHandler handler, int count) throws Exception {
        try (Buffer buffer = preferredAllocator().copyOf("Test", CharsetUtil.US_ASCII)) {
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            Buffer[] buffers = new Buffer[count];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = buffer.copy();
            }
            try {
                assertFalse(channel.writeOutbound(buffers));
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof TestException);
            }
            assertFalse(channel.finish());
            channel.closeFuture().asStage().sync();

            assertNull(channel.readOutbound());
        }
    }

    private static EmbeddedChannel newChannel() {
        // Add a handler so we can access a ChannelHandlerContext via the ChannelPipeline.
        return new EmbeddedChannel(new ChannelHandler() { });
    }

    @Test
    public void testRemoveAndFailAllReentrantFailAll() {
        EmbeddedChannel channel = newChannel();
        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().firstContext().executor(),
                channel.getOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR).newHandle());

        Promise<Void> promise = channel.newPromise();
        promise.asFuture().addListener(future -> queue.removeAndFailAll(new IllegalStateException()));
        Promise<Void> promise2 = channel.newPromise();

        channel.executor().execute(() -> {
            queue.add(1L, promise);
            queue.add(2L, promise2);
            queue.removeAndFailAll(new Exception());
        });

        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
        assertTrue(promise2.isDone());
        assertFalse(promise2.isSuccess());
        assertFalse(channel.finish());
    }

    @Test
    public void testRemoveAndWriteAllReentrantWrite() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                // Convert to writeAndFlush(...) so the promise will be notified by the transport.
                return ctx.writeAndFlush(msg);
            }
        }, new ChannelHandler() { });

        final ChannelHandlerContext lastCtx = channel.pipeline().lastContext();
        final PendingWriteQueue queue = new PendingWriteQueue(lastCtx.executor(),
                channel.getOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR).newHandle());

        Promise<Void> promise = channel.newPromise();
        final Promise<Void> promise3 = channel.newPromise();
        promise.asFuture().addListener(future -> {
            queue.add(3L, promise3);
        });
        Promise<Void> promise2 = channel.newPromise();

        channel.executor().execute(() -> {
            queue.add(1L, promise);
            queue.add(2L, promise2);
            queue.removeAndTransferAll(lastCtx::write);
        });

        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
        assertTrue(promise2.isDone());
        assertTrue(promise2.isSuccess());
        assertFalse(promise3.isDone());
        assertFalse(promise3.isSuccess());

        channel.executor().execute(() -> queue.removeAndTransferAll(lastCtx::write));
        assertTrue(promise3.isDone());
        assertTrue(promise3.isSuccess());
        channel.runPendingTasks();
        assertTrue(channel.finish());

        assertEquals(1L, (Long) channel.readOutbound());
        assertEquals(2L, (Long) channel.readOutbound());
        assertEquals(3L, (Long) channel.readOutbound());
    }

    @Disabled("Need to verify and think about if the assumptions made by this test are valid at all.")
    @Test
    public void testRemoveAndFailAllReentrantWrite() {
        final List<Integer> failOrder = Collections.synchronizedList(new ArrayList<>());
        EmbeddedChannel channel = newChannel();
        final ChannelHandlerContext firstCtx = channel.pipeline().firstContext();
        final PendingWriteQueue queue = new PendingWriteQueue(firstCtx.executor(),
                firstCtx.channel().getOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR).newHandle());

        Promise<Void> promise = channel.newPromise();
        final Promise<Void> promise3 = channel.newPromise();
        promise3.asFuture().addListener(future -> failOrder.add(3));
        promise.asFuture().addListener(future -> {
            failOrder.add(1);
            queue.add(3L, promise3);
        });
        Promise<Void> promise2 = channel.newPromise();
        promise2.asFuture().addListener(future -> failOrder.add(2));
        channel.executor().execute(() -> {
            queue.add(1L, promise);
            queue.add(2L, promise2);
            queue.removeAndFailAll(new Exception());
        });

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
        final ChannelHandlerContext firstCtx = channel.pipeline().firstContext();
        final PendingWriteQueue queue = new PendingWriteQueue(firstCtx.executor(),
                firstCtx.channel().getOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR).newHandle());

        Promise<Void> promise = channel.newPromise();
        promise.asFuture().addListener(future -> queue.removeAndTransferAll(firstCtx::write));
        Promise<Void> promise2 = channel.newPromise();

        channel.executor().execute(() -> {
            queue.add(1L, promise);

            queue.add(2L, promise2);
            queue.removeAndTransferAll(firstCtx::write);
        });

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
    public void testCloseChannelOnCreation() throws Exception {
        EmbeddedChannel channel = newChannel();
        ChannelHandlerContext context = channel.pipeline().firstContext();
        channel.close().asStage().sync();

        final PendingWriteQueue queue = new PendingWriteQueue(context.executor(),
                channel.getOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR).newHandle());

        IllegalStateException ex = new IllegalStateException();
        Promise<Void> promise = channel.newPromise();
        channel.executor().execute(() -> {
            queue.add(1L, promise);
            queue.removeAndFailAll(ex);
        });
        assertSame(ex, promise.cause());
    }

    private static class TestHandler implements ChannelHandler {
        protected PendingWriteQueue queue;
        private int expectedSize;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelActive();
            assertQueueEmpty(queue);
            assertTrue(ctx.channel().isWritable(), "Should be writable");
        }

        @Override
        public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
            Promise<Void> promise = ctx.newPromise();
            queue.add(msg, promise);
            assertFalse(queue.isEmpty());
            assertEquals(++expectedSize, queue.size());
            assertNotNull(queue.current());
            return promise.asFuture();
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            queue = new PendingWriteQueue(ctx.executor(),
                    ctx.channel().getOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR).newHandle());
        }
    }

    private static final class TestException extends Exception {
        private static final long serialVersionUID = -9018570103039458401L;
    }
}
