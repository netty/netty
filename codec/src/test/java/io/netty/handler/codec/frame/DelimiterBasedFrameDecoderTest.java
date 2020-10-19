/*
 * Copyright 2012 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class DelimiterBasedFrameDecoderTest {

    @Test
    public void testFailSlowTooLongFrameRecovery() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new DelimiterBasedFrameDecoder(1, true, false, Delimiters.nulDelimiter()));

        for (int i = 0; i < 2; i ++) {
            ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 1, 2 }));
            try {
                assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 0 })));
                fail(DecoderException.class.getSimpleName() + " must be raised.");
            } catch (TooLongFrameException e) {
                // Expected
            }

            ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 'A', 0 }));
            ByteBuf buf = ch.readInbound();
            assertEquals("A", buf.toString(CharsetUtil.ISO_8859_1));

            buf.release();
        }
    }

    @Test
    public void testFailFastTooLongFrameRecovery() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new DelimiterBasedFrameDecoder(1, Delimiters.nulDelimiter()));

        for (int i = 0; i < 2; i ++) {
            try {
                assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 1, 2 })));
                fail(DecoderException.class.getSimpleName() + " must be raised.");
            } catch (TooLongFrameException e) {
                // Expected
            }

            ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 0, 'A', 0 }));
            ByteBuf buf = ch.readInbound();
            assertEquals("A", buf.toString(CharsetUtil.ISO_8859_1));

            buf.release();
        }
    }
}
