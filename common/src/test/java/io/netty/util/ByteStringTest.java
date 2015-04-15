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
package io.netty.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

/**
 * Test for the {@link ByteString} class.
 */
public class ByteStringTest {
    private byte[] a;
    private byte[] b;
    private int aOffset = 22;
    private int bOffset = 53;
    private int length = 100;
    private ByteString aByteString;
    private ByteString bByteString;
    private ByteString greaterThanAByteString;
    private ByteString lessThanAByteString;
    private Random r = new Random();

    @Before
    public void setup() {
        a = new byte[128];
        b = new byte[256];
        r.nextBytes(a);
        r.nextBytes(b);
        aOffset = 22;
        bOffset = 53;
        length = 100;
        System.arraycopy(a, aOffset, b, bOffset, length);
        aByteString = new ByteString(a, aOffset, length, false);
        bByteString = new ByteString(b, bOffset, length, false);

        int i;
        // Find an element that can be decremented
        for (i = 1; i < length; ++i) {
            if (a[aOffset + 1] > Byte.MIN_VALUE) {
                --a[aOffset + 1];
                break;
            }
        }
        lessThanAByteString = new ByteString(a, aOffset, length, true);
        ++a[aOffset + i]; // Restore the a array to the original value

        // Find an element that can be incremented
        for (i = 1; i < length; ++i) {
            if (a[aOffset + 1] < Byte.MAX_VALUE) {
                ++a[aOffset + 1];
                break;
            }
        }
        greaterThanAByteString = new ByteString(a, aOffset, length, true);
        --a[aOffset + i]; // Restore the a array to the original value
    }

    @Test
    public void testEqualsComparareSelf() {
        assertTrue(ByteString.DEFAULT_COMPARATOR.compare(aByteString, aByteString) == 0);
        assertEquals(bByteString, bByteString);
    }

    @Test
    public void testEqualsComparatorAgainstAnother1() {
        assertTrue(ByteString.DEFAULT_COMPARATOR.compare(bByteString, aByteString) == 0);
        assertEquals(bByteString, aByteString);
        assertEquals(bByteString.hashCode(), aByteString.hashCode());
    }

    @Test
    public void testEqualsComparatorAgainstAnother2() {
        assertTrue(ByteString.DEFAULT_COMPARATOR.compare(aByteString, bByteString) == 0);
        assertEquals(aByteString, bByteString);
        assertEquals(aByteString.hashCode(), bByteString.hashCode());
    }

    @Test
    public void testLessThan() {
        assertTrue(ByteString.DEFAULT_COMPARATOR.compare(lessThanAByteString, aByteString) < 0);
        assertTrue(ByteString.DEFAULT_COMPARATOR.compare(aByteString, lessThanAByteString) > 0);
    }

    @Test
    public void testGreaterThan() {
        assertTrue(ByteString.DEFAULT_COMPARATOR.compare(greaterThanAByteString, aByteString) > 0);
        assertTrue(ByteString.DEFAULT_COMPARATOR.compare(aByteString, greaterThanAByteString) < 0);
    }

    @Test
    public void testSharedMemory() {
        ++a[aOffset];
        ByteString aByteString1 = new ByteString(a, aOffset, length, true);
        ByteString aByteString2 = new ByteString(a, aOffset, length, false);
        assertEquals(aByteString, aByteString1);
        assertEquals(aByteString, aByteString2);
        for (int i = aOffset; i < length; ++i) {
            assertEquals(a[i], aByteString.byteAt(i - aOffset));
        }
    }

    @Test
    public void testNotSharedMemory() {
        ByteString aByteString1 = new ByteString(a, aOffset, length, true);
        ++a[aOffset];
        assertNotEquals(aByteString, aByteString1);
        int i = aOffset;
        assertNotEquals(a[i], aByteString1.byteAt(i - aOffset));
        ++i;
        for (; i < length; ++i) {
            assertEquals(a[i], aByteString1.byteAt(i - aOffset));
        }
    }

    @Test
    public void forEachTest() throws Exception {
        final AtomicReference<Integer> aCount = new AtomicReference<Integer>(0);
        final AtomicReference<Integer> bCount = new AtomicReference<Integer>(0);
        aByteString.forEachByte(new ByteProcessor() {
            int i;
            @Override
            public boolean process(byte value) throws Exception {
                assertEquals("failed at index: " + i, value, bByteString.byteAt(i++));
                aCount.set(aCount.get() + 1);
                return true;
            }
        });
        bByteString.forEachByte(new ByteProcessor() {
            int i;
            @Override
            public boolean process(byte value) throws Exception {
                assertEquals("failed at index: " + i, value, aByteString.byteAt(i++));
                bCount.set(bCount.get() + 1);
                return true;
            }
        });
        assertEquals(aByteString.length(), aCount.get().intValue());
        assertEquals(bByteString.length(), bCount.get().intValue());
    }

    @Test
    public void forEachDescTest() throws Exception {
        final AtomicReference<Integer> aCount = new AtomicReference<Integer>(0);
        final AtomicReference<Integer> bCount = new AtomicReference<Integer>(0);
        aByteString.forEachByteDesc(new ByteProcessor() {
            int i = 1;
            @Override
            public boolean process(byte value) throws Exception {
                assertEquals("failed at index: " + i, value, bByteString.byteAt(bByteString.length() - (i++)));
                aCount.set(aCount.get() + 1);
                return true;
            }
        });
        bByteString.forEachByteDesc(new ByteProcessor() {
            int i = 1;
            @Override
            public boolean process(byte value) throws Exception {
                assertEquals("failed at index: " + i, value, aByteString.byteAt(aByteString.length() - (i++)));
                bCount.set(bCount.get() + 1);
                return true;
            }
        });
        assertEquals(aByteString.length(), aCount.get().intValue());
        assertEquals(bByteString.length(), bCount.get().intValue());
    }

    @Test
    public void subSequenceTest() {
        final int start = 12;
        final int end = aByteString.length();
        ByteString aSubSequence = aByteString.subSequence(start, end, false);
        ByteString bSubSequence = bByteString.subSequence(start, end, true);
        assertEquals(aSubSequence, bSubSequence);
        assertEquals(aSubSequence.hashCode(), bSubSequence.hashCode());
    }

    @Test
    public void copyTest() {
        byte[] aCopy = new byte[aByteString.length()];
        aByteString.copy(0, aCopy, 0, aCopy.length);
        ByteString aByteStringCopy = new ByteString(aCopy, false);
        assertEquals(aByteString, aByteStringCopy);
    }
}
