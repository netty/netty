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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteToMessageCodecTest {

    @Test
    public void testSharable() {
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                new InvalidByteToMessageCodec();
            }
        });
    }

    @Test
    public void testSharable2() {
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                new InvalidByteToMessageCodec2();
            }
        });
    }

    @Test
    public void testForwardPendingData() {
        ByteToMessageCodec<Integer> codec = new ByteToMessageCodec<Integer>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) {
                out.writeInt(msg);
            }

            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                if (in.readableBytes() >= 4) {
                    out.add(in.readInt());
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
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception { }
    }

    @ChannelHandler.Sharable
    private static final class InvalidByteToMessageCodec2 extends ByteToMessageCodec<Integer> {
        InvalidByteToMessageCodec2() {
            super(Integer.class, true);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) throws Exception { }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception { }
    }
}
