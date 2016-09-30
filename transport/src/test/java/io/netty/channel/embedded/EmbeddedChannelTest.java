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
package io.netty.channel.embedded;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EmbeddedChannelTest {

    @Test(timeout = 2000)
    public void promiseDoesNotInfiniteLoop() throws InterruptedException {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                future.channel().close();
            }
        });

        channel.close().syncUninterruptibly();
    }

    @Test
    public void testConstructWithChannelInitializer() {
        final Integer first = 1;
        final Integer second = 2;

        final ChannelHandler handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ctx.fireChannelRead(first);
                ctx.fireChannelRead(second);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(handler);
            }
        });
        ChannelPipeline pipeline = channel.pipeline();
        assertSame(handler, pipeline.firstContext().handler());
        assertTrue(channel.writeInbound(3));
        assertTrue(channel.finish());
        assertSame(first, channel.readInbound());
        assertSame(second, channel.readInbound());
        assertNull(channel.readInbound());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testScheduling() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        final CountDownLatch latch = new CountDownLatch(2);
        ScheduledFuture future = ch.eventLoop().schedule(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        }, 1, TimeUnit.SECONDS);
        future.addListener(new FutureListener() {
            @Override
            public void operationComplete(Future future) throws Exception {
                latch.countDown();
            }
        });
        long next = ch.runScheduledPendingTasks();
        assertTrue(next > 0);
        // Sleep for the nanoseconds but also give extra 50ms as the clock my not be very precise and so fail the test
        // otherwise.
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(next) + 50);
        assertEquals(-1, ch.runScheduledPendingTasks());
        latch.await();
    }

    @Test
    public void testScheduledCancelled() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ScheduledFuture<?> future = ch.eventLoop().schedule(new Runnable() {
            @Override
            public void run() { }
        }, 1, TimeUnit.DAYS);
        ch.finish();
        assertTrue(future.isCancelled());
    }

    @Test(timeout = 3000)
    public void testHandlerAddedExecutedInEventLoop() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final ChannelHandler handler = new ChannelHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                try {
                    assertTrue(ctx.executor().inEventLoop());
                } catch (Throwable cause) {
                    error.set(cause);
                } finally {
                    latch.countDown();
                }
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        assertFalse(channel.finish());
        latch.await();
        Throwable cause = error.get();
        if (cause != null) {
            throw cause;
        }
    }

    @Test
    public void testConstructWithOutHandler() {
        EmbeddedChannel channel = new EmbeddedChannel();
        assertTrue(channel.writeInbound(1));
        assertTrue(channel.writeOutbound(2));
        assertTrue(channel.finish());
        assertSame(1, channel.readInbound());
        assertNull(channel.readInbound());
        assertSame(2, channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    // See https://github.com/netty/netty/issues/4316.
    @Test(timeout = 2000)
    public void testFireChannelInactiveAndUnregisteredOnClose() throws InterruptedException {
        testFireChannelInactiveAndUnregistered(new Action() {
            @Override
            public ChannelFuture doRun(Channel channel) {
                return channel.close();
            }
        });
        testFireChannelInactiveAndUnregistered(new Action() {
            @Override
            public ChannelFuture doRun(Channel channel) {
                return channel.close(channel.newPromise());
            }
        });
    }

    @Test(timeout = 2000)
    public void testFireChannelInactiveAndUnregisteredOnDisconnect() throws InterruptedException {
        testFireChannelInactiveAndUnregistered(new Action() {
            @Override
            public ChannelFuture doRun(Channel channel) {
                return channel.disconnect();
            }
        });

        testFireChannelInactiveAndUnregistered(new Action() {
            @Override
            public ChannelFuture doRun(Channel channel) {
                return channel.disconnect(channel.newPromise());
            }
        });
    }

    private static void testFireChannelInactiveAndUnregistered(Action action) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(3);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                latch.countDown();
                ctx.executor().execute(new Runnable() {
                    @Override
                    public void run() {
                        // Should be executed.
                        latch.countDown();
                    }
                });
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                latch.countDown();
            }
        });
        action.doRun(channel).syncUninterruptibly();
        latch.await();
    }

    private interface Action {
        ChannelFuture doRun(Channel channel);
    }

    @Test
    public void testHasDisconnect() {
        EventOutboundHandler handler = new EventOutboundHandler();
        EmbeddedChannel channel = new EmbeddedChannel(true, handler);
        assertTrue(channel.disconnect().isSuccess());
        assertTrue(channel.close().isSuccess());
        assertEquals(EventOutboundHandler.DISCONNECT, handler.pollEvent());
        assertEquals(EventOutboundHandler.CLOSE, handler.pollEvent());
        assertNull(handler.pollEvent());
    }

    @Test
    public void testHasNoDisconnect() {
        EventOutboundHandler handler = new EventOutboundHandler();
        EmbeddedChannel channel = new EmbeddedChannel(false, handler);
        assertTrue(channel.disconnect().isSuccess());
        assertTrue(channel.close().isSuccess());
        assertEquals(EventOutboundHandler.CLOSE, handler.pollEvent());
        assertEquals(EventOutboundHandler.CLOSE, handler.pollEvent());
        assertNull(handler.pollEvent());
    }

    @Test
    public void testFinishAndReleaseAll() {
        ByteBuf in = Unpooled.buffer();
        ByteBuf out = Unpooled.buffer();
        try {
            EmbeddedChannel channel = new EmbeddedChannel();
            assertTrue(channel.writeInbound(in));
            assertEquals(1, in.refCnt());

            assertTrue(channel.writeOutbound(out));
            assertEquals(1, out.refCnt());

            assertTrue(channel.finishAndReleaseAll());
            assertEquals(0, in.refCnt());
            assertEquals(0, out.refCnt());

            assertNull(channel.readInbound());
            assertNull(channel.readOutbound());
        } finally {
            release(in, out);
        }
    }

    @Test
    public void testReleaseInbound() {
        ByteBuf in = Unpooled.buffer();
        ByteBuf out = Unpooled.buffer();
        try {
            EmbeddedChannel channel = new EmbeddedChannel();
            assertTrue(channel.writeInbound(in));
            assertEquals(1, in.refCnt());

            assertTrue(channel.writeOutbound(out));
            assertEquals(1, out.refCnt());

            assertTrue(channel.releaseInbound());
            assertEquals(0, in.refCnt());
            assertEquals(1, out.refCnt());

            assertTrue(channel.finish());
            assertNull(channel.readInbound());

            ByteBuf buffer = (ByteBuf) channel.readOutbound();
            assertSame(out, buffer);
            buffer.release();

            assertNull(channel.readOutbound());
        } finally {
            release(in, out);
        }
    }

    @Test
    public void testReleaseOutbound() {
        ByteBuf in = Unpooled.buffer();
        ByteBuf out = Unpooled.buffer();
        try {
            EmbeddedChannel channel = new EmbeddedChannel();
            assertTrue(channel.writeInbound(in));
            assertEquals(1, in.refCnt());

            assertTrue(channel.writeOutbound(out));
            assertEquals(1, out.refCnt());

            assertTrue(channel.releaseOutbound());
            assertEquals(1, in.refCnt());
            assertEquals(0, out.refCnt());

            assertTrue(channel.finish());
            assertNull(channel.readOutbound());

            ByteBuf buffer = (ByteBuf) channel.readInbound();
            assertSame(in, buffer);
            buffer.release();

            assertNull(channel.readInbound());
        } finally {
            release(in, out);
        }
    }

    @Test
    public void testWriteLater() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                    throws Exception {
                ctx.executor().execute(new Runnable() {
                    @Override
                    public void run() {
                        ctx.write(msg, promise);
                    }
                });
            }
        });
        Object msg = new Object();

        assertTrue(channel.writeOutbound(msg));
        assertTrue(channel.finish());
        assertSame(msg, channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    @Test
    public void testWriteScheduled() throws InterruptedException  {
        final int delay = 500;
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                    throws Exception {
                ctx.executor().schedule(new Runnable() {
                    @Override
                    public void run() {
                        ctx.writeAndFlush(msg, promise);
                    }
                }, delay, TimeUnit.MILLISECONDS);
            }
        });
        Object msg = new Object();

        assertFalse(channel.writeOutbound(msg));
        Thread.sleep(delay  * 2);
        assertTrue(channel.finish());
        assertSame(msg, channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    private static void release(ByteBuf... buffers) {
        for (ByteBuf buffer : buffers) {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    private static final class EventOutboundHandler extends ChannelOutboundHandlerAdapter {
        static final Integer DISCONNECT = 0;
        static final Integer CLOSE = 1;

        private final Queue<Integer> queue = new ArrayDeque<Integer>();

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            queue.add(DISCONNECT);
            promise.setSuccess();
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            queue.add(CLOSE);
            promise.setSuccess();
        }

        Integer pollEvent() {
            return queue.poll();
        }
    }
}
