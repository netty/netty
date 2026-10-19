/*
 * Copyright 2012 The Netty Project
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.Unpooled.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChannelOutboundBufferTest {

    @Test
    public void testEmptyNioBuffers() {
        TestChannel channel = new TestChannel();
        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);
        assertEquals(0, buffer.nioBufferCount());
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertNotNull(buffers);
        for (ByteBuffer b: buffers) {
            assertNull(b);
        }
        assertEquals(0, buffer.nioBufferCount());
        release(buffer);
    }

    @Test
    public void testNioBuffersCancelledRemoveBytes() {
        TestChannel channel = new TestChannel();
        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);
        ByteBuf b1 = wrappedBuffer(new byte[] { 0 });
        int r1 = b1.readableBytes();
        ChannelPromise p1 = channel.newPromise();
        buffer.addMessage(b1, r1, p1);

        ByteBuf b2 = wrappedBuffer(new byte[] { 0, 1 });
        int r2 = b2.readableBytes();
        ChannelPromise p2 = channel.newPromise();
        buffer.addMessage(b2, r2, p2);
        p2.cancel(false);

        ByteBuf b3 = wrappedBuffer(new byte[] { 0 });
        int r3 = b3.readableBytes();
        ChannelPromise p3 = channel.newPromise();
        buffer.addMessage(b3, r3, p3);
        buffer.addFlush();

        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals(2, buffer.nioBufferCount());
        assertNotNull(buffers);
        assertEquals(r1, buffers[0].remaining());
        assertEquals(r3, buffers[1].remaining());

        buffer.removeBytes(r1 + r3);
        assertEquals(0, b1.refCnt());
        assertEquals(0, b2.refCnt());
        assertEquals(0, b3.refCnt());

        assertTrue(buffer.isEmpty());
        release(buffer);
    }

    @Test
    public void testNioBuffersSingleBacked() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);
        assertEquals(0, buffer.nioBufferCount());

        ByteBuf buf = copiedBuffer("buf1", CharsetUtil.US_ASCII);
        ByteBuffer nioBuf = buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes());
        buffer.addMessage(buf, buf.readableBytes(), channel.voidPromise());
        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertNotNull(buffers);
        assertEquals(1, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        for (int i = 0;  i < buffer.nioBufferCount(); i++) {
            if (i == 0) {
                assertEquals(buffers[i], nioBuf);
            } else {
                assertNull(buffers[i]);
            }
        }
        release(buffer);
    }

    @Test
    public void testNioBuffersExpand() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);

        ByteBuf buf = directBuffer().writeBytes("buf1".getBytes(CharsetUtil.US_ASCII));
        for (int i = 0; i < 64; i++) {
            buffer.addMessage(buf.copy(), buf.readableBytes(), channel.voidPromise());
        }
        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals(64, buffer.nioBufferCount());
        for (int i = 0;  i < buffer.nioBufferCount(); i++) {
            assertEquals(buffers[i], buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()));
        }
        release(buffer);
        buf.release();
    }

    @Test
    public void testNioBuffersExpand2() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);

        CompositeByteBuf comp = compositeBuffer(256);
        ByteBuf buf = directBuffer().writeBytes("buf1".getBytes(CharsetUtil.US_ASCII));
        for (int i = 0; i < 65; i++) {
            comp.addComponent(true, buf.copy());
        }
        buffer.addMessage(comp, comp.readableBytes(), channel.voidPromise());

        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals(65, buffer.nioBufferCount());
        for (int i = 0;  i < buffer.nioBufferCount(); i++) {
            if (i < 65) {
                assertEquals(buffers[i], buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()));
            } else {
                assertNull(buffers[i]);
            }
        }
        release(buffer);
        buf.release();
    }

    @Test
    public void testNioBuffersMaxCount() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);

        CompositeByteBuf comp = compositeBuffer(256);
        ByteBuf buf = directBuffer().writeBytes("buf1".getBytes(CharsetUtil.US_ASCII));
        for (int i = 0; i < 65; i++) {
            comp.addComponent(true, buf.copy());
        }
        assertEquals(65, comp.nioBufferCount());
        buffer.addMessage(comp, comp.readableBytes(), channel.voidPromise());
        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        final int maxCount = 10;    // less than comp.nioBufferCount()
        ByteBuffer[] buffers = buffer.nioBuffers(maxCount, Integer.MAX_VALUE);
        assertTrue(buffer.nioBufferCount() <= maxCount, "Should not be greater than maxCount");
        for (int i = 0;  i < buffer.nioBufferCount(); i++) {
            assertEquals(buffers[i], buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()));
        }
        release(buffer);
        buf.release();
    }

    private static void release(ChannelOutboundBuffer buffer) {
        for (;;) {
            if (!buffer.remove()) {
                break;
            }
        }
    }

    private static final class TestChannel extends AbstractChannel {
        private static final ChannelMetadata TEST_METADATA = new ChannelMetadata(false);
        private final ChannelConfig config = new DefaultChannelConfig(this);

        TestChannel() {
            super(null);
        }

        @Override
        protected AbstractUnsafe newUnsafe() {
            return new TestUnsafe();
        }

        @Override
        protected boolean isCompatible(EventLoop loop) {
            return false;
        }

        @Override
        protected SocketAddress localAddress0() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected SocketAddress remoteAddress0() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doBind(SocketAddress localAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doDisconnect() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doClose() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doBeginRead() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelConfig config() {
            return config;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public ChannelMetadata metadata() {
            return TEST_METADATA;
        }

        final class TestUnsafe extends AbstractUnsafe {
            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Test
    public void testWritability() {
        final StringBuilder buf = new StringBuilder();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                buf.append(ctx.channel().isWritable());
                buf.append(' ');
            }
        });

        ch.config().setWriteBufferLowWaterMark(128 + ChannelOutboundBuffer.CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD);
        ch.config().setWriteBufferHighWaterMark(256 + ChannelOutboundBuffer.CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD);

        ch.write(buffer().writeZero(128));
        // Ensure exceeding the low watermark does not make channel unwritable.
        ch.write(buffer().writeZero(2));
        assertEquals("", buf.toString());

        ch.unsafe().outboundBuffer().addFlush();

        // Ensure exceeding the high watermark makes channel unwritable.
        ch.write(buffer().writeZero(127));
        assertEquals("false ", buf.toString());

        // Ensure going down to the low watermark makes channel writable again by flushing the first write.
        assertTrue(ch.unsafe().outboundBuffer().remove());
        assertTrue(ch.unsafe().outboundBuffer().remove());
        assertEquals(127L + ChannelOutboundBuffer.CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD,
                ch.unsafe().outboundBuffer().totalPendingWriteBytes());
        assertEquals("false true ", buf.toString());

        safeClose(ch);
    }

    @Test
    public void testUserDefinedWritability() {
        final StringBuilder buf = new StringBuilder();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                buf.append(ctx.channel().isWritable());
                buf.append(' ');
            }
        });

        ch.config().setWriteBufferLowWaterMark(128);
        ch.config().setWriteBufferHighWaterMark(256);

        ChannelOutboundBuffer cob = ch.unsafe().outboundBuffer();

        // Ensure that the default value of a user-defined writability flag is true.
        for (int i = 1; i <= 30; i ++) {
            assertTrue(cob.getUserDefinedWritability(i));
        }

        // Ensure that setting a user-defined writability flag to false affects channel.isWritable();
        cob.setUserDefinedWritability(1, false);
        ch.runPendingTasks();
        assertEquals("false ", buf.toString());

        // Ensure that setting a user-defined writability flag to true affects channel.isWritable();
        cob.setUserDefinedWritability(1, true);
        ch.runPendingTasks();
        assertEquals("false true ", buf.toString());

        safeClose(ch);
    }

    @Test
    public void testUserDefinedWritability2() {
        final StringBuilder buf = new StringBuilder();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                buf.append(ctx.channel().isWritable());
                buf.append(' ');
            }
        });

        ch.config().setWriteBufferLowWaterMark(128);
        ch.config().setWriteBufferHighWaterMark(256);

        ChannelOutboundBuffer cob = ch.unsafe().outboundBuffer();

        // Ensure that setting a user-defined writability flag to false affects channel.isWritable()
        cob.setUserDefinedWritability(1, false);
        ch.runPendingTasks();
        assertEquals("false ", buf.toString());

        // Ensure that setting another user-defined writability flag to false does not trigger
        // channelWritabilityChanged.
        cob.setUserDefinedWritability(2, false);
        ch.runPendingTasks();
        assertEquals("false ", buf.toString());

        // Ensure that setting only one user-defined writability flag to true does not affect channel.isWritable()
        cob.setUserDefinedWritability(1, true);
        ch.runPendingTasks();
        assertEquals("false ", buf.toString());

        // Ensure that setting all user-defined writability flags to true affects channel.isWritable()
        cob.setUserDefinedWritability(2, true);
        ch.runPendingTasks();
        assertEquals("false true ", buf.toString());

        safeClose(ch);
    }

    @Test
    public void testMixedWritability() {
        final StringBuilder buf = new StringBuilder();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                buf.append(ctx.channel().isWritable());
                buf.append(' ');
            }
        });

        ch.config().setWriteBufferLowWaterMark(128);
        ch.config().setWriteBufferHighWaterMark(256);

        ChannelOutboundBuffer cob = ch.unsafe().outboundBuffer();

        // Trigger channelWritabilityChanged() by writing a lot.
        ch.write(buffer().writeZero(257));
        assertEquals("false ", buf.toString());

        // Ensure that setting a user-defined writability flag to false does not trigger channelWritabilityChanged()
        cob.setUserDefinedWritability(1, false);
        ch.runPendingTasks();
        assertEquals("false ", buf.toString());

        // Ensure reducing the totalPendingWriteBytes down to zero does not trigger channelWritabilityChanged()
        // because of the user-defined writability flag.
        ch.flush();
        assertEquals(0L, cob.totalPendingWriteBytes());
        assertEquals("false ", buf.toString());

        // Ensure that setting the user-defined writability flag to true triggers channelWritabilityChanged()
        cob.setUserDefinedWritability(1, true);
        ch.runPendingTasks();
        assertEquals("false true ", buf.toString());

        safeClose(ch);
    }

    @Test
    public void testWriteAndFlushFromWritabilityChangedCausedByCancelledWrite() {
        final List<ChannelFuture> reentrantWrites = new ArrayList<ChannelFuture>();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                if (ctx.channel().isWritable() && reentrantWrites.isEmpty()) {
                    reentrantWrites.add(ctx.writeAndFlush(wrappedBuffer(new byte[] { 2 })));
                }
                ctx.fireChannelWritabilityChanged();
            }
        });
        ch.config().setWriteBufferWaterMark(new WriteBufferWaterMark(256, 512));

        ByteBuf cancelledBuf = buffer().writeZero(512);
        ChannelFuture cancelled = ch.write(cancelledBuf);
        ChannelFuture second = ch.write(wrappedBuffer(new byte[] { 1 }));
        assertFalse(ch.isWritable());
        assertTrue(cancelled.cancel(false));

        // Releasing the cancelled write makes the channel writable while addFlush() runs.
        ch.flush();

        assertTrue(ch.isWritable());
        assertEquals(1, reentrantWrites.size());
        assertEquals(0, cancelledBuf.refCnt());
        assertTrue(second.isSuccess());
        assertTrue(reentrantWrites.get(0).isSuccess());
        ChannelOutboundBuffer buffer = ch.unsafe().outboundBuffer();
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
        assertEquals(0, buffer.totalPendingWriteBytes());
        // The cancelled write is replaced by an empty buffer.
        assertOutbound(ch, 0);
        assertOutbound(ch, 1);
        assertOutbound(ch, 2);
        assertNull(ch.readOutbound());

        assertFalse(ch.finish());
        assertFalse(ch.isOpen());
    }

    @Test
    public void testCloseFromWritabilityChangedCausedByCancelledWrite() {
        final AtomicInteger inactive = new AtomicInteger();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                if (ctx.channel().isWritable()) {
                    ctx.close();
                }
                ctx.fireChannelWritabilityChanged();
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                inactive.incrementAndGet();
                ctx.fireChannelInactive();
            }
        });
        ch.config().setWriteBufferWaterMark(new WriteBufferWaterMark(256, 512));

        ByteBuf cancelledBuf = buffer().writeZero(512);
        ChannelFuture cancelled = ch.write(cancelledBuf);
        ByteBuf second = wrappedBuffer(new byte[] { 1 });
        ChannelFuture secondFuture = ch.write(second);
        ByteBuf third = wrappedBuffer(new byte[] { 2 });
        ChannelFuture thirdFuture = ch.write(third);
        assertFalse(ch.isWritable());
        assertTrue(cancelled.cancel(false));

        // Releasing the cancelled write makes the channel writable while addFlush() runs.
        ch.flush();
        ch.runPendingTasks();

        assertFalse(ch.isOpen());
        assertEquals(1, inactive.get());
        assertEquals(0, cancelledBuf.refCnt());
        assertInstanceOf(ClosedChannelException.class, secondFuture.cause());
        assertInstanceOf(ClosedChannelException.class, thirdFuture.cause());
        assertEquals(0, second.refCnt());
        assertEquals(0, third.refCnt());
        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    private static void assertOutbound(EmbeddedChannel ch, int expected) {
        ByteBuf buf = ch.readOutbound();
        assertNotNull(buf);
        try {
            if (expected == 0) {
                assertEquals(0, buf.readableBytes());
            } else {
                assertEquals(1, buf.readableBytes());
                assertEquals(expected, buf.readByte());
            }
        } finally {
            buf.release();
        }
    }

    @Test
    public void testMultipleCancelledWritesWithEarlierFlush() {
        final AtomicInteger writable = new AtomicInteger();
        final AtomicInteger sizeAtEvent = new AtomicInteger(-1);
        final AtomicLong pendingAtEvent = new AtomicLong(-1);
        final AtomicReference<Object> currentAtEvent = new AtomicReference<Object>();
        EmbeddedChannel ch = new EmbeddedChannel();
        final ChannelOutboundBuffer buffer = ch.unsafe().outboundBuffer();
        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                if (ctx.channel().isWritable()) {
                    writable.incrementAndGet();
                    sizeAtEvent.set(buffer.size());
                    pendingAtEvent.set(buffer.totalPendingWriteBytes());
                    currentAtEvent.set(buffer.current());
                }
                ctx.fireChannelWritabilityChanged();
            }
        });
        ch.config().setWriteBufferWaterMark(new WriteBufferWaterMark(400, 800));

        // Use the ChannelOutboundBuffer directly so nothing is written and flushedEntry stays set after the flush.
        ByteBuf live0 = wrappedBuffer(new byte[] { 0 });
        buffer.addMessage(live0, 1, ch.newPromise());
        buffer.addFlush();
        assertEquals(1, buffer.size());
        assertSame(live0, buffer.current());

        // Cancelled writes at the head, in the middle and at the tail of the second batch.
        ByteBuf[] cancelledBufs = new ByteBuf[3];
        ByteBuf[] liveBufs = new ByteBuf[2];
        ChannelPromise[] cancelledPromises = new ChannelPromise[3];
        for (int i = 0; i < 3; i++) {
            cancelledBufs[i] = buffer().writeZero(300);
            cancelledPromises[i] = ch.newPromise();
            buffer.addMessage(cancelledBufs[i], 300, cancelledPromises[i]);
            if (i < 2) {
                liveBufs[i] = wrappedBuffer(new byte[] { (byte) (i + 1) });
                buffer.addMessage(liveBufs[i], 1, ch.newPromise());
            }
        }
        assertFalse(ch.isWritable());
        for (ChannelPromise promise : cancelledPromises) {
            assertTrue(promise.cancel(false));
        }

        buffer.addFlush();

        assertEquals(1, writable.get());
        // The buffer must already be consistent when the event is fired.
        assertEquals(6, sizeAtEvent.get());
        assertEquals(buffer.totalPendingWriteBytes(), pendingAtEvent.get());
        assertSame(live0, currentAtEvent.get());
        assertEquals(6, buffer.size());
        assertTrue(ch.isWritable());
        for (ByteBuf cancelledBuf : cancelledBufs) {
            assertEquals(0, cancelledBuf.refCnt());
        }
        assertEquals(1, live0.refCnt());
        for (ByteBuf liveBuf : liveBufs) {
            assertEquals(1, liveBuf.refCnt());
        }

        ch.close();
        assertEquals(0, live0.refCnt());
        for (ByteBuf liveBuf : liveBufs) {
            assertEquals(0, liveBuf.refCnt());
        }
        assertEquals(0, buffer.totalPendingWriteBytes());
    }

    @Test
    public void testWriteAndFlushFromReleaseOfCancelledWrite() {
        final EmbeddedChannel ch = new EmbeddedChannel();
        final List<ChannelFuture> reentrantWrites = new ArrayList<ChannelFuture>();
        ReferenceCounted cancelledMsg = new AbstractReferenceCounted() {
            @Override
            protected void deallocate() {
                // Release of a cancelled write runs user code which writes and flushes on the same channel.
                reentrantWrites.add(ch.writeAndFlush(wrappedBuffer(new byte[] { 2 })));
            }

            @Override
            public ReferenceCounted touch(Object hint) {
                return this;
            }
        };

        ChannelFuture cancelled = ch.write(cancelledMsg);
        ChannelFuture second = ch.write(wrappedBuffer(new byte[] { 1 }));
        assertTrue(cancelled.cancel(false));

        ch.flush();

        assertEquals(1, reentrantWrites.size());
        assertEquals(0, cancelledMsg.refCnt());
        assertTrue(second.isSuccess());
        assertTrue(reentrantWrites.get(0).isSuccess());
        ChannelOutboundBuffer buffer = ch.unsafe().outboundBuffer();
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
        assertEquals(0, buffer.totalPendingWriteBytes());
        // The cancelled write is replaced by an empty buffer.
        assertOutbound(ch, 0);
        assertOutbound(ch, 1);
        assertOutbound(ch, 2);
        assertNull(ch.readOutbound());

        assertFalse(ch.finish());
        assertFalse(ch.isOpen());
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testWriteTaskRejected() throws Exception {
        final SingleThreadEventExecutor executor = new SingleThreadEventExecutor(
                null, new DefaultThreadFactory("executorPool"),
                true, 1, RejectedExecutionHandlers.reject()) {
            @Override
            protected void run() {
                do {
                    Runnable task = takeTask();
                    if (task != null) {
                        task.run();
                        updateLastExecutionTime();
                    }
                } while (!confirmShutdown());
            }

            @Override
            protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
                return super.newTaskQueue(1);
            }
        };
        final CountDownLatch handlerAddedLatch = new CountDownLatch(1);
        final CountDownLatch handlerRemovedLatch = new CountDownLatch(1);
        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(executor, "handler", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                promise.setFailure(new AssertionError("Should not be called"));
            }

            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
                handlerAddedLatch.countDown();
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
                handlerRemovedLatch.countDown();
            }
        });

        // Lets wait until we are sure the handler was added.
        handlerAddedLatch.await();

        final CountDownLatch executeLatch = new CountDownLatch(1);
        final CountDownLatch runLatch = new CountDownLatch(1);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runLatch.countDown();
                    executeLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        runLatch.await();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                // Will not be executed but ensure the pending count is 1.
            }
        });

        assertEquals(1, executor.pendingTasks());
        assertEquals(0, ch.unsafe().outboundBuffer().totalPendingWriteBytes());

        ByteBuf buffer = buffer(128).writeZero(128);
        ChannelFuture future = ch.write(buffer);
        ch.runPendingTasks();

        assertInstanceOf(RejectedExecutionException.class, future.cause());
        assertEquals(0, buffer.refCnt());

        // In case of rejected task we should not have anything pending.
        assertEquals(0, ch.unsafe().outboundBuffer().totalPendingWriteBytes());
        executeLatch.countDown();

        while (executor.pendingTasks() != 0) {
            // Wait until there is no more pending task left.
            Thread.sleep(10);
        }

        ch.pipeline().remove("handler");

        // Ensure we do not try to shutdown the executor before we handled everything for the Channel. Otherwise
        // the Executor may reject when the Channel tries to add a task to it.
        handlerRemovedLatch.await();

        safeClose(ch);

        executor.shutdownGracefully();
    }

    private static void safeClose(EmbeddedChannel ch) {
        ch.finish();
        for (;;) {
            ByteBuf m = ch.readOutbound();
            if (m == null) {
                break;
            }
            m.release();
        }
    }
}
