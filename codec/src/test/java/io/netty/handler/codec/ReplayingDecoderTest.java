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

import static org.junit.Assert.*;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferIndexFinder;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedStreamChannel;
import io.netty.util.VoidEnum;

import org.junit.Test;

public class ReplayingDecoderTest {

    @Test
    public void testLineProtocol() {
        EmbeddedStreamChannel ch = new EmbeddedStreamChannel(new LineDecoder());

        // Ordinary input
        ch.writeInbound(ChannelBuffers.wrappedBuffer(new byte[] { 'A' }));
        assertNull(ch.readInbound());
        ch.writeInbound(ChannelBuffers.wrappedBuffer(new byte[] { 'B' }));
        assertNull(ch.readInbound());
        ch.writeInbound(ChannelBuffers.wrappedBuffer(new byte[] { 'C' }));
        assertNull(ch.readInbound());
        ch.writeInbound(ChannelBuffers.wrappedBuffer(new byte[] { '\n' }));
        assertEquals(ChannelBuffers.wrappedBuffer(new byte[] { 'A', 'B', 'C' }), ch.readInbound());

        // Truncated input
        ch.writeInbound(ChannelBuffers.wrappedBuffer(new byte[] { 'A' }));
        assertNull(ch.readInbound());
        ch.close();
        assertNull(ch.readInbound());
    }

    private static final class LineDecoder extends ReplayingDecoder<ChannelBuffer, VoidEnum> {

        LineDecoder() {
        }

        @Override
        public ChannelBuffer decode(ChannelHandlerContext ctx, ChannelBuffer in) {
            ChannelBuffer msg = in.readBytes(in.bytesBefore(ChannelBufferIndexFinder.LF));
            in.skipBytes(1);
            return msg;
        }
    }
}
