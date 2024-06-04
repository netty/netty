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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

import static io.netty.buffer.Unpooled.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class FixedCompositeByteBufTest {

    private static ByteBuf newBuffer(ByteBuf... buffers) {
        return new FixedCompositeByteBuf(UnpooledByteBufAllocator.DEFAULT, buffers);
    }

    @Test
    public void testSetBoolean() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setBoolean(0, true);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetByte() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
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
    public void testSetBytesWithByteBuf() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
        final ByteBuf src = wrappedBuffer(new byte[4]);
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setBytes(0, src);
                }
            });
        } finally {
            buf.release();
            src.release();
        }
    }

    @Test
    public void testSetBytesWithByteBuffer() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setBytes(0, ByteBuffer.wrap(new byte[4]));
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetBytesWithInputStream() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() throws IOException {
                    buf.setBytes(0, new ByteArrayInputStream(new byte[4]), 4);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetBytesWithChannel() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() throws IOException {
                    buf.setBytes(0, new ScatteringByteChannel() {
                        @Override
                        public long read(ByteBuffer[] dsts, int offset, int length) {
                            return 0;
                        }

                        @Override
                        public long read(ByteBuffer[] dsts) {
                            return 0;
                        }

                        @Override
                        public int read(ByteBuffer dst) {
                            return 0;
                        }

                        @Override
                        public boolean isOpen() {
                            return true;
                        }

                        @Override
                        public void close() {
                        }
                    }, 4);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetChar() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setChar(0, 'b');
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetDouble() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setDouble(0, 1);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetFloat() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
        try {
            assertThrows(ReadOnlyBufferException.class, new Executable() {
                @Override
                public void execute() {
                    buf.setFloat(0, 1);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    public void testSetInt() throws IOException {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
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
    public void testSetLong() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
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
    public void testSetMedium() {
        final ByteBuf buf = newBuffer(wrappedBuffer(new byte[8]));
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
    public void testGatheringWritesHeap() throws Exception {
        testGatheringWrites(buffer(), buffer());
    }

    @Test
    public void testGatheringWritesDirect() throws Exception {
        testGatheringWrites(directBuffer(), directBuffer());
    }

    @Test
    public void testGatheringWritesMixes() throws Exception {
        testGatheringWrites(buffer(), directBuffer());
    }

    @Test
    public void testGatheringWritesHeapPooled() throws Exception {
        testGatheringWrites(PooledByteBufAllocator.DEFAULT.heapBuffer(),
                PooledByteBufAllocator.DEFAULT.heapBuffer());
    }

    @Test
    public void testGatheringWritesDirectPooled() throws Exception {
        testGatheringWrites(PooledByteBufAllocator.DEFAULT.directBuffer(),
                PooledByteBufAllocator.DEFAULT.directBuffer());
    }

    @Test
    public void testGatheringWritesMixesPooled() throws Exception {
        testGatheringWrites(PooledByteBufAllocator.DEFAULT.heapBuffer(),
                PooledByteBufAllocator.DEFAULT.directBuffer());
    }

    private static void testGatheringWrites(ByteBuf buf1, ByteBuf buf2) throws Exception {
        CompositeByteBuf buf = compositeBuffer();
        buf.addComponent(buf1.writeBytes(new byte[]{1, 2}));
        buf.addComponent(buf2.writeBytes(new byte[]{1, 2}));
        buf.writerIndex(3);
        buf.readerIndex(1);

        AbstractByteBufTest.TestGatheringByteChannel channel = new AbstractByteBufTest.TestGatheringByteChannel();
        buf.readBytes(channel, 2);

        byte[] data = new byte[2];
        buf.getBytes(1, data);
        buf.release();

        assertArrayEquals(data, channel.writtenBytes());
    }

    @Test
    public void testGatheringWritesPartialHeap() throws Exception {
        testGatheringWritesPartial(buffer(), buffer());
    }

    @Test
    public void testGatheringWritesPartialDirect() throws Exception {
        testGatheringWritesPartial(directBuffer(), directBuffer());
    }

    @Test
    public void testGatheringWritesPartialMixes() throws Exception {
        testGatheringWritesPartial(buffer(), directBuffer());
    }

    @Test
    public void testGatheringWritesPartialHeapPooled() throws Exception {
        testGatheringWritesPartial(PooledByteBufAllocator.DEFAULT.heapBuffer(),
                PooledByteBufAllocator.DEFAULT.heapBuffer());
    }

    @Test
    public void testGatheringWritesPartialDirectPooled() throws Exception {
        testGatheringWritesPartial(PooledByteBufAllocator.DEFAULT.directBuffer(),
                PooledByteBufAllocator.DEFAULT.directBuffer());
    }

    @Test
    public void testGatheringWritesPartialMixesPooled() throws Exception {
        testGatheringWritesPartial(PooledByteBufAllocator.DEFAULT.heapBuffer(),
                PooledByteBufAllocator.DEFAULT.directBuffer());
    }

    private static void testGatheringWritesPartial(ByteBuf buf1, ByteBuf buf2) throws Exception {
        buf1.writeBytes(new byte[]{1, 2, 3, 4});
        buf2.writeBytes(new byte[]{1, 2, 3, 4});
        ByteBuf buf = newBuffer(buf1, buf2);
        AbstractByteBufTest.TestGatheringByteChannel channel = new AbstractByteBufTest.TestGatheringByteChannel(1);

        while (buf.isReadable()) {
            buf.readBytes(channel, buf.readableBytes());
        }

        byte[] data = new byte[8];
        buf.getBytes(0, data);
        assertArrayEquals(data, channel.writtenBytes());
        buf.release();
    }

    @Test
    public void testGatheringWritesSingleHeap() throws Exception {
        testGatheringWritesSingleBuf(buffer());
    }

    @Test
    public void testGatheringWritesSingleDirect() throws Exception {
        testGatheringWritesSingleBuf(directBuffer());
    }

    private static void testGatheringWritesSingleBuf(ByteBuf buf1) throws Exception {
        ByteBuf buf = newBuffer(buf1.writeBytes(new byte[]{1, 2, 3, 4}));
        buf.readerIndex(1);

        AbstractByteBufTest.TestGatheringByteChannel channel = new AbstractByteBufTest.TestGatheringByteChannel();
        buf.readBytes(channel, 2);

        byte[] data = new byte[2];
        buf.getBytes(1, data);
        assertArrayEquals(data, channel.writtenBytes());

        buf.release();
    }

    @Test
    public void testCopyingToOtherBuffer() {
        ByteBuf buf1 = directBuffer(10);
        ByteBuf buf2 = buffer(10);
        ByteBuf buf3 = directBuffer(10);
        buf1.writeBytes("a".getBytes(Charset.defaultCharset()));
        buf2.writeBytes("b".getBytes(Charset.defaultCharset()));
        buf3.writeBytes("c".getBytes(Charset.defaultCharset()));
        ByteBuf composite = unmodifiableBuffer(buf1, buf2, buf3);
        ByteBuf copy = directBuffer(3);
        ByteBuf copy2 = buffer(3);
        copy.setBytes(0, composite, 0, 3);
        copy2.setBytes(0, composite, 0, 3);
        copy.writerIndex(3);
        copy2.writerIndex(3);
        assertEquals(0, ByteBufUtil.compare(copy, composite));
        assertEquals(0, ByteBufUtil.compare(copy2, composite));
        assertEquals(0, ByteBufUtil.compare(copy, copy2));
        copy.release();
        copy2.release();
        composite.release();
    }

    @Test
    public void testCopyingToOutputStream() throws IOException {
        ByteBuf buf1 = directBuffer(10);
        ByteBuf buf2 = buffer(10);
        ByteBuf buf3 = directBuffer(10);
        buf1.writeBytes("a".getBytes(Charset.defaultCharset()));
        buf2.writeBytes("b".getBytes(Charset.defaultCharset()));
        buf3.writeBytes("c".getBytes(Charset.defaultCharset()));
        ByteBuf composite = unmodifiableBuffer(buf1, buf2, buf3);
        ByteBuf copy = directBuffer(3);
        ByteBuf copy2 = buffer(3);
        OutputStream copyStream = new ByteBufOutputStream(copy);
        OutputStream copy2Stream = new ByteBufOutputStream(copy2);
        try {
            composite.getBytes(0, copyStream, 3);
            composite.getBytes(0, copy2Stream, 3);
            assertEquals(0, ByteBufUtil.compare(copy, composite));
            assertEquals(0, ByteBufUtil.compare(copy2, composite));
            assertEquals(0, ByteBufUtil.compare(copy, copy2));
        } finally {
            copy.release();
            copy2.release();
            copyStream.close();
            copy2Stream.close();
            composite.release();
        }
    }

    @Test
    public void testExtractNioBuffers() {
        ByteBuf buf1 = directBuffer(10);
        ByteBuf buf2 = buffer(10);
        ByteBuf buf3 = directBuffer(10);
        buf1.writeBytes("a".getBytes(Charset.defaultCharset()));
        buf2.writeBytes("b".getBytes(Charset.defaultCharset()));
        buf3.writeBytes("c".getBytes(Charset.defaultCharset()));
        ByteBuf composite = unmodifiableBuffer(buf1, buf2, buf3);
        ByteBuffer[] byteBuffers = composite.nioBuffers(0, 3);
        assertEquals(3, byteBuffers.length);
        assertEquals(1, byteBuffers[0].limit());
        assertEquals(1, byteBuffers[1].limit());
        assertEquals(1, byteBuffers[2].limit());
        composite.release();
    }

    @Test
    public void testEmptyArray() {
        ByteBuf buf = newBuffer(new ByteBuf[0]);
        buf.release();
    }

    @Test
    public void testHasMemoryAddressWithSingleBuffer() {
        ByteBuf buf1 = directBuffer(10);
        if (!buf1.hasMemoryAddress()) {
            buf1.release();
            return;
        }
        ByteBuf buf = newBuffer(buf1);
        assertTrue(buf.hasMemoryAddress());
        assertEquals(buf1.memoryAddress(), buf.memoryAddress());
        buf.release();
    }

    @Test
    public void testHasMemoryAddressWhenEmpty() {
        Assumptions.assumeTrue(EMPTY_BUFFER.hasMemoryAddress());
        ByteBuf buf = newBuffer(new ByteBuf[0]);
        assertTrue(buf.hasMemoryAddress());
        assertEquals(EMPTY_BUFFER.memoryAddress(), buf.memoryAddress());
        buf.release();
    }

    @Test
    public void testHasNoMemoryAddressWhenMultipleBuffers() {
        ByteBuf buf1 = directBuffer(10);
        if (!buf1.hasMemoryAddress()) {
            buf1.release();
            return;
        }

        ByteBuf buf2 = directBuffer(10);
        ByteBuf buf = newBuffer(buf1, buf2);
        assertFalse(buf.hasMemoryAddress());
        try {
            buf.memoryAddress();
            fail();
        } catch (UnsupportedOperationException expected) {
            // expected
        } finally {
            buf.release();
        }
    }

    @Test
    public void testHasArrayWithSingleBuffer() {
        ByteBuf buf1 = buffer(10);
        ByteBuf buf = newBuffer(buf1);
        assertTrue(buf.hasArray());
        assertArrayEquals(buf1.array(), buf.array());
        buf.release();
    }

    @Test
    public void testHasArrayWhenEmptyAndIsDirect() {
        ByteBuf buf = newBuffer(new ByteBuf[0]);
        assertTrue(buf.hasArray());
        assertArrayEquals(EMPTY_BUFFER.array(), buf.array());
        assertEquals(EMPTY_BUFFER.isDirect(), buf.isDirect());
        assertEquals(EMPTY_BUFFER.hasMemoryAddress(), buf.hasMemoryAddress());
        if (buf.hasMemoryAddress()) {
            assertEquals(EMPTY_BUFFER.memoryAddress(), buf.memoryAddress());
        }
        buf.release();
    }

    @Test
    public void testHasNoArrayWhenMultipleBuffers() {
        ByteBuf buf1 = buffer(10);
        ByteBuf buf2 = buffer(10);
        final ByteBuf buf = newBuffer(buf1, buf2);
        assertFalse(buf.hasArray());
        try {
            assertThrows(UnsupportedOperationException.class, new Executable() {
                @Override
                public void execute() {
                    buf.array();
                }
            });
        } finally {
            buf.release();
        }
    }
}
