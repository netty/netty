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
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.buffer.Unpooled.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        buffer.addMessage(buf, buf.readableBytes(), channel.newPromise());
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
            buffer.addMessage(buf.copy(), buf.readableBytes(), channel.newPromise());
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
        buffer.addMessage(comp, comp.readableBytes(), channel.newPromise());

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
        buffer.addMessage(comp, comp.readableBytes(), channel.newPromise());
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
        private final ChannelConfig config = new DefaultChannelConfig(this);

        TestChannel() {
            super(Mockito.mock(EventLoop.class), null, null);
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
        protected void doDeregister(ChannelPromise promise) {
            promise.setSuccess();
        }

        @Override
        protected void doRegister(ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doBind(SocketAddress localAddress, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doDisconnect(ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doClose(ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
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

        ch.outboundBuffer().addFlush();

        // Ensure exceeding the high watermark makes channel unwritable.
        ch.write(buffer().writeZero(127));
        assertEquals("false ", buf.toString());

        // Ensure going down to the low watermark makes channel writable again by flushing the first write.
        assertTrue(ch.outboundBuffer().remove());
        assertTrue(ch.outboundBuffer().remove());
        assertEquals(127L + ChannelOutboundBuffer.CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD,
                ch.outboundBuffer().totalPendingWriteBytes());
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

        ChannelOutboundBuffer cob = ch.outboundBuffer();

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

        ChannelOutboundBuffer cob = ch.outboundBuffer();

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

        ChannelOutboundBuffer cob = ch.outboundBuffer();

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
