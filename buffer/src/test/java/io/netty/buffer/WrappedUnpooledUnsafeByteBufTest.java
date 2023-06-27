/*
 * Copyright 2016 The Netty Project
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

import static org.junit.jupiter.api.Assertions.assertThrows;

public class WrappedUnpooledUnsafeByteBufTest extends BigEndianUnsafeDirectByteBufTest {

    @BeforeEach
    @Override
    public void init() {
        Assumptions.assumeTrue(PlatformDependent.useDirectBufferNoCleaner(),
                "PlatformDependent.useDirectBufferNoCleaner() returned false, skip tests");
        super.init();
    }

    @Override
    protected ByteBuf newBuffer(int length, int maxCapacity) {
        Assumptions.assumeTrue(maxCapacity == Integer.MAX_VALUE);

        return new WrappedUnpooledUnsafeDirectByteBuf(UnpooledByteBufAllocator.DEFAULT,
                PlatformDependent.allocateMemory(length), length, true);
    }

    @Test
    @Override
    public void testInternalNioBuffer() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testInternalNioBuffer();
            }
        });
    }

    @Test
    @Override
    public void testDuplicateReadGatheringByteChannelMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() throws Exception {
                WrappedUnpooledUnsafeByteBufTest.super.testDuplicateReadGatheringByteChannelMultipleThreads();
            }
        });
    }

    @Test
    @Override
    public void testSliceReadGatheringByteChannelMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() throws Exception {
                WrappedUnpooledUnsafeByteBufTest.super.testSliceReadGatheringByteChannelMultipleThreads();
            }
        });
    }

    @Test
    @Override
    public void testDuplicateReadOutputStreamMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() throws Exception {
                WrappedUnpooledUnsafeByteBufTest.super.testDuplicateReadOutputStreamMultipleThreads();
            }
        });
    }

    @Test
    @Override
    public void testSliceReadOutputStreamMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() throws Exception {
                WrappedUnpooledUnsafeByteBufTest.super.testSliceReadOutputStreamMultipleThreads();
            }
        });
    }

    @Test
    @Override
    public void testDuplicateBytesInArrayMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() throws Exception {
                WrappedUnpooledUnsafeByteBufTest.super.testDuplicateBytesInArrayMultipleThreads();
            }
        });
    }

    @Test
    @Override
    public void testSliceBytesInArrayMultipleThreads() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() throws Exception {
                WrappedUnpooledUnsafeByteBufTest.super.testSliceBytesInArrayMultipleThreads();
            }
        });
    }

    @Test
    @Override
    public void testNioBufferExposeOnlyRegion() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testNioBufferExposeOnlyRegion();
            }
        });
    }

    @Test
    @Override
    public void testGetReadOnlyDirectDst() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testGetReadOnlyDirectDst();
            }
        });
    }

    @Test
    @Override
    public void testGetReadOnlyHeapDst() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testGetReadOnlyHeapDst();
            }
        });
    }

    @Test
    @Override
    public void testReadBytes() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testReadBytes();
            }
        });
    }

    @Test
    @Override
    public void testDuplicateCapacityChange() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testDuplicateCapacityChange();
            }
        });
    }

    @Test
    @Override
    public void testRetainedDuplicateCapacityChange() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testRetainedDuplicateCapacityChange();
            }
        });
    }

    @Test
    @Override
    public void testLittleEndianWithExpand() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testLittleEndianWithExpand();
            }
        });
    }

    @Test
    @Override
    public void testWriteUsAsciiCharSequenceExpand() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testWriteUsAsciiCharSequenceExpand();
            }
        });
    }

    @Test
    @Override
    public void testWriteUtf8CharSequenceExpand() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testWriteUtf8CharSequenceExpand();
            }
        });
    }

    @Test
    @Override
    public void testWriteIso88591CharSequenceExpand() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testWriteIso88591CharSequenceExpand();
            }
        });
    }

    @Test
    @Override
    public void testWriteUtf16CharSequenceExpand() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testWriteUtf16CharSequenceExpand();
            }
        });
    }

    @Test
    @Override
    public void testGetBytesByteBuffer() {
        assertThrows(IndexOutOfBoundsException.class, new Executable() {
            @Override
            public void execute() {
                WrappedUnpooledUnsafeByteBufTest.super.testGetBytesByteBuffer();
            }
        });
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
