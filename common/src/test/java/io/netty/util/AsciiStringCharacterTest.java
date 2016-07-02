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

import org.junit.Assert;
import org.junit.Test;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Random;

import static io.netty.util.AsciiString.contains;
import static io.netty.util.AsciiString.containsIgnoreCase;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Test character encoding and case insensitivity for the {@link AsciiString} class
 */
public class AsciiStringCharacterTest {
    private static final Random r = new Random();

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
            byte[] actual = new AsciiString(b, charset).toByteArray();
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
            byte[] actual = new AsciiString(bString, charset).toByteArray();
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
        assertEquals(sub1, sub2);
        for (int i = start; i < end; ++i) {
            assertEquals(init[i], sub1.byteAt(i - start));
        }
    }

    @Test
    public void testContains() {
        String[] falseLhs = {null, "a", "aa", "aaa" };
        String[] falseRhs = {null, "b", "ba", "baa" };
        for (int i = 0; i < falseLhs.length; ++i) {
            for (int j = 0; j < falseRhs.length; ++j) {
                assertContains(falseLhs[i], falseRhs[i], false, false);
            }
        }

        assertContains("", "", true, true);
        assertContains("AsfdsF", "", true, true);
        assertContains("", "b", false, false);
        assertContains("a", "a", true, true);
        assertContains("a", "b", false, false);
        assertContains("a", "A", false, true);
        String b = "xyz";
        String a = b;
        assertContains(a, b, true, true);

        a = "a" + b;
        assertContains(a, b, true, true);

        a = b + "a";
        assertContains(a, b, true, true);

        a = "a" + b + "a";
        assertContains(a, b, true, true);

        b = "xYz";
        a = "xyz";
        assertContains(a, b, false, true);

        b = "xYz";
        a = "xyzxxxXyZ" + b + "aaa";
        assertContains(a, b, true, true);

        b = "foOo";
        a = "fooofoO";
        assertContains(a, b, false, true);

        b = "Content-Equals: 10000";
        a = "content-equals: 1000";
        assertContains(a, b, false, false);
        a += "0";
        assertContains(a, b, false, true);
    }

    private static void assertContains(String a, String b, boolean caseSensitiveEquals, boolean caseInsenstaiveEquals) {
        assertEquals(caseSensitiveEquals, contains(a, b));
        assertEquals(caseInsenstaiveEquals, containsIgnoreCase(a, b));
    }

    @Test
    public void testCaseSensitivity() {
        int i = 0;
        for (; i < 32; i++) {
            doCaseSensitivity(i);
        }
        final int min = i;
        final int max = 4000;
        final int len = r.nextInt((max - min) + 1) + min;
        doCaseSensitivity(len);
    }

    private static void doCaseSensitivity(int len) {
        // Build an upper case and lower case string
        final int upperA = 'A';
        final int upperZ = 'Z';
        final int upperToLower = (int) 'a' - upperA;
        byte[] lowerCaseBytes = new byte[len];
        StringBuilder upperCaseBuilder = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            char upper = (char) (r.nextInt((upperZ - upperA) + 1) + upperA);
            upperCaseBuilder.append(upper);
            lowerCaseBytes[i] = (byte) (upper + upperToLower);
        }
        String upperCaseString = upperCaseBuilder.toString();
        String lowerCaseString = new String(lowerCaseBytes);
        AsciiString lowerCaseAscii = new AsciiString(lowerCaseBytes, false);
        AsciiString upperCaseAscii = new AsciiString(upperCaseString);
        final String errorString = "len: " + len;
        // Test upper case hash codes are equal
        final int upperCaseExpected = upperCaseAscii.hashCode();
        assertEquals(errorString, upperCaseExpected, AsciiString.hashCode(upperCaseBuilder));
        assertEquals(errorString, upperCaseExpected, AsciiString.hashCode(upperCaseString));
        assertEquals(errorString, upperCaseExpected, upperCaseAscii.hashCode());

        // Test lower case hash codes are equal
        final int lowerCaseExpected = lowerCaseAscii.hashCode();
        assertEquals(errorString, lowerCaseExpected, AsciiString.hashCode(lowerCaseAscii));
        assertEquals(errorString, lowerCaseExpected, AsciiString.hashCode(lowerCaseString));
        assertEquals(errorString, lowerCaseExpected, lowerCaseAscii.hashCode());

        // Test case insensitive hash codes are equal
        final int expectedCaseInsensitive = lowerCaseAscii.hashCode();
        assertEquals(errorString, expectedCaseInsensitive, AsciiString.hashCode(upperCaseBuilder));
        assertEquals(errorString, expectedCaseInsensitive, AsciiString.hashCode(upperCaseString));
        assertEquals(errorString, expectedCaseInsensitive, AsciiString.hashCode(lowerCaseString));
        assertEquals(errorString, expectedCaseInsensitive, AsciiString.hashCode(lowerCaseAscii));
        assertEquals(errorString, expectedCaseInsensitive, AsciiString.hashCode(upperCaseAscii));
        assertEquals(errorString, expectedCaseInsensitive, lowerCaseAscii.hashCode());
        assertEquals(errorString, expectedCaseInsensitive, upperCaseAscii.hashCode());

        // Test that opposite cases are equal
        assertEquals(errorString, lowerCaseAscii.hashCode(), AsciiString.hashCode(upperCaseString));
        assertEquals(errorString, upperCaseAscii.hashCode(), AsciiString.hashCode(lowerCaseString));
    }

    @Test
    public void caseInsensitiveHasherCharBuffer() {
        String s1 = new String("TRANSFER-ENCODING");
        char[] array = new char[128];
        final int offset = 100;
        for (int i = 0; i < s1.length(); ++i) {
            array[offset + i] = s1.charAt(i);
        }
        CharBuffer buffer = CharBuffer.wrap(array, offset, s1.length());
        assertEquals(AsciiString.hashCode(s1), AsciiString.hashCode(buffer));
    }

    @Test
    public void testBooleanUtilityMethods() {
        assertTrue(new AsciiString(new byte[] { 1 }).parseBoolean());
        assertFalse(AsciiString.EMPTY_STRING.parseBoolean());
        assertFalse(new AsciiString(new byte[] { 0 }).parseBoolean());
        assertTrue(new AsciiString(new byte[] { 5 }).parseBoolean());
        assertTrue(new AsciiString(new byte[] { 2, 0 }).parseBoolean());
    }

    @Test
    public void testEqualsIgnoreCase() {
        assertThat(AsciiString.contentEqualsIgnoreCase(null, null), is(true));
        assertThat(AsciiString.contentEqualsIgnoreCase(null, "foo"), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase("bar", null), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase("FoO", "fOo"), is(true));

        // Test variations (Ascii + String, Ascii + Ascii, String + Ascii)
        assertThat(AsciiString.contentEqualsIgnoreCase(new AsciiString("FoO"), "fOo"), is(true));
        assertThat(AsciiString.contentEqualsIgnoreCase(new AsciiString("FoO"), new AsciiString("fOo")), is(true));
        assertThat(AsciiString.contentEqualsIgnoreCase("FoO", new AsciiString("fOo")), is(true));

        // Test variations (Ascii + String, Ascii + Ascii, String + Ascii)
        assertThat(AsciiString.contentEqualsIgnoreCase(new AsciiString("FoO"), "bAr"), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase(new AsciiString("FoO"), new AsciiString("bAr")), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase("FoO", new AsciiString("bAr")), is(false));
    }

    @Test
    public void testIndexOfIgnoreCase() {
        assertEquals(-1, AsciiString.indexOfIgnoreCase(null, "abc", 1));
        assertEquals(-1, AsciiString.indexOfIgnoreCase("abc", null, 1));
        assertEquals(0, AsciiString.indexOfIgnoreCase("", "", 0));
        assertEquals(0, AsciiString.indexOfIgnoreCase("aabaabaa", "A", 0));
        assertEquals(2, AsciiString.indexOfIgnoreCase("aabaabaa", "B", 0));
        assertEquals(1, AsciiString.indexOfIgnoreCase("aabaabaa", "AB", 0));
        assertEquals(5, AsciiString.indexOfIgnoreCase("aabaabaa", "B", 3));
        assertEquals(-1, AsciiString.indexOfIgnoreCase("aabaabaa", "B", 9));
        assertEquals(2, AsciiString.indexOfIgnoreCase("aabaabaa", "B", -1));
        assertEquals(2, AsciiString.indexOfIgnoreCase("aabaabaa", "", 2));
        assertEquals(-1, AsciiString.indexOfIgnoreCase("abc", "", 9));
        assertEquals(0, AsciiString.indexOfIgnoreCase("ãabaabaa", "Ã", 0));
    }

    @Test
    public void testIndexOfIgnoreCaseAscii() {
        assertEquals(-1, AsciiString.indexOfIgnoreCaseAscii(null, "abc", 1));
        assertEquals(-1, AsciiString.indexOfIgnoreCaseAscii("abc", null, 1));
        assertEquals(0, AsciiString.indexOfIgnoreCaseAscii("", "", 0));
        assertEquals(0, AsciiString.indexOfIgnoreCaseAscii("aabaabaa", "A", 0));
        assertEquals(2, AsciiString.indexOfIgnoreCaseAscii("aabaabaa", "B", 0));
        assertEquals(1, AsciiString.indexOfIgnoreCaseAscii("aabaabaa", "AB", 0));
        assertEquals(5, AsciiString.indexOfIgnoreCaseAscii("aabaabaa", "B", 3));
        assertEquals(-1, AsciiString.indexOfIgnoreCaseAscii("aabaabaa", "B", 9));
        assertEquals(2, AsciiString.indexOfIgnoreCaseAscii("aabaabaa", "B", -1));
        assertEquals(2, AsciiString.indexOfIgnoreCaseAscii("aabaabaa", "", 2));
        assertEquals(-1, AsciiString.indexOfIgnoreCaseAscii("abc", "", 9));
    }

    @Test
    public void testTrim() {
        assertEquals("", AsciiString.EMPTY_STRING.trim().toString());
        assertEquals("abc", new AsciiString("  abc").trim().toString());
        assertEquals("abc", new AsciiString("abc  ").trim().toString());
        assertEquals("abc", new AsciiString("  abc  ").trim().toString());
    }

    @Test
    public void testIndexOfChar() {
        assertEquals(-1, AsciiString.indexOf(null, 'a', 0));
        assertEquals(-1, AsciiString.of("").indexOf('a', 0));
        assertEquals(-1, AsciiString.of("abc").indexOf('d', 0));
        assertEquals(-1, AsciiString.of("aabaabaa").indexOf('A', 0));
        assertEquals(0, AsciiString.of("aabaabaa").indexOf('a', 0));
        assertEquals(1, AsciiString.of("aabaabaa").indexOf('a', 1));
        assertEquals(3, AsciiString.of("aabaabaa").indexOf('a', 2));
        assertEquals(3, AsciiString.of("aabdabaa").indexOf('d', 1));
    }

    @Test
    public void testStaticIndexOfChar() {
        assertEquals(-1, AsciiString.indexOf(null, 'a', 0));
        assertEquals(-1, AsciiString.indexOf("", 'a', 0));
        assertEquals(-1, AsciiString.indexOf("abc", 'd', 0));
        assertEquals(-1, AsciiString.indexOf("aabaabaa", 'A', 0));
        assertEquals(0, AsciiString.indexOf("aabaabaa", 'a', 0));
        assertEquals(1, AsciiString.indexOf("aabaabaa", 'a', 1));
        assertEquals(3, AsciiString.indexOf("aabaabaa", 'a', 2));
        assertEquals(3, AsciiString.indexOf("aabdabaa", 'd', 1));
    }

    @Test
    public void testSubStringHashCode() {
        //two "123"s
        assertEquals(AsciiString.hashCode("123"), AsciiString.hashCode("a123".substring(1)));
    }
}
