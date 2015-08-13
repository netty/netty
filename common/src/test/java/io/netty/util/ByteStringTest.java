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
