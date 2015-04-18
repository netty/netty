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

import static io.netty.util.internal.StringUtil.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

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
        assertArrayEquals(new String[] { "foo", "bar" }, split("foo:bar", ':'));
    }

    @Test
    public void splitWithTrailingDelimiter() {
        assertArrayEquals(new String[] { "foo", "bar" }, split("foo,bar,", ','));
    }

    @Test
    public void splitWithTrailingDelimiters() {
        assertArrayEquals(new String[] { "foo", "bar" }, split("foo!bar!!", '!'));
    }

    @Test
    public void splitWithConsecutiveDelimiters() {
        assertArrayEquals(new String[] { "foo", "", "bar" }, split("foo$$bar", '$'));
    }

    @Test
    public void splitWithDelimiterAtBeginning() {
        assertArrayEquals(new String[] { "", "foo", "bar" }, split("#foo#bar", '#'));
    }

    @Test
    public void splitMaxPart() {
        assertArrayEquals(new String[] { "foo", "bar:bar2" }, split("foo:bar:bar2", ':', 2));
        assertArrayEquals(new String[] { "foo", "bar", "bar2" }, split("foo:bar:bar2", ':', 3));
    }

    @Test
    public void substringAfterTest() {
        assertEquals("bar:bar2", substringAfter("foo:bar:bar2", ':'));
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
        CharSequence escapedValue = value;
        for (int i = 0; i < 10; ++i) {
            escapedValue = StringUtil.escapeCsv(escapedValue);
            assertEquals(expected, escapedValue.toString());
        }
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
}
