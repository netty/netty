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
package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DelimiterBasedFrameDecoderTest {

    @Test
    public void testMultipleLinesStrippedDelimiters() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, true,
                Delimiters.lineDelimiter()));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "TestLine\r\ng\r\n", Charset.defaultCharset()));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("TestLine", buf.toString(Charset.defaultCharset()));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("g", buf2.toString(Charset.defaultCharset()));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testIncompleteLinesStrippedDelimiters() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, true,
                Delimiters.lineDelimiter()));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "Test", Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "Line\r\ng\r\n", Charset.defaultCharset()));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("TestLine", buf.toString(Charset.defaultCharset()));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("g", buf2.toString(Charset.defaultCharset()));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testMultipleLines() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, false,
                Delimiters.lineDelimiter()));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "TestLine\r\ng\r\n", Charset.defaultCharset()));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("TestLine\r\n", buf.toString(Charset.defaultCharset()));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("g\r\n", buf2.toString(Charset.defaultCharset()));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testIncompleteLines() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, false,
                Delimiters.lineDelimiter()));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "Test", Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "Line\r\ng\r\n", Charset.defaultCharset()));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("TestLine\r\n", buf.toString(Charset.defaultCharset()));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("g\r\n", buf2.toString(Charset.defaultCharset()));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testDecode() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new DelimiterBasedFrameDecoder(8192, true, Delimiters.lineDelimiter()));

        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "first\r\nsecond\nthird", CharsetUtil.US_ASCII));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("first", buf.toString(CharsetUtil.US_ASCII));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("second", buf2.toString(CharsetUtil.US_ASCII));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    private static Buffer copiedBuffer(BufferAllocator allocator, String str, Charset charset) {
        return allocator.copyOf(str.getBytes(charset));
    }
}
