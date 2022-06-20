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

import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.util.CharsetUtil;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteBufferCollectorTest {
    private static final class TestChannelOutboundBuffer implements ChannelOutboundBuffer {
        private static final class Entry {
            private final Object msg;
            private final Promise<Void> promise;
            boolean flushed;

            Entry(Object msg, Promise<Void> promise) {
                this.msg = msg;
                this.promise = promise;
            }
        }

        private final Queue<Entry> messages = new ArrayDeque<>();

        @Override
        public void addMessage(Object msg, int size, Promise<Void> promise) {
            messages.add(new Entry(msg, promise));
        }

        @Override
        public void addFlush() {
            for (Entry entry : messages) {
                entry.flushed = true;
            }
        }

        @Override
        public Object current() {
            Entry entry = messages.peek();
            if (entry == null || entry.flushed) {
                return null;
            }
            return entry.msg;
        }

        @Override
        public long currentProgress() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void progress(long amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove() {
            Entry entry = messages.peek();
            if (entry == null || entry.flushed) {
                return false;
            }
            messages.remove();

            entry.promise.setSuccess(null);
            Resource.dispose(entry.msg);
            return true;
        }

        @Override
        public boolean remove(Throwable cause) {
            Entry entry = messages.peek();
            if (entry == null || entry.flushed) {
                return false;
            }
            messages.remove();

            entry.promise.setFailure(cause);
            Resource.dispose(entry.msg);
            return true;
        }

        @Override
        public void removeBytes(long writtenBytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWritable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getUserDefinedWritability(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setUserDefinedWritability(int index, boolean writable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            int flushed = 0;
            for (Entry entry: messages) {
                if (!entry.flushed) {
                    break;
                }
                flushed++;
            }
            return flushed;
        }

        @Override
        public long totalPendingWriteBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long bytesBeforeUnwritable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long bytesBeforeWritable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEachFlushedMessage(MessageProcessor processor) throws Exception {
            for (Entry entry: messages) {
                if (!entry.flushed) {
                    break;
                }
                if (!processor.processMessage(entry.msg)) {
                    break;
                }
            }
        }
    }
    @Test
    void testEmptyNioBuffers() throws Exception {
        ChannelOutboundBuffer buffer = new TestChannelOutboundBuffer();
        ByteBufferCollector collector = new ByteBufferCollector();
        assertEquals(0, collector.nioBufferCount());
        ByteBuffer[] buffers = collector.nioBuffers(buffer, 1024, Integer.MAX_VALUE);
        assertNotNull(buffers);
        for (ByteBuffer b: buffers) {
            assertNull(b);
        }
        assertEquals(0, collector.nioBufferCount());
        release(buffer);
    }

    @Test
    public void testNioBuffersSingleBacked() throws Exception {
        ChannelOutboundBuffer buffer = new TestChannelOutboundBuffer();
        ByteBufferCollector collector = new ByteBufferCollector();
        assertEquals(0, collector.nioBufferCount());

        Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1", CharsetUtil.US_ASCII);
        buffer.addMessage(buf, buf.readableBytes(), ImmediateEventExecutor.INSTANCE.newPromise());
        assertEquals(0, collector.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = collector.nioBuffers(buffer, 1024, Integer.MAX_VALUE);
        assertNotNull(buffers);
        assertEquals(1, collector.nioBufferCount(), "Should still be 0 as not flushed yet");
        for (int i = 0;  i < collector.nioBufferCount(); i++) {
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
    public void testNioBuffersExpand() throws Exception {
        ChannelOutboundBuffer buffer = new TestChannelOutboundBuffer();
        ByteBufferCollector collector = new ByteBufferCollector();
        Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1", CharsetUtil.US_ASCII);
        for (int i = 0; i < 64; i++) {
            buffer.addMessage(buf.copy(), buf.readableBytes(), ImmediateEventExecutor.INSTANCE.newPromise());
        }
        assertEquals(0, collector.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = collector.nioBuffers(buffer, 1024, Integer.MAX_VALUE);
        assertEquals(64, collector.nioBufferCount());
        assertEquals(1, buf.countReadableComponents());
        buf.forEachReadable(0, (index, component) -> {
            assertEquals(0, index);
            ByteBuffer expected = component.readableBuffer();
            for (int i = 0;  i < collector.nioBufferCount(); i++) {
                assertEquals(expected, buffers[i]);
            }
            return true;
        });

        release(buffer);
        buf.close();
    }

    @Test
    public void testNioBuffersExpand2() throws Exception {
        ChannelOutboundBuffer buffer = new TestChannelOutboundBuffer();
        ByteBufferCollector collector = new ByteBufferCollector();
        Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1", CharsetUtil.US_ASCII);
        var sends = Stream.generate(() -> buf.copy().send()).limit(65).collect(Collectors.toList());
        CompositeBuffer comp = BufferAllocator.offHeapUnpooled().compose(sends);

        buffer.addMessage(comp, comp.readableBytes(), ImmediateEventExecutor.INSTANCE.newPromise());

        assertEquals(0, collector.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = collector.nioBuffers(buffer, 1024, Integer.MAX_VALUE);
        assertEquals(65, collector.nioBufferCount());
        assertEquals(1, buf.countReadableComponents());
        buf.forEachReadable(0, (index, component) -> {
            assertEquals(0, index);
            ByteBuffer expected = component.readableBuffer();
            for (int i = 0;  i < collector.nioBufferCount(); i++) {
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
    public void testNioBuffersMaxCount() throws Exception {
        ChannelOutboundBuffer buffer = new TestChannelOutboundBuffer();
        ByteBufferCollector collector = new ByteBufferCollector();
        Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1", CharsetUtil.US_ASCII);
        assertEquals(4, buf.readableBytes());
        var sends = Stream.generate(() -> buf.copy().send()).limit(65).collect(Collectors.toList());
        CompositeBuffer comp = BufferAllocator.offHeapUnpooled().compose(sends);

        assertEquals(65, comp.countComponents());
        buffer.addMessage(comp, comp.readableBytes(), ImmediateEventExecutor.INSTANCE.newPromise());
        assertEquals(0, collector.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        final int maxCount = 10;    // less than comp.nioBufferCount()
        ByteBuffer[] buffers = collector.nioBuffers(buffer, maxCount, Integer.MAX_VALUE);
        assertTrue(collector.nioBufferCount() <= maxCount, "Should not be greater than maxCount");
        buf.forEachReadable(0, (index, component) -> {
            assertEquals(0, index);
            ByteBuffer expected = component.readableBuffer();
            for (int i = 0;  i < collector.nioBufferCount(); i++) {
                assertEquals(expected, buffers[i]);
            }
            return true;
        });
        release(buffer);
        buf.close();
    }
    private static void release(ChannelOutboundBuffer buffer) {
        for (;;) {
            if (!buffer.remove()) {
                break;
            }
        }
    }
}
