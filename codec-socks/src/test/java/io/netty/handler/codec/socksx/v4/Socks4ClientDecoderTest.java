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
package io.netty.handler.codec.socksx.v4;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class Socks4ClientDecoderTest {
    private static final Logger logger = LoggerFactory.getLogger(Socks4ClientDecoderTest.class);

    private static void test(Socks4CommandStatus cmdStatus, String dstAddr, int dstPort) {
        logger.debug("Testing cmdStatus: " + cmdStatus);
        Socks4CommandResponse msg = new DefaultSocks4CommandResponse(cmdStatus, dstAddr, dstPort);
        EmbeddedChannel embedder = new EmbeddedChannel(new Socks4ClientDecoder());
        Socks4CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);

        msg = embedder.readInbound();
        assertEquals(msg.status(), cmdStatus);
        if (dstAddr != null) {
            assertEquals(msg.dstAddr(), dstAddr);
        }
        assertEquals(msg.dstPort(), dstPort);
        assertNull(embedder.readInbound());
    }

    /**
     * Verifies that sent socks messages are decoded correctly.
     */
    @Test
    public void testSocksCmdResponseDecoder() {
        test(Socks4CommandStatus.IDENTD_AUTH_FAILURE, null, 0);
        test(Socks4CommandStatus.IDENTD_UNREACHABLE, null, 0);
        test(Socks4CommandStatus.REJECTED_OR_FAILED, null, 0);
        test(Socks4CommandStatus.SUCCESS, null, 0);
    }
}
