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
package io.netty.handler.codec.socks.v4;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socks.v5.SocksV5AddressType;
import io.netty.handler.codec.socks.v5.SocksV5CmdStatus;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SocksV4CmdResponseDecoderTest {
    private static final Logger logger = LoggerFactory.getLogger(SocksV4CmdResponseDecoderTest.class);

    private static void testSocksCmdResponseDecoderWithDifferentParams(
            SocksV4CmdStatus cmdStatus, String host, int port) {
        logger.debug("Testing cmdStatus: " + cmdStatus);
        SocksV4Response msg = new SocksV4CmdResponse(cmdStatus, host, port);
        SocksV4CmdResponseDecoder decoder = new SocksV4CmdResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksV4CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);

        msg = (SocksV4Response) embedder.readInbound();
        assertEquals(((SocksV4CmdResponse) msg).cmdStatus(), cmdStatus);
        if (host != null) {
            assertEquals(((SocksV4CmdResponse) msg).host(), host);
        }
        assertEquals(((SocksV4CmdResponse) msg).port(), port);
        assertNull(embedder.readInbound());
    }

    /**
     * Verifies that sent socks messages are decoded correctly.
     */
    @Test
    public void testSocksCmdResponseDecoder() {
        for (SocksV4CmdStatus cmdStatus : SocksV4CmdStatus.values()) {
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, null, 0);
        }
    }
}
