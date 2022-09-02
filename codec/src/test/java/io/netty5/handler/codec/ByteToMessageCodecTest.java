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

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteToMessageCodecTest {

    @Test
    public void testForwardPendingData() {
        ByteToMessageCodec<Integer> codec = new ByteToMessageCodec<Integer>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, Integer msg, Buffer out) throws Exception {
                out.writeInt(msg);
            }

            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
                if (in.readableBytes() >= 4) {
                    ctx.fireChannelRead(in.readInt());
                }
            }
        };

        Buffer buffer = preferredAllocator().allocate(5);
        buffer.writeInt(1);
        buffer.writeByte((byte) '0');

        EmbeddedChannel ch = new EmbeddedChannel(codec);
        assertTrue(ch.writeInbound(buffer));
        ch.pipeline().remove(codec);
        assertTrue(ch.finish());
        assertEquals(1, (Integer) ch.readInbound());

        try (Buffer buf = ch.readInbound();
             Buffer expected = preferredAllocator().copyOf(new byte[] { '0' })) {
            assertEquals(expected, buf);
        }
        assertNull(ch.readInbound());
        assertNull(ch.readOutbound());
    }
}
