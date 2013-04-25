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
import io.netty.buffer.ByteBufIndexFinder;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.embedded.EmbeddedByteChannel;
import org.junit.Test;

import static org.junit.Assert.*;

public class ReplayingDecoderTest {

    @Test
    public void testLineProtocol() {
        EmbeddedByteChannel ch = new EmbeddedByteChannel(new LineDecoder());

        // Ordinary input
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 'A' }));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 'B' }));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 'C' }));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { '\n' }));
        assertEquals(Unpooled.wrappedBuffer(new byte[] { 'A', 'B', 'C' }), ch.readInbound());

        // Truncated input
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 'A' }));
        assertNull(ch.readInbound());

        ch.finish();
        assertNull(ch.readInbound());
    }

    private static final class LineDecoder extends ReplayingDecoder<Void> {

        LineDecoder() {
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, MessageBuf<Object> out) {
            ByteBuf msg = in.readBytes(in.bytesBefore(ByteBufIndexFinder.LF));
            in.skipBytes(1);
            out.add(msg);
        }
    }

    @Test
    public void testReplacement() throws Exception {
        EmbeddedByteChannel ch = new EmbeddedByteChannel(new BloatedLineDecoder());

        // "AB" should be forwarded to LineDecoder by BloatedLineDecoder.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{'A', 'B'}));
        assertNull(ch.readInbound());

        // "C\n" should be appended to "AB" so that LineDecoder decodes it correctly.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{'C', '\n'}));
        assertEquals(Unpooled.wrappedBuffer(new byte[] { 'A', 'B', 'C' }), ch.readInbound());

        ch.finish();
        assertNull(ch.readInbound());
    }

    private static final class BloatedLineDecoder extends ChannelInboundByteHandlerAdapter {
        @Override
        protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            ctx.pipeline().replace(this, "less-bloated", new LineDecoder());
        }
    }

    @Test
    public void testSingleDecode() throws Exception {
        LineDecoder decoder = new LineDecoder();
        decoder.setSingleDecode(true);
        EmbeddedByteChannel ch = new EmbeddedByteChannel(decoder);

        // "C\n" should be appended to "AB" so that LineDecoder decodes it correctly.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{'C', '\n' , 'B', '\n'}));
        assertEquals(Unpooled.wrappedBuffer(new byte[] {'C' }), ch.readInbound());
        assertNull("Must be null as it must only decode one frame", ch.readInbound());

        ch.finish();
        assertEquals(Unpooled.wrappedBuffer(new byte[] {'B' }), ch.readInbound());
        assertNull(ch.readInbound());
    }
}
