/*
 * Copyright 2014 The Netty Project
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

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteToMessageCodecTest {

    @Test
    public void testSharable() {
        assertThrows(IllegalStateException.class, InvalidByteToMessageCodec::new);
    }

    @Test
    public void testSharable2() {
        assertThrows(IllegalStateException.class, InvalidByteToMessageCodec2::new);
    }

    @Test
    public void testForwardPendingData() {
        ByteToMessageCodec<Integer> codec = new ByteToMessageCodec<Integer>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) throws Exception {
                out.writeInt(msg);
            }

            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                if (in.readableBytes() >= 4) {
                    ctx.fireChannelRead(in.readInt());
                }
            }
        };

        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(1);
        buffer.writeByte('0');

        EmbeddedChannel ch = new EmbeddedChannel(codec);
        assertTrue(ch.writeInbound(buffer));
        ch.pipeline().remove(codec);
        assertTrue(ch.finish());
        assertEquals(1, (Integer) ch.readInbound());

        ByteBuf buf = ch.readInbound();
        assertEquals(Unpooled.wrappedBuffer(new byte[]{'0'}), buf);
        buf.release();
        assertNull(ch.readInbound());
        assertNull(ch.readOutbound());
    }

    @ChannelHandler.Sharable
    private static final class InvalidByteToMessageCodec extends ByteToMessageCodec<Integer> {
        InvalidByteToMessageCodec() {
            super(true);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) throws Exception { }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception { }
    }

    @ChannelHandler.Sharable
    private static final class InvalidByteToMessageCodec2 extends ByteToMessageCodec<Integer> {
        InvalidByteToMessageCodec2() {
            super(Integer.class, true);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) throws Exception { }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception { }
    }
}
