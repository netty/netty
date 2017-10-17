/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler.SslHandlerCoalescingBufferQueue;
import io.netty.util.CharsetUtil;

public class SslHandlerCoalescingBufferQueueTest {

    @Test
    public void shouldFireChannelWritabilityChangedAfterAddFirstRemoveFirst() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final List<Boolean> notifications = new ArrayList<Boolean>();
        doCheckFireChannelWritabilityChanged(new TestChannelHandler(latch, notifications) {

            @Override
            protected void doRemoveWork(ChannelHandlerContext ctx) {
                final SslHandlerCoalescingBufferQueue queue = queueRef.get();
                queue.removeFirst(ctx.newPromise());
            }

            @Override
            protected void doAddWork(ByteBuf msg) {
                final SslHandlerCoalescingBufferQueue queue = queueRef.get();
                queue.addFirst(msg);
            }
        }, latch, notifications, true);
    }

    @Test
    public void shouldFireChannelWritabilityChangedAfterAddRemove() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final List<Boolean> notifications = new ArrayList<Boolean>();
        doCheckFireChannelWritabilityChanged(new TestChannelHandler(latch, notifications) {

            @Override
            protected void doRemoveWork(ChannelHandlerContext ctx) {
                final SslHandlerCoalescingBufferQueue queue = queueRef.get();
                queue.remove(ctx.alloc(), 16, ctx.newPromise());
            }

            @Override
            protected void doAddWork(ByteBuf msg) {
                final SslHandlerCoalescingBufferQueue queue = queueRef.get();
                queue.add(msg);
            }
        }, latch, notifications, true);
    }

    @Test
    public void shouldFireChannelWritabilityChangedAfterAddReleaseAndFailAll() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final List<Boolean> notifications = new ArrayList<Boolean>();
        doCheckFireChannelWritabilityChanged(new TestChannelHandler(latch, notifications) {

            @Override
            protected void doRemoveWork(ChannelHandlerContext ctx) {
                final SslHandlerCoalescingBufferQueue queue = queueRef.get();
                queue.releaseAndFailAll(ctx, new ChannelException("Pending write on removal"));
            }

            @Override
            protected void doAddWork(ByteBuf msg) {
                final SslHandlerCoalescingBufferQueue queue = queueRef.get();
                queue.add(msg);
            }
        }, latch, notifications, false);
    }

    private void doCheckFireChannelWritabilityChanged(TestChannelHandler handler, CountDownLatch latch,
            List<Boolean> notifications, boolean releaseMessage) throws Exception {
        final ByteBuf msg = Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII);

        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.config().setWriteBufferLowWaterMark(1);
        channel.config().setWriteBufferHighWaterMark(3);

        handler.doAddWork(msg);
        channel.runPendingTasks();

        assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
        System.out.println(notifications);
        assertEquals(2, notifications.size());
        assertFalse(notifications.get(0));
        assertTrue(notifications.get(1));

        if (releaseMessage) {
            msg.release();
        }
    }

    private static class TestChannelHandler extends ChannelInboundHandlerAdapter {
        final AtomicReference<ChannelHandlerContext> ctxRef = new AtomicReference<ChannelHandlerContext>();
        final AtomicReference<SslHandlerCoalescingBufferQueue> queueRef =
                new AtomicReference<SslHandlerCoalescingBufferQueue>();
        private final CountDownLatch latch;
        private final List<Boolean> notifications;

        public TestChannelHandler(CountDownLatch latch, List<Boolean> notifications) {
            this.latch = latch;
            this.notifications = notifications;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            ctxRef.set(ctx);
            queueRef.set(new SslHandlerCoalescingBufferQueue(16, ctx));
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            latch.countDown();
            notifications.add(ctx.channel().isWritable());
            if (!ctx.channel().isWritable()) {
                doRemoveWork(ctx);
                ((EmbeddedChannel) ctx.channel()).runPendingTasks();
            }
        }

        protected void doRemoveWork(ChannelHandlerContext ctx) {
        }

        protected void doAddWork(ByteBuf msg) {
        }
    }
}
