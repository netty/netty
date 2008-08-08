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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;


public class CompositeByteArrayTest extends AbstractByteArrayTest {

    private static final int CAPACITY_INCREMENT = 9;
    private static final int COMPONENT_MAX_LENGTH = 256;
    private final Random random = new Random();
    private final List<ByteArray> arrays = new ArrayList<ByteArray>();

    @Override
    protected ByteArray newArray(int length) {
        arrays.clear();
        CompositeByteArray ca = new CompositeByteArray();
        while (ca.length() != length) {
            int aLen = random.nextInt(COMPONENT_MAX_LENGTH) + 1;
            if (ca.length() + aLen > length) {
                aLen = length - ca.length();
            }

            ByteArray a;
            if (random.nextBoolean()) {
                a = new StaticPartialByteArray(
                        new HeapByteArray(COMPONENT_MAX_LENGTH * 2),
                        random.nextInt(COMPONENT_MAX_LENGTH), aLen);
            } else {
                a = new StaticPartialByteArray(
                        new DirectByteArray(COMPONENT_MAX_LENGTH * 2),
                        random.nextInt(COMPONENT_MAX_LENGTH), aLen);
            }

            if (random.nextBoolean()) {
                ca.addFirst(a);
                arrays.add(0, a);
            } else {
                ca.addLast(a);
                arrays.add(a);
            }
        }
        return ca;
    }

    @Override
    protected ByteArray[] components() {
        return arrays.toArray(new ByteArray[arrays.size()]);
    }

    @Test
    public void defaultState() {
        CompositeByteArray buffer = new CompositeByteArray();
        assertEmpty(buffer);
    }

    @Test
    public void sequentialByteAccess() {
        CompositeByteArray buffer = new CompositeByteArray(CAPACITY_INCREMENT);
        for (int i = 0; i < 256; i ++) {
            buffer.write8((byte) i);
        }

        assertLength(256, buffer);
        assertEquals(256 / CAPACITY_INCREMENT + 1, buffer.count());

        for (int i = 0; i < 256; i ++) {
            assertEquals(i, buffer.read8() & 0xFF);
            assertLength(256 - i - 1, buffer);
        }

        assertEmpty(buffer);
    }

    @Test
    public void sequentialShortAccess() {
        CompositeByteArray buffer = new CompositeByteArray(CAPACITY_INCREMENT);
        for (int i = 0; i < 32768; i ++) {
            buffer.writeBE16((short) i);
        }

        assertLength(32768 * 2, buffer);
        assertEquals(32768 / (CAPACITY_INCREMENT / 2), buffer.count());

        for (int i = 0; i < 32768; i ++) {
            assertEquals(i, buffer.readBE16() & 0xFFFF);
            assertLength(32768 * 2 - (i + 1) * 2, buffer);
        }

        assertEmpty(buffer);
    }

    @Test
    public void sequentialIntAccess() {
        CompositeByteArray buffer = new CompositeByteArray(CAPACITY_INCREMENT);
        for (int i = 0; i < 32768; i ++) {
            buffer.writeBE32(i);
        }

        assertLength(32768 * 4, buffer);
        assertEquals(32768 / (CAPACITY_INCREMENT / 4), buffer.count());

        for (int i = 0; i < 32768; i ++) {
            assertEquals(i, buffer.readBE32());
            assertLength(32768 * 4 - (i + 1) * 4, buffer);
        }

        assertEmpty(buffer);
    }

    @Test
    public void sequentialLongAccess() {
        CompositeByteArray buffer = new CompositeByteArray(CAPACITY_INCREMENT);
        for (int i = 0; i < 32768; i ++) {
            buffer.writeBE64(i);
        }

        assertLength(32768 * 8, buffer);
        assertEquals(32768 / (CAPACITY_INCREMENT / 8), buffer.count());

        for (int i = 0; i < 32768; i ++) {
            assertEquals(i, buffer.readBE64());
            assertLength(32768 * 8 - (i + 1) * 8, buffer);
        }

        assertEmpty(buffer);
    }

    @Test
    public void randomByteAccess() {
        CompositeByteArray buffer = new CompositeByteArray(CAPACITY_INCREMENT);
        for (int i = 0; i < 256; i ++) {
            buffer.write8((byte) i);
        }

        assertLength(256, buffer);
        assertEquals(256 / CAPACITY_INCREMENT + 1, buffer.count());

        for (int i = 0; i < 256; i ++) {
            assertEquals(i, buffer.get8(i) & 0xFF);
            assertLength(256, buffer);
            assertEquals(256 / CAPACITY_INCREMENT + 1, buffer.count());
        }
    }

    @Test
    public void randomShortAccess() {
        CompositeByteArray buffer = new CompositeByteArray(CAPACITY_INCREMENT);
        for (int i = 0; i < 4096; i ++) {
            buffer.writeBE16((short) i);
        }

        assertLength(4096 * 2, buffer);
        assertEquals(4096 / (CAPACITY_INCREMENT / 2), buffer.count());

        for (int i = 0; i < 4096; i ++) {
            assertEquals(i, buffer.getBE16(i * 2) & 0xFFFF);
            assertLength(4096 * 2, buffer);
            assertEquals(4096 / (CAPACITY_INCREMENT / 2), buffer.count());
        }
    }

    @Test
    public void randomIntAccess() {
        CompositeByteArray buffer = new CompositeByteArray(CAPACITY_INCREMENT);
        for (int i = 0; i < 4096; i ++) {
            buffer.writeBE32(i);
        }

        assertLength(4096 * 4, buffer);
        assertEquals(4096 / (CAPACITY_INCREMENT / 4), buffer.count());

        for (int i = 0; i < 4096; i ++) {
            assertEquals(i, buffer.getBE32(i * 4));
            assertLength(4096 * 4, buffer);
            assertEquals(4096 / (CAPACITY_INCREMENT / 4), buffer.count());
        }
    }

    @Test
    public void randomLongAccess() {
        CompositeByteArray buffer = new CompositeByteArray(CAPACITY_INCREMENT);
        for (int i = 0; i < 4096; i ++) {
            buffer.writeBE64(i);
        }

        assertLength(4096 * 8, buffer);
        assertEquals(4096 / (CAPACITY_INCREMENT / 8), buffer.count());

        for (int i = 0; i < 4096; i ++) {
            assertEquals(i, buffer.getBE64(i * 8));
            assertLength(4096 * 8, buffer);
            assertEquals(4096 / (CAPACITY_INCREMENT / 8), buffer.count());
        }
    }

    private void assertLength(int expected, CompositeByteArray buffer) {
        assertEquals(expected, buffer.length());
        assertEquals(expected == 0, buffer.empty());
        if (expected == 0) {
            assertEmpty(buffer);
        }
    }

    private void assertEmpty(CompositeByteArray buffer) {
        assertEquals(1, buffer.count());
        assertEquals(0, buffer.length());
        assertTrue(buffer.empty());

        try {
            buffer.read();
            fail();
        } catch (RuntimeException e) {
            // Expected.
        }

        assertEquals(1, buffer.count());
        assertEquals(0, buffer.length());
        assertTrue(buffer.empty());

        try {
            buffer.read8();
            fail();
        } catch (RuntimeException e) {
            // Expected.
        }

        assertEquals(1, buffer.count());
        assertEquals(0, buffer.length());
        assertTrue(buffer.empty());

        try {
            buffer.readBE16();
            fail();
        } catch (RuntimeException e) {
            // Expected.
        }

        assertEquals(1, buffer.count());
        assertEquals(0, buffer.length());
        assertTrue(buffer.empty());

        try {
            buffer.readBE32();
            fail();
        } catch (RuntimeException e) {
            // Expected.
        }

        assertEquals(1, buffer.count());
        assertEquals(0, buffer.length());
        assertTrue(buffer.empty());

        try {
            buffer.readBE64();
            fail();
        } catch (RuntimeException e) {
            // Expected.
        }

        assertEquals(1, buffer.count());
        assertEquals(0, buffer.length());
        assertTrue(buffer.empty());
    }

/*
    @Test
    public void testPerformance1() {
        CompositeByteArray buffer = new CompositeByteArray();
        long startTime1 = System.currentTimeMillis();
        for (int i = 0; i < 10485760 / 2; i ++) {
            buffer.writeBE16((short) 0x42);
        }
        long endTime1 = System.currentTimeMillis();
        System.out.println("W1: " + (endTime1 - startTime1));

        long startTime2 = System.currentTimeMillis();
        for (int i = 0; i < 10485760 / 2; i ++) {
            buffer.readBE16();
        }
        long endTime2 = System.currentTimeMillis();
        System.out.println("R1 : " + (endTime2 - startTime2));
        System.out.println("RW1: " + (endTime1 - startTime1 + endTime2 - startTime2));
    }

    @Test
    public void testPerformance2() {
        long startTime1 = System.currentTimeMillis();
        ByteBuffer buffer = ByteBuffer.allocate(104857600);
        for (int i = 0; i < 10485760/2; i ++) {
            synchronized (buffer) {
                buffer.putShort((short) 0x42);
            }
        }
        long endTime1 = System.currentTimeMillis();
        System.out.println("W2: " + (endTime1 - startTime1));

        long startTime2 = System.currentTimeMillis();
        buffer.position(0);
        for (int i = 0; i < 10485760/2; i ++) {
            synchronized (buffer) {
                buffer.getShort();
            }
        }
        long endTime2 = System.currentTimeMillis();
        System.out.println("R2 : " + (endTime2 - startTime2));
        System.out.println("RW2: " + (endTime1 - startTime1 + endTime2 - startTime2));
    }

    @Test
    public void testPerformance3() {
        testPerformance1();
    }

    @Test
    public void testPerformance4() {
        testPerformance2();
    }

    @Test
    public void testPerformance5() {
        testPerformance1();
    }

    @Test
    public void testPerformance6() {
        testPerformance2();
    }

    @Test
    public void testPerformance7() {
        testPerformance1();
    }

    @Test
    public void testPerformance8() {
        testPerformance2();
    }
*/
}
