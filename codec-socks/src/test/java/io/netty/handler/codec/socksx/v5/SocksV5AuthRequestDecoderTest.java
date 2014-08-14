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
package io.netty.handler.codec.socksx.v5;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.*;

public class SocksV5AuthRequestDecoderTest {

    @Test
    public void testAuthRequestDecoder() {
        String username = "test";
        String password = "test";
        SocksV5AuthRequest msg = new SocksV5AuthRequest(username, password);
        SocksV5AuthRequestDecoder decoder = new SocksV5AuthRequestDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksV5CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksV5AuthRequest) embedder.readInbound();
        assertEquals(msg.username(), username);
        assertEquals(msg.username(), password);
        assertNull(embedder.readInbound());
    }
}
