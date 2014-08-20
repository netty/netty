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
package io.netty.handler.codec.socksx.v4;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class Socks4CmdResponseDecoderTest {
    private static final Logger logger = LoggerFactory.getLogger(Socks4CmdResponseDecoderTest.class);

    private static void testSocksCmdResponseDecoderWithDifferentParams(
            Socks4CmdStatus cmdStatus, String host, int port) {
        logger.debug("Testing cmdStatus: " + cmdStatus);
        Socks4Response msg = new Socks4CmdResponse(cmdStatus, host, port);
        Socks4CmdResponseDecoder decoder = new Socks4CmdResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        Socks4CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);

        msg = embedder.readInbound();
        assertEquals(((Socks4CmdResponse) msg).cmdStatus(), cmdStatus);
        if (host != null) {
            assertEquals(((Socks4CmdResponse) msg).host(), host);
        }
        assertEquals(((Socks4CmdResponse) msg).port(), port);
        assertNull(embedder.readInbound());
    }

    /**
     * Verifies that sent socks messages are decoded correctly.
     */
    @Test
    public void testSocksCmdResponseDecoder() {
        for (Socks4CmdStatus cmdStatus : Socks4CmdStatus.values()) {
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, null, 0);
        }
    }
}
