/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.buffer;

import static net.gleamynode.netty.buffer.ChannelBuffers.*;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractChannelBufferTest {

    private static final int CAPACITY = 4096;
    private static final int BLOCK_SIZE = 128;

    private long seed;
    private Random random;
    private ChannelBuffer buffer;

    protected abstract ChannelBuffer newBuffer(int capacity);
    protected abstract ChannelBuffer[] components();

    @Before
    public void init() {
        buffer = newBuffer(CAPACITY);
        seed = System.currentTimeMillis();
        random = new Random(seed);
    }

    @After
    public void dispose() {
        buffer = null;
    }

    @Test
    public void initialState() {
        assertEquals(CAPACITY, buffer.capacity());
        assertEquals(0, buffer.readerIndex());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void readerIndexBoundaryCheck1() {
        try {
            buffer.writerIndex(0);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.readerIndex(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void readerIndexBoundaryCheck2() {
        try {
            buffer.writerIndex(buffer.capacity());
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.readerIndex(buffer.capacity() + 1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void readerIndexBoundaryCheck3() {
        try {
            buffer.writerIndex(CAPACITY / 2);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.readerIndex(CAPACITY * 3 / 2);
    }

    @Test
    public void readerIndexBoundaryCheck4() {
        buffer.writerIndex(0);
        buffer.readerIndex(0);
        buffer.writerIndex(buffer.capacity());
        buffer.readerIndex(buffer.capacity());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void writerIndexBoundaryCheck1() {
        buffer.writerIndex(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void writerIndexBoundaryCheck2() {
        try {
            buffer.writerIndex(CAPACITY);
            buffer.readerIndex(CAPACITY);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.writerIndex(buffer.capacity() + 1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void writerIndexBoundaryCheck3() {
        try {
            buffer.writerIndex(CAPACITY);
            buffer.readerIndex(CAPACITY / 2);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.writerIndex(CAPACITY / 4);
    }

    @Test
    public void writerIndexBoundaryCheck4() {
        buffer.writerIndex(0);
        buffer.readerIndex(0);
        buffer.writerIndex(CAPACITY);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getByteBoundaryCheck1() {
        buffer.getByte(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getByteBoundaryCheck2() {
        buffer.getByte(buffer.capacity());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getShortBoundaryCheck1() {
        buffer.getShort(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getShortBoundaryCheck2() {
        buffer.getShort(buffer.capacity() - 1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getMediumBoundaryCheck1() {
        buffer.getMedium(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getMediumBoundaryCheck2() {
        buffer.getMedium(buffer.capacity() - 2);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getIntBoundaryCheck1() {
        buffer.getInt(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getIntBoundaryCheck2() {
        buffer.getInt(buffer.capacity() - 3);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getLongBoundaryCheck1() {
        buffer.getLong(-1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getLongBoundaryCheck2() {
        buffer.getLong(buffer.capacity() - 7);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getByteArrayBoundaryCheck1() {
        buffer.getBytes(-1, new byte[0]);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getByteArrayBoundaryCheck2() {
        buffer.getBytes(-1, new byte[0], 0, 0);
    }

    public void getByteArrayBoundaryCheck3() {
        byte[] dst = new byte[4];
        buffer.setInt(0, 0x01020304);
        try {
            buffer.getBytes(0, dst, -1, 4);
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Success
        }

        // No partial copy is expected.
        assertEquals(0, dst[0]);
        assertEquals(0, dst[1]);
        assertEquals(0, dst[2]);
        assertEquals(0, dst[3]);
    }

    public void getByteArrayBoundaryCheck4() {
        byte[] dst = new byte[4];
        buffer.setInt(0, 0x01020304);
        try {
            buffer.getBytes(0, dst, 1, 4);
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Success
        }

        // No partial copy is expected.
        assertEquals(0, dst[0]);
        assertEquals(0, dst[1]);
        assertEquals(0, dst[2]);
        assertEquals(0, dst[3]);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getByteBufferBoundaryCheck() {
        buffer.getBytes(-1, ByteBuffer.allocate(0));
    }

    public void getByteBufferState1() {
        ByteBuffer dst = ByteBuffer.allocate(8);
        dst.position(1);

        buffer.setInt(0, 0x01020304);
        buffer.getBytes(0, dst);

        assertEquals(0x0001020304000000L, dst.getLong(0));
        assertEquals(5, dst.position());
        assertEquals(8, dst.limit());
    }

    public void getByteBufferState2() {
        ByteBuffer dst = ByteBuffer.allocate(4);
        dst.position(1);
        dst.limit(3);

        buffer.setInt(0, 0x01020304);
        buffer.getBytes(1, dst);
        assertEquals(0x00020300, dst.getInt(0));
        assertEquals(3, dst.position());
        assertEquals(3, dst.limit());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void getDirectByteBufferBoundaryCheck() {
        buffer.getBytes(-1, ByteBuffer.allocateDirect(0));
    }

    public void getDirectByteBufferState1() {
        ByteBuffer dst = ByteBuffer.allocateDirect(8);
        dst.position(1);

        buffer.setInt(0, 0x01020304);
        buffer.getBytes(0, dst);

        assertEquals(0x0001020304000000L, dst.getLong(0));
        assertEquals(5, dst.position());
        assertEquals(8, dst.limit());
    }

    public void getDirectByteBufferState2() {
        ByteBuffer dst = ByteBuffer.allocateDirect(4);
        dst.position(1);
        dst.limit(3);

        buffer.setInt(0, 0x01020304);
        buffer.getBytes(1, dst);
        assertEquals(0x00020300, dst.getInt(0));
        assertEquals(3, dst.position());
        assertEquals(3, dst.limit());
    }

    @Test
    public void testRandomByteAccess() {
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            buffer.setByte(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            assertEquals(value, buffer.getByte(i));
        }
    }

    @Test
    public void testRandomShortAccess() {
        for (int i = 0; i < buffer.capacity() - 1; i += 2) {
            short value = (short) random.nextInt();
            buffer.setShort(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 1; i += 2) {
            short value = (short) random.nextInt();
            assertEquals(value, buffer.getShort(i));
        }
    }

    @Test
    public void testRandomMediumAccess() {
        for (int i = 0; i < buffer.capacity() - 2; i += 3) {
            int value = random.nextInt();
            buffer.setMedium(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 2; i += 3) {
            int value = random.nextInt() & 0x00FFFFFF;
            assertEquals(value, buffer.getMedium(i));
        }
    }

    @Test
    public void testRandomIntAccess() {
        for (int i = 0; i < buffer.capacity() - 3; i += 4) {
            int value = random.nextInt();
            buffer.setInt(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 3; i += 4) {
            int value = random.nextInt();
            assertEquals(value, buffer.getInt(i));
        }
    }

    @Test
    public void testRandomLongAccess() {
        for (int i = 0; i < buffer.capacity() - 7; i += 8) {
            long value = random.nextLong();
            buffer.setLong(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 7; i += 8) {
            long value = random.nextLong();
            assertEquals(value, buffer.getLong(i));
        }
    }

    @Test
    public void testSequentialByteAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.writable());
            buffer.writeByte(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.writable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.readable());
            assertEquals(value, buffer.readByte());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.readable());
        assertFalse(buffer.writable());
    }

    @Test
    public void testSequentialShortAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 2) {
            short value = (short) random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.writable());
            buffer.writeShort(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.writable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 2) {
            short value = (short) random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.readable());
            assertEquals(value, buffer.readShort());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.readable());
        assertFalse(buffer.writable());
    }

    @Test
    public void testSequentialMediumAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() / 3 * 3; i += 3) {
            int value = random.nextInt() & 0x00FFFFFF;
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.writable());
            buffer.writeMedium(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity() / 3 * 3, buffer.writerIndex());
        assertEquals(buffer.capacity() % 3, buffer.writableBytes());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() / 3 * 3; i += 3) {
            int value = random.nextInt() & 0x00FFFFFF;
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.readable());
            assertEquals(value, buffer.readMedium());
        }

        assertEquals(buffer.capacity() / 3 * 3, buffer.readerIndex());
        assertEquals(buffer.capacity() / 3 * 3, buffer.writerIndex());
        assertEquals(0, buffer.readableBytes());
        assertEquals(buffer.capacity() % 3, buffer.writableBytes());
    }

    @Test
    public void testSequentialIntAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 4) {
            int value = random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.writable());
            buffer.writeInt(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.writable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 4) {
            int value = random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.readable());
            assertEquals(value, buffer.readInt());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.readable());
        assertFalse(buffer.writable());
    }

    @Test
    public void testSequentialLongAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 8) {
            long value = random.nextLong();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.writable());
            buffer.writeLong(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.writable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 8) {
            long value = random.nextLong();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.readable());
            assertEquals(value, buffer.readLong());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.readable());
        assertFalse(buffer.writable());
    }

    @Test
    public void testByteArrayTransfer() {
        byte[] value = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            buffer.getBytes(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testRandomHeapBufferTransfer() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = wrappedBuffer(valueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            buffer.getBytes(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
        }
    }

    @Test
    public void testRandomDirectBufferTransfer() {
        byte[] tmp = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = directBuffer(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(tmp);
            value.setBytes(0, tmp, 0, value.capacity());
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        ChannelBuffer expectedValue = directBuffer(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(tmp);
            expectedValue.setBytes(0, tmp, 0, expectedValue.capacity());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            buffer.getBytes(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
        }
    }

    @Test
    public void testRandomByteBufferTransfer() {
        ByteBuffer value = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value.array());
            value.clear().position(random.nextInt(BLOCK_SIZE));
            value.limit(value.position() + BLOCK_SIZE);
            buffer.setBytes(i, value);
        }

        random.setSeed(seed);
        ByteBuffer expectedValue = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue.array());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.clear().position(valueOffset).limit(valueOffset + BLOCK_SIZE);
            buffer.getBytes(i, value);
            assertEquals(valueOffset + BLOCK_SIZE, value.position());
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.get(j), value.get(j));
            }
        }
    }

    @Test
    public void testSequentialHeapBufferTransfer1() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = wrappedBuffer(valueContent);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
            assertEquals(0, value.readerIndex());
            assertEquals(valueContent.length, value.writerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(0, value.readerIndex());
            assertEquals(valueContent.length, value.writerIndex());
        }
    }

    @Test
    public void testSequentialHeapBufferTransfer2() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = wrappedBuffer(valueContent);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            int readerIndex = random.nextInt(BLOCK_SIZE);
            value.readerIndex(readerIndex);
            value.writerIndex(readerIndex + BLOCK_SIZE);
            buffer.writeBytes(value);
            assertEquals(readerIndex + BLOCK_SIZE, value.writerIndex());
            assertEquals(value.writerIndex(), value.readerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            value.readerIndex(valueOffset);
            value.writerIndex(valueOffset);
            buffer.readBytes(value, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(valueOffset, value.readerIndex());
            assertEquals(valueOffset + BLOCK_SIZE, value.writerIndex());
        }
    }

    @Test
    public void testSequentialDirectBufferTransfer1() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = directBuffer(BLOCK_SIZE * 2);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            value.setBytes(0, valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
            assertEquals(0, value.readerIndex());
            assertEquals(0, value.writerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.setBytes(0, valueContent);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(0, value.readerIndex());
            assertEquals(0, value.writerIndex());
        }
    }

    @Test
    public void testSequentialDirectBufferTransfer2() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = directBuffer(BLOCK_SIZE * 2);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            value.setBytes(0, valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            int readerIndex = random.nextInt(BLOCK_SIZE);
            value.readerIndex(0);
            value.writerIndex(readerIndex + BLOCK_SIZE);
            value.readerIndex(readerIndex);
            buffer.writeBytes(value);
            assertEquals(readerIndex + BLOCK_SIZE, value.writerIndex());
            assertEquals(value.writerIndex(), value.readerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            value.setBytes(0, valueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            value.readerIndex(valueOffset);
            value.writerIndex(valueOffset);
            buffer.readBytes(value, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(valueOffset, value.readerIndex());
            assertEquals(valueOffset + BLOCK_SIZE, value.writerIndex());
        }
    }

    @Test
    public void testSequentialByteBufferTransfer() {
        buffer.writerIndex(0);
        ByteBuffer value = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value.array());
            value.clear().position(random.nextInt(BLOCK_SIZE));
            value.limit(value.position() + BLOCK_SIZE);
            buffer.writeBytes(value);
        }

        random.setSeed(seed);
        ByteBuffer expectedValue = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue.array());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.clear().position(valueOffset).limit(valueOffset + BLOCK_SIZE);
            buffer.readBytes(value);
            assertEquals(valueOffset + BLOCK_SIZE, value.position());
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.get(j), value.get(j));
            }
        }
    }

    @Test
    public void testDiscardReadBytes() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 4) {
            buffer.writeInt(i);
        }
        ChannelBuffer copy = copiedBuffer(buffer);

        // Make sure there's no effect if called when readerIndex is 0.
        buffer.readerIndex(CAPACITY / 4);
        buffer.markReaderIndex();
        buffer.writerIndex(CAPACITY / 3);
        buffer.markWriterIndex();
        buffer.readerIndex(0);
        buffer.writerIndex(CAPACITY / 2);
        buffer.discardReadBytes();

        assertEquals(0, buffer.readerIndex());
        assertEquals(CAPACITY / 2, buffer.writerIndex());
        assertEquals(copy.slice(0, CAPACITY / 2), buffer.slice(0, CAPACITY / 2));
        buffer.resetReaderIndex();
        assertEquals(CAPACITY / 4, buffer.readerIndex());
        buffer.resetWriterIndex();
        assertEquals(CAPACITY / 3, buffer.writerIndex());

        // Make sure bytes after writerIndex is not copied.
        buffer.readerIndex(1);
        buffer.writerIndex(CAPACITY / 2);
        buffer.discardReadBytes();

        assertEquals(0, buffer.readerIndex());
        assertEquals(CAPACITY / 2 - 1, buffer.writerIndex());
        assertEquals(copy.slice(1, CAPACITY / 2 - 1), buffer.slice(0, CAPACITY / 2 - 1));
        assertEquals(copy.slice(CAPACITY / 2, CAPACITY / 2), buffer.slice(CAPACITY / 2, CAPACITY / 2));

        // Marks also should be relocated.
        buffer.resetReaderIndex();
        assertEquals(CAPACITY / 4 - 1, buffer.readerIndex());
        buffer.resetWriterIndex();
        assertEquals(CAPACITY / 3 - 1, buffer.writerIndex());
    }

    @Test
    public void testCopy() {
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            buffer.setByte(i, value);
        }

        final int readerIndex = CAPACITY / 3;
        final int writerIndex = CAPACITY * 2 / 3;
        buffer.setIndex(readerIndex, writerIndex);

        // Make sure all properties are cpoied.
        ChannelBuffer copy = buffer.copy();
        assertEquals(0, copy.readerIndex());
        assertEquals(buffer.readableBytes(), copy.writerIndex());
        assertEquals(copy.capacity(), buffer.readableBytes());
        for (int i = 0; i < copy.capacity(); i ++) {
            assertEquals(buffer.getByte(i + readerIndex), copy.getByte(i));
        }

        // Make sure the buffer contents are independent from each other.
        buffer.setByte(readerIndex, (byte) (buffer.getByte(readerIndex) + 1));
        assertTrue(buffer.getByte(readerIndex) != copy.getByte(0));
        copy.setByte(1, (byte) (copy.getByte(1) + 1));
        assertTrue(buffer.getByte(readerIndex + 1) != copy.getByte(1));
    }
}
