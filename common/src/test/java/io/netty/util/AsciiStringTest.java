/*
 * Copyright 2014 The Netty Project
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

import static io.netty.util.AsciiString.caseInsensitiveHashCode;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test for the {@link AsciiString} class
 */
public class AsciiStringTest {
    @Test
    public void testGetBytesStringBuilder() {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < 1 << 16; ++i) {
            b.append("eéaà");
        }
        final String bString = b.toString();
        final Charset[] charsets = CharsetUtil.values();
        for (int i = 0; i < charsets.length; ++i) {
            final Charset charset = charsets[i];
            byte[] expected = bString.getBytes(charset);
            byte[] actual = new ByteString(b, charset).toByteArray();
            assertArrayEquals("failure for " + charset, expected, actual);
        }
    }

    @Test
    public void testGetBytesString() {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < 1 << 16; ++i) {
            b.append("eéaà");
        }
        final String bString = b.toString();
        final Charset[] charsets = CharsetUtil.values();
        for (int i = 0; i < charsets.length; ++i) {
            final Charset charset = charsets[i];
            byte[] expected = bString.getBytes(charset);
            byte[] actual = new ByteString(bString, charset).toByteArray();
            assertArrayEquals("failure for " + charset, expected, actual);
        }
    }

    @Test
    public void testGetBytesAsciiString() {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < 1 << 16; ++i) {
            b.append("eéaà");
        }
        final String bString = b.toString();
        // The AsciiString class actually limits the Charset to ISO_8859_1
        byte[] expected = bString.getBytes(CharsetUtil.ISO_8859_1);
        byte[] actual = new AsciiString(bString).toByteArray();
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testComparisonWithString() {
        String string = "shouldn't fail";
        AsciiString ascii = new AsciiString(string.toCharArray());
        Assert.assertEquals(string, ascii.toString());
    }

    @Test
    public void subSequenceTest() {
        byte[] init = {'t', 'h', 'i', 's', ' ', 'i', 's', ' ', 'a', ' ', 't', 'e', 's', 't' };
        AsciiString ascii = new AsciiString(init);
        final int start = 2;
        final int end = init.length;
        AsciiString sub1 = ascii.subSequence(start, end, false);
        AsciiString sub2 = ascii.subSequence(start, end, true);
        assertEquals(sub1.hashCode(), sub2.hashCode());
        assertEquals(sub1.hashCodeCaseInsensitive(), sub2.hashCode());
        assertEquals(sub1, sub2);
        for (int i = start; i < end; ++i) {
            assertEquals(init[i], sub1.byteAt(i - start));
        }
    }

    @Test
    public void caseInsensativeHasher() {
        Random r = new Random();
        int i = 0;
        for (; i < 32; i++) {
            doCaseInsensative(r, i);
        }
        final int min = i;
        final int max = 4000;
        final int len = r.nextInt((max - min) + 1) + min;
        doCaseInsensative(r, len);
    }

    private static void doCaseInsensative(Random r, int len) {
        final int upperA = 'A';
        final int upperZ = 'Z';
        final int upperToLower = (int) 'a' - upperA;
        byte[] bytes = new byte[len];
        StringBuilder b = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            char upper = (char) (r.nextInt((upperZ - upperA) + 1) + upperA);
            b.append(upper);
            bytes[i] = (byte) (upper + upperToLower);
        }
        String s1 = b.toString();
        AsciiString s2 = new AsciiString(bytes, false);
        AsciiString s3 = new AsciiString(s1);
        final String errorString = "len: " + len;
        final int expected = s2.hashCode();
        assertEquals(errorString, expected, caseInsensitiveHashCode(s1));
        assertEquals(errorString, expected, caseInsensitiveHashCode(s2));
        assertEquals(errorString, expected, s2.hashCodeCaseInsensitive());
        assertEquals(errorString, expected, s3.hashCodeCaseInsensitive());
    }

    @Test
    public void caseInsensativeHasherCharBuffer() {
        String s1 = new String("TRANSFER-ENCODING");
        char[] array = new char[128];
        final int offset = 100;
        for (int i = 0; i < s1.length(); ++i) {
            array[offset + i] = s1.charAt(i);
        }
        CharBuffer buffer = CharBuffer.wrap(array, offset, s1.length());
        assertEquals(caseInsensitiveHashCode(s1), caseInsensitiveHashCode(buffer));
    }
}
