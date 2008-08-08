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
package net.gleamynode.netty.array;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.Random;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.DirectByteArray;
import net.gleamynode.netty.array.HeapByteArray;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractByteArrayTest {

    private static final int LENGTH = 1048576;
    private static final int BLOCK_SIZE = 384;

    private long seed;
    private Random random;
    private ByteArray array;

    protected abstract ByteArray newArray(int length);
    protected abstract ByteArray[] components();

    @Before
    public void init() {
        array = newArray(LENGTH);
        assertEquals(LENGTH, array.length());
        assertFalse(array.empty());

        // Make sure the boundary test is working.
        try {
            array.get8(array.firstIndex() - 1);
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Expected.
        }
        try {
            array.get8(array.firstIndex() + array.length());
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Expected.
        }

        seed = System.currentTimeMillis();
        random = new Random(seed);
    }

    @After
    public void dispose() {
        array = null;
    }

    @Test
    public void test8() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length(); i ++) {
            byte value = (byte) random.nextInt();
            array.set8(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length(); i ++) {
            byte value = (byte) random.nextInt();
            assertEquals(value, array.get8(i));
        }
    }

    @Test
    public void testBE16() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 1; i += 2) {
            short value = (short) random.nextInt();
            array.setBE16(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 1; i += 2) {
            short value = (short) random.nextInt();
            assertEquals(value, array.getBE16(i));
            assertEquals(value, reverse16(array.getLE16(i)));
        }
    }

    @Test
    public void testBE24() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 2; i += 3) {
            int value = random.nextInt();
            array.setBE24(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 2; i += 3) {
            int value = random.nextInt() & 0x00FFFFFF;
            assertEquals(value, array.getBE24(i));
            assertEquals(value, reverse24(array.getLE24(i)));
        }
    }

    @Test
    public void testBE32() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 3; i += 4) {
            int value = random.nextInt();
            array.setBE32(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 3; i += 4) {
            int value = random.nextInt();
            assertEquals(value, array.getBE32(i));
            assertEquals(value, reverse32(array.getLE32(i)));
        }
    }

    @Test
    public void testBE48() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 5; i += 6) {
            long value = random.nextLong();
            array.setBE48(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 5; i += 6) {
            long value = random.nextLong() & 0x0000FFFFFFFFFFFFL;
            assertEquals(value, array.getBE48(i));
            assertEquals(value, reverse48(array.getLE48(i)));
        }
    }

    @Test
    public void testBE64() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 7; i += 8) {
            long value = random.nextLong();
            array.setBE64(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 7; i += 8) {
            long value = random.nextLong();
            assertEquals(value, array.getBE64(i));
            assertEquals(value, reverse64(array.getLE64(i)));
        }
    }

    @Test
    public void testLE16() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 1; i += 2) {
            short value = (short) random.nextInt();
            array.setLE16(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 1; i += 2) {
            short value = (short) random.nextInt();
            assertEquals(value, array.getLE16(i));
            assertEquals(value, reverse16(array.getBE16(i)));
        }
    }

    @Test
    public void testLE24() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 2; i += 3) {
            int value = random.nextInt();
            array.setLE24(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 2; i += 3) {
            int value = random.nextInt() & 0x00FFFFFF;
            assertEquals(value, array.getLE24(i));
            assertEquals(value, reverse24(array.getBE24(i)));
        }
    }

    @Test
    public void testLE32() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 3; i += 4) {
            int value = random.nextInt();
            array.setLE32(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 3; i += 4) {
            int value = random.nextInt();
            assertEquals(value, array.getLE32(i));
            assertEquals(value, reverse32(array.getBE32(i)));
        }
    }

    @Test
    public void testLE48() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 5; i += 6) {
            long value = random.nextLong();
            array.setLE48(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 5; i += 6) {
            long value = random.nextLong() & 0x0000FFFFFFFFFFFFL;
            assertEquals(value, array.getLE48(i));
            assertEquals(value, reverse48(array.getBE48(i)));
        }
    }

    @Test
    public void testLE64() {
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 7; i += 8) {
            long value = random.nextLong();
            array.setLE64(i, value);
        }

        random.setSeed(seed);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - 7; i += 8) {
            long value = random.nextLong();
            assertEquals(value, array.getLE64(i));
            assertEquals(value, reverse64(array.getBE64(i)));
        }
    }

    @Test
    public void testJavaByteArrayTransfer() {
        byte[] value = new byte[BLOCK_SIZE * 2];
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            array.set(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE * 2];
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            array.get(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testHeapByteArrayTransfer() {
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ByteArray value = new HeapByteArray(valueContent);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            array.set(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ByteArray expectedValue = new HeapByteArray(expectedValueContent);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            array.get(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.get8(j), value.get8(j));
            }
        }
    }

    @Test
    public void testDirectByteArrayTransfer() {
        byte[] tmp = new byte[BLOCK_SIZE * 2];
        ByteArray value = new DirectByteArray(BLOCK_SIZE * 2);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(tmp);
            value.set(0, tmp, 0, value.length());
            array.set(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        ByteArray expectedValue = new DirectByteArray(BLOCK_SIZE * 2);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(tmp);
            expectedValue.set(0, tmp, 0, expectedValue.length());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            array.get(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.get8(j), value.get8(j));
            }
        }
    }

    @Test
    public void testByteBufferTransfer() {
        ByteBuffer value = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value.array());
            value.clear().position(random.nextInt(BLOCK_SIZE));
            value.limit(value.position() + BLOCK_SIZE);
            array.set(i, value);
        }

        random.setSeed(seed);
        ByteBuffer expectedValue = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = array.firstIndex(); i < array.firstIndex() + array.length() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue.array());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.clear().position(valueOffset).limit(valueOffset + BLOCK_SIZE);
            array.get(i, value);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j ++) {
                assertEquals(expectedValue.get(j), value.get(j));
            }
        }
    }

    private static short reverse16(short v) {
        int b1 = v >>> 0 & 0xff;
        int b2 = v >>> 8 & 0xff;
        return (short) (b2 << 0 | b1 << 8);
    }

    private static int reverse24(int v) {
        int b1 = v >>>  0 & 0xff;
        int b2 = v >>>  8 & 0xff;
        int b3 = v >>> 16 & 0xff;
        return b3 << 0 | b2 << 8 | b1 << 16;
    }

    private static int reverse32(int v) {
        int b1 = v >>>  0 & 0xff;
        int b2 = v >>>  8 & 0xff;
        int b3 = v >>> 16 & 0xff;
        int b4 = v >>> 24 & 0xff;
        return b4 << 0 | b3 << 8 | b2 << 16 | b1 << 24;
    }

    private static long reverse48(long v) {
        long b1 = v >>>  0 & 0xff;
        long b2 = v >>>  8 & 0xff;
        long b3 = v >>> 16 & 0xff;
        long b4 = v >>> 24 & 0xff;
        long b5 = v >>> 32 & 0xff;
        long b6 = v >>> 40 & 0xff;
        return b6 << 0 | b5 << 8 | b4 << 16 | b3 << 24 | b2 << 32 | b1 << 40;
    }

    private static long reverse64(long v) {
        long b1 = v >>>  0 & 0xff;
        long b2 = v >>>  8 & 0xff;
        long b3 = v >>> 16 & 0xff;
        long b4 = v >>> 24 & 0xff;
        long b5 = v >>> 32 & 0xff;
        long b6 = v >>> 40 & 0xff;
        long b7 = v >>> 48 & 0xff;
        long b8 = v >>> 56 & 0xff;
        return b8 << 0 | b7 << 8 | b6 << 16 | b5 << 24 | b4 << 32 | b3 << 40 | b2 << 48 | b1 << 56;
    }
}
