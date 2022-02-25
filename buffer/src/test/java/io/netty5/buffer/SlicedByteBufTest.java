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
package io.netty5.buffer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests sliced channel buffers
 */
public class SlicedByteBufTest extends AbstractByteBufTest {

    @Override
    protected final ByteBuf newBuffer(int length, int maxCapacity) {
        Assumptions.assumeTrue(maxCapacity == Integer.MAX_VALUE);
        int offset = length == 0 ? 0 : ThreadLocalRandom.current().nextInt(length);
        ByteBuf buffer = Unpooled.buffer(length * 2);
        ByteBuf slice = newSlice(buffer, offset, length);
        assertEquals(0, slice.readerIndex());
        assertEquals(length, slice.writerIndex());
        return slice;
    }

    protected ByteBuf newSlice(ByteBuf buffer, int offset, int length) {
        return buffer.slice(offset, length);
    }

    @Test
    public void testIsContiguous() {
        ByteBuf buf = newBuffer(4);
        assertEquals(buf.unwrap().isContiguous(), buf.isContiguous());
        buf.release();
    }

    @Test
    public void shouldNotAllowNullInConstructor() {
        assertThrows(NullPointerException.class, () -> new SlicedByteBuf(null, 0, 0));
    }

    @Test
    @Override
    public void testInternalNioBuffer() {
        assertThrows(IndexOutOfBoundsException.class, super::testInternalNioBuffer);
    }

    @Test
    @Override
    public void testDuplicateReadGatheringByteChannelMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, super::testDuplicateReadGatheringByteChannelMultipleThreads);
    }

    @Test
    @Override
    public void testSliceReadGatheringByteChannelMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, super::testSliceReadGatheringByteChannelMultipleThreads);
    }

    @Test
    @Override
    public void testDuplicateReadOutputStreamMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, super::testDuplicateReadOutputStreamMultipleThreads);
    }

    @Test
    @Override
    public void testSliceReadOutputStreamMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, super::testSliceReadOutputStreamMultipleThreads);
    }

    @Test
    @Override
    public void testDuplicateBytesInArrayMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, super::testDuplicateBytesInArrayMultipleThreads);
    }

    @Test
    @Override
    public void testSliceBytesInArrayMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, super::testSliceBytesInArrayMultipleThreads);
    }

    @Test
    @Override
    public void testNioBufferExposeOnlyRegion() {
        assertThrows(IndexOutOfBoundsException.class, super::testNioBufferExposeOnlyRegion);
    }

    @Test
    @Override
    public void testGetReadOnlyDirectDst() {
        assertThrows(IndexOutOfBoundsException.class, super::testGetReadOnlyDirectDst);
    }

    @Test
    @Override
    public void testGetReadOnlyHeapDst() {
        assertThrows(IndexOutOfBoundsException.class, super::testGetReadOnlyHeapDst);
    }

    @Test
    @Override
    public void testLittleEndianWithExpand() {
        // ignore for SlicedByteBuf
    }

    @Test
    @Override
    public void testReadBytes() {
        // ignore for SlicedByteBuf
    }

    @Test
    @Override
    public void testForEachByteDesc2() {
        // Ignore for SlicedByteBuf
    }

    @Test
    @Override
    public void testForEachByte2() {
        // Ignore for SlicedByteBuf
    }

    @Disabled("Sliced ByteBuf objects don't allow the capacity to change. So this test would fail and shouldn't be run")
    @Override
    public void testDuplicateCapacityChange() {
    }

    @Disabled("Sliced ByteBuf objects don't allow the capacity to change. So this test would fail and shouldn't be run")
    @Override
    public void testRetainedDuplicateCapacityChange() {
    }

    @Test
    public void testReaderIndex() {
        ByteBuf wrapped = Unpooled.buffer(16);
        try {
            wrapped.writerIndex(14);
            wrapped.readerIndex(2);
            ByteBuf slice = wrapped.slice(4, 4);
            assertEquals(0, slice.readerIndex());
            assertEquals(4, slice.writerIndex());

            slice.readerIndex(slice.readerIndex() + 1);
            slice.readerIndex(0);
            assertEquals(0, slice.readerIndex());

            slice.writerIndex(slice.writerIndex() - 1);
            slice.writerIndex(0);
            assertEquals(0, slice.writerIndex());
        } finally {
            wrapped.release();
        }
    }

    @Test
    public void sliceEmptyNotLeak() {
        ByteBuf buffer = Unpooled.buffer(8).retain();
        assertEquals(2, buffer.refCnt());

        ByteBuf slice1 = buffer.slice();
        assertEquals(2, slice1.refCnt());

        ByteBuf slice2 = slice1.slice();
        assertEquals(2, slice2.refCnt());

        assertFalse(slice2.release());
        assertEquals(1, buffer.refCnt());
        assertEquals(1, slice1.refCnt());
        assertEquals(1, slice2.refCnt());

        assertTrue(slice2.release());

        assertEquals(0, buffer.refCnt());
        assertEquals(0, slice1.refCnt());
        assertEquals(0, slice2.refCnt());
    }

    @Override
    @Test
    public void testGetBytesByteBuffer() {
        byte[] bytes = {'a', 'b', 'c', 'd', 'e', 'f', 'g'};
        // Ensure destination buffer is bigger then what is wrapped in the ByteBuf.
        ByteBuffer nioBuffer = ByteBuffer.allocate(bytes.length + 1);
        ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(bytes).slice(0, bytes.length - 1);
        try {
            assertThrows(IndexOutOfBoundsException.class,
                () -> wrappedBuffer.getBytes(wrappedBuffer.readerIndex(), nioBuffer));
        } finally {
            wrappedBuffer.release();
        }
    }

    @Test
    @Override
    public void testWriteUsAsciiCharSequenceExpand() {
        assertThrows(IndexOutOfBoundsException.class, super::testWriteUsAsciiCharSequenceExpand);
    }

    @Test
    @Override
    public void testWriteUtf8CharSequenceExpand() {
        assertThrows(IndexOutOfBoundsException.class, super::testWriteUtf8CharSequenceExpand);
    }

    @Test
    @Override
    public void testWriteIso88591CharSequenceExpand() {
        assertThrows(IndexOutOfBoundsException.class, super::testWriteIso88591CharSequenceExpand);
    }

    @Test
    @Override
    public void testWriteUtf16CharSequenceExpand() {
        assertThrows(IndexOutOfBoundsException.class, super::testWriteUtf16CharSequenceExpand);
    }

    @Test
    public void ensureWritableWithEnoughSpaceShouldNotThrow() {
        ByteBuf slice = newBuffer(10);
        ByteBuf unwrapped = slice.unwrap();
        unwrapped.writerIndex(unwrapped.writerIndex() + 5);
        slice.writerIndex(slice.readerIndex());

        // Run ensureWritable and verify this doesn't change any indexes.
        int originalWriterIndex = slice.writerIndex();
        int originalReadableBytes = slice.readableBytes();
        slice.ensureWritable(originalWriterIndex - slice.writerIndex());
        assertEquals(originalWriterIndex, slice.writerIndex());
        assertEquals(originalReadableBytes, slice.readableBytes());
        slice.release();
    }

    @Test
    public void ensureWritableWithNotEnoughSpaceShouldThrow() {
        ByteBuf slice = newBuffer(10);
        ByteBuf unwrapped = slice.unwrap();
        unwrapped.writerIndex(unwrapped.writerIndex() + 5);
        try {
            assertThrows(IndexOutOfBoundsException.class, () -> slice.ensureWritable(1));
        } finally {
            slice.release();
        }
    }
}
