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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class Socks4CmdRequestDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Socks4CmdRequestDecoderTest.class);

    private static void testSocksV4CmdRequestDecoderWithDifferentParams(String userId,
                                                                        Socks4CmdType cmdType,
                                                                        String host,
                                                                        int port) {
        logger.debug("Testing cmdType: " + cmdType + " userId: " + userId + " host: " + host +
                " port: " + port);
        Socks4CmdRequest msg = new Socks4CmdRequest(userId, cmdType, host, port);
        Socks4CmdRequestDecoder decoder = new Socks4CmdRequestDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        Socks4CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        Object obj = embedder.readInbound();
        msg = (Socks4CmdRequest) obj;
        assertSame(msg.cmdType(), cmdType);
        assertEquals(msg.userId(), userId);
        assertEquals(msg.host(), host);
        assertEquals(msg.port(), port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoder() {
        String[] hosts = {"127.0.0.1", };
        String[] userIds = {"test", };
        int[] ports = {1, 32769, 65535};
        for (Socks4CmdType cmdType : Socks4CmdType.values()) {
            for (String userId : userIds) {
                for (String host : hosts) {
                    for (int port : ports) {
                        testSocksV4CmdRequestDecoderWithDifferentParams(userId, cmdType, host, port);
                    }
                }
            }
        }
    }
}
