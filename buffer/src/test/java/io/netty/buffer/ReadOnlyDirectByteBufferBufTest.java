/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import static io.netty.buffer.AbstractByteBufTest.assertReadyOnlyNioBuffer;
import static io.netty.buffer.AbstractByteBufTest.assertReadyOnlyNioBufferWithPositionLength;
import static io.netty.buffer.AbstractByteBufTest.assertReadyOnlyNioBuffers;
import static io.netty.buffer.AbstractByteBufTest.assertReadyOnlyNioBuffersWithPositionLength;
import static io.netty.buffer.AbstractByteBufTest.testBytesInArrayMultipleThreads;
import static io.netty.buffer.AbstractByteBufTest.testCopyMultipleThreads0;
import static io.netty.buffer.AbstractByteBufTest.testReadGatheringByteChannelMultipleThreads;
import static io.netty.buffer.AbstractByteBufTest.testReadOutputStreamMultipleThreads;
import static io.netty.buffer.AbstractByteBufTest.testToStringMultipleThreads0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ReadOnlyDirectByteBufferBufTest {

    protected ByteBuf buffer(ByteBuffer buffer) {
        return new ReadOnlyByteBufferBuf(UnpooledByteBufAllocator.DEFAULT, buffer);
    }

    protected ByteBuffer allocate(int size) {
        return ByteBuffer.allocateDirect(size);
    }

    @Test
    public void testIsContiguous() {
        ByteBuf buf = buffer(allocate(4).asReadOnlyBuffer());
        assertTrue(buf.isContiguous());
        buf.release();
    }

    @Test
    public void testConstructWithWritable() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                buffer(ReadOnlyDirectByteBufferBufTest.this.allocate(1));
            }
        });
    }

    @Test
    public void shouldIndicateNotWritable() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer()).clear();
        try {
            assertFalse(buf.isWritable());
        } finally {
            buf.release();
        }
    }

    @Test
    public void shouldIndicateNotWritableAnyNumber() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer()).clear();
        try {
            assertFalse(buf.isWritable(1));
        } finally {
            buf.release();
        }
    }

    @Test
    public void ensureWritableIntStatusShouldFailButNotThrow() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer()).clear();
        try {
            int result = buf.ensureWritable(1, false);
            assertEquals(1, result);
        } finally {
            buf.release();
        }
    }

    @Test
    public void ensureWritableForceIntStatusShouldFailButNotThrow() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer()).clear();
        try {
            int result = buf.ensureWritable(1, true);
            assertEquals(1, result);
        } finally {
            buf.release();
        }
    }

    @Test
    public void ensureWritableShouldThrow() {
        final ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer()).clear();
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.ensureWritable(1);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetByte() {
        final ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setByte(0, 1);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetInt() {
        final ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setInt(0, 1);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetShort() {
        final ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setShort(0, 1);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetMedium() {
        final ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setMedium(0, 1);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetLong() {
        final ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setLong(0, 1);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetBytesViaArray() {
        final ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setBytes(0, "test".getBytes());
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetBytesViaBuffer() {
        final ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        final ByteBuf copy = Unpooled.copyInt(1);
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setBytes(0, copy);
                }
            });
        } finally {
            buf.release();
            copy.release();
        }
    }

    @Test
    public void testSetBytesViaStream() throws IOException {
        final ByteBuf buf = buffer(ByteBuffer.allocateDirect(8).asReadOnlyBuffer());
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    buf.setBytes(0, new ByteArrayInputStream("test".getBytes()), 2);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testGetReadByte() {
        ByteBuf buf = buffer(
                ((ByteBuffer) allocate(2).put(new byte[] { (byte) 1, (byte) 2 }).flip()).asReadOnlyBuffer());

        assertEquals(1, buf.getByte(0));
        assertEquals(2, buf.getByte(1));

        assertEquals(1, buf.readByte());
        assertEquals(2, buf.readByte());
        assertFalse(buf.isReadable());

        buf.release();
    }

    @Test
    public void testGetReadInt() {
        ByteBuf buf = buffer(((ByteBuffer) allocate(8).putInt(1).putInt(2).flip()).asReadOnlyBuffer());

        assertEquals(1, buf.getInt(0));
        assertEquals(2, buf.getInt(4));

        assertEquals(1, buf.readInt());
        assertEquals(2, buf.readInt());
        assertFalse(buf.isReadable());

        buf.release();
    }

    @Test
    public void testGetReadShort() {
        ByteBuf buf = buffer(((ByteBuffer) allocate(8)
                .putShort((short) 1).putShort((short) 2).flip()).asReadOnlyBuffer());

        assertEquals(1, buf.getShort(0));
        assertEquals(2, buf.getShort(2));

        assertEquals(1, buf.readShort());
        assertEquals(2, buf.readShort());
        assertFalse(buf.isReadable());

        buf.release();
    }

    @Test
    public void testGetReadLong() {
        ByteBuf buf = buffer(((ByteBuffer) allocate(16)
                .putLong(1).putLong(2).flip()).asReadOnlyBuffer());

        assertEquals(1, buf.getLong(0));
        assertEquals(2, buf.getLong(8));

        assertEquals(1, buf.readLong());
        assertEquals(2, buf.readLong());
        assertFalse(buf.isReadable());

        buf.release();
    }

    @Test
    public void testGetBytesByteBuffer() {
        byte[] bytes = {'a', 'b', 'c', 'd', 'e', 'f', 'g'};
        // Ensure destination buffer is bigger then what is in the ByteBuf.
        final ByteBuffer nioBuffer = ByteBuffer.allocate(bytes.length + 1);
        final ByteBuf buffer = buffer(((ByteBuffer) allocate(bytes.length)
                .put(bytes).flip()).asReadOnlyBuffer());
        try {
            assertThrows(IndexOutOfBoundsException.class, new Executable() {
                @Override
                public void execute() {
                    buffer.getBytes(buffer.readerIndex(), nioBuffer);
                }
            });
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testCopy() {
        ByteBuf buf = buffer(((ByteBuffer) allocate(16).putLong(1).putLong(2).flip()).asReadOnlyBuffer());
        ByteBuf copy = buf.copy();

        assertEquals(buf, copy);

        buf.release();
        copy.release();
    }

    @Test
    public void testCopyWithOffset() {
        ByteBuf buf = buffer(((ByteBuffer) allocate(16).putLong(1).putLong(2).flip()).asReadOnlyBuffer());
        ByteBuf copy = buf.copy(1, 9);

        assertEquals(buf.slice(1, 9), copy);

        buf.release();
        copy.release();
    }

    // Test for https://github.com/netty/netty/issues/1708
    @Test
    public void testWrapBufferWithNonZeroPosition() {
        ByteBuf buf = buffer(((ByteBuffer) allocate(16)
                .putLong(1).flip().position(1)).asReadOnlyBuffer());

        ByteBuf slice = buf.slice();
        assertEquals(buf, slice);

        buf.release();
    }

    @Test
    public void testWrapBufferRoundTrip() {
        ByteBuf buf = buffer(((ByteBuffer) allocate(16).putInt(1).putInt(2).flip()).asReadOnlyBuffer());

        assertEquals(1, buf.readInt());

        ByteBuffer nioBuffer = buf.nioBuffer();

        // Ensure this can be accessed without throwing a BufferUnderflowException
        assertEquals(2, nioBuffer.getInt());

        buf.release();
    }

    @Test
    public void testWrapMemoryMapped() throws Exception {
        File file = PlatformDependent.createTempFile("netty-test", "tmp", null);
        FileChannel output = null;
        FileChannel input = null;
        ByteBuf b1 = null;
        ByteBuf b2 = null;

        try {
            output = new RandomAccessFile(file, "rw").getChannel();
            byte[] bytes = new byte[1024];
            PlatformDependent.threadLocalRandom().nextBytes(bytes);
            output.write(ByteBuffer.wrap(bytes));

            input = new RandomAccessFile(file, "r").getChannel();
            ByteBuffer m = input.map(FileChannel.MapMode.READ_ONLY, 0, input.size());

            b1 = buffer(m);

            ByteBuffer dup = m.duplicate();
            dup.position(2);
            dup.limit(4);

            b2 = buffer(dup);

            assertEquals(b2, b1.slice(2, 2));
        } finally {
            if (b1 != null) {
                b1.release();
            }
            if (b2 != null) {
                b2.release();
            }
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
            file.delete();
        }
    }

    @Test
    public void testMemoryAddress() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertFalse(buf.hasMemoryAddress());
            try {
                buf.memoryAddress();
                fail();
            } catch (UnsupportedOperationException expected) {
                // expected
            }
        } finally {
            buf.release();
        }
    }

    @Test
    public void testDuplicate() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertTrue(buf.duplicate().isReadOnly());
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSlice() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        try {
            assertTrue(buf.slice().isReadOnly());
        } finally {
            buf.release();
        }
    }

    enum DerivedParam {
        None,
        Duplicate,
        Slice
    }

    @ParameterizedTest
    @EnumSource(DerivedParam.class)
    void testIsWritable(DerivedParam param) {
        ByteBuffer buffer = allocate(24);
        ByteBuf buf = buffer(buffer.asReadOnlyBuffer());
        buf.writerIndex(8);

        switch (param) {
            case None:
                break;
            case Duplicate:
                buf = buf.duplicate();
                break;
            case Slice:
                buf = buf.slice(0, buf.capacity()).writerIndex(8);
                break;
        }
        try {
            assertFalse(buf.isWritable());
            assertFalse(buf.isWritable(1));
        } finally {
            buf.release();
        }
    }

    @Test
    public void testDuplicateReadGatheringByteChannelMultipleThreads() throws Exception {
        final byte[] bytes = new byte[8];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        ByteBuffer nioBuffer = allocate(bytes.length);
        nioBuffer.put(bytes);
        nioBuffer.flip();
        final ByteBuf buffer = buffer(nioBuffer.asReadOnlyBuffer());
        try {
            testReadGatheringByteChannelMultipleThreads(buffer, bytes, false);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testSliceReadGatheringByteChannelMultipleThreads() throws Exception {
        final byte[] bytes = new byte[8];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        ByteBuffer nioBuffer = allocate(bytes.length);
        nioBuffer.put(bytes);
        nioBuffer.flip();
        final ByteBuf buffer = buffer(nioBuffer.asReadOnlyBuffer());
        try {
            testReadGatheringByteChannelMultipleThreads(buffer, bytes, true);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDuplicateReadOutputStreamMultipleThreads() throws Exception {
        final byte[] bytes = new byte[8];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        ByteBuffer nioBuffer = allocate(bytes.length);
        nioBuffer.put(bytes);
        nioBuffer.flip();
        final ByteBuf buffer = buffer(nioBuffer.asReadOnlyBuffer());
        try {
            testReadOutputStreamMultipleThreads(buffer, bytes, false);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testSliceReadOutputStreamMultipleThreads() throws Exception {
        final byte[] bytes = new byte[8];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        ByteBuffer nioBuffer = allocate(bytes.length);
        nioBuffer.put(bytes);
        nioBuffer.flip();
        final ByteBuf buffer = buffer(nioBuffer.asReadOnlyBuffer());
        try {
            testReadOutputStreamMultipleThreads(buffer, bytes, true);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDuplicateBytesInArrayMultipleThreads() throws Exception {
        final byte[] bytes = new byte[8];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        ByteBuffer nioBuffer = allocate(bytes.length);
        nioBuffer.put(bytes);
        nioBuffer.flip();
        final ByteBuf buffer = buffer(nioBuffer.asReadOnlyBuffer());
        try {
            testBytesInArrayMultipleThreads(buffer, bytes, false);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testSliceBytesInArrayMultipleThreads() throws Exception {
        final byte[] bytes = new byte[8];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        ByteBuffer nioBuffer = allocate(bytes.length);
        nioBuffer.put(bytes);
        nioBuffer.flip();
        final ByteBuf buffer = buffer(nioBuffer.asReadOnlyBuffer());
        try {
            testBytesInArrayMultipleThreads(buffer, bytes, true);
        } finally {
            buffer.release();
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testToStringMultipleThreads1() throws Throwable {
        String expected = "Hello, World!";
        byte[] bytes = expected.getBytes(CharsetUtil.ISO_8859_1);
        ByteBuffer nioBuffer = allocate(bytes.length);
        nioBuffer.put(bytes);
        nioBuffer.flip();
        final ByteBuf buffer = buffer(nioBuffer.asReadOnlyBuffer());
        try {
            testToStringMultipleThreads0(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testCopyMultipleThreads() throws Throwable {
        final ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            testCopyMultipleThreads0(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadOnlyRelease() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        buf.writerIndex(4);
        testRelease(buf, buf.asReadOnly());
    }

    @Test
    public void testDuplicateRelease() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        buf.writerIndex(4);
        testRelease(buf, buf.duplicate());
    }

    @Test
    public void testSliceRelease() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        buf.writerIndex(4);
        testRelease(buf, buf.slice());
    }

    @Test
    public void testDuplicateDuplicateRelease() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        buf.writerIndex(4);
        testRelease(buf, buf.duplicate().duplicate());
    }

    @Test
    public void testDuplicateSliceRelease() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        buf.writerIndex(4);
        testRelease(buf, buf.duplicate().duplicate());
    }

    @Test
    public void testSliceDuplicateRelease() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        buf.writerIndex(4);
        testRelease(buf, buf.slice().duplicate());
    }

    @Test
    public void testSliceSliceRelease() {
        ByteBuf buf = buffer(allocate(8).asReadOnlyBuffer());
        buf.writerIndex(4);
        testRelease(buf, buf.slice().slice());
    }

    private static void testRelease(ByteBuf parent, ByteBuf derived) {
        assertEquals(1, parent.refCnt());
        assertTrue(derived.release());
        assertEquals(0, parent.refCnt());
    }

    @Test
    public void ensureWritableWithForceAsReadyOnly() {
        ensureWritableReadOnly(true);
    }

    @Test
    public void ensureWritableWithOutForceAsReadOnly() {
        ensureWritableReadOnly(false);
    }

    private void ensureWritableReadOnly(boolean force) {
        ByteBuf buffer = buffer(allocate(8).asReadOnlyBuffer());
        assertEquals(1, buffer.ensureWritable(8, force));
        buffer.release();
    }

    @Test
    public void testReadyOnlyNioBuffer() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBuffer(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlySliceNioBuffer() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBuffer(buffer.slice());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlyDuplicateNioBuffer() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBuffer(buffer.duplicate());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlyNioBufferWithPositionLength() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBufferWithPositionLength(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlySliceNioBufferWithPositionLength() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBufferWithPositionLength(buffer.slice());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlyDuplicateNioBufferWithPositionLength() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBufferWithPositionLength(buffer.duplicate());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlyNioBuffers() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBuffers(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlySliceNioBuffers() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBuffers(buffer.slice());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlyDuplicateNioBuffers() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBuffers(buffer.duplicate());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlyNioBuffersWithPositionLength() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBuffersWithPositionLength(buffer);
        } finally {
            buffer.release();
        }
    }
    @Test
    public void testReadyOnlySliceNioBuffersWithPositionLength() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBuffersWithPositionLength(buffer.slice());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testReadyOnlyDuplicateNioBuffersWithPositionLength() {
        ByteBuf buffer = newRandomReadOnlyBuffer();
        try {
            assertReadyOnlyNioBuffersWithPositionLength(buffer.duplicate());
        } finally {
            buffer.release();
        }
    }

    private ByteBuf newRandomReadOnlyBuffer() {
        final byte[] bytes = new byte[8];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        ByteBuffer nioBuffer = allocate(bytes.length);
        nioBuffer.put(bytes);
        nioBuffer.flip();
        return buffer(nioBuffer.asReadOnlyBuffer());
    }
}
