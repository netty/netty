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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LengthFieldBasedFrameDecoderTest {

    @Test
    public void testDiscardTooLongFrame1() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(32);
        for (int i = 0; i < 32; i++) {
            buf.writeByte(i);
        }
        buf.writeInt(1);
        buf.writeByte('a');
        EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldBasedFrameDecoder(16, 0, 4));
        try {
            channel.writeInbound(buf);
            fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        assertTrue(channel.finish());

        ByteBuf b = channel.readInbound();
        assertEquals(5, b.readableBytes());
        assertEquals(1, b.readInt());
        assertEquals('a', b.readByte());
        b.release();

        assertNull(channel.readInbound());
        channel.finish();
    }

    @Test
    public void testDiscardTooLongFrame2() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(32);
        for (int i = 0; i < 32; i++) {
            buf.writeByte(i);
        }
        buf.writeInt(1);
        buf.writeByte('a');
        EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldBasedFrameDecoder(16, 0, 4));
        try {
            channel.writeInbound(buf.readRetainedSlice(14));
            fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        assertTrue(channel.writeInbound(buf.readRetainedSlice(buf.readableBytes())));

        assertTrue(channel.finish());

        ByteBuf b = channel.readInbound();
        assertEquals(5, b.readableBytes());
        assertEquals(1, b.readInt());
        assertEquals('a', b.readByte());
        b.release();

        assertNull(channel.readInbound());
        channel.finish();

        buf.release();
    }

    @Test
    public void testDecodeNextFrameAfterFrameLengthLessThanInitialBytesToStrip() {
        EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldBasedFrameDecoder(16, 0, 4, 0, 6));

        // The adjusted frame length (4) is less than initialBytesToStrip (6).
        try {
            channel.writeInbound(Unpooled.buffer().writeInt(0));
            fail();
        } catch (CorruptedFrameException e) {
            // expected
        }

        // The next frame must be decoded again.
        ByteBuf buf = Unpooled.buffer().writeInt(4).writeShort(1).writeShort(2);
        assertTrue(channel.writeInbound(buf));
        ByteBuf b = channel.readInbound();
        assertEquals(2, b.readableBytes());
        assertEquals(2, b.readShort());
        b.release();

        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    public void testDecodeNextFrameAfterFrameLengthLessThanInitialBytesToStripInSeparateReads() {
        EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldBasedFrameDecoder(16, 0, 4, 0, 10));

        // The length field arrives first, and the rest of the malformed frame (adjusted length 8 is less than
        // initialBytesToStrip 10) arrives in a later read.
        assertFalse(channel.writeInbound(Unpooled.buffer().writeInt(4)));
        try {
            channel.writeInbound(Unpooled.buffer().writeInt(0));
            fail();
        } catch (CorruptedFrameException e) {
            // expected
        }

        ByteBuf buf = Unpooled.buffer().writeInt(8).writeInt(1).writeShort(2).writeShort(3);
        assertTrue(channel.writeInbound(buf));
        ByteBuf b = channel.readInbound();
        assertEquals(2, b.readableBytes());
        assertEquals(3, b.readShort());
        b.release();

        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }
}
