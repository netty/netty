/*
 * Copyright 2017 The Netty Project
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
package io.netty.buffer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public abstract class ByteBufAllocatorTest {

    protected abstract int defaultMaxCapacity();

    protected abstract int defaultMaxComponents();

    protected abstract ByteBufAllocator newAllocator(boolean preferDirect);

    @Test
    public void testBuffer() {
        testBuffer(true);
        testBuffer(false);
    }

    private void testBuffer(boolean preferDirect) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.buffer(1);
        try {
            assertBuffer(buffer, isDirectExpected(preferDirect), 1, defaultMaxCapacity());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testBufferWithCapacity() {
        testBufferWithCapacity(true, 8);
        testBufferWithCapacity(false, 8);
    }

    private void testBufferWithCapacity(boolean preferDirect, int maxCapacity) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.buffer(1, maxCapacity);
        try {
            assertBuffer(buffer, isDirectExpected(preferDirect), 1, maxCapacity);
        } finally {
            buffer.release();
        }
    }

    protected abstract boolean isDirectExpected(boolean preferDirect);

    @Test
    public void testHeapBuffer() {
        testHeapBuffer(true);
        testHeapBuffer(false);
    }

    private void testHeapBuffer(boolean preferDirect) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.heapBuffer(1);
        try {
            assertBuffer(buffer, false, 1, defaultMaxCapacity());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testHeapBufferMaxCapacity() {
        testHeapBuffer(true, 8);
        testHeapBuffer(false, 8);
    }

    private void testHeapBuffer(boolean preferDirect, int maxCapacity) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.heapBuffer(1, maxCapacity);
        try {
            assertBuffer(buffer, false, 1, maxCapacity);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDirectBuffer() {
        testDirectBuffer(true);
        testDirectBuffer(false);
    }

    private void testDirectBuffer(boolean preferDirect) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.directBuffer(1);
        try {
            assertBuffer(buffer, true, 1, defaultMaxCapacity());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDirectBufferMaxCapacity() {
        testDirectBuffer(true, 8);
        testDirectBuffer(false, 8);
    }

    private void testDirectBuffer(boolean preferDirect, int maxCapacity) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.directBuffer(1, maxCapacity);
        try {
            assertBuffer(buffer, true, 1, maxCapacity);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testCompositeBuffer() {
        testCompositeBuffer(true);
        testCompositeBuffer(false);
    }

    private void testCompositeBuffer(boolean preferDirect) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        CompositeByteBuf buffer = allocator.compositeBuffer();
        try {
            assertCompositeByteBuf(buffer, defaultMaxComponents());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testCompositeBufferWithCapacity() {
        testCompositeHeapBufferWithCapacity(true, 8);
        testCompositeHeapBufferWithCapacity(false, 8);
    }

    @Test
    public void testCompositeHeapBuffer() {
        testCompositeHeapBuffer(true);
        testCompositeHeapBuffer(false);
    }

    private void testCompositeHeapBuffer(boolean preferDirect) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        CompositeByteBuf buffer = allocator.compositeHeapBuffer();
        try {
            assertCompositeByteBuf(buffer, defaultMaxComponents());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testCompositeHeapBufferWithCapacity() {
        testCompositeHeapBufferWithCapacity(true, 8);
        testCompositeHeapBufferWithCapacity(false, 8);
    }

    private void testCompositeHeapBufferWithCapacity(boolean preferDirect, int maxNumComponents) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        CompositeByteBuf buffer = allocator.compositeHeapBuffer(maxNumComponents);
        try {
            assertCompositeByteBuf(buffer, maxNumComponents);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testCompositeDirectBuffer() {
        testCompositeDirectBuffer(true);
        testCompositeDirectBuffer(false);
    }

    private void testCompositeDirectBuffer(boolean preferDirect) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        CompositeByteBuf buffer = allocator.compositeDirectBuffer();
        try {
            assertCompositeByteBuf(buffer, defaultMaxComponents());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testCompositeDirectBufferWithCapacity() {
        testCompositeDirectBufferWithCapacity(true, 8);
        testCompositeDirectBufferWithCapacity(false, 8);
    }

    private void testCompositeDirectBufferWithCapacity(boolean preferDirect, int maxNumComponents) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        CompositeByteBuf buffer = allocator.compositeDirectBuffer(maxNumComponents);
        try {
            assertCompositeByteBuf(buffer, maxNumComponents);
        } finally {
            buffer.release();
        }
    }

    private static void assertBuffer(
            ByteBuf buffer, boolean expectedDirect, int expectedCapacity, int expectedMaxCapacity) {
        assertEquals(expectedDirect, buffer.isDirect());
        assertEquals(expectedCapacity, buffer.capacity());
        assertEquals(expectedMaxCapacity, buffer.maxCapacity());
    }

    private void assertCompositeByteBuf(
            CompositeByteBuf buffer, int expectedMaxNumComponents) {
        assertEquals(0, buffer.numComponents());
        assertEquals(expectedMaxNumComponents, buffer.maxNumComponents());
        assertBuffer(buffer, false, 0, defaultMaxCapacity());
    }
}
