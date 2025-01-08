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
package io.netty.channel.embedded;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class EmbeddedChannelTest {

    @Test
    public void testParent() {
        EmbeddedChannel parent = new EmbeddedChannel();
        EmbeddedChannel channel = new EmbeddedChannel(parent, EmbeddedChannelId.INSTANCE, true, false);
        assertSame(parent, channel.parent());
        assertNull(parent.parent());

        assertFalse(channel.finish());
        assertFalse(parent.finish());
    }

    @Test
    public void testNotRegistered() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(false, false);
        assertFalse(channel.isRegistered());
        channel.register();
        assertTrue(channel.isRegistered());
        assertFalse(channel.finish());
    }

    @Test
    public void testRegistered() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(true, false);
        assertTrue(channel.isRegistered());
        try {
            channel.register();
            fail();
        } catch (IllegalStateException expected) {
            // This is expected the channel is registered already on an EventLoop.
        }
        assertFalse(channel.finish());
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
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
        Future future = ch.eventLoop().schedule(new Runnable() {
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
        Future<?> future = ch.eventLoop().schedule(new Runnable() {
            @Override
            public void run() { }
        }, 1, TimeUnit.DAYS);
        ch.finish();
        assertTrue(future.isCancelled());
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
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

    @Test
    public void testConstructWithChannelId() {
        ChannelId channelId = new CustomChannelId(1);
        EmbeddedChannel channel = new EmbeddedChannel(channelId);
        assertSame(channelId, channel.id());
    }

    // See https://github.com/netty/netty/issues/4316.
    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
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

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
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
    public void testHasNoDisconnectSkipDisconnect() throws InterruptedException {
        EmbeddedChannel channel = new EmbeddedChannel(false, new ChannelOutboundHandlerAdapter() {
            @Override
            public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                promise.tryFailure(new Throwable());
            }
        });
        assertFalse(channel.disconnect().isSuccess());
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

            ByteBuf buffer = channel.readOutbound();
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

            ByteBuf buffer = channel.readInbound();
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
    public void testWriteScheduled() throws InterruptedException {
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
        Thread.sleep(delay * 2);
        assertTrue(channel.finish());
        assertSame(msg, channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    @Test
    public void testFlushInbound() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                latch.countDown();
            }
        });

        channel.flushInbound();

        if (!latch.await(1L, TimeUnit.SECONDS)) {
            fail("Nobody called #channelReadComplete() in time.");
        }
    }

    @Test
    public void testWriteOneInbound() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger flushCount = new AtomicInteger(0);

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ReferenceCountUtil.release(msg);
                latch.countDown();
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                flushCount.incrementAndGet();
            }
        });

        channel.writeOneInbound("Hello, Netty!");

        if (!latch.await(1L, TimeUnit.SECONDS)) {
            fail("Nobody called #channelRead() in time.");
        }

        channel.close().syncUninterruptibly();

        // There was no #flushInbound() call so nobody should have called
        // #channelReadComplete()
        assertEquals(0, flushCount.get());
    }

    @Test
    public void testFlushOutbound() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                latch.countDown();
            }
        });

        channel.flushOutbound();

        if (!latch.await(1L, TimeUnit.SECONDS)) {
            fail("Nobody called #flush() in time.");
        }
    }

    @Test
    public void testWriteOneOutbound() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger flushCount = new AtomicInteger(0);

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                ctx.write(msg, promise);
                latch.countDown();
            }

            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                flushCount.incrementAndGet();
            }
        });

        // This shouldn't trigger a #flush()
        channel.writeOneOutbound("Hello, Netty!");

        if (!latch.await(1L, TimeUnit.SECONDS)) {
            fail("Nobody called #write() in time.");
        }

        channel.close().syncUninterruptibly();

        // There was no #flushOutbound() call so nobody should have called #flush()
        assertEquals(0, flushCount.get());
    }

    @Test
    public void testEnsureOpen() throws InterruptedException {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.close().syncUninterruptibly();

        try {
            channel.writeOutbound("Hello, Netty!");
            fail("This should have failed with a ClosedChannelException");
        } catch (Exception expected) {
            assertTrue(expected instanceof ClosedChannelException);
        }

        try {
            channel.writeInbound("Hello, Netty!");
            fail("This should have failed with a ClosedChannelException");
        } catch (Exception expected) {
            assertTrue(expected instanceof ClosedChannelException);
        }
    }

    @Test
    public void testHandleInboundMessage() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        EmbeddedChannel channel = new EmbeddedChannel() {
            @Override
            protected void handleInboundMessage(Object msg) {
                latch.countDown();
            }
        };

        channel.writeOneInbound("Hello, Netty!");

        if (!latch.await(1L, TimeUnit.SECONDS)) {
            fail("Nobody called #handleInboundMessage() in time.");
        }
    }

    @Test
    public void testHandleOutboundMessage() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        EmbeddedChannel channel = new EmbeddedChannel() {
            @Override
            protected void handleOutboundMessage(Object msg) {
                latch.countDown();
            }
        };

        channel.writeOneOutbound("Hello, Netty!");
        if (latch.await(50L, TimeUnit.MILLISECONDS)) {
            fail("Somebody called unexpectedly #flush()");
        }

        channel.flushOutbound();
        if (!latch.await(1L, TimeUnit.SECONDS)) {
            fail("Nobody called #handleOutboundMessage() in time.");
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testChannelInactiveFired() throws InterruptedException {
        final AtomicBoolean inactive = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                ctx.close();
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                inactive.set(true);
            }
        });
        channel.pipeline().fireExceptionCaught(new IllegalStateException());

        assertTrue(inactive.get());
    }

    @Test
    public void testReRegisterEventLoop() throws Exception {
        final CountDownLatch unregisteredLatch = new CountDownLatch(1);
        final CountDownLatch registeredLatch = new CountDownLatch(2);
        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) {
                unregisteredLatch.countDown();
            }

            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                registeredLatch.countDown();
            }
        });

        final EmbeddedEventLoop embeddedEventLoop = new EmbeddedEventLoop();
        channel.deregister().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                embeddedEventLoop.register(channel);
            }
        });

        if (!unregisteredLatch.await(5, TimeUnit.SECONDS)) {
            fail("Channel was not unregistered in time.");
        }

        if (!registeredLatch.await(5, TimeUnit.SECONDS)) {
            fail("Channel was not registered in time.");
        }

        final CountDownLatch taskLatch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                taskLatch.countDown();
            }
        });

        channel.runPendingTasks();
        if (!taskLatch.await(5, TimeUnit.SECONDS)) {
            fail("Task was not executed in time.");
        }
    }

    @Test
    void testRunPendingTasksForNotRegisteredChannel() {
        final EmbeddedChannel channel = new EmbeddedChannel(false, false);
        long nextScheduledTaskTime = 0;
        try {
            nextScheduledTaskTime = channel.runScheduledPendingTasks();
            channel.checkException();
        } catch (Throwable t) {
            fail("Channel should not throw an exception for scheduled pending tasks if it is not registered", t);
        }

        assertEquals(-1L, nextScheduledTaskTime);

        try {
            channel.runPendingTasks();
            channel.checkException();
        } catch (Throwable t) {
            fail("Channel should not throw an exception for pending tasks if it is not registered", t);
        }
    }

    @Test
    @Timeout(30) // generous timeout, just make sure we don't actually wait for the full 10 mins...
    void testAdvanceTime() {
        EmbeddedChannel channel = new EmbeddedChannel();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
            }
        };
        ScheduledFuture<?> future10 = channel.eventLoop().schedule(runnable, 10, TimeUnit.MINUTES);
        ScheduledFuture<?> future20 = channel.eventLoop().schedule(runnable, 20, TimeUnit.MINUTES);

        channel.runPendingTasks();
        assertFalse(future10.isDone());
        assertFalse(future20.isDone());

        channel.advanceTimeBy(10, TimeUnit.MINUTES);
        channel.runPendingTasks();
        assertTrue(future10.isDone());
        assertFalse(future20.isDone());
    }

    @Test
    @Timeout(30) // generous timeout, just make sure we don't actually wait for the full 10 mins...
    void testFreezeTime() {
        EmbeddedChannel channel = new EmbeddedChannel();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
            }
        };

        channel.freezeTime();
        // this future will complete after 10min
        ScheduledFuture<?> future10 = channel.eventLoop().schedule(runnable, 10, TimeUnit.MINUTES);
        // this future will complete after 10min + 1ns
        ScheduledFuture<?> future101 = channel.eventLoop().schedule(runnable,
                TimeUnit.MINUTES.toNanos(10) + 1, TimeUnit.NANOSECONDS);
        // this future will complete after 20min
        ScheduledFuture<?> future20 = channel.eventLoop().schedule(runnable, 20, TimeUnit.MINUTES);

        channel.runPendingTasks();
        assertFalse(future10.isDone());
        assertFalse(future101.isDone());
        assertFalse(future20.isDone());

        channel.advanceTimeBy(10, TimeUnit.MINUTES);
        channel.runPendingTasks();
        assertTrue(future10.isDone());
        assertFalse(future101.isDone());
        assertFalse(future20.isDone());

        channel.unfreezeTime();
        channel.runPendingTasks();
        assertTrue(future101.isDone());
        assertFalse(future20.isDone());
    }

    @Test
    void testHasPendingTasks() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.freezeTime();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
            }
        };

        // simple execute
        assertFalse(channel.hasPendingTasks());
        channel.eventLoop().execute(runnable);
        assertTrue(channel.hasPendingTasks());
        channel.runPendingTasks();
        assertFalse(channel.hasPendingTasks());

        // schedule in the future (note: time is frozen above)
        channel.eventLoop().schedule(runnable, 1, TimeUnit.SECONDS);
        assertFalse(channel.hasPendingTasks());
        channel.runPendingTasks();
        assertFalse(channel.hasPendingTasks());
        channel.advanceTimeBy(1, TimeUnit.SECONDS);
        assertTrue(channel.hasPendingTasks());
        channel.runPendingTasks();
        assertFalse(channel.hasPendingTasks());
    }

    @Test
    void testReentrantClose() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            boolean runningRead;

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                runningRead = true;
                try {
                    ctx.channel().close();
                } finally {
                    runningRead = false;
                }
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
                if (runningRead) {
                    throw new IllegalStateException("Reentrant handlerRemoved");
                }
            }
        });
        channel.writeInbound("foo");
        channel.checkException();
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
