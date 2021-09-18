/*
 * Copyright 2014 The Netty Project
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

import org.junit.jupiter.api.Test;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Random;

import static io.netty.util.AsciiString.contains;
import static io.netty.util.AsciiString.containsIgnoreCase;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test character encoding and case insensitivity for the {@link AsciiString} class
 */
public class AsciiStringCharacterTest {
    private static final Random r = new Random();

    @Test
    public void testContentEqualsIgnoreCase() {
        byte[] bytes = { 32, 'a' };
        AsciiString asciiString = new AsciiString(bytes, 1, 1, false);
        // https://github.com/netty/netty/issues/9475
        assertFalse(asciiString.contentEqualsIgnoreCase("b"));
        assertFalse(asciiString.contentEqualsIgnoreCase(AsciiString.of("b")));
    }

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
            assertArrayEquals(expected, actual, "failure for " + charset);
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
            assertArrayEquals(expected, actual, "failure for " + charset);
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
        assertEquals(string, ascii.toString());
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
        assertEquals(upperCaseExpected, AsciiString.hashCode(upperCaseBuilder), errorString);
        assertEquals(upperCaseExpected, AsciiString.hashCode(upperCaseString), errorString);
        assertEquals(upperCaseExpected, upperCaseAscii.hashCode(), errorString);

        // Test lower case hash codes are equal
        final int lowerCaseExpected = lowerCaseAscii.hashCode();
        assertEquals(lowerCaseExpected, AsciiString.hashCode(lowerCaseAscii), errorString);
        assertEquals(lowerCaseExpected, AsciiString.hashCode(lowerCaseString), errorString);
        assertEquals(lowerCaseExpected, lowerCaseAscii.hashCode(), errorString);

        // Test case insensitive hash codes are equal
        final int expectedCaseInsensitive = lowerCaseAscii.hashCode();
        assertEquals(expectedCaseInsensitive, AsciiString.hashCode(upperCaseBuilder), errorString);
        assertEquals(expectedCaseInsensitive, AsciiString.hashCode(upperCaseString), errorString);
        assertEquals(expectedCaseInsensitive, AsciiString.hashCode(lowerCaseString), errorString);
        assertEquals(expectedCaseInsensitive, AsciiString.hashCode(lowerCaseAscii), errorString);
        assertEquals(expectedCaseInsensitive, AsciiString.hashCode(upperCaseAscii), errorString);
        assertEquals(expectedCaseInsensitive, lowerCaseAscii.hashCode(), errorString);
        assertEquals(expectedCaseInsensitive, upperCaseAscii.hashCode(), errorString);

        // Test that opposite cases are equal
        assertEquals(lowerCaseAscii.hashCode(), AsciiString.hashCode(upperCaseString), errorString);
        assertEquals(upperCaseAscii.hashCode(), AsciiString.hashCode(lowerCaseString), errorString);
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
        assertThat(AsciiString.contentEqualsIgnoreCase("FoO", "bar"), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase("Foo", "foobar"), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase("foobar", "Foo"), is(false));

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
        assertEquals(1, new AsciiString("abcd", 1, 2).indexOf('c', 0));
        assertEquals(2, new AsciiString("abcd", 1, 3).indexOf('d', 2));
        assertEquals(0, new AsciiString("abcd", 1, 2).indexOf('b', 0));
        assertEquals(-1, new AsciiString("abcd", 0, 2).indexOf('c', 0));
        assertEquals(-1, new AsciiString("abcd", 1, 3).indexOf('a', 0));
    }

    @Test
    public void testIndexOfCharSequence() {
        assertEquals(0, new AsciiString("abcd").indexOf("abcd", 0));
        assertEquals(0, new AsciiString("abcd").indexOf("abc", 0));
        assertEquals(1, new AsciiString("abcd").indexOf("bcd", 0));
        assertEquals(1, new AsciiString("abcd").indexOf("bc", 0));
        assertEquals(1, new AsciiString("abcdabcd").indexOf("bcd", 0));
        assertEquals(0, new AsciiString("abcd", 1, 2).indexOf("bc", 0));
        assertEquals(0, new AsciiString("abcd", 1, 3).indexOf("bcd", 0));
        assertEquals(1, new AsciiString("abcdabcd", 4, 4).indexOf("bcd", 0));
        assertEquals(3, new AsciiString("012345").indexOf("345", 3));
        assertEquals(3, new AsciiString("012345").indexOf("345", 0));

        // Test with empty string
        assertEquals(0, new AsciiString("abcd").indexOf("", 0));
        assertEquals(1, new AsciiString("abcd").indexOf("", 1));
        assertEquals(3, new AsciiString("abcd", 1, 3).indexOf("", 4));

        // Test not found
        assertEquals(-1, new AsciiString("abcd").indexOf("abcde", 0));
        assertEquals(-1, new AsciiString("abcdbc").indexOf("bce", 0));
        assertEquals(-1, new AsciiString("abcd", 1, 3).indexOf("abc", 0));
        assertEquals(-1, new AsciiString("abcd", 1, 2).indexOf("bd", 0));
        assertEquals(-1, new AsciiString("012345").indexOf("345", 4));
        assertEquals(-1, new AsciiString("012345").indexOf("abc", 3));
        assertEquals(-1, new AsciiString("012345").indexOf("abc", 0));
        assertEquals(-1, new AsciiString("012345").indexOf("abcdefghi", 0));
        assertEquals(-1, new AsciiString("012345").indexOf("abcdefghi", 4));
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
    public void testLastIndexOfCharSequence() {
        assertEquals(0, new AsciiString("abcd").lastIndexOf("abcd", 0));
        assertEquals(0, new AsciiString("abcd").lastIndexOf("abc", 4));
        assertEquals(1, new AsciiString("abcd").lastIndexOf("bcd", 4));
        assertEquals(1, new AsciiString("abcd").lastIndexOf("bc", 4));
        assertEquals(5, new AsciiString("abcdabcd").lastIndexOf("bcd", 10));
        assertEquals(0, new AsciiString("abcd", 1, 2).lastIndexOf("bc", 2));
        assertEquals(0, new AsciiString("abcd", 1, 3).lastIndexOf("bcd", 3));
        assertEquals(1, new AsciiString("abcdabcd", 4, 4).lastIndexOf("bcd", 4));
        assertEquals(3, new AsciiString("012345").lastIndexOf("345", 3));
        assertEquals(3, new AsciiString("012345").lastIndexOf("345", 6));

        // Test with empty string
        assertEquals(0, new AsciiString("abcd").lastIndexOf("", 0));
        assertEquals(1, new AsciiString("abcd").lastIndexOf("", 1));
        assertEquals(3, new AsciiString("abcd", 1, 3).lastIndexOf("", 4));

        // Test not found
        assertEquals(-1, new AsciiString("abcd").lastIndexOf("abcde", 0));
        assertEquals(-1, new AsciiString("abcdbc").lastIndexOf("bce", 0));
        assertEquals(-1, new AsciiString("abcd", 1, 3).lastIndexOf("abc", 0));
        assertEquals(-1, new AsciiString("abcd", 1, 2).lastIndexOf("bd", 0));
        assertEquals(-1, new AsciiString("012345").lastIndexOf("345", 2));
        assertEquals(-1, new AsciiString("012345").lastIndexOf("abc", 3));
        assertEquals(-1, new AsciiString("012345").lastIndexOf("abc", 0));
        assertEquals(-1, new AsciiString("012345").lastIndexOf("abcdefghi", 0));
        assertEquals(-1, new AsciiString("012345").lastIndexOf("abcdefghi", 4));
    }

    @Test
    public void testReplace() {
        AsciiString abcd = new AsciiString("abcd");
        assertEquals(new AsciiString("adcd"), abcd.replace('b', 'd'));
        assertEquals(new AsciiString("dbcd"), abcd.replace('a', 'd'));
        assertEquals(new AsciiString("abca"), abcd.replace('d', 'a'));
        assertSame(abcd, abcd.replace('x', 'a'));
        assertEquals(new AsciiString("cc"), new AsciiString("abcd", 1, 2).replace('b', 'c'));
        assertEquals(new AsciiString("bb"), new AsciiString("abcd", 1, 2).replace('c', 'b'));
        assertEquals(new AsciiString("bddd"), new AsciiString("abcdc", 1, 4).replace('c', 'd'));
        assertEquals(new AsciiString("xbcxd"), new AsciiString("abcada", 0, 5).replace('a', 'x'));
    }

    @Test
    public void testSubStringHashCode() {
        //two "123"s
        assertEquals(AsciiString.hashCode("123"), AsciiString.hashCode("a123".substring(1)));
    }

    @Test
    public void testIndexOf() {
        AsciiString foo = AsciiString.of("This is a test");
        int i1 = foo.indexOf(' ', 0);
        assertEquals(4, i1);
        int i2 = foo.indexOf(' ', i1 + 1);
        assertEquals(7, i2);
        int i3 = foo.indexOf(' ', i2 + 1);
        assertEquals(9, i3);
        assertTrue(i3 + 1 < foo.length());
        int i4 = foo.indexOf(' ', i3 + 1);
        assertEquals(i4, -1);
    }
}
