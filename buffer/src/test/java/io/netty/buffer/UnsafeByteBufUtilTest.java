/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.internal.PlatformDependent;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static io.netty.util.internal.PlatformDependent.directBufferAddress;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class UnsafeByteBufUtilTest {
    @Before
    public void checkHasUnsafe() {
        Assume.assumeTrue("sun.misc.Unsafe not found, skip tests", PlatformDependent.hasUnsafe());
    }

    @Test
    public void testSetBytesOnReadOnlyByteBuffer() throws Exception {
        byte[] testData = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int length = testData.length;

        ByteBuffer readOnlyBuffer = ByteBuffer.wrap(testData).asReadOnlyBuffer();

        UnpooledByteBufAllocator alloc = new UnpooledByteBufAllocator(true);
        UnpooledDirectByteBuf targetBuffer = new UnpooledDirectByteBuf(alloc, length, length);

        try {
            UnsafeByteBufUtil.setBytes(targetBuffer, directBufferAddress(targetBuffer.nioBuffer()), 0, readOnlyBuffer);

            byte[] check = new byte[length];
            targetBuffer.getBytes(0, check, 0, length);

            assertArrayEquals("The byte array's copy does not equal the original", testData, check);
        } finally {
            targetBuffer.release();
        }
    }

    @Test
    public void testSetBytesOnReadOnlyByteBufferWithPooledAlloc() throws Exception {
        byte[] testData = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int length = testData.length;

        ByteBuffer readOnlyBuffer = ByteBuffer.wrap(testData).asReadOnlyBuffer();

        int pageSize = 4096;

        // create memory pool with one page
        ByteBufAllocator alloc = new PooledByteBufAllocator(true, 1, 1, pageSize, 0);
        UnpooledDirectByteBuf targetBuffer = new UnpooledDirectByteBuf(alloc, length, length);

        ByteBuf b1 = alloc.heapBuffer(16);
        ByteBuf b2 = alloc.heapBuffer(16);

        try {
            // just check that two following buffers share same array but different offset
            assertEquals(pageSize, b1.array().length);
            assertArrayEquals(b1.array(), b2.array());
            assertNotEquals(b1.arrayOffset(), b2.arrayOffset());

            UnsafeByteBufUtil.setBytes(targetBuffer, directBufferAddress(targetBuffer.nioBuffer()), 0, readOnlyBuffer);

            byte[] check = new byte[length];
            targetBuffer.getBytes(0, check, 0, length);

            assertArrayEquals("The byte array's copy does not equal the original", testData, check);
        } finally {
            targetBuffer.release();
            b1.release();
            b2.release();
        }
    }

    @Test
    public void testSetBytesWithByteArray() {
        final byte[] testData = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        final int length = testData.length;

        final UnpooledByteBufAllocator alloc = new UnpooledByteBufAllocator(true);
        final UnpooledDirectByteBuf targetBuffer = new UnpooledDirectByteBuf(alloc, length, length);

        try {
            UnsafeByteBufUtil.setBytes(targetBuffer,
                    directBufferAddress(targetBuffer.nioBuffer()), 0, testData, 0, length);

            final byte[] check = new byte[length];
            targetBuffer.getBytes(0, check, 0, length);

            assertArrayEquals("The byte array's copy does not equal the original", testData, check);
        } finally {
            targetBuffer.release();
        }
    }

    @Test
    public void testSetBytesWithZeroLength() {
        final byte[] testData = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        final int length = testData.length;

        final UnpooledByteBufAllocator alloc = new UnpooledByteBufAllocator(true);
        final UnpooledDirectByteBuf targetBuffer = new UnpooledDirectByteBuf(alloc, length, length);

        try {
            final byte[] beforeSet = new byte[length];
            targetBuffer.getBytes(0, beforeSet, 0, length);

            UnsafeByteBufUtil.setBytes(targetBuffer,
                    directBufferAddress(targetBuffer.nioBuffer()), 0, testData, 0, 0);

            final byte[] check = new byte[length];
            targetBuffer.getBytes(0, check, 0, length);

            assertArrayEquals(beforeSet, check);
        } finally {
            targetBuffer.release();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testSetBytesWithNullByteArray() {

        final UnpooledByteBufAllocator alloc = new UnpooledByteBufAllocator(true);
        final UnpooledDirectByteBuf targetBuffer = new UnpooledDirectByteBuf(alloc, 8, 8);

        try {
            UnsafeByteBufUtil.setBytes(targetBuffer,
                    directBufferAddress(targetBuffer.nioBuffer()), 0, (byte[]) null, 0, 8);
        } finally {
            targetBuffer.release();
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetBytesOutOfBounds() {
        // negative index
        testSetBytesOutOfBounds0(4, 4, -1, 0, 4);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetBytesOutOfBounds2() {
        // negative length
        testSetBytesOutOfBounds0(4, 4, 0, 0, -1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetBytesOutOfBounds3() {
        // buffer length oversize
        testSetBytesOutOfBounds0(4, 8, 0, 0, 5);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetBytesOutOfBounds4() {
        // buffer length oversize
        testSetBytesOutOfBounds0(4, 4, 3, 0, 3);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetBytesOutOfBounds5() {
        // negative srcIndex
        testSetBytesOutOfBounds0(4, 4, 0, -1, 4);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetBytesOutOfBounds6() {
        // src length oversize
        testSetBytesOutOfBounds0(8, 4, 0, 0, 5);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetBytesOutOfBounds7() {
        // src length oversize
        testSetBytesOutOfBounds0(4, 4, 0, 1, 4);
    }

    private static void testSetBytesOutOfBounds0(int lengthOfBuffer,
                                                 int lengthOfBytes,
                                                 int index,
                                                 int srcIndex,
                                                 int length) {
        final UnpooledByteBufAllocator alloc = new UnpooledByteBufAllocator(true);
        final UnpooledDirectByteBuf targetBuffer = new UnpooledDirectByteBuf(alloc, lengthOfBuffer, lengthOfBuffer);

        try {
            UnsafeByteBufUtil.setBytes(targetBuffer,
                    directBufferAddress(targetBuffer.nioBuffer()), index, new byte[lengthOfBytes], srcIndex, length);
        } finally {
            targetBuffer.release();
        }
    }

}
