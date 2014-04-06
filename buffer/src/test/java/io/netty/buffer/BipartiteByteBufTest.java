/*
 * Copyright 2014 The Netty Project
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

import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ScatteringByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BipartiteByteBufTest extends AbstractByteBufTest {
    private BipartiteByteBuf buffer;

    @Test
    public void testPartGetterSetter() {
        BipartiteByteBuf bbuf = UnpooledByteBufAllocator.DEFAULT.bipartiteBuffer();

        ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer();
        ByteBuf buf2 = UnpooledByteBufAllocator.DEFAULT.buffer();

        try {
            bbuf.part2(buf1);
            fail();
        } catch (IllegalStateException e) {
            // avoid checkstyle complaints
            assertTrue(true);
        }

        assertNull(bbuf.part1());
        assertNull(bbuf.part2());

        bbuf.part1(buf1)
            .part2(buf2);

        assertEquals(buf1, bbuf.part1());
        assertEquals(buf2, bbuf.part2());

        bbuf.part1(buf2)
            .part2(buf1);

        assertEquals(buf1, bbuf.part2());
        assertEquals(buf2, bbuf.part1());
    }

    @Test
    public void testGetByte() {
        BipartiteByteBuf bbuf = newBuf1to25();
        assertEquals(ByteOrder.BIG_ENDIAN, bbuf.order());
        for (int i = 1; i <= 25; i++) {
            assertEquals(i, bbuf.getByte(i - 1));
        }
    }

    @Test
    public void testGetShort() {
        BipartiteByteBuf bbuf = newBuf1to25();
        assertEquals(ByteOrder.BIG_ENDIAN, bbuf.order());
        // data from buf1
        assertEquals(0x0203, bbuf.getShort(1));
        // data from buf2
        assertEquals(0x1112, bbuf.getShort(16));
        // first byte from buf1, second byte from buf2
        assertEquals(0x1011, bbuf.getShort(15));
    }

    @Test
    public void testGetMedium() {
        BipartiteByteBuf bbuf = newBuf1to25();
        assertEquals(ByteOrder.BIG_ENDIAN, bbuf.order());
        // data from buf1
        assertEquals(0x040506, bbuf.getMedium(3));
        // data from buf2
        assertEquals(0x171819, bbuf.getMedium(22));
        // one byte from buf1, two bytes from buf2
        assertEquals(0x101112, bbuf.getMedium(15));
    }

    @Test
    public void testGetInt() {
        BipartiteByteBuf bbuf = newBuf1to25();
        assertEquals(ByteOrder.BIG_ENDIAN, bbuf.order());
        // data from buf1
        assertEquals(0x0C0D0E0F, bbuf.getInt(11));
        // data from buf2
        assertEquals(0x12131415, bbuf.getInt(17));
        // three bytes from buf1, one byte from buf2
        assertEquals(0x0E0F1011, bbuf.getInt(13));
        // two bytes from buf1, two bytes from buf2
        assertEquals(0x0F101112, bbuf.getInt(14));
        // one byte from buf1, three bytes from buf2
        assertEquals(0x10111213, bbuf.getInt(15));
    }

    @Test
    public void testGetLong() {
        BipartiteByteBuf bbuf = newBuf1to25();
        assertEquals(ByteOrder.BIG_ENDIAN, bbuf.order());
        // data from buf1
        assertEquals(0x08090A0B0C0D0E0FL, bbuf.getLong(7));
        // data from buf2
        assertEquals(0x1213141516171819L, bbuf.getLong(17));
        // two bytes from part1, six bytes from part2
        assertEquals(0x0F10111213141516L, bbuf.getLong(14));
        // 7 bytes from part1, one byte from part2
        assertEquals(0x0A0B0C0D0E0F1011L, bbuf.getLong(9));
    }

    @Test
    public void testSetByte() {
        BipartiteByteBuf bbuf = newBufZeroed25();
        assertEquals(ByteOrder.BIG_ENDIAN, bbuf.order());
        for (int i = 1; i <= bbuf.capacity(); i++) {
            bbuf.setByte(i - 1, i);
        }
        for (int i = 1; i <= bbuf.capacity(); i++) {
            assertEquals(i, bbuf.getByte(i - 1));
        }
    }

    @Test
    public void testSetShort() {
        BipartiteByteBuf bbuf = newBufZeroed25();
        assertEquals(ByteOrder.BIG_ENDIAN, bbuf.order());
        // set value in part1
        bbuf.setShort(0, 1025);
        assertEquals(1025, bbuf.getShort(0));
        // set value in part2
        bbuf.setShort(20, 2049);
        assertEquals(2049, bbuf.getShort(20));
        // set last byte of part1, first byte of part2
        bbuf.setShort(15, 513);
        assertEquals(513, bbuf.getShort(15));
    }

    @Ignore (value = "https://github.com/netty/netty/issues/2373")
    @Test
    public void testIsDirect() {
        BipartiteByteBuf bbuf = UnpooledByteBufAllocator.DEFAULT.bipartiteBuffer();
        assertFalse(bbuf.isDirect());
        bbuf.part1(UnpooledByteBufAllocator.DEFAULT.directBuffer(8));
        assertTrue(bbuf.isDirect());
        bbuf.part2(UnpooledByteBufAllocator.DEFAULT.heapBuffer(8));
        assertFalse(bbuf.isDirect());
    }

    @Test
    public void testCapacity() {
        BipartiteByteBuf bbuf = UnpooledByteBufAllocator.DEFAULT.bipartiteBuffer();
        assertEquals(0, bbuf.capacity());

        ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer(1024);
        bbuf.part1(buf1);
        assertEquals(0, bbuf.capacity());
        buf1.setIndex(0, 100);
        assertEquals(0, bbuf.capacity());
        bbuf.part1(buf1);
        assertEquals(100, bbuf.capacity());
        buf1.setIndex(0, 10);
        bbuf.part2(buf1);
        assertEquals(110, bbuf.capacity());
    }

    @Test
    public void testSetBytesScatteringByteChannel() throws IOException {
        BipartiteByteBuf bbuf = UnpooledByteBufAllocator.DEFAULT.bipartiteBuffer();
        ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer(1024).setIndex(0, 1024);
        ByteBuf buf2 = UnpooledByteBufAllocator.DEFAULT.buffer(2048).setIndex(0, 2048);
        bbuf.part1(buf1).part2(buf2);

        byte[] data = new byte[3072];
        new Random().nextBytes(data);

        TestScatteringByteChannel ch = new TestScatteringByteChannel(data);
        bbuf.setBytes(0, ch, 3072);

        for (int i = 0; i < data.length; i++) {
            assertEquals(data[i], bbuf.getByte(i));
        }
    }

    @Test
    public void testCapacityResize() {
        BipartiteByteBuf bbuf = UnpooledByteBufAllocator.DEFAULT.bipartiteBuffer();
        assertEquals(0, bbuf.capacity());
        bbuf.capacity(0);
        assertEquals(0, bbuf.capacity());
        // Increase capacity
        bbuf.capacity(4096);
        assertEquals(4096, bbuf.capacity());
        for (int i = 0; i < 1024; i++) {
            bbuf.writeInt(i);
        }
        for (int i = 0; i < 1024; i++) {
            assertEquals(i, bbuf.readInt());
        }

        assertNotNull(bbuf.part1());
        assertNull(bbuf.part2());

        bbuf.capacity(8192);
        for (int i = 0; i < 1024; i++) {
            bbuf.writeInt(1024 + i);
        }
        bbuf.resetReaderIndex();
        for (int i = 0; i < 2048; i++) {
            assertEquals(i, bbuf.readInt());
        }

        assertNotNull(bbuf.part1());
        assertNotNull(bbuf.part2());

        bbuf.capacity(12288);
        for (int i = 0; i < 1024; i++) {
            bbuf.writeInt(2048 + i);
        }
        bbuf.resetReaderIndex();
        for (int i = 0; i < 3072; i++) {
            assertEquals(i, bbuf.readInt());
        }

        // Decrease capacity
        bbuf.capacity(7168);
        assertEquals(7168, bbuf.capacity());
        assertEquals(7168, bbuf.readerIndex());
        assertEquals(7168, bbuf.writerIndex());
        bbuf.resetReaderIndex();
        for (int i = 0; i < 7168 / 4; i++) {
            assertEquals(i, bbuf.readInt());
        }

        bbuf.capacity(1024);
        assertEquals(1024, bbuf.capacity());
        assertEquals(1024, bbuf.readerIndex());
        assertEquals(1024, bbuf.writerIndex());
        assertNull(bbuf.part2());
        bbuf.resetReaderIndex();
        for (int i = 0; i < 256; i++) {
            assertEquals(i, bbuf.readInt());
        }

        bbuf.capacity(0);
        assertEquals(0, bbuf.capacity());
        assertNull(bbuf.part1());
    }

    private BipartiteByteBuf newBuf1to25() {
        BipartiteByteBuf bbuf = UnpooledByteBufAllocator.DEFAULT.bipartiteBuffer();
        ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer(16);
        buf1.writeBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        ByteBuf buf2 = UnpooledByteBufAllocator.DEFAULT.buffer(9);
        buf2.writeBytes(new byte[] {17, 18, 19, 20, 21, 22, 23, 24, 25});
        return bbuf.part1(buf1).part2(buf2);
    }

    private BipartiteByteBuf newBufZeroed25() {
        BipartiteByteBuf bbuf = UnpooledByteBufAllocator.DEFAULT.bipartiteBuffer();
        ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer(16);
        buf1.writeLong(0).writeLong(0);
        ByteBuf buf2 = UnpooledByteBufAllocator.DEFAULT.buffer(9);
        buf2.writeLong(0).writeByte(0);
        return bbuf.part1(buf1).part2(buf2);
    }

    @Override
    protected ByteBuf newBuffer(int capacity) {
        buffer = UnpooledByteBufAllocator.DEFAULT.bipartiteBuffer();
        int part1Len = capacity / 2;
        int part2Len = (int) Math.ceil(capacity / 2.0);

        ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.buffer(part1Len);
        for (int i = 0; i < part1Len; i++) {
            buf1.writeByte(0);
        }
        ByteBuf buf2 = UnpooledByteBufAllocator.DEFAULT.buffer(part2Len);
        for (int i = 0; i < part2Len; i++) {
            buf2.writeByte(0);
        }
        buffer.part1(buf1).part2(buf2);
        assertEquals(capacity, buffer.capacity());

        return buffer;
    }

    @Override
    protected ByteBuf[] components() {
        return new ByteBuf[] {buffer.part1(), buffer.part2()};
    }

    static final class TestScatteringByteChannel implements ScatteringByteChannel {
        private byte[] data;
        private int index;

        TestScatteringByteChannel(byte[] data) {
            this.data = data;
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            long bytesRead = 0;
            for (int i = offset; i < length; i++) {
                for (; index < data.length && dsts[i].hasRemaining(); index++) {
                    dsts[i].put(data[index]);
                    bytesRead++;
                }
            }
            return bytesRead;
        }

        @Override
        public long read(ByteBuffer[] dsts) throws IOException {
            return read(dsts, 0, dsts.length);
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return (int) Math.min(read(new ByteBuffer[] {dst}, 0, 1), Integer.MAX_VALUE);
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() throws IOException {
            data = null;
        }
    }
}
