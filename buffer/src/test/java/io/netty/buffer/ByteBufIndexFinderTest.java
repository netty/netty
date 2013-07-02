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
package io.netty.buffer;

import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Tests the index-finding capabilities of channel buffers
 */
@SuppressWarnings("deprecation")
public class ByteBufIndexFinderTest {

    @Test
    public void testForward() {
        ByteBuf buf = Unpooled.copiedBuffer(
                "abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx",
                CharsetUtil.ISO_8859_1);

        assertEquals(3, buf.indexOf(Integer.MIN_VALUE, buf.capacity(), ByteBufIndexFinder.CRLF));
        assertEquals(6, buf.indexOf(3, buf.capacity(), ByteBufIndexFinder.NOT_CRLF));
        assertEquals(9, buf.indexOf(6, buf.capacity(), ByteBufIndexFinder.CR));
        assertEquals(11, buf.indexOf(9, buf.capacity(), ByteBufIndexFinder.NOT_CR));
        assertEquals(14, buf.indexOf(11, buf.capacity(), ByteBufIndexFinder.LF));
        assertEquals(16, buf.indexOf(14, buf.capacity(), ByteBufIndexFinder.NOT_LF));
        assertEquals(19, buf.indexOf(16, buf.capacity(), ByteBufIndexFinder.NUL));
        assertEquals(21, buf.indexOf(19, buf.capacity(), ByteBufIndexFinder.NOT_NUL));
        assertEquals(24, buf.indexOf(21, buf.capacity(), ByteBufIndexFinder.LINEAR_WHITESPACE));
        assertEquals(28, buf.indexOf(24, buf.capacity(), ByteBufIndexFinder.NOT_LINEAR_WHITESPACE));
        assertEquals(-1, buf.indexOf(28, buf.capacity(), ByteBufIndexFinder.LINEAR_WHITESPACE));
    }

    @Test
    public void testBackward() {
        ByteBuf buf = Unpooled.copiedBuffer(
                "abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx",
                CharsetUtil.ISO_8859_1);

        assertEquals(27, buf.indexOf(Integer.MAX_VALUE, 0, ByteBufIndexFinder.LINEAR_WHITESPACE));
        assertEquals(23, buf.indexOf(28, 0, ByteBufIndexFinder.NOT_LINEAR_WHITESPACE));
        assertEquals(20, buf.indexOf(24, 0, ByteBufIndexFinder.NUL));
        assertEquals(18, buf.indexOf(21, 0, ByteBufIndexFinder.NOT_NUL));
        assertEquals(15, buf.indexOf(19, 0, ByteBufIndexFinder.LF));
        assertEquals(13, buf.indexOf(16, 0, ByteBufIndexFinder.NOT_LF));
        assertEquals(10, buf.indexOf(14, 0, ByteBufIndexFinder.CR));
        assertEquals(8, buf.indexOf(11, 0, ByteBufIndexFinder.NOT_CR));
        assertEquals(5, buf.indexOf(9, 0, ByteBufIndexFinder.CRLF));
        assertEquals(2, buf.indexOf(6, 0, ByteBufIndexFinder.NOT_CRLF));
        assertEquals(-1, buf.indexOf(3, 0, ByteBufIndexFinder.CRLF));
    }
}
