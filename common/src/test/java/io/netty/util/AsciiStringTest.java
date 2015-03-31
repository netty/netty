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

import static org.junit.Assert.assertArrayEquals;

import java.nio.charset.Charset;

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
}
