/*
 * Copyright 2015 The Netty Project
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

import org.junit.Test;

import java.nio.ByteBuffer;

import static io.netty.util.internal.PlatformDependent.directBufferAddress;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class UnsafeByteBufUtilTest {

    @Test
    public void testSetBytesOnReadOnlyByteBuffer() throws Exception {
        byte[] testData = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
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
        byte[] testData = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
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
            assertEquals(b1.array().length, pageSize);
            assertEquals(b1.array(), b2.array());
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

}
