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
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import java.net.UnknownHostException;

import static org.junit.Assert.*;

public class SocksCmdRequestDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksCmdRequestDecoderTest.class);

    private static void testSocksCmdRequestDecoderWithDifferentParams(SocksCmdType cmdType,
                                                                      SocksAddressType addressType,
                                                                      String host,
                                                                      int port) {
        logger.debug("Testing cmdType: " + cmdType + " addressType: " + addressType + " host: " + host +
                " port: " + port);
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (msg.addressType() == SocksAddressType.UNKNOWN) {
            assertTrue(embedder.readInbound() instanceof UnknownSocksRequest);
        } else {
            msg = embedder.readInbound();
            assertSame(msg.cmdType(), cmdType);
            assertSame(msg.addressType(), addressType);
            assertEquals(msg.host(), host);
            assertEquals(msg.port(), port);
        }
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderIPv4() {
        String[] hosts = {"127.0.0.1", };
        int[] ports = {1, 32769, 65535 };
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.IPv4, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderIPv6() throws UnknownHostException {
        String[] hosts = {SocksCommonUtils.ipv6toStr(SocketUtils.addressByName("::1").getAddress())};
        int[] ports = {1, 32769, 65535};
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.IPv6, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderDomain() {
        String[] hosts = {"google.com" ,
                          "مثال.إختبار",
                          "παράδειγμα.δοκιμή",
                          "مثال.آزمایشی",
                          "пример.испытание",
                          "בײַשפּיל.טעסט",
                          "例子.测试",
                          "例子.測試",
                          "उदाहरण.परीक्षा",
                          "例え.テスト",
                          "실례.테스트",
                          "உதாரணம்.பரிட்சை"};
        int[] ports = {1, 32769, 65535};
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.DOMAIN, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderUnknown() {
        String host = "google.com";
        int port = 80;
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.UNKNOWN, host, port);
        }
    }
}
