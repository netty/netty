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
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LengthFieldBasedFrameDecoderTest {
    @Test
    public void testFailSlowTooLongFrameRecovery() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(5, 0, 4, 0, 4, false));

        for (int i = 0; i < 2; i ++) {
            assertFalse(ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 0, 0, 0, 2 })));
            try {
                assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 0, 0 })));
                fail(DecoderException.class.getSimpleName() + " must be raised.");
            } catch (TooLongFrameException e) {
                // Expected
            }

            ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 0, 0, 0, 1, 'A' }));
            ByteBuf buf = ch.readInbound();
            assertEquals("A", buf.toString(CharsetUtil.ISO_8859_1));
            buf.release();
        }
    }

    @Test
    public void testFailFastTooLongFrameRecovery() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(5, 0, 4, 0, 4));

        for (int i = 0; i < 2; i ++) {
            try {
                assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 0, 0, 0, 2 })));
                fail(DecoderException.class.getSimpleName() + " must be raised.");
            } catch (TooLongFrameException e) {
                // Expected
            }

            ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 0, 0, 0, 0, 0, 1, 'A' }));
            ByteBuf buf = ch.readInbound();
            assertEquals("A", buf.toString(CharsetUtil.ISO_8859_1));
            buf.release();
        }
    }
}
