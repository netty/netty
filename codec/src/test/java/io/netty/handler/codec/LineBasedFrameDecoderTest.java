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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import static io.netty.buffer.Unpooled.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class LineBasedFrameDecoderTest {
    @Test
    public void testDecodeWithStrip() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, true, false));

        ch.writeInbound(copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII));

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
    public void testDecodeWithoutStrip() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, false, false));

        ch.writeInbound(copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII));

        ByteBuf buf = ch.readInbound();
        assertEquals("first\r\n", buf.toString(CharsetUtil.US_ASCII));

        ByteBuf buf2 = ch.readInbound();
        assertEquals("second\n", buf2.toString(CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());
        ch.finish();
        ReferenceCountUtil.release(ch.readInbound());

        buf.release();
        buf2.release();
    }

    @Test
    public void testTooLongLine1() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(16, false, false));

        try {
            ch.writeInbound(copiedBuffer("12345678901234567890\r\nfirst\nsecond", CharsetUtil.US_ASCII));
            fail();
        } catch (Exception e) {
            assertThat(e, is(instanceOf(TooLongFrameException.class)));
        }

        ByteBuf buf = ch.readInbound();
        ByteBuf buf2 = copiedBuffer("first\n", CharsetUtil.US_ASCII);
        assertThat(buf, is(buf2));
        assertThat(ch.finish(), is(false));

        buf.release();
        buf2.release();
    }

    @Test
    public void testTooLongLine2() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(16, false, false));

        assertFalse(ch.writeInbound(copiedBuffer("12345678901234567", CharsetUtil.US_ASCII)));
        try {
            ch.writeInbound(copiedBuffer("890\r\nfirst\r\n", CharsetUtil.US_ASCII));
            fail();
        } catch (Exception e) {
            assertThat(e, is(instanceOf(TooLongFrameException.class)));
        }

        ByteBuf buf = ch.readInbound();
        ByteBuf buf2 = copiedBuffer("first\r\n", CharsetUtil.US_ASCII);
        assertThat(buf, is(buf2));
        assertThat(ch.finish(), is(false));

        buf.release();
        buf2.release();
    }

    @Test
    public void testTooLongLineWithFailFast() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(16, false, true));

        try {
            ch.writeInbound(copiedBuffer("12345678901234567", CharsetUtil.US_ASCII));
            fail();
        } catch (Exception e) {
            assertThat(e, is(instanceOf(TooLongFrameException.class)));
        }

        assertThat(ch.writeInbound(copiedBuffer("890", CharsetUtil.US_ASCII)), is(false));
        assertThat(ch.writeInbound(copiedBuffer("123\r\nfirst\r\n", CharsetUtil.US_ASCII)), is(true));

        ByteBuf buf = ch.readInbound();
        ByteBuf buf2 = copiedBuffer("first\r\n", CharsetUtil.US_ASCII);
        assertThat(buf, is(buf2));
        assertThat(ch.finish(), is(false));

        buf.release();
        buf2.release();
    }

    @Test
    public void testDecodeSplitsCorrectly() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, false, false));

        assertTrue(ch.writeInbound(copiedBuffer("line\r\n.\r\n", CharsetUtil.US_ASCII)));

        ByteBuf buf = ch.readInbound();
        assertEquals("line\r\n", buf.toString(CharsetUtil.US_ASCII));

        ByteBuf buf2 = ch.readInbound();
        assertEquals(".\r\n", buf2.toString(CharsetUtil.US_ASCII));
        assertFalse(ch.finishAndReleaseAll());

        buf.release();
        buf2.release();
    }

    @Test
    public void testFragmentedDecode() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, false, false));

        assertFalse(ch.writeInbound(copiedBuffer("huu", CharsetUtil.US_ASCII)));
        assertNull(ch.readInbound());

        assertFalse(ch.writeInbound(copiedBuffer("haa\r", CharsetUtil.US_ASCII)));
        assertNull(ch.readInbound());

        assertTrue(ch.writeInbound(copiedBuffer("\nhuuhaa\r\n", CharsetUtil.US_ASCII)));
        ByteBuf buf = ch.readInbound();
        assertEquals("huuhaa\r\n", buf.toString(CharsetUtil.US_ASCII));

        ByteBuf buf2 = ch.readInbound();
        assertEquals("huuhaa\r\n", buf2.toString(CharsetUtil.US_ASCII));
        assertFalse(ch.finishAndReleaseAll());

        buf.release();
        buf2.release();
    }

    @Test
    public void testEmptyLine() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, true, false));

        assertTrue(ch.writeInbound(copiedBuffer("\nabcna\r\n", CharsetUtil.US_ASCII)));

        ByteBuf buf = ch.readInbound();
        assertEquals("", buf.toString(CharsetUtil.US_ASCII));

        ByteBuf buf2 = ch.readInbound();
        assertEquals("abcna", buf2.toString(CharsetUtil.US_ASCII));

        assertFalse(ch.finishAndReleaseAll());

        buf.release();
        buf2.release();
    }

    @Test
    public void testNotFailFast() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(2, false, false));
        assertFalse(ch.writeInbound(wrappedBuffer(new byte[] { 0, 1, 2 })));
        assertFalse(ch.writeInbound(wrappedBuffer(new byte[]{ 3, 4 })));
        try {
            ch.writeInbound(wrappedBuffer(new byte[] { '\n' }));
            fail();
        } catch (TooLongFrameException expected) {
            // Expected once we received a full frame.
        }
        assertFalse(ch.writeInbound(wrappedBuffer(new byte[] { '5' })));
        assertTrue(ch.writeInbound(wrappedBuffer(new byte[] { '\n' })));

        ByteBuf expected = wrappedBuffer(new byte[] { '5', '\n' });
        ByteBuf buffer = ch.readInbound();
        assertEquals(expected, buffer);
        expected.release();
        buffer.release();

        assertFalse(ch.finish());
    }
}
