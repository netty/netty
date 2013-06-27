/*
 * Copyright 2013 The Netty Project
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

public class ByteBufProcessorTest {
    @Test
    public void testForward() {
        ByteBuf buf = Unpooled.copiedBuffer("abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx", CharsetUtil.ISO_8859_1);

        assertEquals(3,  buf.forEachByte(0,  buf.capacity(), ByteBufProcessor.FIND_CRLF));
        assertEquals(6,  buf.forEachByte(3,  buf.capacity(), ByteBufProcessor.FIND_NON_CRLF));
        assertEquals(9,  buf.forEachByte(6,  buf.capacity(), ByteBufProcessor.FIND_CR));
        assertEquals(11, buf.forEachByte(9,  buf.capacity(), ByteBufProcessor.FIND_NON_CR));
        assertEquals(14, buf.forEachByte(11, buf.capacity(), ByteBufProcessor.FIND_LF));
        assertEquals(16, buf.forEachByte(14, buf.capacity(), ByteBufProcessor.FIND_NON_LF));
        assertEquals(19, buf.forEachByte(16, buf.capacity(), ByteBufProcessor.FIND_NUL));
        assertEquals(21, buf.forEachByte(19, buf.capacity(), ByteBufProcessor.FIND_NON_NUL));
        assertEquals(24, buf.forEachByte(21, buf.capacity(), ByteBufProcessor.FIND_LINEAR_WHITESPACE));
        assertEquals(28, buf.forEachByte(24, buf.capacity(), ByteBufProcessor.FIND_NON_LINEAR_WHITESPACE));
        assertEquals(-1, buf.forEachByte(28, buf.capacity(), ByteBufProcessor.FIND_LINEAR_WHITESPACE));
    }

    @Test
    public void testBackward() {
        ByteBuf buf = Unpooled.copiedBuffer("abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx", CharsetUtil.ISO_8859_1);

        assertEquals(27, buf.forEachByte(buf.capacity() - 1, -1, ByteBufProcessor.FIND_LINEAR_WHITESPACE));
        assertEquals(23, buf.forEachByte(27, -1, ByteBufProcessor.FIND_NON_LINEAR_WHITESPACE));
        assertEquals(20, buf.forEachByte(23, -1, ByteBufProcessor.FIND_NUL));
        assertEquals(18, buf.forEachByte(20, -1, ByteBufProcessor.FIND_NON_NUL));
        assertEquals(15, buf.forEachByte(18, -1, ByteBufProcessor.FIND_LF));
        assertEquals(13, buf.forEachByte(15, -1, ByteBufProcessor.FIND_NON_LF));
        assertEquals(10, buf.forEachByte(13, -1, ByteBufProcessor.FIND_CR));
        assertEquals(8,  buf.forEachByte(10, -1, ByteBufProcessor.FIND_NON_CR));
        assertEquals(5,  buf.forEachByte(8,  -1, ByteBufProcessor.FIND_CRLF));
        assertEquals(2,  buf.forEachByte(5,  -1, ByteBufProcessor.FIND_NON_CRLF));
        assertEquals(-1, buf.forEachByte(2,  -1, ByteBufProcessor.FIND_CRLF));
    }
}
