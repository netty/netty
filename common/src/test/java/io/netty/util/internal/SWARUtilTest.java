/*
 * Copyright 2024 The Netty Project
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
package io.netty.util.internal;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SWARUtilTest {

    private final Random random = new Random();

    @Test
    void containsUpperCaseLong() {
        // given
        final byte[] asciiTable = getExtendedAsciiTable();
        shuffleArray(asciiTable, random);

        // when
        for (int idx = 0; idx < asciiTable.length; idx += Long.BYTES) {
            final long value = getLong(asciiTable, idx);
            final boolean actual = SWARUtil.containsUpperCase(value);
            boolean expected = false;
            for (int i = 0; i < Long.BYTES; i++) {
                expected |= Character.isUpperCase(asciiTable[idx + i]);
            }
            // then
            assertEquals(expected, actual);
        }
    }

    @Test
    void containsUpperCaseInt() {
        // given
        final byte[] asciiTable = getExtendedAsciiTable();
        shuffleArray(asciiTable, random);

        // when
        for (int idx = 0; idx < asciiTable.length; idx += Integer.BYTES) {
            final int value = getInt(asciiTable, idx);
            final boolean containsUpperCase = SWARUtil.containsUpperCase(value);
            boolean expectedContainsUpperCase = false;
            for (int i = 0; i < Integer.BYTES; i++) {
                expectedContainsUpperCase |= Character.isUpperCase(asciiTable[idx + i]);
            }
            // then
            assertEquals(expectedContainsUpperCase, containsUpperCase);
        }
    }

    @Test
    void containsLowerCaseLong() {
        // given
        final byte[] asciiTable = getExtendedAsciiTable();
        shuffleArray(asciiTable, random);

        // when
        for (int idx = 0; idx < asciiTable.length; idx += Long.BYTES) {
            final long value = getLong(asciiTable, idx);
            final boolean actual = SWARUtil.containsLowerCase(value);
            boolean expected = false;
            for (int i = 0; i < Long.BYTES; i++) {
                expected |= Character.isLowerCase(asciiTable[idx + i]);
            }
            // then
            assertEquals(expected, actual);
        }
    }

    @Test
    void containsLowerCaseInt() {
        // given
        final byte[] asciiTable = getExtendedAsciiTable();
        shuffleArray(asciiTable, random);

        // when
        for (int idx = 0; idx < asciiTable.length; idx += Integer.BYTES) {
            final int value = getInt(asciiTable, idx);
            final boolean actual = SWARUtil.containsLowerCase(value);
            boolean expected = false;
            for (int i = 0; i < Integer.BYTES; i++) {
                expected |= Character.isLowerCase(asciiTable[idx + i]);
            }
            // then
            assertEquals(expected, actual);
        }
    }

    @Test
    void toUpperCaseLong() {
        // given
        final byte[] asciiTable = getExtendedAsciiTable();
        shuffleArray(asciiTable, random);

        // when
        for (int idx = 0; idx < asciiTable.length; idx += Long.BYTES) {
            final long value = getLong(asciiTable, idx);
            final long actual = SWARUtil.toUpperCase(value);
            long expected = 0L;
            for (int i = 0; i < Long.BYTES; i++) {
                final byte b = (byte) Character.toUpperCase(asciiTable[idx + i]);
                expected |= (long) ((b & 0xff)) << (56 - (Long.BYTES * i));
            }
            // then
            assertEquals(expected, actual);
        }
    }

    @Test
    void toUpperCaseInt() {
        // given
        final byte[] asciiTable = getExtendedAsciiTable();
        shuffleArray(asciiTable, random);

        // when
        for (int idx = 0; idx < asciiTable.length; idx += Integer.BYTES) {
            final int value = getInt(asciiTable, idx);
            final int actual = SWARUtil.toUpperCase(value);
            int expected = 0;
            for (int i = 0; i < Integer.BYTES; i++) {
                final byte b = (byte) Character.toUpperCase(asciiTable[idx + i]);
                expected |= (b & 0xff) << (24 - (Byte.SIZE * i));
            }
            // then
            assertEquals(expected, actual);
        }
    }

    @Test
    void toLowerCaseLong() {
        // given
        final byte[] asciiTable = getExtendedAsciiTable();
        shuffleArray(asciiTable, random);

        // when
        for (int idx = 0; idx < asciiTable.length; idx += Long.BYTES) {
            final long value = getLong(asciiTable, idx);
            final long actual = SWARUtil.toLowerCase(value);
            long expected = 0L;
            for (int i = 0; i < Long.BYTES; i++) {
                final byte b = (byte) Character.toLowerCase(asciiTable[idx + i]);
                expected |= (long) ((b & 0xff)) << (56 - (Byte.SIZE * i));
            }
            // then
            assertEquals(expected, actual);
        }
    }

    @Test
    void toLowerCaseInt() {
        // given
        final byte[] asciiTable = getExtendedAsciiTable();
        shuffleArray(asciiTable, random);

        // when
        for (int idx = 0; idx < asciiTable.length; idx += Integer.BYTES) {
            final int value = getInt(asciiTable, idx);
            final int actual = SWARUtil.toLowerCase(value);
            int expected = 0;
            for (int i = 0; i < Integer.BYTES; i++) {
                final byte b = (byte) Character.toLowerCase(asciiTable[idx + i]);
                expected |= (b & 0xff) << (24 - (Byte.SIZE * i));
            }
            // then
            assertEquals(expected, actual);
        }
    }

    private static void shuffleArray(byte[] array, Random random) {
        for (int i = array.length - 1; i > 0; i--) {
            final int index = random.nextInt(i + 1);
            final byte tmp = array[index];
            array[index] = array[i];
            array[i] = tmp;
        }
    }

    private static byte[] getExtendedAsciiTable() {
        final byte[] table = new byte[256];
        for (int i = 0; i < 256; i++) {
            table[i] = (byte) i;
        }
        return table;
    }

    private static long getLong(byte[] bytes, int idx) {
        assert idx >= 0 && bytes.length >= idx + 8;
        return (long) bytes[idx] << 56 |
               ((long) bytes[idx + 1] & 0xff) << 48 |
               ((long) bytes[idx + 2] & 0xff) << 40 |
               ((long) bytes[idx + 3] & 0xff) << 32 |
               ((long) bytes[idx + 4] & 0xff) << 24 |
               ((long) bytes[idx + 5] & 0xff) << 16 |
               ((long) bytes[idx + 6] & 0xff) << 8 |
               (long) bytes[idx + 7] & 0xff;
    }

    private static int getInt(byte[] bytes, int idx) {
        assert idx >= 0 && bytes.length >= idx + 4;
        return bytes[idx] << 24 |
               (bytes[idx + 1] & 0xff) << 16 |
               (bytes[idx + 2] & 0xff) << 8 |
               bytes[idx + 3] & 0xff;
    }
}
