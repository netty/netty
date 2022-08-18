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

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteBufferCollectorTest {

    @Test
    public void testEmptyNioBuffers() {
        ByteBufferCollector collector = newCollector();
        assertEmpty(collector, new TestBuffer());
    }

    private static void assertEmpty(ByteBufferCollector collector, TestBuffer buffer) {
        collector.prepare(Integer.MAX_VALUE, Long.MAX_VALUE);
        buffer.forEach(collector);
        ByteBuffer[] buffers = collector.nioBuffers();
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
        TestBuffer buffer = new TestBuffer();
        Buffer buf = BufferAllocator.onHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII);

        // Still empty as not flushed.
        assertEmpty(collector, buffer);

        buffer.add(buf);
        collector.prepare(Integer.MAX_VALUE, Long.MAX_VALUE);
        buffer.forEach(collector);
        ByteBuffer[] buffers = collector.nioBuffers();

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
        TestBuffer buffer = new TestBuffer();
        assertEmpty(collector, buffer);

        try (Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII)) {
            for (int i = 0; i < 64; i++) {
                buffer.add(buf.copy());
            }

            collector.prepare(Integer.MAX_VALUE, Long.MAX_VALUE);
            buffer.forEach(collector);
            ByteBuffer[] buffers = collector.nioBuffers();
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
        TestBuffer buffer = new TestBuffer();
        assertEmpty(collector, buffer);

        try (Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII)) {
            var sends = Stream.generate(() -> buf.copy().send()).limit(65).collect(Collectors.toList());
            CompositeBuffer comp = BufferAllocator.offHeapUnpooled().compose(sends);
            buffer.add(comp);
            collector.prepare(Integer.MAX_VALUE, Long.MAX_VALUE);
            buffer.forEach(collector);
            ByteBuffer[] buffers = collector.nioBuffers();
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
        TestBuffer buffer = new TestBuffer();

        try (Buffer buf = BufferAllocator.offHeapUnpooled().copyOf("buf1", StandardCharsets.US_ASCII)) {
            assertEquals(4, buf.readableBytes());
            var sends = Stream.generate(() -> buf.copy().send()).limit(65).collect(Collectors.toList());
            CompositeBuffer comp = BufferAllocator.offHeapUnpooled().compose(sends);

            assertEquals(65, comp.countComponents());
            buffer.add(comp);

            final int maxCount = 10;    // less than comp.nioBufferCount()
            collector.prepare(maxCount, Long.MAX_VALUE);
            buffer.forEach(collector);
            ByteBuffer[] buffers = collector.nioBuffers();

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

    private static final class TestBuffer extends ArrayList<Object> {

        void forEach(Predicate<Object> function) {
            for (Object o: this) {
                if (!function.test(o)) {
                    return;
                }
            }
        }
    }
}
