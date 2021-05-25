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
package io.netty.handler.codec.socksx.v5;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Socks5PasswordAuthRequestDecoderTest {

    @Test
    public void testAuthRequestDecoder() {
        String username = "testUsername";
        String password = "testPassword";
        Socks5PasswordAuthRequest msg = new DefaultSocks5PasswordAuthRequest(username, password);
        EmbeddedChannel embedder = new EmbeddedChannel(new Socks5PasswordAuthRequestDecoder());
        Socks5CommonTestUtils.writeFromClientToServer(embedder, msg);
        msg = embedder.readInbound();
        assertEquals(username, msg.username());
        assertEquals(password, msg.password());
        assertNull(embedder.readInbound());
    }
}
