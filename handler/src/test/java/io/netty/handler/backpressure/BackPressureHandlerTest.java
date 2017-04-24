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
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackPressureHandlerTest {

    @Test
    public void testBackpressure() {
        final AtomicBoolean writableOnFlush = new AtomicBoolean(true);
        final AtomicInteger flushCount = new AtomicInteger();
        final AtomicInteger readCount = new AtomicInteger();
        final AtomicBoolean supressFlush = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(128, 512));
        channel.config().setAutoRead(false);

        // Add the handlers after we set auto read to false.
        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                flushCount.incrementAndGet();
                writableOnFlush.set(ctx.channel().isWritable());
                if (!supressFlush.get()) {
                    ctx.flush();
                }
            }

            @Override
            public void read(ChannelHandlerContext ctx) throws Exception {
                readCount.incrementAndGet();
                ctx.read();
            }
        }, new BackPressureHandler());

        assertEquals(0, flushCount.get());
        ChannelFuture future = channel.write(Unpooled.buffer().writeZero(129));
        assertFalse(future.isDone());
        assertEquals(0, flushCount.get());
        channel.read();
        assertEquals(1, readCount.get());

        supressFlush.set(true);
        // This should trigger a flush and while the flush is executed the Channel should still be non-writable
        ChannelFuture future2 = channel.write(Unpooled.buffer().writeZero(1024));
        assertFalse(future2.isDone());
        assertEquals(1, flushCount.get());
        assertFalse(writableOnFlush.get());

        // This read will be supressed by the BackpressureHandler so the count should not change
        channel.read();
        assertEquals(1, readCount.get());

        // Flush again after we not supress it anymore. This should trigger a channelWritabilityChanged event
        // that will cause a read operation as one was pending.
        supressFlush.set(false);
        channel.flush();
        assertEquals(2, readCount.get());

        assertTrue(future.isDone());
        assertTrue(future2.isDone());

        ByteBuf buffer = channel.readOutbound();
        buffer.release();

        buffer = channel.readOutbound();
        buffer.release();

        assertFalse(channel.finish());
    }

    @Test
    public void testReadOnRemoval() {
        final AtomicInteger readCount = new AtomicInteger();
        final AtomicBoolean supressFlush = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(128, 512));
        channel.config().setAutoRead(false);

        // Add the handlers after we set auto read to false.
        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                if (!supressFlush.get()) {
                    ctx.flush();
                }
            }

            @Override
            public void read(ChannelHandlerContext ctx) throws Exception {
                readCount.incrementAndGet();
                ctx.read();
            }
        }, new BackPressureHandler());

        ChannelFuture future = channel.write(Unpooled.buffer().writeZero(129));
        assertFalse(future.isDone());
        channel.read();
        assertEquals(1, readCount.get());

        supressFlush.set(true);

        // This should trigger a flush and while the flush is executed the Channel should still be non-writable
        ChannelFuture future2 = channel.write(Unpooled.buffer().writeZero(1024));
        assertFalse(future2.isDone());

        // This read will be supressed by the BackpressureHandler so the count should not change
        channel.read();
        assertEquals(1, readCount.get());

        // Removal of the handler should trigger a read as the read was previously suppressed
        channel.pipeline().remove(BackPressureHandler.class);

        assertEquals(2, readCount.get());

        supressFlush.set(false);
        channel.flush();

        assertTrue(future.isDone());
        assertTrue(future2.isDone());

        ByteBuf buffer = channel.readOutbound();
        buffer.release();

        buffer = channel.readOutbound();
        buffer.release();

        assertFalse(channel.finish());
    }

    @Test
    public void testNoPendingReadOnRemoval() {
        final AtomicInteger readCount = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(128, 512));
        channel.config().setAutoRead(false);

        // Add the handlers after we set auto read to false.
        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {

            @Override
            public void read(ChannelHandlerContext ctx) throws Exception {
                readCount.incrementAndGet();
                ctx.read();
            }
        }, new BackPressureHandler());

        // Removal of the handler shouldnt  trigger a read as there was no read previously suppressed
        channel.pipeline().remove(BackPressureHandler.class);

        assertEquals(0, readCount.get());
        assertFalse(channel.finish());
    }
}
