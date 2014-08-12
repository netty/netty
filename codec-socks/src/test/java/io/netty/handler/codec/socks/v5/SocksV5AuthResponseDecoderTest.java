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

public class SocksV5AuthResponseDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(
            SocksV5AuthResponseDecoderTest.class);

    private static void testSocksAuthResponseDecoderWithDifferentParams(SocksV5AuthStatus authStatus) {
        logger.debug("Testing SocksAuthResponseDecoder with authStatus: " + authStatus);
        SocksV5AuthResponse msg = new SocksV5AuthResponse(authStatus);
        SocksV5AuthResponseDecoder decoder = new SocksV5AuthResponseDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksV5CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksV5AuthResponse) embedder.readInbound();
        assertSame(msg.authStatus(), authStatus);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testSocksCmdResponseDecoder() {
        for (SocksV5AuthStatus authStatus: SocksV5AuthStatus.values()) {
            testSocksAuthResponseDecoderWithDifferentParams(authStatus);
        }
    }
}
