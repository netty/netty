/*
 * Copyright 2014 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class PendingWriteQueueTest {

    @Test
    public void testRemoveAndWrite() {
        assertWrite(new TestHandler() {
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                Assert.assertFalse("Should not be writable anymore", ctx.channel().isWritable());

                ChannelFuture future = queue.removeAndWrite();
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
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
                Assert.assertFalse("Should not be writable anymore", ctx.channel().isWritable());

                ChannelFuture future = queue.removeAndWriteAll();
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
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

        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                ctxRef.set(ctx);
                queueRef.set(new PendingWriteQueue(ctx));
            }

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
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
            buffers[i] = buffer.duplicate().retain();
        }
        Assert.assertTrue(channel.writeOutbound(buffers));
        Assert.assertTrue(channel.finish());
        channel.closeFuture().syncUninterruptibly();

        for (int i = 0; i < buffers.length; i++) {
            assertBuffer(channel, buffer);
        }
        buffer.release();
        Assert.assertNull(channel.readOutbound());
    }

    private static void assertBuffer(EmbeddedChannel channel, ByteBuf buffer) {
        ByteBuf written = channel.readOutbound();
        Assert.assertEquals(buffer, written);
        written.release();
    }

    private static void assertQueueEmpty(PendingWriteQueue queue) {
        Assert.assertTrue(queue.isEmpty());
        Assert.assertEquals(0, queue.size());
        Assert.assertNull(queue.current());
        Assert.assertNull(queue.removeAndWrite());
        Assert.assertNull(queue.removeAndWriteAll());
    }

    private static void assertWriteFails(ChannelHandler handler, int count) {
        final ByteBuf buffer = Unpooled.copiedBuffer("Test", CharsetUtil.US_ASCII);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        ByteBuf[] buffers = new ByteBuf[count];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = buffer.duplicate().retain();
        }
        try {
            Assert.assertFalse(channel.writeOutbound(buffers));
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof TestException);
        }
        Assert.assertFalse(channel.finish());
        channel.closeFuture().syncUninterruptibly();

        buffer.release();
        Assert.assertNull(channel.readOutbound());
    }

    @Test
    public void testRemoveAndFailAllReentrance() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().firstContext());

        ChannelPromise promise = channel.newPromise();
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                queue.removeAndFailAll(new IllegalStateException());
            }
        });
        queue.add(1L, promise);

        ChannelPromise promise2 = channel.newPromise();
        queue.add(2L, promise2);
        queue.removeAndFailAll(new Exception());
        assertFalse(promise.isSuccess());
        assertFalse(promise2.isSuccess());
        assertFalse(channel.finish());
    }

    @Test
    public void testRemoveAndWriteAllReentrance() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        final PendingWriteQueue queue = new PendingWriteQueue(channel.pipeline().firstContext());

        ChannelPromise promise = channel.newPromise();
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
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

    private static class TestHandler extends ChannelDuplexHandler {
        protected PendingWriteQueue queue;
        private int expectedSize;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            assertQueueEmpty(queue);
            Assert.assertTrue("Should be writable", ctx.channel().isWritable());
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            queue.add(msg, promise);
            Assert.assertFalse(queue.isEmpty());
            Assert.assertEquals(++ expectedSize, queue.size());
            Assert.assertNotNull(queue.current());
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            queue = new PendingWriteQueue(ctx);
        }
    }

    private static final class TestException extends Exception { }
}
