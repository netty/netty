/*
 * Copyright 2013 The Netty Project
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
import org.junit.Assert;
import org.junit.Test;


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
            Assert.fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        Assert.assertTrue(channel.finish());

        ByteBuf b = channel.readInbound();
        Assert.assertEquals(5, b.readableBytes());
        Assert.assertEquals(1, b.readInt());
        Assert.assertEquals('a', b.readByte());
        b.release();

        Assert.assertNull(channel.readInbound());
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
            Assert.fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        Assert.assertTrue(channel.writeInbound(buf.readRetainedSlice(buf.readableBytes())));

        Assert.assertTrue(channel.finish());

        ByteBuf b = channel.readInbound();
        Assert.assertEquals(5, b.readableBytes());
        Assert.assertEquals(1, b.readInt());
        Assert.assertEquals('a', b.readByte());
        b.release();

        Assert.assertNull(channel.readInbound());
        channel.finish();

        buf.release();
    }

    @Test
    public void goodFrameAfterTooLongFrame() {
        ByteBuf goodBuf1 = Unpooled.buffer();
        goodBuf1.writeInt(16);
        for (int i = 0; i < 16; i++) {
            goodBuf1.writeByte(i);
        }

        ByteBuf longBuf = Unpooled.buffer();
        longBuf.writeInt(32);
        for (int i = 0; i < 32; i++) {
            longBuf.writeByte(i);
        }

        ByteBuf goodBuf2 = Unpooled.buffer();
        goodBuf2.writeInt(2);
        goodBuf2.writeByte('x');
        goodBuf2.writeByte('y');

        EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldBasedFrameDecoder(20, 0, 4));
        // write first record (16 bits, should succeed)
        channel.writeInbound(goodBuf1);
        ByteBuf in1 = channel.readInbound();
        assertByteBufsMatch(goodBuf1, in1);

        try {
            // write too-long frame in 3 chunks to hit all code paths in decode method
            channel.writeInbound(longBuf.retainedSlice(0, 20),
                                 longBuf.retainedSlice(20, 8),
                                 longBuf.retainedSlice(28, 8));
            Assert.fail();
        } catch (TooLongFrameException expected) {
            // do nothing
        }

        channel.writeInbound(goodBuf2);
        ByteBuf in2 = channel.readInbound();
        assertByteBufsMatch(goodBuf2, in2);

        Assert.assertFalse(channel.finishAndReleaseAll());
    }

    private static void assertByteBufsMatch(ByteBuf expected, ByteBuf actual) {
        for (int i = 0; i < actual.readableBytes(); i++) {
            Assert.assertEquals(expected.getByte(i), actual.getByte(i));
        }
    }
}
