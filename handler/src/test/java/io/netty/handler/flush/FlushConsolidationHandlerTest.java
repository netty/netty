/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.flush;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class FlushConsolidationHandlerTest {

    private static final int EXPLICIT_FLUSH_AFTER_FLUSHES = 3;

    @Test
    public void testFlushViaScheduledTask() {
        final AtomicInteger flushCount = new AtomicInteger();
        EmbeddedChannel channel = newChannel(flushCount,  true);
        // Flushes should not go through immediately, as they're scheduled as an async task
        channel.flush();
        assertEquals(0, flushCount.get());
        channel.flush();
        assertEquals(0, flushCount.get());
        // Trigger the execution of the async task
        channel.runPendingTasks();
        assertEquals(1, flushCount.get());
        assertFalse(channel.finish());
    }

    @Test
    public void testFlushViaThresholdOutsideOfReadLoop() {
        final AtomicInteger flushCount = new AtomicInteger();
        EmbeddedChannel channel = newChannel(flushCount, true);
        // After a given threshold, the async task should be bypassed and a flush should be triggered immediately
        for (int i = 0; i < EXPLICIT_FLUSH_AFTER_FLUSHES; i++) {
            channel.flush();
        }
        assertEquals(1, flushCount.get());
        assertFalse(channel.finish());
    }

    @Test
    public void testImmediateFlushOutsideOfReadLoop() {
        final AtomicInteger flushCount = new AtomicInteger();
        EmbeddedChannel channel = newChannel(flushCount, false);
        channel.flush();
        assertEquals(1, flushCount.get());
        assertFalse(channel.finish());
    }

    @Test
    public void testFlushViaReadComplete() {
        final AtomicInteger flushCount = new AtomicInteger();
        EmbeddedChannel channel = newChannel(flushCount, false);
        // Flush should go through as there is no read loop in progress.
        channel.flush();
        channel.runPendingTasks();
        assertEquals(1, flushCount.get());

        // Simulate read loop;
        channel.pipeline().fireChannelRead(1L);
        assertEquals(1, flushCount.get());
        channel.pipeline().fireChannelRead(2L);
        assertEquals(1, flushCount.get());
        assertNull(channel.readOutbound());
        channel.pipeline().fireChannelReadComplete();
        assertEquals(2, flushCount.get());
        // Now flush again as the read loop is complete.
        channel.flush();
        channel.runPendingTasks();
        assertEquals(3, flushCount.get());
        assertEquals(1L, channel.readOutbound());
        assertEquals(2L, channel.readOutbound());
        assertNull(channel.readOutbound());
        assertFalse(channel.finish());
    }

    @Test
    public void testFlushViaClose() {
        final AtomicInteger flushCount = new AtomicInteger();
        EmbeddedChannel channel = newChannel(flushCount, false);
        // Simulate read loop;
        channel.pipeline().fireChannelRead(1L);
        assertEquals(0, flushCount.get());
        assertNull(channel.readOutbound());
        channel.close();
        assertEquals(1, flushCount.get());
        assertEquals(1L, channel.readOutbound());
        assertNull(channel.readOutbound());
        assertFalse(channel.finish());
    }

    @Test
    public void testFlushViaDisconnect() {
        final AtomicInteger flushCount = new AtomicInteger();
        EmbeddedChannel channel = newChannel(flushCount, false);
        // Simulate read loop;
        channel.pipeline().fireChannelRead(1L);
        assertEquals(0, flushCount.get());
        assertNull(channel.readOutbound());
        channel.disconnect();
        assertEquals(1, flushCount.get());
        assertEquals(1L, channel.readOutbound());
        assertNull(channel.readOutbound());
        assertFalse(channel.finish());
    }

    @Test(expected = IllegalStateException.class)
    public void testFlushViaException() {
        final AtomicInteger flushCount = new AtomicInteger();
        EmbeddedChannel channel = newChannel(flushCount, false);
        // Simulate read loop;
        channel.pipeline().fireChannelRead(1L);
        assertEquals(0, flushCount.get());
        assertNull(channel.readOutbound());
        channel.pipeline().fireExceptionCaught(new IllegalStateException());
        assertEquals(1, flushCount.get());
        assertEquals(1L, channel.readOutbound());
        assertNull(channel.readOutbound());
        channel.finish();
    }

    @Test
    public void testFlushViaRemoval() {
        final AtomicInteger flushCount = new AtomicInteger();
        EmbeddedChannel channel = newChannel(flushCount, false);
        // Simulate read loop;
        channel.pipeline().fireChannelRead(1L);
        assertEquals(0, flushCount.get());
        assertNull(channel.readOutbound());
        channel.pipeline().remove(FlushConsolidationHandler.class);
        assertEquals(1, flushCount.get());
        assertEquals(1L, channel.readOutbound());
        assertNull(channel.readOutbound());
        assertFalse(channel.finish());
    }

    /**
     * See https://github.com/netty/netty/issues/9923
     */
    @Test
    public void testResend() throws Exception {
        final AtomicInteger flushCount = new AtomicInteger();
        final EmbeddedChannel channel = newChannel(flushCount, true);
        channel.writeAndFlush(1L).addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                channel.writeAndFlush(1L);
            }
        });
        channel.flushOutbound();
        assertEquals(1L, channel.readOutbound());
        assertEquals(1L, channel.readOutbound());
        assertNull(channel.readOutbound());
        assertFalse(channel.finish());
    }

    private static EmbeddedChannel newChannel(final AtomicInteger flushCount, boolean consolidateWhenNoReadInProgress) {
        return new EmbeddedChannel(
                new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void flush(ChannelHandlerContext ctx) throws Exception {
                        flushCount.incrementAndGet();
                        ctx.flush();
                    }
                },
                new FlushConsolidationHandler(EXPLICIT_FLUSH_AFTER_FLUSHES, consolidateWhenNoReadInProgress),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ctx.writeAndFlush(msg);
                    }
                });
    }
}
