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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.Charset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * A multi-byte, non-line delimiter that straddles the resumed-scan boundary must still be
     * found. This guards the scan-offset optimization added to fix the quadratic rescanning of
     * {@link DelimiterBasedFrameDecoder#decode(ChannelHandlerContext, ByteBuf)}: the decoder must
     * not skip past a match whose bytes span the previously-scanned region and newly-arrived data.
     */
    @Test
    public void testMultiByteDelimiterFoundAcrossResumedScanBoundary() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(
                8192, true, Unpooled.copiedBuffer("###", CharsetUtil.US_ASCII)));

        // "AB#" - contains only a one-byte prefix of the delimiter; nothing to emit yet.
        ch.writeInbound(Unpooled.copiedBuffer("AB#", CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());

        // "#" - now "AB##"; still only a two-byte prefix of the delimiter.
        ch.writeInbound(Unpooled.copiedBuffer("#", CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());

        // "#" - now "AB###"; the delimiter is completed and spans the resumed-scan boundary.
        ch.writeInbound(Unpooled.copiedBuffer("#", CharsetUtil.US_ASCII));
        ByteBuf buf = ch.readInbound();
        assertEquals("AB", buf.toString(CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());
        buf.release();

        // A second frame after the first still decodes correctly.
        ch.writeInbound(Unpooled.copiedBuffer("CD###", CharsetUtil.US_ASCII));
        ByteBuf buf2 = ch.readInbound();
        assertEquals("CD", buf2.toString(CharsetUtil.US_ASCII));
        buf2.release();

        assertFalse(ch.finish());
    }

    /**
     * Reproduces the DoS scenario: a peer streams delimiter-free bytes one at a time. Before the
     * fix, {@link DelimiterBasedFrameDecoder#decode(ChannelHandlerContext, ByteBuf)} rescanned the
     * whole accumulated buffer from the reader index on every call (no persisted scan-progress
     * offset), making total work quadratic in the number of bytes dripped. With the fix, work is
     * linear, so growing the input 8x should grow the runtime roughly 8x, not roughly 64x.
     */
    @Test
    @Timeout(60)
    public void testDripFeedWithoutDelimiterDoesNotScaleQuadratically() {
        // Warm up the JIT so timing isn't dominated by interpretation/compilation.
        dripFeedNulDelimited(5000);

        long smallElapsedNanos = dripFeedNulDelimited(25000);
        long largeElapsedNanos = dripFeedNulDelimited(200000);

        double ratio = (double) largeElapsedNanos / Math.max(1, smallElapsedNanos);
        // Linear scaling predicts ~8x for 8x more input; quadratic scaling predicts ~64x.
        // Use a generous threshold so the test isn't flaky, while still catching a regression.
        assertTrue(ratio < 24,
                "runtime scaled by " + ratio + "x for 8x the input; expected roughly linear growth "
                        + "(small=" + smallElapsedNanos + "ns, large=" + largeElapsedNanos + "ns)");
    }

    /**
     * With more than one delimiter, a single large buffer made of many tiny frames must not cause every frame to
     * rescan the rest of the buffer for the delimiters that did not match first.
     */
    @Test
    @Timeout(60)
    public void testManyFramesInOneBufferWithMultipleDelimitersDoesNotScaleQuadratically() {
        // Warm up the JIT so timing isn't dominated by interpretation/compilation.
        decodeAllEmptyFrames(5000);

        long smallElapsedNanos = decodeAllEmptyFrames(20000);
        long largeElapsedNanos = decodeAllEmptyFrames(160000);

        double ratio = (double) largeElapsedNanos / Math.max(1, smallElapsedNanos);
        // Linear scaling predicts ~8x for 8x more input; quadratic scaling predicts ~64x.
        assertTrue(ratio < 24,
                "runtime scaled by " + ratio + "x for 8x the input; expected roughly linear growth "
                        + "(small=" + smallElapsedNanos + "ns, large=" + largeElapsedNanos + "ns)");
    }

    private static long decodeAllEmptyFrames(int byteCount) {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(
                Integer.MAX_VALUE, Unpooled.copiedBuffer("a", CharsetUtil.US_ASCII),
                Unpooled.copiedBuffer("b", CharsetUtil.US_ASCII)));
        byte[] bytes = new byte[byteCount];
        Arrays.fill(bytes, (byte) 'a');
        try {
            long start = System.nanoTime();
            ch.writeInbound(Unpooled.wrappedBuffer(bytes));
            long elapsed = System.nanoTime() - start;
            assertEquals(byteCount, ch.inboundMessages().size());
            return elapsed;
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    /**
     * The window based search used for multiple delimiters must pick the same delimiter as a full search, wherever
     * the delimiters are placed relative to the window boundaries.
     */
    @Test
    public void testMultipleDelimitersChooseShortestFrameAcrossWindowBoundaries() {
        for (int pos = 0; pos < 600; pos++) {
            String prefix = repeat('z', pos);
            // The longer delimiter comes first, but the shorter one is found earlier.
            assertSingleFrame(prefix, prefix + "x" + "abc" + "zzz");
            // The longer delimiter is found earlier.
            assertSingleFrame(prefix, prefix + "abc" + "zzz" + "x");
            // The longer delimiter starts before "x" but only partially precedes it, so "x" wins.
            assertSingleFrame(prefix + "ab", prefix + "ab" + "x");
            // Both delimiters present, the one starting at the same offset as another match is the first one.
            assertSingleFrame(prefix, prefix + "abc");
        }
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static void assertSingleFrame(String expectedFrame, String input) {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(
                Integer.MAX_VALUE, Unpooled.copiedBuffer("abc", CharsetUtil.US_ASCII),
                Unpooled.copiedBuffer("x", CharsetUtil.US_ASCII)));
        try {
            ch.writeInbound(Unpooled.copiedBuffer(input, CharsetUtil.US_ASCII));
            ByteBuf frame = ch.readInbound();
            assertEquals(expectedFrame, frame.toString(CharsetUtil.US_ASCII), "input: " + input);
            frame.release();
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    private static long dripFeedNulDelimited(int byteCount) {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(
                Integer.MAX_VALUE, Delimiters.nulDelimiter()));
        ByteBuf single = Unpooled.buffer(1);
        try {
            long start = System.nanoTime();
            for (int i = 0; i < byteCount; i++) {
                single.setIndex(0, 0);
                single.writeByte('a');
                ch.writeInbound(single.retainedDuplicate());
                ReferenceCountUtil.release(ch.readInbound());
            }
            return System.nanoTime() - start;
        } finally {
            single.release();
            ch.finishAndReleaseAll();
        }
    }
}
