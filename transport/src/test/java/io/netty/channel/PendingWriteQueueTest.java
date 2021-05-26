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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PendingWriteQueueTest {

    @Test
    public void testRemoveAndWrite() {
        assertWrite(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                assertFalse(ctx.channel().isWritable(), "Should not be writable anymore");

                ChannelFuture future = queue.removeAndWrite();
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        assertQueueEmpty(queue);
                    }
                });
                super.flush(ctx);
            }
        }, 1);
    }

    @Test
    public void testRemoveAndWriteAll() {
        assertWrite(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                assertFalse(ctx.channel().isWritable(), "Should not be writable anymore");

                ChannelFuture future = queue.removeAndWriteAll();
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        assertQueueEmpty(queue);
                    }
                });
                super.flush(ctx);
            }
        }, 3);
    }

    @Test
    public void testRemoveAndFail() {
        assertWriteFails(new TestHandler() {

            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                queue.removeAndFail(new TestException());
                super.flush(ctx);
            }
        }, 1);
    }

    @Test
    public void testRemoveAndFailAll() {
        assertWriteFails(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                queue.removeAndFailAll(new TestException());
                super.flush(ctx);
            }
        }, 3);
    }

    @Test
    public void shouldFireChannelWritabilityChangedAfterRemoval() {
        final AtomicReference<ChannelHandlerContext> ctxRef = new AtomicReference<ChannelHandlerContext>();
        final AtomicReference<PendingWriteQueue> queueRef = new AtomicReference<PendingWriteQueue>();
        final ByteBuf msg = Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII);

        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
                ctxRef.set(ctx);
                queueRef.set(new PendingWriteQueue(ctx));
            }

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                final PendingWriteQueue queue = queueRef.get();

                final ByteBuf msg = (ByteBuf) queue.current();
                if (msg == null) {
                    return;
                }

                assertThat(msg.refCnt(), is(1));

                // This call will trigger another channelWritabilityChanged() event because the number of
                // pending bytes will go below the low watermark.
                //
                // If PendingWriteQueue.remove() did not remove the current entry before triggering
                // channelWritabilityChanged() event, we will end up with attempting to remove the same
                // element twice, resulting in the double release.
                queue.remove();

                assertThat(msg.refCnt(), is(0));
            }
        });

        channel.config().setWriteBufferLowWaterMark(1);
        channel.config().setWriteBufferHighWaterMark(3);

        final PendingWriteQueue queue = queueRef.get();

        // Trigger channelWritabilityChanged() by adding a message that's larger than the high watermark.
        queue.add(msg, channel.newPromise());

        channel.finish();

        assertThat(msg.refCnt(), is(0));
    }

    private static void assertWrite(ChannelHandler handler, int count) {
        final ByteBuf buffer = Unpooled.copiedBuffer("Test", CharsetUtil.US_ASCII);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.config().setWriteBufferLowWaterMark(1);
        channel.config().setWriteBufferHighWaterMark(3);

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
        assertNull(queue.removeAndWrite());
        assertNull(queue.removeAndWriteAll());
    }

    private static void assertWriteFails(ChannelHandler handler, int count) {
        final ByteBuf buffer = Unpooled.copiedBuffer("Test", CharsetUtil.US_ASCII);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        ByteBuf[] buffers = new ByteBuf[count];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = buffer.retainedDuplicate();
        }
        try {
            assertFalse(channel.writeOutbound(buffers));
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof TestException);
        }
        assertFalse(channel.finish());
        channel.closeFuture().syncUninterruptibly();

        buffer.release();
        assertNull(channel.readOutbound());
    }

    private static EmbeddedChannel newChannel() {
        // Add a handler so we can access a ChannelHandlerContext via the ChannelPipeline.
        return new EmbeddedChannel(new ChannelHandlerAdapter() { });
    }

    @Test
    public void testRemoveAndFailAllReentrantFailAll() {
        EmbeddedChannel channel = newChannel();
        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().firstContext());

        ChannelPromise promise = channel.newPromise();
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                queue.removeAndFailAll(new IllegalStateException());
            }
        });
        queue.add(1L, promise);

        ChannelPromise promise2 = channel.newPromise();
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
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                // Convert to writeAndFlush(...) so the promise will be notified by the transport.
                ctx.writeAndFlush(msg, promise);
            }
        }, new ChannelOutboundHandlerAdapter());

        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().lastContext());

        ChannelPromise promise = channel.newPromise();
        final ChannelPromise promise3 = channel.newPromise();
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                queue.add(3L, promise3);
            }
        });
        queue.add(1L, promise);
        ChannelPromise promise2 = channel.newPromise();
        queue.add(2L, promise2);
        queue.removeAndWriteAll();

        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
        assertTrue(promise2.isDone());
        assertTrue(promise2.isSuccess());
        assertTrue(promise3.isDone());
        assertTrue(promise3.isSuccess());
        assertTrue(channel.finish());
        assertEquals(1L, channel.readOutbound());
        assertEquals(2L, channel.readOutbound());
        assertEquals(3L, channel.readOutbound());
    }

    @Test
    public void testRemoveAndWriteAllWithVoidPromise() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                // Convert to writeAndFlush(...) so the promise will be notified by the transport.
                ctx.writeAndFlush(msg, promise);
            }
        }, new ChannelOutboundHandlerAdapter());

        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().lastContext());

        ChannelPromise promise = channel.newPromise();
        queue.add(1L, promise);
        queue.add(2L, channel.voidPromise());
        queue.removeAndWriteAll();

        assertTrue(channel.finish());
        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
        assertEquals(1L, channel.readOutbound());
        assertEquals(2L, channel.readOutbound());
    }

    @Test
    public void testRemoveAndFailAllReentrantWrite() {
        final List<Integer> failOrder = Collections.synchronizedList(new ArrayList<Integer>());
        EmbeddedChannel channel = newChannel();
        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().firstContext());

        ChannelPromise promise = channel.newPromise();
        final ChannelPromise promise3 = channel.newPromise();
        promise3.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                failOrder.add(3);
            }
        });
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                failOrder.add(1);
                queue.add(3L, promise3);
            }
        });
        queue.add(1L, promise);

        ChannelPromise promise2 = channel.newPromise();
        promise2.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                failOrder.add(2);
            }
        });
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

        ChannelPromise promise = channel.newPromise();
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                queue.removeAndWriteAll();
            }
        });
        queue.add(1L, promise);

        ChannelPromise promise2 = channel.newPromise();
        queue.add(2L, promise2);
        queue.removeAndWriteAll();
        channel.flush();
        assertTrue(promise.isSuccess());
        assertTrue(promise2.isSuccess());
        assertTrue(channel.finish());

        assertEquals(1L, channel.readOutbound());
        assertEquals(2L, channel.readOutbound());
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
        ChannelPromise promise = channel.newPromise();
        queue.add(1L, promise);
        queue.removeAndFailAll(ex);
        assertSame(ex, promise.cause());
    }

    private static class TestHandler extends ChannelDuplexHandler {
        protected PendingWriteQueue queue;
        private int expectedSize;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            assertQueueEmpty(queue);
            assertTrue(ctx.channel().isWritable(), "Should be writable");
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            queue.add(msg, promise);
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
