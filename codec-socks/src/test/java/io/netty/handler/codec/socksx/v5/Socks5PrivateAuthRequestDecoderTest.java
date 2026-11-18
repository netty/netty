/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Socks5PrivateAuthRequestDecoderTest {

    @Test
    public void testAuthRequestDecoder() {
        byte[] token = { 1, 2, 3, 4 };
        EmbeddedChannel embedder = new EmbeddedChannel(new Socks5PrivateAuthRequestDecoder());
        embedder.writeInbound(Unpooled.wrappedBuffer(new byte[] { 0x01, 0x04, 1, 2, 3, 4, 'G', 'E', 'T' }));

        Socks5PrivateAuthRequest msg = embedder.readInbound();
        assertTrue(msg.decoderResult().isSuccess());
        assertArrayEquals(token, msg.privateToken());

        ByteBuf remaining = embedder.readInbound();
        assertEquals("GET", remaining.toString(CharsetUtil.US_ASCII));
        remaining.release();
        assertNull(embedder.readInbound());
        assertFalse(embedder.finish());
    }

    @Test
    public void testDiscardsDataAfterFailure() {
        EmbeddedChannel embedder = new EmbeddedChannel(new Socks5PrivateAuthRequestDecoder());
        embedder.writeInbound(Unpooled.wrappedBuffer(new byte[] { 0x02, 0x03, 'a', 'b', 'c' }));

        Socks5PrivateAuthRequest msg = embedder.readInbound();
        assertTrue(msg.decoderResult().isFailure());

        // Nothing received after a failed sub-negotiation may be passed to the next handler.
        embedder.writeInbound(Unpooled.wrappedBuffer(new byte[] { 'G', 'E', 'T' }));
        Object next = embedder.readInbound();
        ReferenceCountUtil.release(next);
        assertNull(next);
        assertFalse(embedder.finish());
    }
}
