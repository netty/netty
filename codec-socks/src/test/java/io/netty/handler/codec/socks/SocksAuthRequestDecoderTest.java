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
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.*;

public class SocksAuthRequestDecoderTest {

    private static final String username = "testUserName";
    private static final String password = "testPassword";

    @Test
    public void testAuthRequestDecoder() {
        SocksAuthRequest msg = new SocksAuthRequest(username, password);
        SocksAuthRequestDecoder decoder = new SocksAuthRequestDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = embedder.readInbound();
        assertEquals(username, msg.username());
        assertEquals(password, msg.password());
        assertNull(embedder.readInbound());
    }

    @Test
    public void testAuthRequestDecoderPartialSend() {
        EmbeddedChannel ch = new EmbeddedChannel(new SocksAuthRequestDecoder());
        ByteBuf byteBuf = Unpooled.buffer(16);

        // Send username and password size
        byteBuf.writeByte(SocksSubnegotiationVersion.AUTH_PASSWORD.byteValue());
        byteBuf.writeByte(username.length());
        byteBuf.writeBytes(username.getBytes());
        byteBuf.writeByte(password.length());
        ch.writeInbound(byteBuf);

        // Check that channel is empty
        assertNull(ch.readInbound());

        // Send password
        ByteBuf byteBuf2 = Unpooled.buffer();
        byteBuf2.writeBytes(password.getBytes());
        ch.writeInbound(byteBuf2);

        // Read message from channel
        SocksAuthRequest msg = ch.readInbound();

        // Check message
        assertEquals(username, msg.username());
        assertEquals(password, msg.password());

        assertFalse(ch.finishAndReleaseAll());
    }
}
