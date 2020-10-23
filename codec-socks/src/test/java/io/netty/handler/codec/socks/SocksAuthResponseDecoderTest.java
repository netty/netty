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
package io.netty.handler.codec.socks;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class SocksAuthResponseDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksAuthResponseDecoderTest.class);

    private static void testSocksAuthResponseDecoderWithDifferentParams(SocksAuthStatus authStatus) {
        logger.debug("Testing SocksAuthResponseDecoder with authStatus: " + authStatus);
        SocksAuthResponse msg = new SocksAuthResponse(authStatus);
        SocksAuthResponseDecoder decoder = new SocksAuthResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = embedder.readInbound();
        assertSame(msg.authStatus(), authStatus);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testSocksCmdResponseDecoder() {
        for (SocksAuthStatus authStatus: SocksAuthStatus.values()) {
            testSocksAuthResponseDecoderWithDifferentParams(authStatus);
        }
    }
}
