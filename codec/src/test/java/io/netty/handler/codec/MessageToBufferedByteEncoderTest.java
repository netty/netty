/*
 * Copyright 2016 The Netty Project
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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.*;

public class MessageToBufferedByteEncoderTest {

    @Test
    public void testEncode() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageToBufferedByteEncoder<Integer>() {
            ByteBuf buffer;

            @Override
            protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) throws Exception {
                if (buffer == null) {
                    buffer = out;
                } else {
                    assertSame(buffer, out);
                }
                out.writeInt(msg);
            }
        });
        ChannelFuture future1 = channel.write(1);
        ChannelFuture future2 = channel.write(2);
        assertFalse(future1.isDone());
        assertFalse(future2.isDone());
        channel.flush();
        assertTrue(future1.isSuccess());
        assertTrue(future2.isSuccess());
        assertTrue(channel.finish());
        ByteBuf buffer = channel.readOutbound();
        try {
            assertEquals(1, buffer.readInt());
            assertEquals(2, buffer.readInt());
            assertFalse(buffer.isReadable());
            assertNull(channel.readOutbound());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testEncodeThrows() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageToBufferedByteEncoder<Integer>() {

            @Override
            protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) throws Exception {
                if (out.readableBytes() > 0) {
                    // This should have MessageToBufferedByteEncoder produce an exception.
                    out.readerIndex(out.writerIndex());
                } else {
                    out.writeInt(msg);
                }
            }
        });
        ChannelFuture future1 = channel.write(1);
        ChannelFuture future2 = channel.write(2);
        assertFalse(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future2.cause().getCause() instanceof IllegalStateException);
        channel.flush();
        assertTrue(future1.isSuccess());
        assertTrue(channel.finish());
        ByteBuf buffer = channel.readOutbound();
        assertEquals(1, buffer.readInt());
        assertFalse(buffer.isReadable());
        assertNull(channel.readOutbound());
    }
}
