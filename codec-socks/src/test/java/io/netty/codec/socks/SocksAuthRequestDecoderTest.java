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
package io.netty.codec.socks;

import io.netty.channel.embedded.EmbeddedByteChannel;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SocksAuthRequestDecoderTest {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SocksAuthRequestDecoderTest.class);
    @Test
    public void testAuthRequestDecoder() {
        String username = "test";
        String password = "test";
        SocksAuthRequest msg = new SocksAuthRequest(username, password);
        SocksAuthRequestDecoder decoder = new SocksAuthRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksAuthRequest) embedder.readInbound();
        assertTrue(msg.getUsername().equals(username));
        assertTrue(msg.getUsername().equals(password));
        assertNull(embedder.readInbound());
    }
}
