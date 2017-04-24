/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.backpressure;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

public class BackPressureAutoReadHandlerTest {

    @Test
    public void testBackpressure() {
        final AtomicBoolean writableOnFlush = new AtomicBoolean(true);
        final AtomicInteger flushCount = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                flushCount.incrementAndGet();
                writableOnFlush.set(ctx.channel().isWritable());
                ctx.flush();
            }
        }, new  BackPressureAutoReadHandler());
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(128, 512));
        assertTrue(channel.config().isAutoRead());
        assertEquals(0, flushCount.get());
        ChannelFuture future = channel.write(Unpooled.buffer().writeZero(129));
        assertFalse(future.isDone());
        assertEquals(0, flushCount.get());
        assertTrue(channel.config().isAutoRead());
        // This should trigger a flush and while the flush is executed the Channel should still be non-writable
        ChannelFuture future2 = channel.write(Unpooled.buffer().writeZero(1024));
        assertEquals(1, flushCount.get());
        assertFalse(writableOnFlush.get());

        assertTrue(future.isDone());
        assertTrue(future2.isDone());

        ByteBuf buffer = channel.readOutbound();
        buffer.release();

        buffer = channel.readOutbound();
        buffer.release();

        assertFalse(channel.finish());
    }

    @Test
    public void testThrowIfNotAutoRead() {
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.config().setAutoRead(false);
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                error.compareAndSet(null, cause);
            }
        });
        channel.pipeline().addFirst(new BackPressureAutoReadHandler());
        assertTrue(error.get().getCause() instanceof IllegalStateException);
        assertFalse(channel.finish());
    }

    @Test
    public void testNotResetAutoRead() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addFirst("backpressure", new BackPressureAutoReadHandler());
        channel.config().setAutoRead(false);
        channel.pipeline().remove("backpressure");
        assertFalse(channel.config().isAutoRead());
        assertFalse(channel.finish());
    }
}
