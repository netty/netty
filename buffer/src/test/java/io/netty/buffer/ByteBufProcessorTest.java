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

import static io.netty.util.ReferenceCountUtil.releaseLater;
import static org.junit.Assert.*;

public class ByteBufProcessorTest {
    @Test
    public void testForward() {
        final ByteBuf buf = releaseLater(
                Unpooled.copiedBuffer("abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx", CharsetUtil.ISO_8859_1));
        final int length = buf.readableBytes();

        assertEquals(3,  buf.forEachByte(0,  length, ByteBufProcessor.FIND_CRLF));
        assertEquals(6,  buf.forEachByte(3,  length - 3, ByteBufProcessor.FIND_NON_CRLF));
        assertEquals(9,  buf.forEachByte(6,  length - 6, ByteBufProcessor.FIND_CR));
        assertEquals(11, buf.forEachByte(9,  length - 9, ByteBufProcessor.FIND_NON_CR));
        assertEquals(14, buf.forEachByte(11, length - 11, ByteBufProcessor.FIND_LF));
        assertEquals(16, buf.forEachByte(14, length - 14, ByteBufProcessor.FIND_NON_LF));
        assertEquals(19, buf.forEachByte(16, length - 16, ByteBufProcessor.FIND_NUL));
        assertEquals(21, buf.forEachByte(19, length - 19, ByteBufProcessor.FIND_NON_NUL));
        assertEquals(24, buf.forEachByte(21, length - 21, ByteBufProcessor.FIND_LINEAR_WHITESPACE));
        assertEquals(28, buf.forEachByte(24, length - 24, ByteBufProcessor.FIND_NON_LINEAR_WHITESPACE));
        assertEquals(-1, buf.forEachByte(28, length - 28, ByteBufProcessor.FIND_LINEAR_WHITESPACE));
    }

    @Test
    public void testBackward() {
        final ByteBuf buf = releaseLater(
                Unpooled.copiedBuffer("abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx", CharsetUtil.ISO_8859_1));
        final int length = buf.readableBytes();

        assertEquals(27, buf.forEachByteDesc(0, length, ByteBufProcessor.FIND_LINEAR_WHITESPACE));
        assertEquals(23, buf.forEachByteDesc(0, 28, ByteBufProcessor.FIND_NON_LINEAR_WHITESPACE));
        assertEquals(20, buf.forEachByteDesc(0, 24, ByteBufProcessor.FIND_NUL));
        assertEquals(18, buf.forEachByteDesc(0, 21, ByteBufProcessor.FIND_NON_NUL));
        assertEquals(15, buf.forEachByteDesc(0, 19, ByteBufProcessor.FIND_LF));
        assertEquals(13, buf.forEachByteDesc(0, 16, ByteBufProcessor.FIND_NON_LF));
        assertEquals(10, buf.forEachByteDesc(0, 14, ByteBufProcessor.FIND_CR));
        assertEquals(8,  buf.forEachByteDesc(0, 11, ByteBufProcessor.FIND_NON_CR));
        assertEquals(5,  buf.forEachByteDesc(0, 9, ByteBufProcessor.FIND_CRLF));
        assertEquals(2,  buf.forEachByteDesc(0, 6, ByteBufProcessor.FIND_NON_CRLF));
        assertEquals(-1, buf.forEachByteDesc(0, 3, ByteBufProcessor.FIND_CRLF));
    }
}
