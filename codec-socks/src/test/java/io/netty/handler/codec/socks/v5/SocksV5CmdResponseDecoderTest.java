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
package io.netty.handler.codec.socks.v5;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class SocksV5CmdResponseDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksV5CmdResponseDecoderTest.class);

    private static void testSocksCmdResponseDecoderWithDifferentParams(
            SocksV5CmdStatus cmdStatus, SocksV5AddressType addressType, String host, int port) {
        logger.debug("Testing cmdStatus: " + cmdStatus + " addressType: " + addressType);
        SocksV5Response msg = new SocksV5CmdResponse(cmdStatus, addressType, host, port);
        SocksV5CmdResponseDecoder decoder = new SocksV5CmdResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksV5CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (addressType == SocksV5AddressType.UNKNOWN) {
            assertTrue(embedder.readInbound() instanceof UnknownSocksV5Response);
        } else {
            msg = (SocksV5Response) embedder.readInbound();
            assertEquals(((SocksV5CmdResponse) msg).cmdStatus(), cmdStatus);
            if (host != null) {
                assertEquals(((SocksV5CmdResponse) msg).host(), host);
            }
            assertEquals(((SocksV5CmdResponse) msg).port(), port);
        }
        assertNull(embedder.readInbound());
    }

    /**
     * Verifies that sent socks messages are decoded correctly.
     */
    @Test
    public void testSocksCmdResponseDecoder() {
        for (SocksV5CmdStatus cmdStatus : SocksV5CmdStatus.values()) {
            for (SocksV5AddressType addressType : SocksV5AddressType.values()) {
                testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, addressType, null, 0);
            }
        }
    }

    /**
     * Verifies that invalid bound host will fail with IllegalArgumentException during encoding.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidAddress() {
        testSocksCmdResponseDecoderWithDifferentParams(SocksV5CmdStatus.SUCCESS, SocksV5AddressType.IPv4, "1", 80);
    }

    /**
     * Verifies that send socks messages are decoded correctly when bound host and port are set.
     */
    @Test
    public void testSocksCmdResponseDecoderIncludingHost() {
        for (SocksV5CmdStatus cmdStatus : SocksV5CmdStatus.values()) {
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, SocksV5AddressType.IPv4,
                    "127.0.0.1", 80);
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, SocksV5AddressType.DOMAIN,
                    "testDomain.com", 80);
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, SocksV5AddressType.IPv6,
                    "2001:db8:85a3:42:1000:8a2e:370:7334", 80);
            testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, SocksV5AddressType.IPv6,
                    "1111:111:11:1:0:0:0:1", 80);
        }
    }
}
