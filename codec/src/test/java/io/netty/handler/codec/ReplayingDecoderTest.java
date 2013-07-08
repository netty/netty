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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.List;

import org.junit.Test;

public class ReplayingDecoderTest {

    @Test
    public void testLineProtocol() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineDecoder());

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
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            ByteBuf msg = in.readBytes(in.bytesBefore((byte) '\n'));
            out.add(msg);
            in.skipBytes(1);
        }
    }

    @Test
    public void testReplacement() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new BloatedLineDecoder());

        // "AB" should be forwarded to LineDecoder by BloatedLineDecoder.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{'A', 'B'}));
        assertNull(ch.readInbound());

        // "C\n" should be appended to "AB" so that LineDecoder decodes it correctly.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{'C', '\n'}));
        assertEquals(Unpooled.wrappedBuffer(new byte[] { 'A', 'B', 'C' }), ch.readInbound());

        ch.finish();
        assertNull(ch.readInbound());
    }

    private static final class BloatedLineDecoder extends ChannelInboundHandlerAdapter {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.pipeline().replace(this, "less-bloated", new LineDecoder());
            ctx.pipeline().fireMessageReceived(msg);
        }
    }

    @Test
    public void testSingleDecode() throws Exception {
        LineDecoder decoder = new LineDecoder();
        decoder.setSingleDecode(true);
        EmbeddedChannel ch = new EmbeddedChannel(decoder);

        // "C\n" should be appended to "AB" so that LineDecoder decodes it correctly.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{'C', '\n' , 'B', '\n'}));
        assertEquals(Unpooled.wrappedBuffer(new byte[] {'C' }), ch.readInbound());
        assertNull("Must be null as it must only decode one frame", ch.readInbound());

        ch.read();
        ch.finish();
        assertEquals(Unpooled.wrappedBuffer(new byte[] {'B' }), ch.readInbound());
        assertNull(ch.readInbound());
    }
}
