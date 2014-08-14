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
import sun.net.util.IPAddressUtil;

import static org.junit.Assert.*;

public class Socks5CmdRequestDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Socks5CmdRequestDecoderTest.class);

    private static void testSocksCmdRequestDecoderWithDifferentParams(Socks5CmdType cmdType,
                                                                      Socks5AddressType addressType,
                                                                      String host,
                                                                      int port) {
        logger.debug("Testing cmdType: " + cmdType + " addressType: " + addressType + " host: " + host +
                " port: " + port);
        Socks5CmdRequest msg = new Socks5CmdRequest(cmdType, addressType, host, port);
        Socks5CmdRequestDecoder decoder = new Socks5CmdRequestDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        Socks5CommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (msg.addressType() == Socks5AddressType.UNKNOWN) {
            assertTrue(embedder.readInbound() instanceof UnknownSocks5Request);
        } else {
            msg = (Socks5CmdRequest) embedder.readInbound();
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
        for (Socks5CmdType cmdType : Socks5CmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, Socks5AddressType.IPv4, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderIPv6() {
        String[] hosts = { Socks5CommonUtils.ipv6toStr(IPAddressUtil.textToNumericFormatV6("::1"))};
        int[] ports = {1, 32769, 65535};
        for (Socks5CmdType cmdType : Socks5CmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, Socks5AddressType.IPv6, host, port);
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
        for (Socks5CmdType cmdType : Socks5CmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, Socks5AddressType.DOMAIN, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderUnknown() {
        String host = "google.com";
        int port = 80;
        for (Socks5CmdType cmdType : Socks5CmdType.values()) {
            testSocksCmdRequestDecoderWithDifferentParams(cmdType, Socks5AddressType.UNKNOWN, host, port);
        }
    }
}
