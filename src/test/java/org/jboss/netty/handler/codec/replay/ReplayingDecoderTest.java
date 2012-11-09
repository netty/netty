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
package org.jboss.netty.handler.codec.replay;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferIndexFinder;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

public class ReplayingDecoderTest {

    @Test
    public void testLineProtocol() {
        DecoderEmbedder<ChannelBuffer> e = new DecoderEmbedder<ChannelBuffer>(
                new LineDecoder());

        // Ordinary input
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'A' }));
        assertNull(e.poll());
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'B' }));
        assertNull(e.poll());
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'C' }));
        assertNull(e.poll());
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { '\n' }));
        assertEquals(ChannelBuffers.wrappedBuffer(new byte[] { 'A', 'B', 'C' }), e.poll());

        // Truncated input
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'A' }));
        assertNull(e.poll());
        e.finish();
        assertNull(e.poll());
    }

    @Test
    public void testAssertFailure() {
        final AtomicBoolean fail = new AtomicBoolean(true);
        DecoderEmbedder<ChannelBuffer> e = new DecoderEmbedder<ChannelBuffer>(
                new ReplayingDecoder<VoidEnum>() {

                    @Override
                    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, VoidEnum state) throws Exception {
                        if (fail.get()) {
                            for (;;) {
                                try {
                                buffer.readByte();
                                } catch (ReplayError er) {
                                    break;
                                }
                            }
                            fail.set(false);
                            throw new Exception();


                        }


                        buffer.readByte();
                        return new Object();
                    }

                });
        try {
            e.offer(ChannelBuffers.copiedBuffer("TESTME!!!!", CharsetUtil.US_ASCII));
            fail();
        } catch (CodecEmbedderException ex) {
            // expected
        }

        // this will trigger an assert error when asserts are enabled via the -ea
        // jvm switch. This is the default when run via the maven sunfire plugin
        e.offer(ChannelBuffers.copiedBuffer("TESTME!!!!", CharsetUtil.US_ASCII));
        e.finish();
    }
    private static final class LineDecoder extends ReplayingDecoder<VoidEnum> {

        LineDecoder() {
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel,
                ChannelBuffer buffer, VoidEnum state) throws Exception {
            ChannelBuffer msg = buffer.readBytes(
                    buffer.bytesBefore(ChannelBufferIndexFinder.LF));
            buffer.skipBytes(1);
            return msg;
        }
    }
}
