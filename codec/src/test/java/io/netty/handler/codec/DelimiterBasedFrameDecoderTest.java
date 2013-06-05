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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class DelimiterBasedFrameDecoderTest {

    @Test
    public void testMultipleLinesStrippedDelimiters() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, true,
                Delimiters.lineDelimiter()));
        ch.writeInbound(Unpooled.copiedBuffer("TestLine\r\ng\r\n", Charset.defaultCharset()));
        assertEquals("TestLine", ((ByteBuf) ch.readInbound()).toString(Charset.defaultCharset()));
        assertEquals("g", ((ByteBuf) ch.readInbound()).toString(Charset.defaultCharset()));
        assertNull(ch.readInbound());
    }

    @Test
    public void testIncompleteLinesStrippedDelimiters() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, true,
                Delimiters.lineDelimiter()));
        ch.writeInbound(Unpooled.copiedBuffer("Test", Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.copiedBuffer("Line\r\ng\r\n", Charset.defaultCharset()));
        assertEquals("TestLine", ((ByteBuf) ch.readInbound()).toString(Charset.defaultCharset()));
        assertEquals("g", ((ByteBuf) ch.readInbound()).toString(Charset.defaultCharset()));
        assertNull(ch.readInbound());
    }

    @Test
    public void testMultipleLines() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, false,
                Delimiters.lineDelimiter()));
        ch.writeInbound(Unpooled.copiedBuffer("TestLine\r\ng\r\n", Charset.defaultCharset()));
        assertEquals("TestLine\r\n", ((ByteBuf) ch.readInbound()).toString(Charset.defaultCharset()));
        assertEquals("g\r\n", ((ByteBuf) ch.readInbound()).toString(Charset.defaultCharset()));
        assertNull(ch.readInbound());
    }

    @Test
    public void testIncompleteLines() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, false,
                Delimiters.lineDelimiter()));
        ch.writeInbound(Unpooled.copiedBuffer("Test", Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.copiedBuffer("Line\r\ng\r\n", Charset.defaultCharset()));
        assertEquals("TestLine\r\n", ((ByteBuf) ch.readInbound()).toString(Charset.defaultCharset()));
        assertEquals("g\r\n", ((ByteBuf) ch.readInbound()).toString(Charset.defaultCharset()));
        assertNull(ch.readInbound());
    }

    @Test
    public void testDecode() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new DelimiterBasedFrameDecoder(8192, true, Delimiters.lineDelimiter()));

        ch.writeInbound(Unpooled.copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII));
        assertEquals("first", ((ByteBuf) ch.readInbound()).toString(CharsetUtil.US_ASCII));
        assertEquals("second", ((ByteBuf) ch.readInbound()).toString(CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());
    }
}
