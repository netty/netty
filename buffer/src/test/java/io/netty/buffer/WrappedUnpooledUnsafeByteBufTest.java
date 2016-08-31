/*
 * Copyright 2016 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.PlatformDependent;
import org.junit.Test;

import java.io.IOException;

public class WrappedUnpooledUnsafeByteBufTest extends BigEndianUnsafeDirectByteBufTest {

    @Override
    protected ByteBuf newBuffer(int length) {
        return new WrappedUnpooledUnsafeDirectByteBuf(UnpooledByteBufAllocator.DEFAULT,
                PlatformDependent.allocateMemory(length), length, true);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testInternalNioBuffer() {
        super.testInternalNioBuffer();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testDuplicateReadGatheringByteChannelMultipleThreads() throws Exception {
        super.testDuplicateReadGatheringByteChannelMultipleThreads();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testSliceReadGatheringByteChannelMultipleThreads() throws Exception {
        super.testSliceReadGatheringByteChannelMultipleThreads();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testDuplicateReadOutputStreamMultipleThreads() throws Exception {
        super.testDuplicateReadOutputStreamMultipleThreads();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testSliceReadOutputStreamMultipleThreads() throws Exception {
        super.testSliceReadOutputStreamMultipleThreads();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testDuplicateBytesInArrayMultipleThreads() throws Exception {
        super.testDuplicateBytesInArrayMultipleThreads();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testSliceBytesInArrayMultipleThreads() throws Exception {
        super.testSliceBytesInArrayMultipleThreads();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testNioBufferExposeOnlyRegion() {
        super.testNioBufferExposeOnlyRegion();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testEnsureWritableAfterRelease() {
        super.testEnsureWritableAfterRelease();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testWriteZeroAfterRelease() throws IOException {
        super.testWriteZeroAfterRelease();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testGetReadOnlyDirectDst() {
        super.testGetReadOnlyDirectDst();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testGetReadOnlyHeapDst() {
        super.testGetReadOnlyHeapDst();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testReadBytes() {
        super.testReadBytes();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    @Override
    public void testLittleEndianWithExpand() {
        super.testLittleEndianWithExpand();
    }

    @Test
    @Override
    public void testForEachByteDesc2() {
        // Ignore
    }

    @Test
    @Override
    public void testForEachByte2() {
        // Ignore
    }
}
