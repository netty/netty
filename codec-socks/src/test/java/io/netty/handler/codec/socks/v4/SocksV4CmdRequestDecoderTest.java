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
import io.netty.handler.codec.socks.v5.SocksV5CmdRequest;
import io.netty.handler.codec.socks.v5.SocksV5CmdType;
import io.netty.handler.codec.socks.v5.UnknownSocksV5Request;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SocksV4CmdRequestDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksV4CmdRequestDecoderTest.class);

    private static void testSocksV4CmdRequestDecoderWithDifferentParams(String userId,
                                                                        SocksV4CmdType cmdType,
                                                                        String host,
                                                                        int port) {
        logger.debug("Testing cmdType: " + cmdType + " userId: " + userId + " host: " + host +
                " port: " + port);
        SocksV4CmdRequest msg = new SocksV4CmdRequest(userId, cmdType, host, port);
        SocksV4CmdRequestDecoder decoder = new SocksV4CmdRequestDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksV4CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        Object obj = embedder.readInbound();
        msg = (SocksV4CmdRequest) obj;
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
        for (SocksV4CmdType cmdType : SocksV4CmdType.values()) {
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
