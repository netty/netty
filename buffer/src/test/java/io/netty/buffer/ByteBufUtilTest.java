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
package io.netty.buffer;

import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.nio.charset.Charset;

import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.util.ReferenceCountUtil.releaseLater;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ByteBufUtilTest {

    @Test
    public void testWriteUsAscii() {
        String usAscii = "NettyRocks";
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUsAsciiWrapped() {
        String usAscii = "NettyRocks";
        ByteBuf buf = unreleasableBuffer(releaseLater(Unpooled.buffer(16)));
        assertWrapped(buf);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = unreleasableBuffer(releaseLater(Unpooled.buffer(16)));
        assertWrapped(buf2);
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8() {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(usAscii.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, usAscii);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8Surrogates() {
        // leading surrogate + trailing surrogate
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uD800')
                                .append('\uDC00')
                                .append('b')
                                .toString();
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8InvalidOnlyTrailingSurrogate() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uDC00')
                                .append('b')
                                .toString();
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8InvalidOnlyLeadingSurrogate() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uD800')
                                .append('b')
                                .toString();
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8InvalidSurrogatesSwitched() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uDC00')
                                .append('\uD800')
                                .append('b')
                                .toString();
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8InvalidTwoLeadingSurrogates() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uD800')
                                .append('\uD800')
                                .append('b')
                                .toString();
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8InvalidTwoTrailingSurrogates() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uDC00')
                                .append('\uDC00')
                                .append('b')
                                .toString();
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8InvalidEndOnLeadingSurrogate() {
        String surrogateString = new StringBuilder(2)
                                .append('\uD800')
                                .toString();
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8InvalidEndOnTrailingSurrogate() {
        String surrogateString = new StringBuilder(2)
                                .append('\uDC00')
                                .toString();
        ByteBuf buf = releaseLater(Unpooled.buffer(16));
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8Wrapped() {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = unreleasableBuffer(releaseLater(Unpooled.buffer(16)));
        assertWrapped(buf);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = unreleasableBuffer(releaseLater(Unpooled.buffer(16)));
        assertWrapped(buf2);
        ByteBufUtil.writeUtf8(buf2, usAscii);

        assertEquals(buf, buf2);
    }

    private static void assertWrapped(ByteBuf buf) {
        assertTrue(buf instanceof WrappedByteBuf);
    }

    @Test
    public void testDecodeUsAscii() {
        testDecodeString("This is a test", CharsetUtil.US_ASCII);
    }

    @Test
    public void testDecodeUtf8() {
        testDecodeString("Some UTF-8 like äÄ∏ŒŒ", CharsetUtil.UTF_8);
    }

    private static void testDecodeString(String text, Charset charset) {
        ByteBuf buffer = Unpooled.copiedBuffer(text, charset);
        assertEquals(text, ByteBufUtil.decodeString(buffer, 0, buffer.readableBytes(), charset));
        buffer.release();
    }

    @Test
    public void testToStringDoesNotThrowIndexOutOfBounds() {
        CompositeByteBuf buffer = Unpooled.compositeBuffer();
        try {
            byte[] bytes = "1234".getBytes(CharsetUtil.UTF_8);
            buffer.addComponent(Unpooled.buffer(bytes.length).writeBytes(bytes));
            buffer.addComponent(Unpooled.buffer(bytes.length).writeBytes(bytes));
            assertEquals("1234", buffer.toString(bytes.length, bytes.length, CharsetUtil.UTF_8));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testIsTextWithUtf8() {
        byte[][] validUtf8Bytes = new byte[][]{
                "netty".getBytes(CharsetUtil.UTF_8),
                new byte[]{(byte) 0x24},
                new byte[]{(byte) 0xC2, (byte) 0xA2},
                new byte[]{(byte) 0xE2, (byte) 0x82, (byte) 0xAC},
                new byte[]{(byte) 0xF0, (byte) 0x90, (byte) 0x8D, (byte) 0x88},
                new byte[]{(byte) 0x24,
                        (byte) 0xC2, (byte) 0xA2,
                        (byte) 0xE2, (byte) 0x82, (byte) 0xAC,
                        (byte) 0xF0, (byte) 0x90, (byte) 0x8D, (byte) 0x88} // multiple characters
        };
        for (byte[] bytes : validUtf8Bytes) {
            assertIsText(bytes, true, CharsetUtil.UTF_8);
        }
        byte[][] invalidUtf8Bytes = new byte[][]{
                new byte[]{(byte) 0x80},
                new byte[]{(byte) 0xF0, (byte) 0x82, (byte) 0x82, (byte) 0xAC}, // Overlong encodings
                new byte[]{(byte) 0xC2},                                        // not enough bytes
                new byte[]{(byte) 0xE2, (byte) 0x82},                           // not enough bytes
                new byte[]{(byte) 0xF0, (byte) 0x90, (byte) 0x8D},              // not enough bytes
                new byte[]{(byte) 0xC2, (byte) 0xC0},                           // not correct bytes
                new byte[]{(byte) 0xE2, (byte) 0x82, (byte) 0xC0},              // not correct bytes
                new byte[]{(byte) 0xF0, (byte) 0x90, (byte) 0x8D, (byte) 0xC0}, // not correct bytes
                new byte[]{(byte) 0xC1, (byte) 0x80},                           // out of lower bound
                new byte[]{(byte) 0xE0, (byte) 0x80, (byte) 0x80},              // out of lower bound
                new byte[]{(byte) 0xED, (byte) 0xAF, (byte) 0x80}               // out of upper bound
        };
        for (byte[] bytes : invalidUtf8Bytes) {
            assertIsText(bytes, false, CharsetUtil.UTF_8);
        }
    }

    @Test
    public void testIsTextWithoutOptimization() {
        byte[] validBytes = new byte[]{(byte) 0x01, (byte) 0xD8, (byte) 0x37, (byte) 0xDC};
        byte[] invalidBytes = new byte[]{(byte) 0x01, (byte) 0xD8};

        assertIsText(validBytes, true, CharsetUtil.UTF_16LE);
        assertIsText(invalidBytes, false, CharsetUtil.UTF_16LE);
    }

    @Test
    public void testIsTextWithAscii() {
        byte[] validBytes = new byte[]{(byte) 0x00, (byte) 0x01, (byte) 0x37, (byte) 0x7F};
        byte[] invalidBytes = new byte[]{(byte) 0x80, (byte) 0xFF};

        assertIsText(validBytes, true, CharsetUtil.US_ASCII);
        assertIsText(invalidBytes, false, CharsetUtil.US_ASCII);
    }

    @Test
    public void testIsTextWithInvalidIndexAndLength() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeBytes(new byte[4]);
            int[][] validIndexLengthPairs = new int[][] {
                    new int[]{4, 0},
                    new int[]{0, 4},
                    new int[]{1, 3},
            };
            for (int[] pair : validIndexLengthPairs) {
                assertTrue(ByteBufUtil.isText(buffer, pair[0], pair[1], CharsetUtil.US_ASCII));
            }
            int[][] invalidIndexLengthPairs = new int[][]{
                    new int[]{4, 1},
                    new int[]{-1, 2},
                    new int[]{3, -1},
                    new int[]{3, -2},
                    new int[]{5, 0},
                    new int[]{1, 5},
            };
            for (int[] pair : invalidIndexLengthPairs) {
                try {
                    ByteBufUtil.isText(buffer, pair[0], pair[1], CharsetUtil.US_ASCII);
                    fail("Expected IndexOutOfBoundsException");
                } catch (IndexOutOfBoundsException e) {
                    // expected
                }
            }
        } finally {
            buffer.release();
        }
    }

    private static void assertIsText(byte[] bytes, boolean expected, Charset charset) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeBytes(bytes);
            assertEquals(expected, ByteBufUtil.isText(buffer, charset));
        } finally {
            buffer.release();
        }
    }
}
