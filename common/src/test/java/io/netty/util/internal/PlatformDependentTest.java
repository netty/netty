/*
 * Copyright 2015 The Netty Project
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
package io.netty.util.internal;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class PlatformDependentTest {
    private static final Random r = new Random();
    @Test
    public void testEquals() {
        byte[] bytes1 = {'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd'};
        byte[] bytes2 = {'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd'};
        assertNotSame(bytes1, bytes2);
        assertTrue(PlatformDependent.equals(bytes1, 0, bytes2, 0, bytes1.length));
        assertTrue(PlatformDependent.equals(bytes1, 2, bytes2, 2, bytes1.length - 2));

        bytes1 = new byte[] {1, 2, 3, 4, 5, 6};
        bytes2 = new byte[] {1, 2, 3, 4, 5, 6, 7};
        assertNotSame(bytes1, bytes2);
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes2, 1, bytes1.length));
        assertTrue(PlatformDependent.equals(bytes2, 0, bytes1, 0, bytes1.length));

        bytes1 = new byte[] {1, 2, 3, 4};
        bytes2 = new byte[] {1, 2, 3, 5};
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes2, 0, bytes1.length));
        assertTrue(PlatformDependent.equals(bytes1, 0, bytes2, 0, 3));

        bytes1 = new byte[] {1, 2, 3, 4};
        bytes2 = new byte[] {1, 3, 3, 4};
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes2, 0, bytes1.length));
        assertTrue(PlatformDependent.equals(bytes1, 2, bytes2, 2, bytes1.length - 2));

        bytes1 = new byte[0];
        bytes2 = new byte[0];
        assertNotSame(bytes1, bytes2);
        assertTrue(PlatformDependent.equals(bytes1, 0, bytes2, 0, 0));

        bytes1 = new byte[100];
        bytes2 = new byte[100];
        for (int i = 0; i < 100; i++) {
            bytes1[i] = (byte) i;
            bytes2[i] = (byte) i;
        }
        assertTrue(PlatformDependent.equals(bytes1, 0, bytes2, 0, bytes1.length));
        bytes1[50] = 0;
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes2, 0, bytes1.length));
        assertTrue(PlatformDependent.equals(bytes1, 51, bytes2, 51, bytes1.length - 51));
        assertTrue(PlatformDependent.equals(bytes1, 0, bytes2, 0, 50));

        bytes1 = new byte[]{1, 2, 3, 4, 5};
        bytes2 = new byte[]{3, 4, 5};
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes2, 0, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes1, 2, bytes2, 0, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes2, 0, bytes1, 2, bytes2.length));

        for (int i = 0; i < 1000; ++i) {
            bytes1 = new byte[i];
            r.nextBytes(bytes1);
            bytes2 = bytes1.clone();
            assertTrue(PlatformDependent.equals(bytes1, 0, bytes2, 0, bytes1.length));
        }
    }

    private static char randomCharInByteRange() {
        return (char) r.nextInt(255 + 1);
    }

    @Test
    public void testHashCodeAscii() {
        for (int i = 0; i < 1000; ++i) {
            // byte[] and char[] need to be initialized such that there values are within valid "ascii" range
            byte[] bytes = new byte[i];
            char[] bytesChar = new char[i];
            for (int j = 0; j < bytesChar.length; ++j) {
                bytesChar[j] = randomCharInByteRange();
                bytes[j] = (byte) (bytesChar[j] & 0xff);
            }
            String string = new String(bytesChar);
            assertEquals("length=" + i,
                         PlatformDependent.hashCodeAsciiSafe(bytes, 0, bytes.length),
                         PlatformDependent.hashCodeAscii(bytes, 0, bytes.length));
            assertEquals("length=" + i,
                    PlatformDependent.hashCodeAsciiSafe(string),
                    PlatformDependent.hashCodeAscii(string));
            assertEquals("length=" + i,
                    PlatformDependent.hashCodeAscii(bytes, 0, bytes.length),
                    PlatformDependent.hashCodeAscii(string));
        }
    }
}
