/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DelimiterBasedFrameDecoderTest {

    @Test
    public void testMultipleLinesStrippedDelimiters() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, true,
                Delimiters.lineDelimiter()));
        ch.writeInbound(Unpooled.copiedBuffer("TestLine\r\ng\r\n", Charset.defaultCharset()));

        ByteBuf buf = ch.readInbound();
        assertEquals("TestLine", buf.toString(Charset.defaultCharset()));

        ByteBuf buf2 = ch.readInbound();
        assertEquals("g", buf2.toString(Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.finish();

        buf.release();
        buf2.release();
    }

    @Test
    public void testIncompleteLinesStrippedDelimiters() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, true,
                Delimiters.lineDelimiter()));
        ch.writeInbound(Unpooled.copiedBuffer("Test", Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.copiedBuffer("Line\r\ng\r\n", Charset.defaultCharset()));

        ByteBuf buf = ch.readInbound();
        assertEquals("TestLine", buf.toString(Charset.defaultCharset()));

        ByteBuf buf2 = ch.readInbound();
        assertEquals("g", buf2.toString(Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.finish();

        buf.release();
        buf2.release();
    }

    @Test
    public void testMultipleLines() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, false,
                Delimiters.lineDelimiter()));
        ch.writeInbound(Unpooled.copiedBuffer("TestLine\r\ng\r\n", Charset.defaultCharset()));

        ByteBuf buf = ch.readInbound();
        assertEquals("TestLine\r\n", buf.toString(Charset.defaultCharset()));

        ByteBuf buf2 = ch.readInbound();
        assertEquals("g\r\n", buf2.toString(Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.finish();

        buf.release();
        buf2.release();
    }

    @Test
    public void testIncompleteLines() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, false,
                Delimiters.lineDelimiter()));
        ch.writeInbound(Unpooled.copiedBuffer("Test", Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.copiedBuffer("Line\r\ng\r\n", Charset.defaultCharset()));

        ByteBuf buf = ch.readInbound();
        assertEquals("TestLine\r\n", buf.toString(Charset.defaultCharset()));

        ByteBuf buf2 = ch.readInbound();
        assertEquals("g\r\n", buf2.toString(Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.finish();

        buf.release();
        buf2.release();
    }

    @Test
    public void testDecode() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new DelimiterBasedFrameDecoder(8192, true, Delimiters.lineDelimiter()));

        ch.writeInbound(Unpooled.copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII));

        ByteBuf buf = ch.readInbound();
        assertEquals("first", buf.toString(CharsetUtil.US_ASCII));

        ByteBuf buf2 = ch.readInbound();
        assertEquals("second", buf2.toString(CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());
        ch.finish();

        ReferenceCountUtil.release(ch.readInbound());

        buf.release();
        buf2.release();
    }

    @Test
    public void testMaxLengthFrameWithDelimiterSplitAcrossReads() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(4, ascii("##")));
        // The frame has maxFrameLength bytes, the first delimiter byte is in the same read.
        ch.writeInbound(ascii("ABCD#"));
        ch.writeInbound(ascii("#ok##"));
        assertFrame(ch, "ABCD");
        assertFrame(ch, "ok");
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDelimiterSplitAcrossReadsWhileDiscarding() {
        final EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(4, ascii("##")));
        // failFast: the exception is raised as soon as the frame is known to be too long.
        assertThrows(TooLongFrameException.class, () -> ch.writeInbound(ascii("AAAAAAAAAA#")));
        // The rest of the delimiter ends the discarded frame, the next frame must not be lost.
        ch.writeInbound(ascii("#ok##"));
        assertFrame(ch, "ok");
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDelimiterSplitAcrossReadsWhileDiscardingNoFailFast() {
        final EmbeddedChannel ch = new EmbeddedChannel(
                new DelimiterBasedFrameDecoder(4, true, false, ascii("##")));
        ch.writeInbound(ascii("AAAAAAAAAA#"));
        // The exception is raised once the delimiter that ends the too long frame was read.
        assertThrows(TooLongFrameException.class, () -> ch.writeInbound(ascii("#ok##")));
        ch.writeInbound(ascii("next##"));
        assertFrame(ch, "ok");
        assertFrame(ch, "next");
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testMaxFrameLengthEnforcedWithDelimiterSplitAcrossReads() {
        final EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(4, ascii("##")));
        // One byte more than maxFrameLength.
        assertThrows(TooLongFrameException.class, () -> ch.writeInbound(ascii("ABCDE#")));
        ch.writeInbound(ascii("#ok##"));
        assertFrame(ch, "ok");
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    private static ByteBuf ascii(String s) {
        return Unpooled.copiedBuffer(s, CharsetUtil.US_ASCII);
    }

    private static void assertFrame(EmbeddedChannel ch, String expected) {
        ByteBuf frame = ch.readInbound();
        assertEquals(expected, frame.toString(CharsetUtil.US_ASCII));
        frame.release();
    }
}
