/*
 * Copyright 2012 The Netty Project
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

import io.netty.util.IllegalReferenceCountException;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Tests sliced channel buffers
 */
public class SlicedByteBufTest extends AbstractByteBufTest {

    private final Random random = new Random();
    private ByteBuf buffer;

    @Override
    protected ByteBuf newBuffer(int length) {
        buffer = Unpooled.wrappedBuffer(
                new byte[length * 2], random.nextInt(length - 1) + 1, length);
        assertEquals(length, buffer.writerIndex());
        return buffer;
    }

    @Override
    protected ByteBuf[] components() {
        return new ByteBuf[] { buffer };
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullInConstructor() {
        new SlicedByteBuf(null, 0, 0);
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

    @Test
    @Override
    public void testLittleEndianWithExpand() {
       // ignore for SlicedByteBuf
    }
}
