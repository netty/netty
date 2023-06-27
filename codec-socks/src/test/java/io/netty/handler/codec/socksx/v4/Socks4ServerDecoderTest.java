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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class Socks4ServerDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Socks4ServerDecoderTest.class);

    private static void test(String userId, Socks4CommandType type, String dstAddr, int dstPort) {
        logger.debug(
                "Testing type: " + type + " dstAddr: " + dstAddr + " dstPort: " + dstPort +
                " userId: " + userId);

        Socks4CommandRequest msg = new DefaultSocks4CommandRequest(type, dstAddr, dstPort, userId);
        EmbeddedChannel embedder = new EmbeddedChannel(new Socks4ServerDecoder());
        Socks4CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = embedder.readInbound();
        assertSame(msg.type(), type);
        assertEquals(msg.dstAddr(), dstAddr);
        assertEquals(msg.dstPort(), dstPort);
        assertEquals(msg.userId(), userId);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoder() {
        String[] hosts = { "127.0.0.1", };
        String[] userIds = { "test", };
        int[] ports = {1, 32769, 65535};

        for (Socks4CommandType cmdType : Arrays.asList(Socks4CommandType.BIND,
                                                       Socks4CommandType.CONNECT)) {
            for (String userId : userIds) {
                for (String host : hosts) {
                    for (int port : ports) {
                        test(userId, cmdType, host, port);
                    }
                }
            }
        }
    }
}
