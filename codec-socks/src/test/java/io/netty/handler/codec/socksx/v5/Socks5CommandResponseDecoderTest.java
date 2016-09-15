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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Socks5CommandResponseDecoderTest {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(Socks5CommandResponseDecoderTest.class);

    private static final Socks5CommandStatus[] STATUSES = {
            Socks5CommandStatus.ADDRESS_UNSUPPORTED,
            Socks5CommandStatus.COMMAND_UNSUPPORTED,
            Socks5CommandStatus.CONNECTION_REFUSED,
            Socks5CommandStatus.FAILURE,
            Socks5CommandStatus.FORBIDDEN,
            Socks5CommandStatus.HOST_UNREACHABLE,
            Socks5CommandStatus.NETWORK_UNREACHABLE,
            Socks5CommandStatus.SUCCESS,
            Socks5CommandStatus.TTL_EXPIRED
    };

    private static void test(
            Socks5CommandStatus status, Socks5AddressType bndAddrType, String bndAddr, int bndPort) {
        logger.debug("Testing status: " + status + " bndAddrType: " + bndAddrType);
        Socks5CommandResponse msg =
                new DefaultSocks5CommandResponse(status, bndAddrType, bndAddr, bndPort);
        EmbeddedChannel embedder = new EmbeddedChannel(new Socks5CommandResponseDecoder());
        Socks5CommonTestUtils.writeFromServerToClient(embedder, msg);
        msg = embedder.readInbound();
        assertEquals(msg.status(), status);
        if (bndAddr != null) {
            assertEquals(msg.bndAddr(), bndAddr);
        }
        assertEquals(msg.bndPort(), bndPort);
        assertNull(embedder.readInbound());
    }

    /**
     * Verifies that sent socks messages are decoded correctly.
     */
    @Test
    public void testSocksCmdResponseDecoder() {
        for (Socks5CommandStatus cmdStatus: STATUSES) {
            for (Socks5AddressType addressType : Arrays.asList(Socks5AddressType.DOMAIN,
                                                               Socks5AddressType.IPv4,
                                                               Socks5AddressType.IPv6)) {
                test(cmdStatus, addressType, null, 0);
            }
        }
    }

    /**
     * Verifies that invalid bound host will fail with IllegalArgumentException during encoding.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidAddress() {
        test(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "1", 80);
    }

    /**
     * Verifies that send socks messages are decoded correctly when bound host and port are set.
     */
    @Test
    public void testSocksCmdResponseDecoderIncludingHost() {
        for (Socks5CommandStatus cmdStatus : STATUSES) {
            test(cmdStatus, Socks5AddressType.IPv4,
                 "127.0.0.1", 80);
            test(cmdStatus, Socks5AddressType.DOMAIN,
                 "testDomain.com", 80);
            test(cmdStatus, Socks5AddressType.IPv6,
                 "2001:db8:85a3:42:1000:8a2e:370:7334", 80);
            test(cmdStatus, Socks5AddressType.IPv6,
                 "1111:111:11:1::1", 80);
        }
    }
}
