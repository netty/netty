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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import io.netty.util.CharsetUtil;

import java.util.Random;

import org.junit.Test;

public class PlatformDependentTest {

    @Test
    public void testEquals() {
        byte[] bytes1 = {'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd'};
        byte[] bytes2 = {'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd'};
        assertNotSame(bytes1, bytes2);
        assertTrue(PlatformDependent.equals(bytes1, 0, bytes1.length, bytes2, 0, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes1, 2, bytes1.length, bytes2, 2, bytes2.length));

        bytes1 = new byte[] {1, 2, 3, 4, 5, 6};
        bytes2 = new byte[] {1, 2, 3, 4, 5, 6, 7};
        assertNotSame(bytes1, bytes2);
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes1.length, bytes2, 0, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes2, 0, 6, bytes1, 0, 6));

        bytes1 = new byte[] {1, 2, 3, 4};
        bytes2 = new byte[] {1, 2, 3, 5};
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes1.length, bytes2, 0, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes1, 0, 3, bytes2, 0, 3));

        bytes1 = new byte[] {1, 2, 3, 4};
        bytes2 = new byte[] {1, 3, 3, 4};
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes1.length, bytes2, 0, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes1, 2, bytes1.length, bytes2, 2, bytes2.length));

        bytes1 = new byte[0];
        bytes2 = new byte[0];
        assertNotSame(bytes1, bytes2);
        assertTrue(PlatformDependent.equals(bytes1, 0, 0, bytes2, 0, 0));

        bytes1 = new byte[100];
        bytes2 = new byte[100];
        for (int i = 0; i < 100; i++) {
            bytes1[i] = (byte) i;
            bytes2[i] = (byte) i;
        }
        assertTrue(PlatformDependent.equals(bytes1, 0, bytes1.length, bytes2, 0, bytes2.length));
        bytes1[50] = 0;
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes1.length, bytes2, 0, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes1, 51, bytes1.length, bytes2, 51, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes1, 0, 50, bytes2, 0, 50));

        bytes1 = new byte[]{1, 2, 3, 4, 5};
        bytes2 = new byte[]{3, 4, 5};
        assertFalse(PlatformDependent.equals(bytes1, 0, bytes1.length, bytes2, 0, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes1, 2, bytes1.length, bytes2, 0, bytes2.length));
        assertTrue(PlatformDependent.equals(bytes2, 0, bytes2.length, bytes1, 2, bytes1.length));
    }

    @Test
    public void testHashCode() {
        HashCodeGenerator hasher = PlatformDependent.hashCodeGenerator();
        final int bytes1Len = 256;
        final int bytes2Len = bytes1Len >> 1;
        final int bytes1Start = bytes2Len;
        final int bytes2Start = bytes2Len >> 1;
        int subSequenceLen = bytes2Len - bytes2Start;
        // We want to have a number that is divisible by 7 to ensure we hit all the
        // getlong/getint/getchar/direct lookup branches.
        while (subSequenceLen % 7 != 0) {
            --subSequenceLen;
        }
        byte[] bytes1 = new byte[bytes1Len];
        byte[] bytes2 = new byte[bytes2Len];
        Random r = new Random();
        r.nextBytes(bytes1);
        System.arraycopy(bytes1, bytes1Start, bytes2, bytes2Start, subSequenceLen);
        assertNotSame(bytes1, bytes2);

        final int expected = hasher.hashCode(bytes2, bytes2Start, subSequenceLen);
        // Test that two separate arrays with the same value for a given range yield the same hash code.
        assertEquals(expected, hasher.hashCode(bytes1, bytes1Start, subSequenceLen));
    }

    @Test
    public void testHashDifferntTypesSameResults() {
        HashCodeGenerator hasher = PlatformDependent.hashCodeGenerator();

        Random r = new Random();
        int i = 0;
        for (; i < 32; ++i) {
            runHasherEqualityTest(hasher, i, r);
        }
        final int min = i;
        final int max = 20000;
        int len = r.nextInt((max - min) + 1) + min;
        // We now want to test a "random" length array but differnt permutations to test
        // interesting boundary conditions.
        while (len % 8 != 0) {
            ++len;
        }
        for (i = len - 8; i < len; ++i) {
            runHasherEqualityTest(hasher, i, r);
        }
    }

    @Test
    public void testHashTypeOptimizations() {
        HashCodeGenerator hasher = PlatformDependent.hashCodeGenerator();

        int i = 0;
        for (; i < 32; ++i) {
            runTypeOptimizations(hasher, i);
        }
        Random r = new Random();
        final int min = i;
        final int max = 20000;
        int len = r.nextInt((max - min) + 1) + min;
        // We now want to test a "random" length array but differnt permutations to test
        // interesting boundary conditions.
        while (len % 8 != 0) {
            ++len;
        }
        for (i = len - 8; i < len; ++i) {
            runTypeOptimizations(hasher, i);
        }
    }

    private void runTypeOptimizations(HashCodeGenerator hasher, int len) {
        StringBuilder b = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            b.append((byte) i);
        }
        String s = b.toString();
        byte[] bytes = s.getBytes(CharsetUtil.US_ASCII);

        assertEquals(s.length(), bytes.length);

        final int expected =  hasher.hashCode(bytes);
        final String errorMsg = "len: " + len;
        assertEquals(errorMsg, expected, hasher.hashCodeAsBytes(s));
        assertEquals(errorMsg, expected, hasher.hashCodeAsBytes(b));
    }

    private static void runHasherEqualityTest(HashCodeGenerator hasher, int len, Random r) {
        byte[] bytes = new byte[len];
        char[] charsAsBytes = new char[bytes.length];
        r.nextBytes(bytes);

        for (int i = 0; i < bytes.length; ++i) {
            charsAsBytes[i] = (char) (bytes[i] & 0xFF);
        }

        String a = new String(charsAsBytes);
        final int expected =  hasher.hashCode(bytes);
        final String errorMsg = "len: " + len;
        assertEquals(errorMsg, expected, hasher.hashCodeAsBytes(a));
        assertEquals(errorMsg, expected, hasher.hashCodeAsBytes(charsAsBytes));
    }
}
