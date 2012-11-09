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
package org.jboss.netty.util.internal;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test for {@link StringUtil}.
 */
public class StringUtilTest {

    @Test
    public void stripControlCharactersObjectNull() {
        assertNull(StringUtil.stripControlCharacters(null));
    }

    @Test
    public void stripControlCharactersNull() {
        assertNull(StringUtil.stripControlCharacters(null));
    }

    @Test
    public void stripControlCharactersRightTrim() {
        final char controlCode = 0x0000;
        final String object = "abbb" + controlCode;
        assertEquals(5, object.length());

        final String stripped = StringUtil.stripControlCharacters(object);
        assertFalse(object.equals(stripped));
        assertEquals(4, stripped.length());
    }

    @Test
    public void stripControlCharactersLeftTrim() {
        final char controlCode = 0x0000;
        final String string = controlCode + "abbb";
        assertEquals(5, string.length());

        final String stripped = StringUtil.stripControlCharacters(string);
        assertFalse(string.equals(stripped));
        assertEquals(4, stripped.length());
    }

    @Test
    public void stripControlCharacters() {
        for (char i = 0x0000; i <= 0x001F; i ++) {
            assertStripped(i);
        }
        for (char i = 0x007F; i <= 0x009F; i ++) {
            assertStripped(i);
        }
    }

    private static void assertStripped(final char controlCode) {
        final Object object = "aaa" + controlCode + "bbb";
        final String stripped = StringUtil.stripControlCharacters(object);
        assertEquals("aaa bbb", stripped);
    }

    @Test
    public void stripNonControlCharacter() {
        final char controlCode = 0x002F;
        final String string = controlCode + "abbb";
        final String stripped = StringUtil.stripControlCharacters(string);
        assertEquals("The string should be unchanged", string, stripped);
    }

    @Test
    public void splitSimple() {
        assertArrayEquals(new String[] { "foo", "bar" }, StringUtil.split("foo:bar", ':'));
    }

    @Test
    public void splitWithTrailingDelimiter() {
        assertArrayEquals(new String[] { "foo", "bar" }, StringUtil.split("foo,bar,", ','));
    }

    @Test
    public void splitWithTrailingDelimiters() {
        assertArrayEquals(new String[] { "foo", "bar" }, StringUtil.split("foo!bar!!", '!'));
    }

    @Test
    public void splitWithConsecutiveDelimiters() {
        assertArrayEquals(new String[] { "foo", "", "bar" }, StringUtil.split("foo$$bar", '$'));
    }

    @Test
    public void splitWithDelimiterAtBeginning() {
        assertArrayEquals(new String[] { "", "foo", "bar" }, StringUtil.split("#foo#bar", '#'));
    }
}
