/*
 * Copyright 2015 The Netty Project
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
package io.netty.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import io.netty.util.ByteProcessor.IndexOfProcessor;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

/**
 * Test the underlying memory methods for the {@link AsciiString} class.
 */
public class AsciiStringMemoryTest {
    private byte[] a;
    private byte[] b;
    private int aOffset = 22;
    private int bOffset = 53;
    private int length = 100;
    private AsciiString aAsciiString;
    private AsciiString bAsciiString;
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
        aAsciiString = new AsciiString(a, aOffset, length, false);
        bAsciiString = new AsciiString(b, bOffset, length, false);
    }

    @Test
    public void testSharedMemory() {
        ++a[aOffset];
        AsciiString aAsciiString1 = new AsciiString(a, aOffset, length, true);
        AsciiString aAsciiString2 = new AsciiString(a, aOffset, length, false);
        assertEquals(aAsciiString, aAsciiString1);
        assertEquals(aAsciiString, aAsciiString2);
        for (int i = aOffset; i < length; ++i) {
            assertEquals(a[i], aAsciiString.byteAt(i - aOffset));
        }
    }

    @Test
    public void testNotSharedMemory() {
        AsciiString aAsciiString1 = new AsciiString(a, aOffset, length, true);
        ++a[aOffset];
        assertNotEquals(aAsciiString, aAsciiString1);
        int i = aOffset;
        assertNotEquals(a[i], aAsciiString1.byteAt(i - aOffset));
        ++i;
        for (; i < length; ++i) {
            assertEquals(a[i], aAsciiString1.byteAt(i - aOffset));
        }
    }

    @Test
    public void forEachTest() throws Exception {
        final AtomicReference<Integer> aCount = new AtomicReference<Integer>(0);
        final AtomicReference<Integer> bCount = new AtomicReference<Integer>(0);
        aAsciiString.forEachByte(new ByteProcessor() {
            int i;
            @Override
            public boolean process(byte value) throws Exception {
                assertEquals("failed at index: " + i, value, bAsciiString.byteAt(i++));
                aCount.set(aCount.get() + 1);
                return true;
            }
        });
        bAsciiString.forEachByte(new ByteProcessor() {
            int i;
            @Override
            public boolean process(byte value) throws Exception {
                assertEquals("failed at index: " + i, value, aAsciiString.byteAt(i++));
                bCount.set(bCount.get() + 1);
                return true;
            }
        });
        assertEquals(aAsciiString.length(), aCount.get().intValue());
        assertEquals(bAsciiString.length(), bCount.get().intValue());
    }

    @Test
    public void forEachWithIndexEndTest() throws Exception {
        assertNotEquals(-1, aAsciiString.forEachByte(aAsciiString.length() - 1,
                1, new IndexOfProcessor(aAsciiString.byteAt(aAsciiString.length() - 1))));
    }

    @Test
    public void forEachWithIndexBeginTest() throws Exception {
        assertNotEquals(-1, aAsciiString.forEachByte(0,
                1, new IndexOfProcessor(aAsciiString.byteAt(0))));
    }

    @Test
    public void forEachDescTest() throws Exception {
        final AtomicReference<Integer> aCount = new AtomicReference<Integer>(0);
        final AtomicReference<Integer> bCount = new AtomicReference<Integer>(0);
        aAsciiString.forEachByteDesc(new ByteProcessor() {
            int i = 1;
            @Override
            public boolean process(byte value) throws Exception {
                assertEquals("failed at index: " + i, value, bAsciiString.byteAt(bAsciiString.length() - (i++)));
                aCount.set(aCount.get() + 1);
                return true;
            }
        });
        bAsciiString.forEachByteDesc(new ByteProcessor() {
            int i = 1;
            @Override
            public boolean process(byte value) throws Exception {
                assertEquals("failed at index: " + i, value, aAsciiString.byteAt(aAsciiString.length() - (i++)));
                bCount.set(bCount.get() + 1);
                return true;
            }
        });
        assertEquals(aAsciiString.length(), aCount.get().intValue());
        assertEquals(bAsciiString.length(), bCount.get().intValue());
    }

    @Test
    public void forEachDescWithIndexEndTest() throws Exception {
        assertNotEquals(-1, bAsciiString.forEachByteDesc(bAsciiString.length() - 1,
                1, new IndexOfProcessor(bAsciiString.byteAt(bAsciiString.length() - 1))));
    }

    @Test
    public void forEachDescWithIndexBeginTest() throws Exception {
        assertNotEquals(-1, bAsciiString.forEachByteDesc(0,
                1, new IndexOfProcessor(bAsciiString.byteAt(0))));
    }

    @Test
    public void subSequenceTest() {
        final int start = 12;
        final int end = aAsciiString.length();
        AsciiString aSubSequence = aAsciiString.subSequence(start, end, false);
        AsciiString bSubSequence = bAsciiString.subSequence(start, end, true);
        assertEquals(aSubSequence, bSubSequence);
        assertEquals(aSubSequence.hashCode(), bSubSequence.hashCode());
    }

    @Test
    public void copyTest() {
        byte[] aCopy = new byte[aAsciiString.length()];
        aAsciiString.copy(0, aCopy, 0, aCopy.length);
        AsciiString aAsciiStringCopy = new AsciiString(aCopy, false);
        assertEquals(aAsciiString, aAsciiStringCopy);
    }
}
