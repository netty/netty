/*
 * Copyright 2023 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsciiStringUtilTest {

    @Test
    public void toLowerCaseTest() {
        final byte[] eAsciiTable = getExtendedAsciiTable();
        // test single byte
        for (int i = 0; i < eAsciiTable.length; i++) {
            final byte b = eAsciiTable[i];
            final byte expected = 'A' <= b && b <= 'Z'? (byte) Character.toLowerCase(b) : b;
            final byte actual = AsciiStringUtil.toLowerCase(b);
            assertEquals(expected, actual);
        }


        // test 4 bytes
        for (int i = 0; i < eAsciiTable.length; i += 4) {
            final int word = getInt(eAsciiTable, i);
            final int converted = AsciiStringUtil.SWARByteUtil.toLowerCase(word);
            for (int j = 0; j < Integer.BYTES; ++j) {
                final byte expected = AsciiStringUtil.toLowerCase(eAsciiTable[i + j]);
                final byte actual = getByte(converted, j);
                assertEquals(expected, actual);
            }
        }

        // test 8 bytes
        for (int i = 0; i < eAsciiTable.length; i += 8) {
            final long word = getLong(eAsciiTable, i);
            final long converted = AsciiStringUtil.SWARByteUtil.toLowerCase(word);
            for (int j = 0; j < Long.BYTES; ++j) {
                final byte expected = AsciiStringUtil.toLowerCase(eAsciiTable[i + j]);
                final byte actual = getByte(converted, j);
                assertEquals(expected, actual);
            }
        }
    }

    @Test
    public void toUpperCaseTest() {
        final byte[] eAsciiTable = getExtendedAsciiTable();
        // test single byte
        for (int i = 0; i < eAsciiTable.length; i++) {
            final byte b = eAsciiTable[i];
            final byte expected = 'a' <= b && b <= 'z'? (byte) Character.toUpperCase(b) : b;
            final byte actual = AsciiStringUtil.toUpperCase(b);
            assertEquals(expected, actual);
        }

        // test 4 bytes
        for (int i = 0; i < eAsciiTable.length; i += 4) {
            final int word = getInt(eAsciiTable, i);
            final int converted = AsciiStringUtil.SWARByteUtil.toUpperCase(word);
            for (int j = 0; j < Integer.BYTES; ++j) {
                final byte expected = AsciiStringUtil.toUpperCase(eAsciiTable[i + j]);
                final byte actual = getByte(converted, j);
                assertEquals(expected, actual);
            }
        }

        // test 8 bytes
        for (int i = 0; i < eAsciiTable.length; i += 8) {
            final long word = getLong(eAsciiTable, i);
            final long converted = AsciiStringUtil.SWARByteUtil.toUpperCase(word);
            for (int j = 0; j < Long.BYTES; ++j) {
                final byte expected = AsciiStringUtil.toUpperCase(eAsciiTable[i + j]);
                final byte actual = getByte(converted, j);
                assertEquals(expected, actual);
            }
        }
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

    private static byte getByte(long word, int idx) {
        assert idx >= 0 && idx < 8;
        return (byte) (word >>> (7 - idx) * 8 & 0xff);
    }


    private static byte getByte(int word, int idx) {
        assert idx >= 0 && idx < 4;
        return (byte) (word >>> (3 - idx) * 8 & 0xff);
    }

    private static byte[] getExtendedAsciiTable() {
        final byte[] table = new byte[256];
        for (int i = 0; i < 256; i++) {
            table[i] = (byte) i;
        }
        return table;
    }

}