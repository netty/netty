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
package io.netty.handler.codec.frame;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.netty.buffer.Unpooled.*;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class LengthFieldPrependerTest {

    private ByteBuf msg;

    @BeforeEach
    public void setUp() throws Exception {
        msg = copiedBuffer("A", CharsetUtil.ISO_8859_1);
    }

    @Test
    public void testPrependLength() throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel(new LengthFieldPrepender(4));
        ch.writeOutbound(msg);
        ByteBuf buf = ch.readOutbound();
        assertEquals(4, buf.readableBytes());
        assertEquals(msg.readableBytes(), buf.readInt());
        buf.release();

        buf = ch.readOutbound();
        assertSame(buf, msg);
        buf.release();
    }

    @Test
    public void testPrependLengthIncludesLengthFieldLength() throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel(new LengthFieldPrepender(4, true));
        ch.writeOutbound(msg);
        ByteBuf buf = ch.readOutbound();
        assertEquals(4, buf.readableBytes());
        assertEquals(5, buf.readInt());
        buf.release();

        buf = ch.readOutbound();
        assertSame(buf, msg);
        buf.release();
    }

    @Test
    public void testPrependAdjustedLength() throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel(new LengthFieldPrepender(4, -1));
        ch.writeOutbound(msg);
        ByteBuf buf = ch.readOutbound();
        assertEquals(4, buf.readableBytes());
        assertEquals(msg.readableBytes() - 1, buf.readInt());
        buf.release();

        buf = ch.readOutbound();
        assertSame(buf, msg);
        buf.release();
    }

    @Test
    public void testAdjustedLengthLessThanZero() throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel(new LengthFieldPrepender(4, -2));
        try {
            ch.writeOutbound(msg);
            fail(EncoderException.class.getSimpleName() + " must be raised.");
        } catch (EncoderException e) {
            // Expected
        }
    }

    @Test
    public void testPrependLengthInLittleEndian() throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel(new LengthFieldPrepender(ByteOrder.LITTLE_ENDIAN, 4, 0, false));
        ch.writeOutbound(msg);
        ByteBuf buf = ch.readOutbound();
        assertEquals(4, buf.readableBytes());
        byte[] writtenBytes = new byte[buf.readableBytes()];
        buf.getBytes(0, writtenBytes);
        assertEquals(1, writtenBytes[0]);
        assertEquals(0, writtenBytes[1]);
        assertEquals(0, writtenBytes[2]);
        assertEquals(0, writtenBytes[3]);
        buf.release();

        buf = ch.readOutbound();
        assertSame(buf, msg);
        buf.release();
        assertFalse(ch.finish(), "The channel must have been completely read");
    }

}
