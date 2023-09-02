/*
 * Copyright 2013 The Netty Project
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
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

public class OctetCountingFrameDecoderTest {
    @Test
    public void testValidFrames() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes("5 HELLO".getBytes());
        buf.writeBytes("6 HELLO\n".getBytes());
        buf.writeBytes("11 HELLO WORLD".getBytes());
        EmbeddedChannel channel = new EmbeddedChannel(new OctetCountingFrameDecoder(64, 2, null));

        assertTrue(channel.writeInbound(buf));
        assertTrue(channel.finish());

        ByteBuf read = channel.readInbound();
        assertEquals("HELLO", read.toString(Charset.defaultCharset()));
        read.release();
        read = channel.readInbound();
        assertEquals("HELLO\n", read.toString(Charset.defaultCharset()));
        read.release();
        read = channel.readInbound();
        assertEquals("HELLO WORLD", read.toString(Charset.defaultCharset()));
        read.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testSecondaryDecoderFallback() {
        ByteToMessageDecoder secondaryDecoder = new LineBasedFrameDecoder(64, true, true);
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes("5 HELLO".getBytes());
        buf.writeBytes("HELLO\n".getBytes());
        buf.writeBytes("11 HELLO WORLD".getBytes());
        EmbeddedChannel channel = new EmbeddedChannel(new OctetCountingFrameDecoder(64, 2, secondaryDecoder));

        assertTrue(channel.writeInbound(buf));
        assertTrue(channel.finish());

        ByteBuf read = channel.readInbound();
        assertEquals("HELLO", read.toString(Charset.defaultCharset()));
        read.release();
        read = channel.readInbound();
        assertEquals("HELLO", read.toString(Charset.defaultCharset()));
        read.release();
        read = channel.readInbound();
        assertEquals("HELLO WORLD", read.toString(Charset.defaultCharset()));
        read.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testInvalidFrameStartingZero() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes("05 TEST".getBytes());
        EmbeddedChannel channel = new EmbeddedChannel(new OctetCountingFrameDecoder(64, 2, null));

        try {
            channel.writeInbound(buf);
            fail();
        } catch (CorruptedFrameException e) {
            // expected
        }
        buf.release();
    }

    @Test
    public void testInvalidFrameMissingSpace() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes("5TEST".getBytes());
        EmbeddedChannel channel = new EmbeddedChannel(new OctetCountingFrameDecoder(64, 2, null));

        try {
            channel.writeInbound(buf);
            fail();
        } catch (CorruptedFrameException e) {
            // expected
        }
        buf.release();
    }

    @Test
    public void testMaxLengthFieldExceed() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes("900 LONG LENGTH FIELD".getBytes());
        EmbeddedChannel channel = new EmbeddedChannel(new OctetCountingFrameDecoder(64, 2, null));

        try {
            channel.writeInbound(buf);
            fail();
        } catch (DecoderException e) {
            // expected
        }
        buf.release();
    }
}
