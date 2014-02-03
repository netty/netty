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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import static io.netty.buffer.Unpooled.*;
import static io.netty.util.ReferenceCountUtil.releaseLater;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class LineBasedFrameDecoderTest {
    @Test
    public void testDecodeWithStrip() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, true, false));

        ch.writeInbound(copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII));
        assertEquals("first", releaseLater((ByteBuf) ch.readInbound()).toString(CharsetUtil.US_ASCII));
        assertEquals("second", releaseLater((ByteBuf) ch.readInbound()).toString(CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());
        ch.finish();

        ReferenceCountUtil.release(ch.readInbound());
    }

    @Test
    public void testDecodeWithoutStrip() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8192, false, false));

        ch.writeInbound(copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII));
        assertEquals("first\r\n", releaseLater((ByteBuf) ch.readInbound()).toString(CharsetUtil.US_ASCII));
        assertEquals("second\n", releaseLater((ByteBuf) ch.readInbound()).toString(CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());
        ch.finish();
        ReferenceCountUtil.release(ch.readInbound());
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

        assertThat(releaseLater((ByteBuf) ch.readInbound()),
                is(releaseLater(copiedBuffer("first\n", CharsetUtil.US_ASCII))));
        assertThat(ch.finish(), is(false));
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

        assertThat(releaseLater((ByteBuf) ch.readInbound()),
                is(releaseLater(copiedBuffer("first\r\n", CharsetUtil.US_ASCII))));
        assertThat(ch.finish(), is(false));
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
        assertThat(releaseLater((ByteBuf) ch.readInbound()),
                is(releaseLater(copiedBuffer("first\r\n", CharsetUtil.US_ASCII))));
        assertThat(ch.finish(), is(false));
    }
}
