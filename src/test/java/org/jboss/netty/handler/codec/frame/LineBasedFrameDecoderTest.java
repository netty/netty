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
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.junit.Assert.*;

public class LineBasedFrameDecoderTest {
    @Test
    public void testDecodeWithStrip() throws Exception {
        DecoderEmbedder<ChannelBuffer> embedder = new DecoderEmbedder<ChannelBuffer>(
                new LineBasedFrameDecoder(8192, true, false));

        assertTrue(embedder.offer(copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII)));
        assertTrue(embedder.finish());
        assertEquals("first", embedder.poll().toString(CharsetUtil.US_ASCII));
        assertEquals("second", embedder.poll().toString(CharsetUtil.US_ASCII));
        assertNull(embedder.poll());

    }

    @Test
    public void testDecodeWithoutStrip() throws Exception {
        DecoderEmbedder<ChannelBuffer> embedder = new DecoderEmbedder<ChannelBuffer>(
                new LineBasedFrameDecoder(8192, false, false));

        assertTrue(embedder.offer(copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII)));
        assertTrue(embedder.finish());
        assertEquals("first\r\n", embedder.poll().toString(CharsetUtil.US_ASCII));
        assertEquals("second\n", embedder.poll().toString(CharsetUtil.US_ASCII));
        assertNull(embedder.poll());

    }

    @Test
    public void testTooLongLine1() throws Exception {
        DecoderEmbedder<ChannelBuffer> embedder = new DecoderEmbedder<ChannelBuffer>(
                new LineBasedFrameDecoder(16, false, false));

        try {
            embedder.offer(copiedBuffer("12345678901234567890\r\nfirst\nsecond", CharsetUtil.US_ASCII));
            fail();
        } catch (CodecEmbedderException e) {
            assertThat(e.getCause(), is(instanceOf(TooLongFrameException.class)));
        }

        embedder.offer(wrappedBuffer(new byte[1])); // A workaround that triggers decode() once again.

        assertThat(embedder.size(), is(1));
        assertTrue(embedder.finish());

        assertThat(embedder.size(), is(1));
        assertThat(embedder.poll().toString(CharsetUtil.US_ASCII), is("first\n"));
    }

    @Test
    public void testTooLongLine2() throws Exception {
        DecoderEmbedder<ChannelBuffer> embedder = new DecoderEmbedder<ChannelBuffer>(
                new LineBasedFrameDecoder(16, false, false));

        assertFalse(embedder.offer(copiedBuffer("12345678901234567", CharsetUtil.US_ASCII)));
        try {
            embedder.offer(copiedBuffer("890\r\nfirst\r\n", CharsetUtil.US_ASCII));
            fail();
        } catch (CodecEmbedderException e) {
            assertThat(e.getCause(), is(instanceOf(TooLongFrameException.class)));
        }

        embedder.offer(wrappedBuffer(new byte[1])); // A workaround that triggers decode() once again.

        assertThat(embedder.size(), is(1));
        assertTrue(embedder.finish());

        assertThat(embedder.size(), is(1));
        assertEquals("first\r\n", embedder.poll().toString(CharsetUtil.US_ASCII));
    }

    @Test
    public void testTooLongLineWithFailFast() throws Exception {
        DecoderEmbedder<ChannelBuffer> embedder = new DecoderEmbedder<ChannelBuffer>(
                new LineBasedFrameDecoder(16, false, true));

        try {
            embedder.offer(copiedBuffer("12345678901234567", CharsetUtil.US_ASCII));
            fail();
        } catch (CodecEmbedderException e) {
            assertThat(e.getCause(), is(instanceOf(TooLongFrameException.class)));
        }

        assertThat(embedder.offer(copiedBuffer("890", CharsetUtil.US_ASCII)), is(false));
        assertThat(embedder.offer(copiedBuffer("123\r\nfirst\r\n", CharsetUtil.US_ASCII)), is(true));
        assertThat(embedder.size(), is(1));
        assertTrue(embedder.finish());

        assertThat(embedder.size(), is(1));
        assertEquals("first\r\n", embedder.poll().toString(CharsetUtil.US_ASCII));
    }
}
