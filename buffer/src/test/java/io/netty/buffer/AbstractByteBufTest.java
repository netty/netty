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

import io.netty.util.CharsetUtil;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.Channels;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.Unpooled.*;
import static io.netty.util.ReferenceCountUtil.*;
import static io.netty.util.internal.EmptyArrays.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * An abstract test class for channel buffers
 */
public abstract class AbstractByteBufTest {

    private static final int CAPACITY = 4096; // Must be even
    private static final int BLOCK_SIZE = 128;

    private long seed;
    private Random random;
    private ByteBuf buffer;

    protected abstract ByteBuf newBuffer(int capacity);

    protected boolean discardReadBytesDoesNotMoveWritableBytes() {
        return true;
    }

    @Before
    public void init() {
        buffer = newBuffer(CAPACITY);
        seed = System.currentTimeMillis();
        random = new Random(seed);
    }

    @After
    public void dispose() {
        if (buffer != null) {
            assertThat(buffer.release(), is(true));
            assertThat(buffer.refCnt(), is(0));

            try {
                buffer.release();
            } catch (Exception e) {
                // Ignore.
            }
            buffer = null;
        }
    }

    @Test
    public void initialState() {
        assertEquals(CAPACITY, buffer.capacity());
        assertEquals(0, buffer.readerIndex());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void readerIndexBoundaryCheck1() {
        try {
            buffer.writerIndex(0);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.readerIndex(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void readerIndexBoundaryCheck2() {
        try {
            buffer.writerIndex(buffer.capacity());
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.readerIndex(buffer.capacity() + 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
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

    @Test(expected = IndexOutOfBoundsException.class)
    public void writerIndexBoundaryCheck1() {
        buffer.writerIndex(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void writerIndexBoundaryCheck2() {
        try {
            buffer.writerIndex(CAPACITY);
            buffer.readerIndex(CAPACITY);
        } catch (IndexOutOfBoundsException e) {
            fail();
        }
        buffer.writerIndex(buffer.capacity() + 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
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

        buffer.writeBytes(ByteBuffer.wrap(EMPTY_BYTES));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getBooleanBoundaryCheck1() {
        buffer.getBoolean(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getBooleanBoundaryCheck2() {
        buffer.getBoolean(buffer.capacity());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteBoundaryCheck1() {
        buffer.getByte(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteBoundaryCheck2() {
        buffer.getByte(buffer.capacity());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getShortBoundaryCheck1() {
        buffer.getShort(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getShortBoundaryCheck2() {
        buffer.getShort(buffer.capacity() - 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getMediumBoundaryCheck1() {
        buffer.getMedium(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getMediumBoundaryCheck2() {
        buffer.getMedium(buffer.capacity() - 2);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getIntBoundaryCheck1() {
        buffer.getInt(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getIntBoundaryCheck2() {
        buffer.getInt(buffer.capacity() - 3);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getLongBoundaryCheck1() {
        buffer.getLong(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getLongBoundaryCheck2() {
        buffer.getLong(buffer.capacity() - 7);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteArrayBoundaryCheck1() {
        buffer.getBytes(-1, EMPTY_BYTES);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteArrayBoundaryCheck2() {
        buffer.getBytes(-1, EMPTY_BYTES, 0, 0);
    }

    @Test
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

    @Test
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

    @Test(expected = IndexOutOfBoundsException.class)
    public void getByteBufferBoundaryCheck() {
        buffer.getBytes(-1, ByteBuffer.allocate(0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void copyBoundaryCheck1() {
        buffer.copy(-1, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void copyBoundaryCheck2() {
        buffer.copy(0, buffer.capacity() + 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void copyBoundaryCheck3() {
        buffer.copy(buffer.capacity() + 1, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void copyBoundaryCheck4() {
        buffer.copy(buffer.capacity(), 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void setIndexBoundaryCheck1() {
        buffer.setIndex(-1, CAPACITY);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void setIndexBoundaryCheck2() {
        buffer.setIndex(CAPACITY / 2, CAPACITY / 4);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void setIndexBoundaryCheck3() {
        buffer.setIndex(0, CAPACITY + 1);
    }

    @Test
    public void getByteBufferState() {
        ByteBuffer dst = ByteBuffer.allocate(4);
        dst.position(1);
        dst.limit(3);

        buffer.setByte(0, (byte) 1);
        buffer.setByte(1, (byte) 2);
        buffer.setByte(2, (byte) 3);
        buffer.setByte(3, (byte) 4);
        buffer.getBytes(1, dst);

        assertEquals(3, dst.position());
        assertEquals(3, dst.limit());

        dst.clear();
        assertEquals(0, dst.get(0));
        assertEquals(2, dst.get(1));
        assertEquals(3, dst.get(2));
        assertEquals(0, dst.get(3));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getDirectByteBufferBoundaryCheck() {
        buffer.getBytes(-1, ByteBuffer.allocateDirect(0));
    }

    @Test
    public void getDirectByteBufferState() {
        ByteBuffer dst = ByteBuffer.allocateDirect(4);
        dst.position(1);
        dst.limit(3);

        buffer.setByte(0, (byte) 1);
        buffer.setByte(1, (byte) 2);
        buffer.setByte(2, (byte) 3);
        buffer.setByte(3, (byte) 4);
        buffer.getBytes(1, dst);

        assertEquals(3, dst.position());
        assertEquals(3, dst.limit());

        dst.clear();
        assertEquals(0, dst.get(0));
        assertEquals(2, dst.get(1));
        assertEquals(3, dst.get(2));
        assertEquals(0, dst.get(3));
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
    public void testRandomUnsignedByteAccess() {
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            buffer.setByte(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i ++) {
            int value = random.nextInt() & 0xFF;
            assertEquals(value, buffer.getUnsignedByte(i));
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
    public void testRandomUnsignedShortAccess() {
        for (int i = 0; i < buffer.capacity() - 1; i += 2) {
            short value = (short) random.nextInt();
            buffer.setShort(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 1; i += 2) {
            int value = random.nextInt() & 0xFFFF;
            assertEquals(value, buffer.getUnsignedShort(i));
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
            int value = random.nextInt() << 8 >> 8;
            assertEquals(value, buffer.getMedium(i));
        }
    }

    @Test
    public void testRandomUnsignedMediumAccess() {
        for (int i = 0; i < buffer.capacity() - 2; i += 3) {
            int value = random.nextInt();
            buffer.setMedium(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 2; i += 3) {
            int value = random.nextInt() & 0x00FFFFFF;
            assertEquals(value, buffer.getUnsignedMedium(i));
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
    public void testRandomUnsignedIntAccess() {
        for (int i = 0; i < buffer.capacity() - 3; i += 4) {
            int value = random.nextInt();
            buffer.setInt(i, value);
        }

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() - 3; i += 4) {
            long value = random.nextInt() & 0xFFFFFFFFL;
            assertEquals(value, buffer.getUnsignedInt(i));
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
    public void testSetZero() {
        buffer.clear();
        while (buffer.isWritable()) {
            buffer.writeByte((byte) 0xFF);
        }

        for (int i = 0; i < buffer.capacity();) {
            int length = Math.min(buffer.capacity() - i, random.nextInt(32));
            buffer.setZero(i, length);
            i += length;
        }

        for (int i = 0; i < buffer.capacity(); i ++) {
            assertEquals(0, buffer.getByte(i));
        }
    }

    @Test
    public void testSequentialByteAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeByte(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readByte());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
    }

    @Test
    public void testSequentialUnsignedByteAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeByte(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i ++) {
            int value = random.nextInt() & 0xFF;
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readUnsignedByte());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
    }

    @Test
    public void testSequentialShortAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 2) {
            short value = (short) random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeShort(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 2) {
            short value = (short) random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readShort());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
    }

    @Test
    public void testSequentialUnsignedShortAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 2) {
            short value = (short) random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeShort(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 2) {
            int value = random.nextInt() & 0xFFFF;
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readUnsignedShort());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
    }

    @Test
    public void testSequentialMediumAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() / 3 * 3; i += 3) {
            int value = random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeMedium(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity() / 3 * 3, buffer.writerIndex());
        assertEquals(buffer.capacity() % 3, buffer.writableBytes());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() / 3 * 3; i += 3) {
            int value = random.nextInt() << 8 >> 8;
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readMedium());
        }

        assertEquals(buffer.capacity() / 3 * 3, buffer.readerIndex());
        assertEquals(buffer.capacity() / 3 * 3, buffer.writerIndex());
        assertEquals(0, buffer.readableBytes());
        assertEquals(buffer.capacity() % 3, buffer.writableBytes());
    }

    @Test
    public void testSequentialUnsignedMediumAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() / 3 * 3; i += 3) {
            int value = random.nextInt() & 0x00FFFFFF;
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeMedium(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity() / 3 * 3, buffer.writerIndex());
        assertEquals(buffer.capacity() % 3, buffer.writableBytes());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity() / 3 * 3; i += 3) {
            int value = random.nextInt() & 0x00FFFFFF;
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readUnsignedMedium());
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
            assertTrue(buffer.isWritable());
            buffer.writeInt(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 4) {
            int value = random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readInt());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
    }

    @Test
    public void testSequentialUnsignedIntAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 4) {
            int value = random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeInt(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 4) {
            long value = random.nextInt() & 0xFFFFFFFFL;
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readUnsignedInt());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
    }

    @Test
    public void testSequentialLongAccess() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 8) {
            long value = random.nextLong();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.isWritable());
            buffer.writeLong(value);
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isWritable());

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i += 8) {
            long value = random.nextLong();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.isReadable());
            assertEquals(value, buffer.readLong());
        }

        assertEquals(buffer.capacity(), buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.isReadable());
        assertFalse(buffer.isWritable());
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
    public void testRandomByteArrayTransfer1() {
        byte[] value = new byte[BLOCK_SIZE];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            buffer.setBytes(i, value);
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE];
        ByteBuf expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            buffer.getBytes(i, value);
            for (int j = 0; j < BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.getByte(j), value[j]);
            }
        }
    }

    @Test
    public void testRandomByteArrayTransfer2() {
        byte[] value = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ByteBuf expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            buffer.getBytes(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.getByte(j), value[j]);
            }
        }
    }

    @Test
    public void testRandomHeapBufferTransfer1() {
        byte[] valueContent = new byte[BLOCK_SIZE];
        ByteBuf value = wrappedBuffer(valueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            value.setIndex(0, BLOCK_SIZE);
            buffer.setBytes(i, value);
            assertEquals(BLOCK_SIZE, value.readerIndex());
            assertEquals(BLOCK_SIZE, value.writerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE];
        ByteBuf expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            value.clear();
            buffer.getBytes(i, value);
            assertEquals(0, value.readerIndex());
            assertEquals(BLOCK_SIZE, value.writerIndex());
            for (int j = 0; j < BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
        }
    }

    @Test
    public void testRandomHeapBufferTransfer2() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ByteBuf value = wrappedBuffer(valueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ByteBuf expectedValue = wrappedBuffer(expectedValueContent);
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
        ByteBuf value = releaseLater(directBuffer(BLOCK_SIZE * 2));
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(tmp);
            value.setBytes(0, tmp, 0, value.capacity());
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        ByteBuf expectedValue = releaseLater(directBuffer(BLOCK_SIZE * 2));
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
    public void testSequentialByteArrayTransfer1() {
        byte[] value = new byte[BLOCK_SIZE];
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value);
            for (int j = 0; j < BLOCK_SIZE; j ++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testSequentialByteArrayTransfer2() {
        byte[] value = new byte[BLOCK_SIZE * 2];
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            int readerIndex = random.nextInt(BLOCK_SIZE);
            buffer.writeBytes(value, readerIndex, BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testSequentialHeapBufferTransfer1() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ByteBuf value = wrappedBuffer(valueContent);
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
        ByteBuf expectedValue = wrappedBuffer(expectedValueContent);
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
        ByteBuf value = wrappedBuffer(valueContent);
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
        ByteBuf expectedValue = wrappedBuffer(expectedValueContent);
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
        ByteBuf value = releaseLater(directBuffer(BLOCK_SIZE * 2));
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
        ByteBuf expectedValue = releaseLater(wrappedBuffer(expectedValueContent));
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
        ByteBuf value = releaseLater(directBuffer(BLOCK_SIZE * 2));
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
        ByteBuf expectedValue = releaseLater(wrappedBuffer(expectedValueContent));
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
    public void testSequentialByteBufferBackedHeapBufferTransfer1() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ByteBuf value = wrappedBuffer(ByteBuffer.allocate(BLOCK_SIZE * 2));
        value.writerIndex(0);
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
        ByteBuf expectedValue = wrappedBuffer(expectedValueContent);
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
    public void testSequentialByteBufferBackedHeapBufferTransfer2() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ByteBuf value = wrappedBuffer(ByteBuffer.allocate(BLOCK_SIZE * 2));
        value.writerIndex(0);
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
        ByteBuf expectedValue = wrappedBuffer(expectedValueContent);
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
    public void testSequentialCopiedBufferTransfer1() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            byte[] value = new byte[BLOCK_SIZE];
            random.nextBytes(value);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            ByteBuf actualValue = buffer.readBytes(BLOCK_SIZE);
            assertEquals(wrappedBuffer(expectedValue), actualValue);

            // Make sure if it is a copied buffer.
            actualValue.setByte(0, (byte) (actualValue.getByte(0) + 1));
            assertFalse(buffer.getByte(i) == actualValue.getByte(0));
            actualValue.release();
        }
    }

    @Test
    public void testSequentialSlice1() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            byte[] value = new byte[BLOCK_SIZE];
            random.nextBytes(value);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            ByteBuf actualValue = buffer.readSlice(BLOCK_SIZE);
            assertEquals(buffer.order(), actualValue.order());
            assertEquals(wrappedBuffer(expectedValue), actualValue);

            // Make sure if it is a sliced buffer.
            actualValue.setByte(0, (byte) (actualValue.getByte(0) + 1));
            assertEquals(buffer.getByte(i), actualValue.getByte(0));
        }
    }

    @Test
    public void testWriteZero() {
        try {
            buffer.writeZero(-1);
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }

        buffer.clear();
        while (buffer.isWritable()) {
            buffer.writeByte((byte) 0xFF);
        }

        buffer.clear();
        for (int i = 0; i < buffer.capacity();) {
            int length = Math.min(buffer.capacity() - i, random.nextInt(32));
            buffer.writeZero(length);
            i += length;
        }

        assertEquals(0, buffer.readerIndex());
        assertEquals(buffer.capacity(), buffer.writerIndex());

        for (int i = 0; i < buffer.capacity(); i ++) {
            assertEquals(0, buffer.getByte(i));
        }
    }

    @Test
    public void testDiscardReadBytes() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i += 4) {
            buffer.writeInt(i);
        }
        ByteBuf copy = releaseLater(copiedBuffer(buffer));

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

        if (discardReadBytesDoesNotMoveWritableBytes()) {
            // If writable bytes were copied, the test should fail to avoid unnecessary memory bandwidth consumption.
            assertFalse(copy.slice(CAPACITY / 2, CAPACITY / 2).equals(buffer.slice(CAPACITY / 2 - 1, CAPACITY / 2)));
        } else {
            assertEquals(copy.slice(CAPACITY / 2, CAPACITY / 2), buffer.slice(CAPACITY / 2 - 1, CAPACITY / 2));
        }

        // Marks also should be relocated.
        buffer.resetReaderIndex();
        assertEquals(CAPACITY / 4 - 1, buffer.readerIndex());
        buffer.resetWriterIndex();
        assertEquals(CAPACITY / 3 - 1, buffer.writerIndex());
    }

    /**
     * The similar test case with {@link #testDiscardReadBytes()} but this one
     * discards a large chunk at once.
     */
    @Test
    public void testDiscardReadBytes2() {
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i ++) {
            buffer.writeByte((byte) i);
        }
        ByteBuf copy = releaseLater(copiedBuffer(buffer));

        // Discard the first (CAPACITY / 2 - 1) bytes.
        buffer.setIndex(CAPACITY / 2 - 1, CAPACITY - 1);
        buffer.discardReadBytes();
        assertEquals(0, buffer.readerIndex());
        assertEquals(CAPACITY / 2, buffer.writerIndex());
        for (int i = 0; i < CAPACITY / 2; i ++) {
            assertEquals(copy.slice(CAPACITY / 2 - 1 + i, CAPACITY / 2 - i), buffer.slice(i, CAPACITY / 2 - i));
        }
    }

    @Test
    public void testStreamTransfer1() throws Exception {
        byte[] expected = new byte[buffer.capacity()];
        random.nextBytes(expected);

        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            ByteArrayInputStream in = new ByteArrayInputStream(expected, i, BLOCK_SIZE);
            assertEquals(BLOCK_SIZE, buffer.setBytes(i, in, BLOCK_SIZE));
            assertEquals(-1, buffer.setBytes(i, in, 0));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            buffer.getBytes(i, out, BLOCK_SIZE);
        }

        assertTrue(Arrays.equals(expected, out.toByteArray()));
    }

    @Test
    public void testStreamTransfer2() throws Exception {
        byte[] expected = new byte[buffer.capacity()];
        random.nextBytes(expected);
        buffer.clear();

        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            ByteArrayInputStream in = new ByteArrayInputStream(expected, i, BLOCK_SIZE);
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(in, BLOCK_SIZE);
            assertEquals(i + BLOCK_SIZE, buffer.writerIndex());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            assertEquals(i, buffer.readerIndex());
            buffer.readBytes(out, BLOCK_SIZE);
            assertEquals(i + BLOCK_SIZE, buffer.readerIndex());
        }

        assertTrue(Arrays.equals(expected, out.toByteArray()));
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

        // Make sure all properties are copied.
        ByteBuf copy = releaseLater(buffer.copy());
        assertEquals(0, copy.readerIndex());
        assertEquals(buffer.readableBytes(), copy.writerIndex());
        assertEquals(buffer.readableBytes(), copy.capacity());
        assertSame(buffer.order(), copy.order());
        for (int i = 0; i < copy.capacity(); i ++) {
            assertEquals(buffer.getByte(i + readerIndex), copy.getByte(i));
        }

        // Make sure the buffer content is independent from each other.
        buffer.setByte(readerIndex, (byte) (buffer.getByte(readerIndex) + 1));
        assertTrue(buffer.getByte(readerIndex) != copy.getByte(0));
        copy.setByte(1, (byte) (copy.getByte(1) + 1));
        assertTrue(buffer.getByte(readerIndex + 1) != copy.getByte(1));
    }

    @Test
    public void testDuplicate() {
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte value = (byte) random.nextInt();
            buffer.setByte(i, value);
        }

        final int readerIndex = CAPACITY / 3;
        final int writerIndex = CAPACITY * 2 / 3;
        buffer.setIndex(readerIndex, writerIndex);

        // Make sure all properties are copied.
        ByteBuf duplicate = buffer.duplicate();
        assertEquals(buffer.readerIndex(), duplicate.readerIndex());
        assertEquals(buffer.writerIndex(), duplicate.writerIndex());
        assertEquals(buffer.capacity(), duplicate.capacity());
        assertSame(buffer.order(), duplicate.order());
        for (int i = 0; i < duplicate.capacity(); i ++) {
            assertEquals(buffer.getByte(i), duplicate.getByte(i));
        }

        // Make sure the buffer content is shared.
        buffer.setByte(readerIndex, (byte) (buffer.getByte(readerIndex) + 1));
        assertEquals(buffer.getByte(readerIndex), duplicate.getByte(readerIndex));
        duplicate.setByte(1, (byte) (duplicate.getByte(1) + 1));
        assertEquals(buffer.getByte(1), duplicate.getByte(1));
    }

    @Test
    public void testSliceEndianness() throws Exception {
        assertEquals(buffer.order(), buffer.slice(0, buffer.capacity()).order());
        assertEquals(buffer.order(), buffer.slice(0, buffer.capacity() - 1).order());
        assertEquals(buffer.order(), buffer.slice(1, buffer.capacity() - 1).order());
        assertEquals(buffer.order(), buffer.slice(1, buffer.capacity() - 2).order());
    }

    @Test
    public void testSliceIndex() throws Exception {
        assertEquals(0, buffer.slice(0, buffer.capacity()).readerIndex());
        assertEquals(0, buffer.slice(0, buffer.capacity() - 1).readerIndex());
        assertEquals(0, buffer.slice(1, buffer.capacity() - 1).readerIndex());
        assertEquals(0, buffer.slice(1, buffer.capacity() - 2).readerIndex());

        assertEquals(buffer.capacity(), buffer.slice(0, buffer.capacity()).writerIndex());
        assertEquals(buffer.capacity() - 1, buffer.slice(0, buffer.capacity() - 1).writerIndex());
        assertEquals(buffer.capacity() - 1, buffer.slice(1, buffer.capacity() - 1).writerIndex());
        assertEquals(buffer.capacity() - 2, buffer.slice(1, buffer.capacity() - 2).writerIndex());
    }

    @Test
    @SuppressWarnings("ObjectEqualsNull")
    public void testEquals() {
        assertFalse(buffer.equals(null));
        assertFalse(buffer.equals(new Object()));

        byte[] value = new byte[32];
        buffer.setIndex(0, value.length);
        random.nextBytes(value);
        buffer.setBytes(0, value);

        assertEquals(buffer, wrappedBuffer(value));
        assertEquals(buffer, wrappedBuffer(value).order(LITTLE_ENDIAN));

        value[0] ++;
        assertFalse(buffer.equals(wrappedBuffer(value)));
        assertFalse(buffer.equals(wrappedBuffer(value).order(LITTLE_ENDIAN)));
    }

    @Test
    public void testCompareTo() {
        try {
            buffer.compareTo(null);
            fail();
        } catch (NullPointerException e) {
            // Expected
        }

        // Fill the random stuff
        byte[] value = new byte[32];
        random.nextBytes(value);
        // Prevent overflow / underflow
        if (value[0] == 0) {
            value[0] ++;
        } else if (value[0] == -1) {
            value[0] --;
        }

        buffer.setIndex(0, value.length);
        buffer.setBytes(0, value);

        assertEquals(0, buffer.compareTo(wrappedBuffer(value)));
        assertEquals(0, buffer.compareTo(wrappedBuffer(value).order(LITTLE_ENDIAN)));

        value[0] ++;
        assertTrue(buffer.compareTo(wrappedBuffer(value)) < 0);
        assertTrue(buffer.compareTo(wrappedBuffer(value).order(LITTLE_ENDIAN)) < 0);
        value[0] -= 2;
        assertTrue(buffer.compareTo(wrappedBuffer(value)) > 0);
        assertTrue(buffer.compareTo(wrappedBuffer(value).order(LITTLE_ENDIAN)) > 0);
        value[0] ++;

        assertTrue(buffer.compareTo(wrappedBuffer(value, 0, 31)) > 0);
        assertTrue(buffer.compareTo(wrappedBuffer(value, 0, 31).order(LITTLE_ENDIAN)) > 0);
        assertTrue(buffer.slice(0, 31).compareTo(wrappedBuffer(value)) < 0);
        assertTrue(buffer.slice(0, 31).compareTo(wrappedBuffer(value).order(LITTLE_ENDIAN)) < 0);
    }

    @Test
    public void testCompareTo2() {
        byte[] bytes = {1, 2, 3, 4};
        byte[] bytesReversed = {4, 3, 2, 1};

        ByteBuf buf1 = newBuffer(4).clear().writeBytes(bytes).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuf buf2 = newBuffer(4).clear().writeBytes(bytesReversed).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuf buf3 = newBuffer(4).clear().writeBytes(bytes).order(ByteOrder.BIG_ENDIAN);
        ByteBuf buf4 = newBuffer(4).clear().writeBytes(bytesReversed).order(ByteOrder.BIG_ENDIAN);
        try {
            assertEquals(buf1.compareTo(buf2), buf3.compareTo(buf4));
            assertEquals(buf2.compareTo(buf1), buf4.compareTo(buf3));
            assertEquals(buf1.compareTo(buf3), buf2.compareTo(buf4));
            assertEquals(buf3.compareTo(buf1), buf4.compareTo(buf2));
        } finally {
            buf1.release();
            buf2.release();
            buf3.release();
            buf4.release();
        }
    }

    @Test
    public void testToString() {
        buffer.clear();
        buffer.writeBytes(releaseLater(copiedBuffer("Hello, World!", CharsetUtil.ISO_8859_1)));
        assertEquals("Hello, World!", buffer.toString(CharsetUtil.ISO_8859_1));
    }

    @Test
    public void testIndexOf() {
        buffer.clear();
        buffer.writeByte((byte) 1);
        buffer.writeByte((byte) 2);
        buffer.writeByte((byte) 3);
        buffer.writeByte((byte) 2);
        buffer.writeByte((byte) 1);

        assertEquals(-1, buffer.indexOf(1, 4, (byte) 1));
        assertEquals(-1, buffer.indexOf(4, 1, (byte) 1));
        assertEquals(1, buffer.indexOf(1, 4, (byte) 2));
        assertEquals(3, buffer.indexOf(4, 1, (byte) 2));
    }

    @Test
    public void testNioBuffer1() {
        Assume.assumeTrue(buffer.nioBufferCount() == 1);

        byte[] value = new byte[buffer.capacity()];
        random.nextBytes(value);
        buffer.clear();
        buffer.writeBytes(value);

        assertRemainingEquals(ByteBuffer.wrap(value), buffer.nioBuffer());
    }

    @Test
    public void testToByteBuffer2() {
        Assume.assumeTrue(buffer.nioBufferCount() == 1);

        byte[] value = new byte[buffer.capacity()];
        random.nextBytes(value);
        buffer.clear();
        buffer.writeBytes(value);

        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            assertRemainingEquals(ByteBuffer.wrap(value, i, BLOCK_SIZE), buffer.nioBuffer(i, BLOCK_SIZE));
        }
    }

    private static void assertRemainingEquals(ByteBuffer expected, ByteBuffer actual) {
        int remaining = expected.remaining();
        int remaining2 = actual.remaining();

        assertEquals(remaining, remaining2);
        byte[] array1 = new byte[remaining];
        byte[] array2 = new byte[remaining2];
        expected.get(array1);
        actual.get(array2);
        assertArrayEquals(array1, array2);
    }

    @Test
    public void testToByteBuffer3() {
        Assume.assumeTrue(buffer.nioBufferCount() == 1);

        assertEquals(buffer.order(), buffer.nioBuffer().order());
    }

    @Test
    public void testSkipBytes1() {
        buffer.setIndex(CAPACITY / 4, CAPACITY / 2);

        buffer.skipBytes(CAPACITY / 4);
        assertEquals(CAPACITY / 4 * 2, buffer.readerIndex());

        try {
            buffer.skipBytes(CAPACITY / 4 + 1);
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }

        // Should remain unchanged.
        assertEquals(CAPACITY / 4 * 2, buffer.readerIndex());
    }

    @Test
    public void testHashCode() {
        ByteBuf elemA = releaseLater(buffer(15));
        ByteBuf elemB = releaseLater(directBuffer(15));
        elemA.writeBytes(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5 });
        elemB.writeBytes(new byte[] { 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9 });

        Set<ByteBuf> set = new HashSet<ByteBuf>();
        set.add(elemA);
        set.add(elemB);

        assertEquals(2, set.size());
        assertTrue(set.contains(releaseLater(elemA.copy())));

        ByteBuf elemBCopy = releaseLater(elemB.copy());
        assertTrue(set.contains(elemBCopy));

        buffer.clear();
        buffer.writeBytes(elemA.duplicate());

        assertTrue(set.remove(buffer));
        assertFalse(set.contains(elemA));
        assertEquals(1, set.size());

        buffer.clear();
        buffer.writeBytes(elemB.duplicate());
        assertTrue(set.remove(buffer));
        assertFalse(set.contains(elemB));
        assertEquals(0, set.size());
    }

    // Test case for https://github.com/netty/netty/issues/325
    @Test
    public void testDiscardAllReadBytes() {
        buffer.writerIndex(buffer.capacity());
        buffer.readerIndex(buffer.writerIndex());
        buffer.discardReadBytes();
    }

    @Test
    public void testForEachByte() {
        buffer.clear();
        for (int i = 0; i < CAPACITY; i ++) {
            buffer.writeByte(i + 1);
        }

        final AtomicInteger lastIndex = new AtomicInteger();
        buffer.setIndex(CAPACITY / 4, CAPACITY * 3 / 4);
        assertThat(buffer.forEachByte(new ByteBufProcessor() {
            int i = CAPACITY / 4;

            @Override
            public boolean process(byte value) throws Exception {
                assertThat(value, is((byte) (i + 1)));
                lastIndex.set(i);
                i ++;
                return true;
            }
        }), is(-1));

        assertThat(lastIndex.get(), is(CAPACITY * 3 / 4 - 1));
    }

    @Test
    public void testForEachByteAbort() {
        buffer.clear();
        for (int i = 0; i < CAPACITY; i ++) {
            buffer.writeByte(i + 1);
        }

        final int stop = CAPACITY / 2;
        assertThat(buffer.forEachByte(CAPACITY / 3, CAPACITY / 3, new ByteBufProcessor() {
            int i = CAPACITY / 3;

            @Override
            public boolean process(byte value) throws Exception {
                assertThat(value, is((byte) (i + 1)));
                if (i == stop) {
                    return false;
                }

                i++;
                return true;
            }
        }), is(stop));
    }

    @Test
    public void testForEachByteDesc() {
        buffer.clear();
        for (int i = 0; i < CAPACITY; i ++) {
            buffer.writeByte(i + 1);
        }

        final AtomicInteger lastIndex = new AtomicInteger();
        assertThat(buffer.forEachByteDesc(CAPACITY / 4, CAPACITY * 2 / 4, new ByteBufProcessor() {
            int i = CAPACITY * 3 / 4 - 1;

            @Override
            public boolean process(byte value) throws Exception {
                assertThat(value, is((byte) (i + 1)));
                lastIndex.set(i);
                i --;
                return true;
            }
        }), is(-1));

        assertThat(lastIndex.get(), is(CAPACITY / 4));
    }

    @Test
    public void testInternalNioBuffer() {
        testInternalNioBuffer(128);
        testInternalNioBuffer(1024);
        testInternalNioBuffer(4 * 1024);
        testInternalNioBuffer(64 * 1024);
        testInternalNioBuffer(32 * 1024 * 1024);
        testInternalNioBuffer(64 * 1024 * 1024);
    }

    private void testInternalNioBuffer(int a) {
        ByteBuf buffer = releaseLater(newBuffer(2));
        ByteBuffer buf = buffer.internalNioBuffer(0, 1);
        assertEquals(1, buf.remaining());

        byte[] data = new byte[a];
        ThreadLocalRandom.current().nextBytes(data);
        buffer.writeBytes(data);

        buf = buffer.internalNioBuffer(0, a);
        assertEquals(a, buf.remaining());

        for (int i = 0; i < a; i++) {
            assertEquals(data[i], buf.get());
        }
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testDuplicateReadGatheringByteChannelMultipleThreads() throws Exception {
        testReadGatheringByteChannelMultipleThreads(false);
    }

    @Test
    public void testSliceReadGatheringByteChannelMultipleThreads() throws Exception {
        testReadGatheringByteChannelMultipleThreads(true);
    }

    private void testReadGatheringByteChannelMultipleThreads(final boolean slice) throws Exception {
        final byte[] bytes = new byte[8];
        random.nextBytes(bytes);

        final ByteBuf buffer = releaseLater(newBuffer(8));
        buffer.writeBytes(bytes);
        final CountDownLatch latch = new CountDownLatch(60000);
        final CyclicBarrier barrier = new CyclicBarrier(11);
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (latch.getCount() > 0) {
                        ByteBuf buf;
                        if (slice) {
                           buf = buffer.slice();
                        } else {
                           buf = buffer.duplicate();
                        }
                        TestGatheringByteChannel channel = new TestGatheringByteChannel();

                        while (buf.isReadable()) {
                            try {
                                buf.readBytes(channel, buf.readableBytes());
                            } catch (IOException e) {
                                // Never happens
                                return;
                            }
                        }
                        assertArrayEquals(bytes, channel.writtenBytes());
                        latch.countDown();
                    }
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }).start();
        }
        latch.await(10, TimeUnit.SECONDS);
        barrier.await(5, TimeUnit.SECONDS);
    }

    @Test
    public void testDuplicateReadOutputStreamMultipleThreads() throws Exception {
        testReadOutputStreamMultipleThreads(false);
    }

    @Test
    public void testSliceReadOutputStreamMultipleThreads() throws Exception {
        testReadOutputStreamMultipleThreads(true);
    }

    private void testReadOutputStreamMultipleThreads(final boolean slice) throws Exception {
        final byte[] bytes = new byte[8];
        random.nextBytes(bytes);

        final ByteBuf buffer = releaseLater(newBuffer(8));
        buffer.writeBytes(bytes);
        final CountDownLatch latch = new CountDownLatch(60000);
        final CyclicBarrier barrier = new CyclicBarrier(11);
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (latch.getCount() > 0) {
                        ByteBuf buf;
                        if (slice) {
                            buf = buffer.slice();
                        } else {
                            buf = buffer.duplicate();
                        }
                        ByteArrayOutputStream out = new ByteArrayOutputStream();

                        while (buf.isReadable()) {
                            try {
                                buf.readBytes(out, buf.readableBytes());
                            } catch (IOException e) {
                                // Never happens
                                return;
                            }
                        }
                        assertArrayEquals(bytes, out.toByteArray());
                        latch.countDown();
                    }
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }).start();
        }
        latch.await(10, TimeUnit.SECONDS);
        barrier.await(5, TimeUnit.SECONDS);
    }

    @Test
    public void testDuplicateBytesInArrayMultipleThreads() throws Exception {
        testBytesInArrayMultipleThreads(false);
    }

    @Test
    public void testSliceBytesInArrayMultipleThreads() throws Exception {
        testBytesInArrayMultipleThreads(true);
    }

    private void testBytesInArrayMultipleThreads(final boolean slice) throws Exception {
        final byte[] bytes = new byte[8];
        random.nextBytes(bytes);

        final ByteBuf buffer = releaseLater(newBuffer(8));
        buffer.writeBytes(bytes);
        final AtomicReference<Throwable> cause = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(60000);
        final CyclicBarrier barrier = new CyclicBarrier(11);
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (cause.get() == null && latch.getCount() > 0) {
                        ByteBuf buf;
                        if (slice) {
                            buf = buffer.slice();
                        } else {
                            buf = buffer.duplicate();
                        }

                        byte[] array = new byte[8];
                        buf.readBytes(array);

                        assertArrayEquals(bytes, array);

                        Arrays.fill(array, (byte) 0);
                        buf.getBytes(0, array);
                        assertArrayEquals(bytes, array);

                        latch.countDown();
                    }
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }).start();
        }
        latch.await(10, TimeUnit.SECONDS);
        barrier.await(5, TimeUnit.SECONDS);
        assertNull(cause.get());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void readByteThrowsIndexOutOfBoundsException() {
        final ByteBuf buffer = releaseLater(newBuffer(8));
        buffer.writeByte(0);
        assertEquals((byte) 0, buffer.readByte());
        buffer.readByte();
    }

    @Test
    @SuppressWarnings("ForLoopThatDoesntUseLoopVariable")
    public void testNioBufferExposeOnlyRegion() {
        final ByteBuf buffer = releaseLater(newBuffer(8));
        byte[] data = new byte[8];
        random.nextBytes(data);
        buffer.writeBytes(data);

        ByteBuffer nioBuf = buffer.nioBuffer(1, data.length - 2);
        assertEquals(0, nioBuf.position());
        assertEquals(6, nioBuf.remaining());

        for (int i = 1; nioBuf.hasRemaining(); i++) {
            assertEquals(data[i], nioBuf.get());
        }
    }

    // See:
    // - https://github.com/netty/netty/issues/2587
    // - https://github.com/netty/netty/issues/2580
    @Test
    public void testLittleEndianWithExpand() {
        ByteBuf buffer = releaseLater(newBuffer(0)).order(LITTLE_ENDIAN);
        buffer.writeInt(0x12345678);
        assertEquals("78563412", ByteBufUtil.hexDump(buffer));
    }

    private ByteBuf releasedBuffer() {
        ByteBuf buffer = newBuffer(8);
        assertTrue(buffer.release());
        return buffer;
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testDiscardReadBytesAfterRelease() {
        releasedBuffer().discardReadBytes();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testDiscardSomeReadBytesAfterRelease() {
        releasedBuffer().discardSomeReadBytes();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testEnsureWritableAfterRelease() {
        releasedBuffer().ensureWritable(16);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBooleanAfterRelease() {
        releasedBuffer().getBoolean(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetByteAfterRelease() {
        releasedBuffer().getByte(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetUnsignedByteAfterRelease() {
        releasedBuffer().getUnsignedByte(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetShortAfterRelease() {
        releasedBuffer().getShort(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetUnsignedShortAfterRelease() {
        releasedBuffer().getUnsignedShort(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetMediumAfterRelease() {
        releasedBuffer().getMedium(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetUnsignedMediumAfterRelease() {
        releasedBuffer().getUnsignedMedium(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetIntAfterRelease() {
        releasedBuffer().getInt(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetUnsignedIntAfterRelease() {
        releasedBuffer().getUnsignedInt(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetLongAfterRelease() {
        releasedBuffer().getLong(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetCharAfterRelease() {
        releasedBuffer().getChar(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetFloatAfterRelease() {
        releasedBuffer().getFloat(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetDoubleAfterRelease() {
        releasedBuffer().getDouble(0);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease() {
        releasedBuffer().getBytes(0, releaseLater(buffer(8)));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease2() {
        releasedBuffer().getBytes(0, releaseLater(buffer()), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease3() {
        releasedBuffer().getBytes(0, releaseLater(buffer()), 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease4() {
        releasedBuffer().getBytes(0, new byte[8]);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease5() {
        releasedBuffer().getBytes(0, new byte[8], 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease6() {
        releasedBuffer().getBytes(0, ByteBuffer.allocate(8));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease7() throws IOException {
        releasedBuffer().getBytes(0, new ByteArrayOutputStream(), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testGetBytesAfterRelease8() throws IOException {
        releasedBuffer().getBytes(0, new DevNullGatheringByteChannel(), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBooleanAfterRelease() {
        releasedBuffer().setBoolean(0, true);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetByteAfterRelease() {
        releasedBuffer().setByte(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetShortAfterRelease() {
        releasedBuffer().setShort(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetMediumAfterRelease() {
        releasedBuffer().setMedium(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetIntAfterRelease() {
        releasedBuffer().setInt(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetLongAfterRelease() {
        releasedBuffer().setLong(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetCharAfterRelease() {
        releasedBuffer().setChar(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetFloatAfterRelease() {
        releasedBuffer().setFloat(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetDoubleAfterRelease() {
        releasedBuffer().setDouble(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease() {
        releasedBuffer().setBytes(0, releaseLater(buffer()));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease2() {
        releasedBuffer().setBytes(0, releaseLater(buffer()), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease3() {
        releasedBuffer().setBytes(0, releaseLater(buffer()), 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease4() {
        releasedBuffer().setBytes(0, new byte[8]);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease5() {
        releasedBuffer().setBytes(0, new byte[8], 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease6() {
        releasedBuffer().setBytes(0, ByteBuffer.allocate(8));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease7() throws IOException {
        releasedBuffer().setBytes(0, new ByteArrayInputStream(new byte[8]), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetBytesAfterRelease8() throws IOException {
        releasedBuffer().setBytes(0, new TestScatteringByteChannel(), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testSetZeroAfterRelease() {
        releasedBuffer().setZero(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBooleanAfterRelease() {
        releasedBuffer().readBoolean();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadByteAfterRelease() {
        releasedBuffer().readByte();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadUnsignedByteAfterRelease() {
        releasedBuffer().readUnsignedByte();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadShortAfterRelease() {
        releasedBuffer().readShort();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadUnsignedShortAfterRelease() {
        releasedBuffer().readUnsignedShort();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadMediumAfterRelease() {
        releasedBuffer().readMedium();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadUnsignedMediumAfterRelease() {
        releasedBuffer().readUnsignedMedium();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadIntAfterRelease() {
        releasedBuffer().readInt();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadUnsignedIntAfterRelease() {
        releasedBuffer().readUnsignedInt();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadLongAfterRelease() {
        releasedBuffer().readLong();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadCharAfterRelease() {
        releasedBuffer().readChar();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadFloatAfterRelease() {
        releasedBuffer().readFloat();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadDoubleAfterRelease() {
        releasedBuffer().readDouble();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease() {
        releasedBuffer().readBytes(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease2() {
        releasedBuffer().readBytes(releaseLater(buffer(8)));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease3() {
        releasedBuffer().readBytes(releaseLater(buffer(8), 1));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease4() {
        releasedBuffer().readBytes(releaseLater(buffer(8)), 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease5() {
        releasedBuffer().readBytes(new byte[8]);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease6() {
        releasedBuffer().readBytes(new byte[8], 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease7() {
        releasedBuffer().readBytes(ByteBuffer.allocate(8));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease8() throws IOException {
        releasedBuffer().readBytes(new ByteArrayOutputStream(), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease9() throws IOException {
        releasedBuffer().readBytes(new ByteArrayOutputStream(), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReadBytesAfterRelease10() throws IOException {
        releasedBuffer().readBytes(new DevNullGatheringByteChannel(), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBooleanAfterRelease() {
        releasedBuffer().writeBoolean(true);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteByteAfterRelease() {
        releasedBuffer().writeByte(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteShortAfterRelease() {
        releasedBuffer().writeShort(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteMediumAfterRelease() {
        releasedBuffer().writeMedium(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteIntAfterRelease() {
        releasedBuffer().writeInt(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteLongAfterRelease() {
        releasedBuffer().writeLong(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteCharAfterRelease() {
        releasedBuffer().writeChar(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteFloatAfterRelease() {
        releasedBuffer().writeFloat(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteDoubleAfterRelease() {
        releasedBuffer().writeDouble(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease() {
        releasedBuffer().writeBytes(releaseLater(buffer(8)));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease2() {
        releasedBuffer().writeBytes(releaseLater(copiedBuffer(new byte[8])), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease3() {
        releasedBuffer().writeBytes(releaseLater(buffer(8)), 0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease4() {
        releasedBuffer().writeBytes(new byte[8]);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease5() {
        releasedBuffer().writeBytes(new byte[8], 0 , 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease6() {
        releasedBuffer().writeBytes(ByteBuffer.allocate(8));
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease7() throws IOException {
        releasedBuffer().writeBytes(new ByteArrayInputStream(new byte[8]), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteBytesAfterRelease8() throws IOException {
        releasedBuffer().writeBytes(new TestScatteringByteChannel(), 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testWriteZeroAfterRelease() throws IOException {
        releasedBuffer().writeZero(1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testForEachByteAfterRelease() {
        releasedBuffer().forEachByte(new TestByteBufProcessor());
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testForEachByteAfterRelease1() {
        releasedBuffer().forEachByte(0, 1, new TestByteBufProcessor());
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testForEachByteDescAfterRelease() {
        releasedBuffer().forEachByteDesc(new TestByteBufProcessor());
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testForEachByteDescAfterRelease1() {
        releasedBuffer().forEachByteDesc(0, 1, new TestByteBufProcessor());
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testCopyAfterRelease() {
        releasedBuffer().copy();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testCopyAfterRelease1() {
        releasedBuffer().copy();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testNioBufferAfterRelease() {
        releasedBuffer().nioBuffer();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testNioBufferAfterRelease1() {
        releasedBuffer().nioBuffer(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testInternalNioBufferAfterRelease() {
        releasedBuffer().internalNioBuffer(0, 1);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testNioBuffersAfterRelease() {
        releasedBuffer().nioBuffers();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testNioBuffersAfterRelease2() {
        releasedBuffer().nioBuffers(0, 1);
    }

    @Test
    public void testArrayAfterRelease() {
        ByteBuf buf = releasedBuffer();
        if (buf.hasArray()) {
            try {
                buf.array();
                fail();
            } catch (IllegalReferenceCountException e) {
                // expected
            }
        }
    }

    @Test
    public void testMemoryAddressAfterRelease() {
        ByteBuf buf = releasedBuffer();
        if (buf.hasMemoryAddress()) {
            try {
                buf.memoryAddress();
                fail();
            } catch (IllegalReferenceCountException e) {
                // expected
            }
        }
    }

    @Test
    public void testSliceRelease() {
        ByteBuf buf = newBuffer(8);
        assertEquals(1, buf.refCnt());
        assertTrue(buf.slice().release());
        assertEquals(0, buf.refCnt());
    }

    @Test
    public void testDuplicateRelease() {
        ByteBuf buf = newBuffer(8);
        assertEquals(1, buf.refCnt());
        assertTrue(buf.duplicate().release());
        assertEquals(0, buf.refCnt());
    }

    // Test-case trying to reproduce:
    // https://github.com/netty/netty/issues/2843
    @Test
    public void testRefCnt() throws Exception {
        testRefCnt0(false);
    }

    // Test-case trying to reproduce:
    // https://github.com/netty/netty/issues/2843
    @Test
    public void testRefCnt2() throws Exception {
        testRefCnt0(true);
    }

    @Test
    public void testEmptyNioBuffers() throws Exception {
        ByteBuf buffer = releaseLater(newBuffer(8));
        buffer.clear();
        assertFalse(buffer.isReadable());
        ByteBuffer[] nioBuffers = buffer.nioBuffers();
        assertEquals(1, nioBuffers.length);
        assertFalse(nioBuffers[0].hasRemaining());
    }

    @Test
    public void testGetReadOnlyDirectDst() {
        testGetReadOnlyDst(true);
    }

    @Test
    public void testGetReadOnlyHeapDst() {
        testGetReadOnlyDst(false);
    }

    private void testGetReadOnlyDst(boolean direct) {
        byte[] bytes = { 'a', 'b', 'c', 'd' };

        ByteBuf buffer = releaseLater(newBuffer(bytes.length));
        buffer.writeBytes(bytes);

        ByteBuffer dst = direct ? ByteBuffer.allocateDirect(bytes.length) : ByteBuffer.allocate(bytes.length);
        ByteBuffer readOnlyDst = dst.asReadOnlyBuffer();
        try {
            buffer.getBytes(0, readOnlyDst);
            fail();
        } catch (ReadOnlyBufferException e) {
            // expected
        }
        assertEquals(0, readOnlyDst.position());
    }

    @Test
    public void testReadBytes() {
        ByteBuf buffer = newBuffer(8);
        byte[] bytes = new byte[8];
        buffer.writeBytes(bytes);

        ByteBuf buffer2 = buffer.readBytes(4);
        assertSame(buffer.alloc(), buffer2.alloc());
        assertEquals(4, buffer.readerIndex());
        assertTrue(buffer.release());
        assertEquals(0, buffer.refCnt());
        assertTrue(buffer2.release());
        assertEquals(0, buffer2.refCnt());
    }

    @Test
    public void testForEachByteDesc2() {
        byte[] expected = {1, 2, 3, 4};
        ByteBuf buf = newBuffer(expected.length);
        try {
            buf.writeBytes(expected);
            final byte[] bytes = new byte[expected.length];
            int i = buf.forEachByteDesc(new ByteBufProcessor() {
                private int index = bytes.length - 1;

                @Override
                public boolean process(byte value) throws Exception {
                    bytes[index--] = value;
                    return true;
                }
            });
            assertEquals(-1, i);
            assertArrayEquals(expected, bytes);
        } finally {
            buf.release();
        }
    }

    @Test
    public void testForEachByte2() {
        byte[] expected = {1, 2, 3, 4};
        ByteBuf buf = newBuffer(expected.length);
        try {
            buf.writeBytes(expected);
            final byte[] bytes = new byte[expected.length];
            int i = buf.forEachByte(new ByteBufProcessor() {
                private int index;

                @Override
                public boolean process(byte value) throws Exception {
                    bytes[index++] = value;
                    return true;
                }
            });
            assertEquals(-1, i);
            assertArrayEquals(expected, bytes);
        } finally {
            buf.release();
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetBytesByteBuffer() {
        byte[] bytes = {'a', 'b', 'c', 'd', 'e', 'f', 'g'};
        // Ensure destination buffer is bigger then what is in the ByteBuf.
        ByteBuffer nioBuffer = ByteBuffer.allocate(bytes.length + 1);
        ByteBuf buffer = newBuffer(bytes.length);
        try {
            buffer.writeBytes(bytes);
            buffer.getBytes(buffer.readerIndex(), nioBuffer);
        } finally {
            buffer.release();
        }
    }

    private void testRefCnt0(final boolean parameter) throws Exception {
        for (int i = 0; i < 10; i++) {
            final CountDownLatch latch = new CountDownLatch(1);
            final CountDownLatch innerLatch = new CountDownLatch(1);

            final ByteBuf buffer = newBuffer(4);
            assertEquals(1, buffer.refCnt());
            final AtomicInteger cnt = new AtomicInteger(Integer.MAX_VALUE);
            Thread t1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean released;
                    if (parameter) {
                        released = buffer.release(buffer.refCnt());
                    } else {
                        released = buffer.release();
                    }
                    assertTrue(released);
                    Thread t2 = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            cnt.set(buffer.refCnt());
                            latch.countDown();
                        }
                    });
                    t2.start();
                    try {
                        // Keep Thread alive a bit so the ThreadLocal caches are not freed
                        innerLatch.await();
                    } catch (InterruptedException ignore) {
                        // ignore
                    }
                }
            });
            t1.start();

            latch.await();
            assertEquals(0, cnt.get());
            innerLatch.countDown();
        }
    }

    static final class TestGatheringByteChannel implements GatheringByteChannel {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final WritableByteChannel channel = Channels.newChannel(out);
        private final int limit;
        TestGatheringByteChannel(int limit) {
            this.limit = limit;
        }

        TestGatheringByteChannel() {
            this(Integer.MAX_VALUE);
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            long written = 0;
            for (; offset < length; offset++) {
                written += write(srcs[offset]);
                if (written >= limit) {
                    break;
                }
            }
            return written;
        }

        @Override
        public long write(ByteBuffer[] srcs) throws IOException {
            return write(srcs, 0, srcs.length);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            int oldLimit = src.limit();
            if (limit < src.remaining()) {
                src.limit(src.position() + limit);
            }
            int w = channel.write(src);
            src.limit(oldLimit);
            return w;
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        public byte[] writtenBytes() {
            return out.toByteArray();
        }
    }

    private static final class DevNullGatheringByteChannel implements GatheringByteChannel {
        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long write(ByteBuffer[] srcs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int write(ByteBuffer src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestScatteringByteChannel implements ScatteringByteChannel {
        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long read(ByteBuffer[] dsts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(ByteBuffer dst) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestByteBufProcessor implements ByteBufProcessor {
        @Override
        public boolean process(byte value) throws Exception {
            return true;
        }
    }
}
