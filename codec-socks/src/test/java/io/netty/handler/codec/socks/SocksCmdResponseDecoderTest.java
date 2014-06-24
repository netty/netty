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
package io.netty.handler.codec.socks;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import static io.netty.handler.codec.socks.SocksCmdStatus.*;
import static org.junit.Assert.*;

public class SocksCmdResponseDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksCmdResponseDecoderTest.class);
    private static final SocksCmdStatus[] CMD_STATUSES_SOCKS4 = {
            SUCCESS4, FAILURE4, FAILURE4_IDENTD_CONFIRM, FAILURE4_IDENTD_NOT_RUN
    };
    private static final SocksCmdStatus[] CMD_STATUSES_SOCKS5 = {
            SUCCESS, FAILURE, FORBIDDEN, NETWORK_UNREACHABLE, HOST_UNREACHABLE, REFUSED, TTL_EXPIRED,
            COMMAND_NOT_SUPPORTED, ADDRESS_NOT_SUPPORTED, UNASSIGNED
    };

    private static void testSocks5CmdResponseDecoderWithDifferentParams(
            SocksCmdStatus cmdStatus, SocksAddressType addressType, String host, int port) {
        logger.debug("Testing cmdStatus: " + cmdStatus + " addressType: " + addressType);
        SocksResponse msg = new SocksCmdResponse(cmdStatus, addressType, host, port);
        SocksCmdResponseDecoder decoder = new SocksCmdResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (addressType == SocksAddressType.UNKNOWN) {
            assertTrue(embedder.readInbound() instanceof UnknownSocksResponse);
        } else {
            msg = (SocksResponse) embedder.readInbound();
            assertEquals(((SocksCmdResponse) msg).cmdStatus(), cmdStatus);
            if (host != null) {
                assertEquals(((SocksCmdResponse) msg).host(), host);
            }
            assertEquals(((SocksCmdResponse) msg).port(), port);
        }
        assertNull(embedder.readInbound());
    }

    private static void testSocks4CmdResponseDecoderWithDifferentParams(
            SocksCmdStatus cmdStatus) {
        logger.debug("Testing cmdStatus: " + cmdStatus);
        SocksResponse msg = new SocksCmdResponse(cmdStatus);
        SocksCmdResponseDecoder decoder = new SocksCmdResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksResponse) embedder.readInbound();
        assertEquals(((SocksCmdResponse) msg).cmdStatus(), cmdStatus);
        assertNull(embedder.readInbound());
    }

    /**
     * Verifies that sent socks messages are decoded correctly.
     */
    @Test
    public void testSocksCmdResponseDecoder() {
        for (SocksCmdStatus cmdStatus : CMD_STATUSES_SOCKS5) {
            for (SocksAddressType addressType : SocksAddressType.values()) {
                testSocks5CmdResponseDecoderWithDifferentParams(cmdStatus, addressType, null, 0);
            }
        }
    }

    /**
     * Verifies that invalid bound host will fail with IllegalArgumentException during encoding.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidAddress() {
        testSocks5CmdResponseDecoderWithDifferentParams(SUCCESS, SocksAddressType.IPv4, "1", 80);
    }

    /**
     * Verifies that send socks messages are decoded correctly when bound host and port are set.
     */
    @Test
    public void testSocksCmdResponseDecoderIncludingHost() {
        for (SocksCmdStatus cmdStatus : CMD_STATUSES_SOCKS5) {
            testSocks5CmdResponseDecoderWithDifferentParams(cmdStatus, SocksAddressType.IPv4,
                    "127.0.0.1", 80);
            testSocks5CmdResponseDecoderWithDifferentParams(cmdStatus, SocksAddressType.DOMAIN,
                    "testDomain.com", 80);
            testSocks5CmdResponseDecoderWithDifferentParams(cmdStatus, SocksAddressType.IPv6,
                    "2001:db8:85a3:42:1000:8a2e:370:7334", 80);
            testSocks5CmdResponseDecoderWithDifferentParams(cmdStatus, SocksAddressType.IPv6,
                    "1111:111:11:1:0:0:0:1", 80);
        }

        for (SocksCmdStatus cmdStatus : CMD_STATUSES_SOCKS4) {
            testSocks4CmdResponseDecoderWithDifferentParams(cmdStatus);
        }
    }
}
