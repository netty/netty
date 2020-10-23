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
package io.netty.handler.codec.socksx.v5;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class Socks5PasswordAuthResponseDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(
            Socks5PasswordAuthResponseDecoderTest.class);

    private static void test(Socks5PasswordAuthStatus status) {
        logger.debug("Testing Socks5PasswordAuthResponseDecoder with status: " + status);
        Socks5PasswordAuthResponse msg = new DefaultSocks5PasswordAuthResponse(status);
        EmbeddedChannel embedder = new EmbeddedChannel(new Socks5PasswordAuthResponseDecoder());
        Socks5CommonTestUtils.writeFromServerToClient(embedder, msg);
        msg = embedder.readInbound();
        assertSame(msg.status(), status);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testSocksCmdResponseDecoder() {
        test(Socks5PasswordAuthStatus.SUCCESS);
        test(Socks5PasswordAuthStatus.FAILURE);
    }
}
