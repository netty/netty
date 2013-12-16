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

import static org.junit.Assert.*;

public class SocksCmdResponseDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksCmdResponseDecoderTest.class);

    private static void testSocksCmdResponseDecoderWithDifferentParams(
            SocksCmdStatus cmdStatus, SocksAddressType addressType) {
        logger.debug("Testing cmdStatus: " + cmdStatus + " addressType: " + addressType);
        SocksResponse msg = new SocksCmdResponse(cmdStatus, addressType);
        SocksCmdResponseDecoder decoder = new SocksCmdResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (addressType == SocksAddressType.UNKNOWN) {
            assertTrue(embedder.readInbound() instanceof UnknownSocksResponse);
        } else {
            msg = embedder.readInbound();
            assertEquals(((SocksCmdResponse) msg).cmdStatus(), cmdStatus);
        }
        assertNull(embedder.readInbound());
    }

    @Test
    public void testSocksCmdResponseDecoder() {
        for (SocksCmdStatus cmdStatus: SocksCmdStatus.values()) {
            for (SocksAddressType addressType: SocksAddressType.values()) {
                testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, addressType);
            }
        }
    }
}
