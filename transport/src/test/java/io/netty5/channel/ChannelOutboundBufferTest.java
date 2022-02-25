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
package io.netty5.channel;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.CompositeByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.Send;
import io.netty5.channel.ChannelOutboundBuffer.MessageProcessor;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.netty5.buffer.Unpooled.buffer;
import static io.netty5.buffer.Unpooled.compositeBuffer;
import static io.netty5.buffer.Unpooled.copiedBuffer;
import static io.netty5.buffer.Unpooled.directBuffer;
import static org.assertj.core.api.Assertions.assertThat;
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
    public void flushingEmptyBuffers() throws Exception {
        TestChannel channel = new TestChannel();
        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);
        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(0);
        buffer.addMessage(buf, 0, channel.newPromise());
        buffer.addFlush();
        AtomicInteger messageCounter = new AtomicInteger();
        MessageProcessor messageProcessor = msg -> {
            assertNotNull(msg);
            messageCounter.incrementAndGet();
            return true;
        };
        buffer.forEachFlushedMessage(messageProcessor);
        assertThat(messageCounter.get()).isOne();
        buffer.removeBytes(0); // This must remove the empty buffer.
        messageCounter.set(0);
        buffer.forEachFlushedMessage(messageProcessor);
        assertThat(messageCounter.get()).isZero();
    }

    @Test
    public void testNioBuffersSingleBackedByteBuf() {
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
                assertEquals(buffers[0], nioBuf);
            } else {
                assertNull(buffers[i]);
            }
        }
        release(buffer);
    }

    @Test
    public void testNioBuffersSingleBacked() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);
        assertEquals(0, buffer.nioBufferCount());

        Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1".getBytes(CharsetUtil.US_ASCII));
        buffer.addMessage(buf, buf.readableBytes(), channel.newPromise());
        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertNotNull(buffers);
        assertEquals(1, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        for (int i = 0;  i < buffer.nioBufferCount(); i++) {
            if (i == 0) {
                assertEquals(1, buf.countReadableComponents());
                buf.forEachReadable(0, (index, component) -> {
                    assertEquals(0, index, "Expected buffer to only have a single component.");
                    assertEquals(buffers[0], component.readableBuffer());
                    return true;
                });
            } else {
                assertNull(buffers[i]);
            }
        }
        release(buffer);
    }

    @Test
    public void testNioBuffersExpandByteBuf() {
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
    public void testNioBuffersExpand() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);

        Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1".getBytes(CharsetUtil.US_ASCII));
        for (int i = 0; i < 64; i++) {
            buffer.addMessage(buf.copy(), buf.readableBytes(), channel.newPromise());
        }
        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals(64, buffer.nioBufferCount());
        assertEquals(1, buf.countReadableComponents());
        buf.forEachReadable(0, (index, component) -> {
            assertEquals(0, index);
            ByteBuffer expected = component.readableBuffer();
            for (int i = 0;  i < buffer.nioBufferCount(); i++) {
                assertEquals(expected, buffers[i]);
            }
            return true;
        });

        release(buffer);
        buf.close();
    }

    @Test
    public void testNioBuffersExpand2ByteBuf() {
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
    public void testNioBuffersExpand2() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);

        Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1".getBytes(CharsetUtil.US_ASCII));
        var sends = Stream.generate(() -> buf.copy().send()).limit(65).toArray(Send[]::new);
        @SuppressWarnings("unchecked")
        CompositeBuffer comp = CompositeBuffer.compose(BufferAllocator.offHeapUnpooled(), sends);

        buffer.addMessage(comp, comp.readableBytes(), channel.newPromise());

        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals(65, buffer.nioBufferCount());
        assertEquals(1, buf.countReadableComponents());
        buf.forEachReadable(0, (index, component) -> {
            assertEquals(0, index);
            ByteBuffer expected = component.readableBuffer();
            for (int i = 0;  i < buffer.nioBufferCount(); i++) {
                if (i < 65) {
                    assertEquals(expected, buffers[i]);
                } else {
                    assertNull(buffers[i]);
                }
            }
            return true;
        });

        release(buffer);
        buf.close();
    }

    @Test
    public void testNioBuffersMaxCountByteBuf() {
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

    @Test
    public void testNioBuffersMaxCount() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);

        Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1".getBytes(CharsetUtil.US_ASCII));
        assertEquals(4, buf.readableBytes());
        var sends = Stream.generate(() -> buf.copy().send()).limit(65).toArray(Send[]::new);
        @SuppressWarnings("unchecked")
        CompositeBuffer comp = CompositeBuffer.compose(BufferAllocator.offHeapUnpooled(), sends);

        assertEquals(65, comp.countComponents());
        buffer.addMessage(comp, comp.readableBytes(), channel.newPromise());
        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        final int maxCount = 10;    // less than comp.nioBufferCount()
        ByteBuffer[] buffers = buffer.nioBuffers(maxCount, Integer.MAX_VALUE);
        assertTrue(buffer.nioBufferCount() <= maxCount, "Should not be greater than maxCount");
        buf.forEachReadable(0, (index, component) -> {
            assertEquals(0, index);
            ByteBuffer expected = component.readableBuffer();
            for (int i = 0;  i < buffer.nioBufferCount(); i++) {
                assertEquals(expected, buffers[i]);
            }
            return true;
        });
        release(buffer);
        buf.close();
    }

    @Test
    public void removeBytesByteBuf() {
        TestChannel channel = new TestChannel();
        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);
        ByteBuf buf = directBuffer().writeBytes("buf1".getBytes(CharsetUtil.US_ASCII));
        int size = buf.readableBytes();
        buffer.addMessage(buf, size, channel.newPromise());
        buffer.addFlush();
        assertEquals(0, buffer.currentProgress());
        buffer.removeBytes(size / 2);
        assertEquals(size / 2, buffer.currentProgress());
        assertThat(buffer.current()).isNotNull();
        buffer.removeBytes(size);
        assertNull(buffer.current());
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.currentProgress());
    }

    @Test
    public void removeBytes() {
        TestChannel channel = new TestChannel();
        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel);
        Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1".getBytes(CharsetUtil.US_ASCII));
        int size = buf.readableBytes();
        buffer.addMessage(buf, size, channel.newPromise());
        buffer.addFlush();
        assertEquals(0, buffer.currentProgress());
        buffer.removeBytes(size / 2);
        assertEquals(size / 2, buffer.currentProgress());
        assertThat(buffer.current()).isNotNull();
        buffer.removeBytes(size);
        assertNull(buffer.current());
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.currentProgress());
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
            super(null, new SingleThreadEventLoop(Executors.defaultThreadFactory(),
                    new IoHandler() {
                @Override
                public int run(IoExecutionContext runner) {
                    return 0;
                }

                @Override
                public void wakeup(boolean inEventLoop) {
                    // NOOP
                }

                @Override
                public void destroy() {
                    // NOOP
                }

                @Override
                public void register(Channel channel) {
                    // NOOP
                }

                @Override
                public void prepareToDestroy() {
                    // NOOP
                }

                @Override
                public void deregister(Channel channel) {
                    // NOOP
                }
            }));
        }

        @Override
        protected AbstractUnsafe newUnsafe() {
            return new TestUnsafe();
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
        protected void doBind(SocketAddress localAddress) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doDisconnect() throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doClose() throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doBeginRead() throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) throws Exception {
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
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Test
    public void testWritability() {
        final StringBuilder buf = new StringBuilder();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                buf.append(ctx.channel().isWritable());
                buf.append(' ');
            }
        });

        ch.config().setWriteBufferLowWaterMark(128 + ChannelOutboundBuffer.CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD);
        ch.config().setWriteBufferHighWaterMark(256 + ChannelOutboundBuffer.CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD);

        ch.write(buffer().writeZero(128));
        // Ensure exceeding the low watermark does not make channel unwritable.
        ch.write(buffer().writeZero(2));
        assertThat(buf.toString()).isEmpty();

        ch.unsafe().outboundBuffer().addFlush();

        // Ensure exceeding the high watermark makes channel unwritable.
        ch.write(buffer().writeZero(127));
        assertThat(buf.toString()).isEqualTo("false ");

        // Ensure going down to the low watermark makes channel writable again by flushing the first write.
        assertTrue(ch.unsafe().outboundBuffer().remove());
        assertTrue(ch.unsafe().outboundBuffer().remove());
        assertThat(ch.unsafe().outboundBuffer().totalPendingWriteBytes()).isEqualTo(
                127L + ChannelOutboundBuffer.CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD);
        assertThat(buf.toString()).isEqualTo("false true ");

        safeClose(ch);
    }

    @Test
    public void testUserDefinedWritability() {
        final StringBuilder buf = new StringBuilder();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
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
        assertThat(buf.toString()).isEqualTo("false ");

        // Ensure that setting a user-defined writability flag to true affects channel.isWritable();
        cob.setUserDefinedWritability(1, true);
        ch.runPendingTasks();
        assertThat(buf.toString()).isEqualTo("false true ");

        safeClose(ch);
    }

    @Test
    public void testUserDefinedWritability2() {
        final StringBuilder buf = new StringBuilder();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
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
        assertThat(buf.toString()).isEqualTo("false ");

        // Ensure that setting another user-defined writability flag to false does not trigger
        // channelWritabilityChanged.
        cob.setUserDefinedWritability(2, false);
        ch.runPendingTasks();
        assertThat(buf.toString()).isEqualTo("false ");

        // Ensure that setting only one user-defined writability flag to true does not affect channel.isWritable()
        cob.setUserDefinedWritability(1, true);
        ch.runPendingTasks();
        assertThat(buf.toString()).isEqualTo("false ");

        // Ensure that setting all user-defined writability flags to true affects channel.isWritable()
        cob.setUserDefinedWritability(2, true);
        ch.runPendingTasks();
        assertThat(buf.toString()).isEqualTo("false true ");

        safeClose(ch);
    }

    @Test
    public void testMixedWritability() {
        final StringBuilder buf = new StringBuilder();
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                buf.append(ctx.channel().isWritable());
                buf.append(' ');
            }
        });

        ch.config().setWriteBufferLowWaterMark(128);
        ch.config().setWriteBufferHighWaterMark(256);

        ChannelOutboundBuffer cob = ch.unsafe().outboundBuffer();

        ch.executor().execute(() -> {
            // Trigger channelWritabilityChanged() by writing a lot.
            ch.write(buffer().writeZero(257));
            assertThat(buf.toString()).isEqualTo("false ");

            // Ensure that setting a user-defined writability flag to false does not trigger channelWritabilityChanged()
            cob.setUserDefinedWritability(1, false);
            ch.runPendingTasks();
            assertThat(buf.toString()).isEqualTo("false ");

            // Ensure reducing the totalPendingWriteBytes down to zero does not trigger channelWritabilityChanged()
            // because of the user-defined writability flag.
            ch.flush();
            assertThat(cob.totalPendingWriteBytes()).isEqualTo(0L);
            assertThat(buf.toString()).isEqualTo("false ");

            // Ensure that setting the user-defined writability flag to true triggers channelWritabilityChanged()
            cob.setUserDefinedWritability(1, true);
            ch.runPendingTasks();
            assertThat(buf.toString()).isEqualTo("false true ");
        });

        safeClose(ch);
    }

    private static void safeClose(EmbeddedChannel ch) {
        ch.finish();
        Object m;
        do {
            m = ch.readOutbound();
            if (m instanceof ByteBuf) {
                ((ByteBuf) m).release();
            }
            if (m instanceof Buffer) {
                ((Buffer) m).close();
            }
        } while (m != null);
    }
}
