/*
 * Copyright 2022 The Netty Project
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
package io.netty5.channel.socket.nio;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;

import io.netty5.channel.WriteHandleFactory;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.netty5.util.concurrent.ImmediateEventExecutor.INSTANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ByteBufferCollectorTest {

    @Test
    public void testEmptyNioBuffers() {
        ByteBufferCollector collector = newCollector();
        ChannelOutboundBuffer buffer = newOutboundBuffer();
        assertEmpty(collector, buffer);
    }

    private static void assertEmpty(ByteBufferCollector collector, ChannelOutboundBuffer buffer) {
        ByteBuffer[] buffers = collector.collect(buffer, Integer.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(0, collector.nioBufferCount());
        assertNotNull(buffers);
        for (ByteBuffer b : buffers) {
            assertNull(b);
        }
        assertEquals(0, collector.nioBufferSize());
    }

    @Test
    public void testNioBuffersSingleBacked() {
        ByteBufferCollector collector = newCollector();
        ChannelOutboundBuffer buffer = newOutboundBuffer();
        Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII);
        buffer.addMessage(buf, buf.readableBytes(), ImmediateEventExecutor.INSTANCE.newPromise());

        // Still empty as not flushed.
        assertEmpty(collector, buffer);

        buffer.addFlush();
        ByteBuffer[] buffers = collector.collect(buffer, Integer.MAX_VALUE, Long.MAX_VALUE);
        assertNotNull(buffers);
        assertEquals(1, collector.nioBufferCount(), "Should still be 0 as not flushed yet");
        for (int i = 0; i < collector.nioBufferCount(); i++) {
            if (i == 0) {
                assertEquals(1, buf.countReadableComponents());
                try (var iteration = buf.forEachComponent()) {
                    var component = iteration.firstReadable();
                    assertEquals(buffers[0], component.readableBuffer());
                    assertNull(component.nextReadable(), "Expected buffer to only have a single component.");
                }
            } else {
                assertNull(buffers[i]);
            }
        }
    }

    @Test
    public void testNioBuffersExpand() {
        ByteBufferCollector collector = newCollector();
        ChannelOutboundBuffer buffer = newOutboundBuffer();

        try (Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII)) {
            for (int i = 0; i < 64; i++) {
                buffer.addMessage(buf.copy(), buf.readableBytes(), ImmediateEventExecutor.INSTANCE.newPromise());
            }
            // Still empty as not flushed.
            assertEmpty(collector, buffer);

            buffer.addFlush();
            ByteBuffer[] buffers = collector.collect(buffer, Integer.MAX_VALUE, Long.MAX_VALUE);
            assertEquals(64, collector.nioBufferCount());
            assertEquals(1, buf.countReadableComponents());
            try (var iteration = buf.forEachComponent()) {
                var component = iteration.firstReadable();
                ByteBuffer expected = component.readableBuffer();
                for (int i = 0; i < collector.nioBufferCount(); i++) {
                    assertEquals(expected, buffers[i]);
                }
                assertNull(component.nextReadable());
            }
        }
    }

    @Test
    public void testNioBuffersExpand2() {
        ByteBufferCollector collector = newCollector();
        ChannelOutboundBuffer buffer = newOutboundBuffer();

        try (Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII)) {
            var sends = Stream.generate(() -> buf.copy().send()).limit(65).collect(Collectors.toList());
            CompositeBuffer comp = BufferAllocator.offHeapUnpooled().compose(sends);

            buffer.addMessage(comp, comp.readableBytes(), ImmediateEventExecutor.INSTANCE.newPromise());

            // Still empty as not flushed.
            assertEmpty(collector, buffer);

            buffer.addFlush();
            ByteBuffer[] buffers = collector.collect(buffer, Integer.MAX_VALUE, Long.MAX_VALUE);
            assertEquals(65, collector.nioBufferCount());
            assertEquals(1, buf.countReadableComponents());
            try (var iteration = buf.forEachComponent()) {
                var component = iteration.firstReadable();
                ByteBuffer expected = component.readableBuffer();
                for (int i = 0; i < collector.nioBufferCount(); i++) {
                    if (i < 65) {
                        assertEquals(expected, buffers[i]);
                    } else {
                        assertNull(buffers[i]);
                    }
                }
                assertNull(component.nextReadable());
            }
        }
    }

    @Test
    public void testNioBuffersMaxCount() {
        ByteBufferCollector collector = newCollector();
        ChannelOutboundBuffer buffer = newOutboundBuffer();

        try (Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII)) {
            assertEquals(4, buf.readableBytes());
            var sends = Stream.generate(() -> buf.copy().send()).limit(65).collect(Collectors.toList());
            CompositeBuffer comp = BufferAllocator.offHeapUnpooled().compose(sends);

            assertEquals(65, comp.countComponents());
            buffer.addMessage(comp, comp.readableBytes(), ImmediateEventExecutor.INSTANCE.newPromise());

            // Still empty as not flushed.
            assertEmpty(collector, buffer);

            buffer.addFlush();
            final int maxCount = 10;    // less than comp.nioBufferCount()
            ByteBuffer[] buffers = collector.collect(buffer, maxCount, Integer.MAX_VALUE);
            assertTrue(collector.nioBufferCount() <= maxCount, "Should not be greater than maxCount");
            try (var iteration = buf.forEachComponent()) {
                var component = iteration.firstReadable();
                ByteBuffer expected = component.readableBuffer();
                for (int i = 0; i < collector.nioBufferCount(); i++) {
                    assertEquals(expected, buffers[i]);
                }
                assertNull(component.nextReadable());
            }
        }
    }

    private static ByteBufferCollector newCollector() {
        ByteBufferCollector collector = new ByteBufferCollector();
        collector.reset();
        return collector;
    }

    private static ChannelOutboundBuffer newOutboundBuffer() {
        EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);
        when(eventLoop.newPromise()).thenReturn(INSTANCE.newPromise());
        when(eventLoop.isCompatible(any())).thenReturn(true);

        return new TestChannel(eventLoop).outbound();
    }

    private static class TestChannel extends AbstractChannel<Channel, SocketAddress, SocketAddress> {

        TestChannel(EventLoop eventLoop) {
            super(null, eventLoop, false);
        }

        ChannelOutboundBuffer outbound() {
            return outboundBuffer();
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
        protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected SocketAddress localAddress0() {
            return null;
        }

        @Override
        protected SocketAddress remoteAddress0() {
            return null;
        }

        @Override
        protected void doBind(SocketAddress localAddress) { }

        @Override
        protected void doDisconnect() { }

        @Override
        protected void doClose() { }

        @Override
        protected void doRead(boolean wasReadPendingAlready) { }

        @Override
        protected boolean doReadNow(ReadSink readSink) {
            return false;
        }

        @Override
        protected void doWrite(ChannelOutboundBuffer in, WriteHandleFactory.WriteHandle writeHandle) throws Exception {
            // NOOP
        }

        @Override
        protected void doShutdown(ChannelShutdownDirection direction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isShutdown(ChannelShutdownDirection direction) {
            return !isActive();
        }
    }
}
