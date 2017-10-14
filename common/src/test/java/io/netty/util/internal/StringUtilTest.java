/*
 * Copyright 2012 The Netty Project
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

import java.util.Arrays;

import static io.netty.util.internal.StringUtil.NEWLINE;
import static io.netty.util.internal.StringUtil.commonSuffixOfLength;
import static io.netty.util.internal.StringUtil.simpleClassName;
import static io.netty.util.internal.StringUtil.substringAfter;
import static io.netty.util.internal.StringUtil.toHexString;
import static io.netty.util.internal.StringUtil.toHexStringPadded;
import static io.netty.util.internal.StringUtil.unescapeCsv;
import static io.netty.util.internal.StringUtil.unescapeCsvFields;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class StringUtilTest {

    @Test
    public void ensureNewlineExists() {
        assertNotNull(NEWLINE);
    }

    @Test
    public void testToHexString() {
        assertThat(toHexString(new byte[] { 0 }), is("0"));
        assertThat(toHexString(new byte[] { 1 }), is("1"));
        assertThat(toHexString(new byte[] { 0, 0 }), is("0"));
        assertThat(toHexString(new byte[] { 1, 0 }), is("100"));
        assertThat(toHexString(EmptyArrays.EMPTY_BYTES), is(""));
    }

    @Test
    public void testToHexStringPadded() {
        assertThat(toHexStringPadded(new byte[]{0}), is("00"));
        assertThat(toHexStringPadded(new byte[]{1}), is("01"));
        assertThat(toHexStringPadded(new byte[]{0, 0}), is("0000"));
        assertThat(toHexStringPadded(new byte[]{1, 0}), is("0100"));
        assertThat(toHexStringPadded(EmptyArrays.EMPTY_BYTES), is(""));
    }

    @Test
    public void splitSimple() {
        assertArrayEquals(new String[] { "foo", "bar" }, "foo:bar".split(":"));
    }

    @Test
    public void splitWithTrailingDelimiter() {
        assertArrayEquals(new String[] { "foo", "bar" }, "foo,bar,".split(","));
    }

    @Test
    public void splitWithTrailingDelimiters() {
        assertArrayEquals(new String[] { "foo", "bar" }, "foo!bar!!".split("!"));
    }

    @Test
    public void splitWithTrailingDelimitersDot() {
        assertArrayEquals(new String[] { "foo", "bar" }, "foo.bar..".split("\\."));
    }

    @Test
    public void splitWithTrailingDelimitersEq() {
        assertArrayEquals(new String[] { "foo", "bar" }, "foo=bar==".split("="));
    }

    @Test
    public void splitWithTrailingDelimitersSpace() {
        assertArrayEquals(new String[] { "foo", "bar" }, "foo bar  ".split(" "));
    }

    @Test
    public void splitWithConsecutiveDelimiters() {
        assertArrayEquals(new String[] { "foo", "", "bar" }, "foo$$bar".split("\\$"));
    }

    @Test
    public void splitWithDelimiterAtBeginning() {
        assertArrayEquals(new String[] { "", "foo", "bar" }, "#foo#bar".split("#"));
    }

    @Test
    public void splitMaxPart() {
        assertArrayEquals(new String[] { "foo", "bar:bar2" }, "foo:bar:bar2".split(":", 2));
        assertArrayEquals(new String[] { "foo", "bar", "bar2" }, "foo:bar:bar2".split(":", 3));
    }

    @Test
    public void substringAfterTest() {
        assertEquals("bar:bar2", substringAfter("foo:bar:bar2", ':'));
    }

    @Test
    public void commonSuffixOfLengthTest() {
        // negative length suffixes are never common
        checkNotCommonSuffix("abc", "abc", -1);

        // null has no suffix
        checkNotCommonSuffix("abc", null, 0);
        checkNotCommonSuffix(null, null, 0);

        // any non-null string has 0-length suffix
        checkCommonSuffix("abc", "xx", 0);

        checkCommonSuffix("abc", "abc", 0);
        checkCommonSuffix("abc", "abc", 1);
        checkCommonSuffix("abc", "abc", 2);
        checkCommonSuffix("abc", "abc", 3);
        checkNotCommonSuffix("abc", "abc", 4);

        checkCommonSuffix("abcd", "cd", 1);
        checkCommonSuffix("abcd", "cd", 2);
        checkNotCommonSuffix("abcd", "cd", 3);

        checkCommonSuffix("abcd", "axcd", 1);
        checkCommonSuffix("abcd", "axcd", 2);
        checkNotCommonSuffix("abcd", "axcd", 3);

        checkNotCommonSuffix("abcx", "abcy", 1);
    }

    private static void checkNotCommonSuffix(String s, String p, int len) {
        assertFalse(checkCommonSuffixSymmetric(s, p, len));
    }

    private static void checkCommonSuffix(String s, String p, int len) {
        assertTrue(checkCommonSuffixSymmetric(s, p, len));
    }

    private static boolean checkCommonSuffixSymmetric(String s, String p, int len) {
        boolean sp = commonSuffixOfLength(s, p, len);
        boolean ps = commonSuffixOfLength(p, s, len);
        assertEquals(sp, ps);
        return sp;
    }

    @Test (expected = NullPointerException.class)
    public void escapeCsvNull() {
        StringUtil.escapeCsv(null);
    }

    @Test
    public void escapeCsvEmpty() {
        CharSequence value = "";
        CharSequence expected = value;
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvUnquoted() {
        CharSequence value = "something";
        CharSequence expected = value;
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvAlreadyQuoted() {
        CharSequence value = "\"something\"";
        CharSequence expected = "\"something\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithQuote() {
        CharSequence value = "s\"";
        CharSequence expected = "\"s\"\"\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithQuoteInMiddle() {
        CharSequence value = "some text\"and more text";
        CharSequence expected = "\"some text\"\"and more text\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithQuoteInMiddleAlreadyQuoted() {
        CharSequence value = "\"some text\"and more text\"";
        CharSequence expected = "\"some text\"\"and more text\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithQuotedWords() {
        CharSequence value = "\"foo\"\"goo\"";
        CharSequence expected = "\"foo\"\"goo\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithAlreadyEscapedQuote() {
        CharSequence value = "foo\"\"goo";
        CharSequence expected = "foo\"\"goo";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvEndingWithQuote() {
        CharSequence value = "some\"";
        CharSequence expected = "\"some\"\"\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithSingleQuote() {
        CharSequence value = "\"";
        CharSequence expected = "\"\"\"\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithSingleQuoteAndCharacter() {
        CharSequence value = "\"f";
        CharSequence expected = "\"\"\"f\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvAlreadyEscapedQuote() {
        CharSequence value = "\"some\"\"";
        CharSequence expected = "\"some\"\"\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvQuoted() {
        CharSequence value = "\"foo,goo\"";
        CharSequence expected = value;
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithLineFeed() {
        CharSequence value = "some text\n more text";
        CharSequence expected = "\"some text\n more text\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithSingleLineFeedCharacter() {
        CharSequence value = "\n";
        CharSequence expected = "\"\n\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithMultipleLineFeedCharacter() {
        CharSequence value = "\n\n";
        CharSequence expected = "\"\n\n\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithQuotedAndLineFeedCharacter() {
        CharSequence value = " \" \n ";
        CharSequence expected = "\" \"\" \n \"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithLineFeedAtEnd() {
        CharSequence value = "testing\n";
        CharSequence expected = "\"testing\n\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithComma() {
        CharSequence value = "test,ing";
        CharSequence expected = "\"test,ing\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithSingleComma() {
        CharSequence value = ",";
        CharSequence expected = "\",\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithSingleCarriageReturn() {
        CharSequence value = "\r";
        CharSequence expected = "\"\r\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithMultipleCarriageReturn() {
        CharSequence value = "\r\r";
        CharSequence expected = "\"\r\r\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithCarriageReturn() {
        CharSequence value = "some text\r more text";
        CharSequence expected = "\"some text\r more text\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithQuotedAndCarriageReturnCharacter() {
        CharSequence value = "\"\r";
        CharSequence expected = "\"\"\"\r\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithCarriageReturnAtEnd() {
        CharSequence value = "testing\r";
        CharSequence expected = "\"testing\r\"";
        escapeCsv(value, expected);
    }

    @Test
    public void escapeCsvWithCRLFCharacter() {
        CharSequence value = "\r\n";
        CharSequence expected = "\"\r\n\"";
        escapeCsv(value, expected);
    }

    private static void escapeCsv(CharSequence value, CharSequence expected) {
        escapeCsv(value, expected, false);
    }

    private static void escapeCsvWithTrimming(CharSequence value, CharSequence expected) {
        escapeCsv(value, expected, true);
    }

    private static void escapeCsv(CharSequence value, CharSequence expected, boolean trimOws) {
        CharSequence escapedValue = value;
        for (int i = 0; i < 10; ++i) {
            escapedValue = StringUtil.escapeCsv(escapedValue, trimOws);
            assertEquals(expected, escapedValue.toString());
        }
    }

    @Test
    public void escapeCsvWithTrimming() {
        assertSame("", StringUtil.escapeCsv("", true));
        assertSame("ab", StringUtil.escapeCsv("ab", true));

        escapeCsvWithTrimming("", "");
        escapeCsvWithTrimming(" \t ", "");
        escapeCsvWithTrimming("ab", "ab");
        escapeCsvWithTrimming("a b", "a b");
        escapeCsvWithTrimming(" \ta \tb", "a \tb");
        escapeCsvWithTrimming("a \tb \t", "a \tb");
        escapeCsvWithTrimming("\t a \tb \t", "a \tb");
        escapeCsvWithTrimming("\"\t a b \"", "\"\t a b \"");
        escapeCsvWithTrimming(" \"\t a b \"\t", "\"\t a b \"");
        escapeCsvWithTrimming(" testing\t\n ", "\"testing\t\n\"");
        escapeCsvWithTrimming("\ttest,ing ", "\"test,ing\"");
    }

    @Test
    public void escapeCsvGarbageFree() {
        // 'StringUtil#escapeCsv()' should return same string object if string didn't changing.
        assertSame("1", StringUtil.escapeCsv("1", true));
        assertSame(" 123 ", StringUtil.escapeCsv(" 123 ", false));
        assertSame("\" 123 \"", StringUtil.escapeCsv("\" 123 \"", true));
        assertSame("\"\"", StringUtil.escapeCsv("\"\"", true));
        assertSame("123 \"\"", StringUtil.escapeCsv("123 \"\"", true));
        assertSame("123\"\"321", StringUtil.escapeCsv("123\"\"321", true));
        assertSame("\"123\"\"321\"", StringUtil.escapeCsv("\"123\"\"321\"", true));
    }

    @Test
    public void testUnescapeCsv() {
        assertEquals("", unescapeCsv(""));
        assertEquals("\"", unescapeCsv("\"\"\"\""));
        assertEquals("\"\"", unescapeCsv("\"\"\"\"\"\""));
        assertEquals("\"\"\"", unescapeCsv("\"\"\"\"\"\"\"\""));
        assertEquals("\"netty\"", unescapeCsv("\"\"\"netty\"\"\""));
        assertEquals("netty", unescapeCsv("netty"));
        assertEquals("netty", unescapeCsv("\"netty\""));
        assertEquals("\r", unescapeCsv("\"\r\""));
        assertEquals("\n", unescapeCsv("\"\n\""));
        assertEquals("hello,netty", unescapeCsv("\"hello,netty\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvWithSingleQuote() {
        unescapeCsv("\"");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvWithOddQuote() {
        unescapeCsv("\"\"\"");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvWithCRAndWithoutQuote() {
        unescapeCsv("\r");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvWithLFAndWithoutQuote() {
        unescapeCsv("\n");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvWithCommaAndWithoutQuote() {
        unescapeCsv(",");
    }

    @Test
    public void escapeCsvAndUnEscapeCsv() {
        assertEscapeCsvAndUnEscapeCsv("");
        assertEscapeCsvAndUnEscapeCsv("netty");
        assertEscapeCsvAndUnEscapeCsv("hello,netty");
        assertEscapeCsvAndUnEscapeCsv("hello,\"netty\"");
        assertEscapeCsvAndUnEscapeCsv("\"");
        assertEscapeCsvAndUnEscapeCsv(",");
        assertEscapeCsvAndUnEscapeCsv("\r");
        assertEscapeCsvAndUnEscapeCsv("\n");
    }

    private static void assertEscapeCsvAndUnEscapeCsv(String value) {
        assertEquals(value, unescapeCsv(StringUtil.escapeCsv(value)));
    }

    @Test
    public void testUnescapeCsvFields() {
        assertEquals(Arrays.asList(""), unescapeCsvFields(""));
        assertEquals(Arrays.asList("", ""), unescapeCsvFields(","));
        assertEquals(Arrays.asList("a", ""), unescapeCsvFields("a,"));
        assertEquals(Arrays.asList("", "a"), unescapeCsvFields(",a"));
        assertEquals(Arrays.asList("\""), unescapeCsvFields("\"\"\"\""));
        assertEquals(Arrays.asList("\"", "\""), unescapeCsvFields("\"\"\"\",\"\"\"\""));
        assertEquals(Arrays.asList("netty"), unescapeCsvFields("netty"));
        assertEquals(Arrays.asList("hello", "netty"), unescapeCsvFields("hello,netty"));
        assertEquals(Arrays.asList("hello,netty"), unescapeCsvFields("\"hello,netty\""));
        assertEquals(Arrays.asList("hello", "netty"), unescapeCsvFields("\"hello\",\"netty\""));
        assertEquals(Arrays.asList("a\"b", "c\"d"), unescapeCsvFields("\"a\"\"b\",\"c\"\"d\""));
        assertEquals(Arrays.asList("a\rb", "c\nd"), unescapeCsvFields("\"a\rb\",\"c\nd\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvFieldsWithCRWithoutQuote() {
        unescapeCsvFields("a,\r");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvFieldsWithLFWithoutQuote() {
        unescapeCsvFields("a,\r");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvFieldsWithQuote() {
        unescapeCsvFields("a,\"");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvFieldsWithQuote2() {
        unescapeCsvFields("\",a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unescapeCsvFieldsWithQuote3() {
        unescapeCsvFields("a\"b,a");
    }

    @Test
    public void testSimpleClassName() throws Exception {
        testSimpleClassName(String.class);
    }

    @Test
    public void testSimpleInnerClassName() throws Exception {
        testSimpleClassName(TestClass.class);
    }

    private static void testSimpleClassName(Class<?> clazz) throws Exception {
        Package pkg = clazz.getPackage();
        String name;
        if (pkg != null) {
            name = clazz.getName().substring(pkg.getName().length() + 1);
        } else {
            name = clazz.getName();
        }
        assertEquals(name, simpleClassName(clazz));
    }

    private static final class TestClass { }

    @Test
    public void testEndsWith() {
        assertFalse(StringUtil.endsWith("", 'u'));
        assertTrue(StringUtil.endsWith("u", 'u'));
        assertTrue(StringUtil.endsWith("-u", 'u'));
        assertFalse(StringUtil.endsWith("-", 'u'));
        assertFalse(StringUtil.endsWith("u-", 'u'));
    }

    @Test
    public void trimOws() {
        assertSame("", StringUtil.trimOws(""));
        assertEquals("", StringUtil.trimOws(" \t "));
        assertSame("a", StringUtil.trimOws("a"));
        assertEquals("a", StringUtil.trimOws(" a"));
        assertEquals("a", StringUtil.trimOws("a "));
        assertEquals("a", StringUtil.trimOws(" a "));
        assertSame("abc", StringUtil.trimOws("abc"));
        assertEquals("abc", StringUtil.trimOws("\tabc"));
        assertEquals("abc", StringUtil.trimOws("abc\t"));
        assertEquals("abc", StringUtil.trimOws("\tabc\t"));
        assertSame("a\t b", StringUtil.trimOws("a\t b"));
        assertEquals("", StringUtil.trimOws("\t ").toString());
        assertEquals("a b", StringUtil.trimOws("\ta b \t").toString());
    }
}
