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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.ByteBuffer;

import static io.netty.util.internal.PlatformDependent.directBufferAddress;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UnsafeByteBufUtilTest {
    @BeforeEach
    public void checkHasUnsafe() {
        Assumptions.assumeTrue(PlatformDependent.hasUnsafe(), "sun.misc.Unsafe not found, skip tests");
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

            assertArrayEquals(testData, check, "The byte array's copy does not equal the original");
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

            assertArrayEquals(testData, check, "The byte array's copy does not equal the original");
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

            assertArrayEquals(testData, check, "The byte array's copy does not equal the original");
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

    @Test
    public void testSetBytesWithNullByteArray() {

        final UnpooledByteBufAllocator alloc = new UnpooledByteBufAllocator(true);
        final UnpooledDirectByteBuf targetBuffer = new UnpooledDirectByteBuf(alloc, 8, 8);

        try {
            assertThrows(NullPointerException.class, new Executable() {
                @Override
                public void execute() {
                    UnsafeByteBufUtil.setBytes(targetBuffer,
                            directBufferAddress(targetBuffer.nioBuffer()), 0, (byte[]) null, 0, 8);
                }
            });
        } finally {
            targetBuffer.release();
        }
    }

    @Test
    public void testSetBytesOutOfBounds() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                // negative index
                testSetBytesOutOfBounds0(4, 4, -1, 0, 4);
            }
        });
    }

    @Test
    public void testSetBytesOutOfBounds2() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                // negative length
                testSetBytesOutOfBounds0(4, 4, 0, 0, -1);
            }
        });
    }

    @Test
    public void testSetBytesOutOfBounds3() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                // buffer length oversize
                testSetBytesOutOfBounds0(4, 8, 0, 0, 5);
            }
        });
    }

    @Test
    public void testSetBytesOutOfBounds4() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                // buffer length oversize
                testSetBytesOutOfBounds0(4, 4, 3, 0, 3);
            }
        });
    }

    @Test
    public void testSetBytesOutOfBounds5() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                // negative srcIndex
                testSetBytesOutOfBounds0(4, 4, 0, -1, 4);
            }
        });
    }

    @Test
    public void testSetBytesOutOfBounds6() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                // src length oversize
                testSetBytesOutOfBounds0(8, 4, 0, 0, 5);
            }
        });
    }

    @Test
    public void testSetBytesOutOfBounds7() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                // src length oversize
                testSetBytesOutOfBounds0(4, 4, 0, 1, 4);
            }
        });
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
