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
package org.jboss.netty.handler.codec.socks;

import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.junit.Test;

import static org.junit.Assert.*;

public class SocksAuthRequestDecoderTest {
    @Test
    public void testAuthRequestDecoder() throws Exception{
        String username = "test";
        String password = "test";
        SocksAuthRequest msg = new SocksAuthRequest(username, password);
        SocksAuthRequestDecoder decoder = new SocksAuthRequestDecoder();
        DecoderEmbedder<SocksAuthRequest> embedder = new DecoderEmbedder<SocksAuthRequest>(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = embedder.poll();
        assertEquals(msg.getUsername(), username);
        assertEquals(msg.getUsername(), password);
        assertNull(embedder.poll());
    }
}
