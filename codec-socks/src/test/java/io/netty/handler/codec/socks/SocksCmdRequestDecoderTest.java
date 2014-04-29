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
import sun.net.util.IPAddressUtil;

import static org.junit.Assert.*;

public class SocksCmdRequestDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksCmdRequestDecoderTest.class);
    private static final SocksCmdType[] SOCKS4_CMD_TYPES = {SocksCmdType.CONNECT, SocksCmdType.BIND};

    private static void testSocks5CmdRequestDecoderWithDifferentParams(SocksCmdType cmdType,
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
            msg = (SocksCmdRequest) embedder.readInbound();
            assertSame(msg.cmdType(), cmdType);
            assertSame(msg.addressType(), addressType);
            assertEquals(msg.host(), host);
            assertEquals(msg.port(), port);
        }
        assertNull(embedder.readInbound());
    }

    private static void testSocks4CmdRequestDecoderWithDifferentParams(SocksCmdType cmdType,
                                                                       String userId,
                                                                       int port,
                                                                       String host) {
        logger.debug("Testing cmdType: " + cmdType + " host: " + host + " port: " + port);
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, port, host);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = (SocksCmdRequest) embedder.readInbound();
        assertSame(msg.cmdType(), cmdType);
        assertEquals(msg.host(), host);
        assertEquals(msg.port(), port);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderIPv4() {
        String[] hosts = {"127.0.0.1"};
        int[] ports = {1, 32769, 65535};
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocks5CmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.IPv4, host, port);
                }
            }
        }

        for (SocksCmdType cmdType : SOCKS4_CMD_TYPES) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocks4CmdRequestDecoderWithDifferentParams(cmdType, "netty", port, host);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderIPv6() {
        String[] hosts = {SocksCommonUtils.ipv6toStr(IPAddressUtil.textToNumericFormatV6("::1"))};
        int[] ports = {1, 32769, 65535};
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocks5CmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.IPv6, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderDomain() {
        String[] hosts = {"google.com",
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
                    testSocks5CmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.DOMAIN, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderUnknown() {
        String host = "google.com";
        int port = 80;
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            testSocks5CmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.UNKNOWN, host, port);
        }
    }

    @Test
    public void testCmdRequestDecoderUserId() {

        for (SocksCmdType cmdType : SOCKS4_CMD_TYPES) {
            testSocks4CmdRequestDecoderWithDifferentParams(cmdType, "netty", 213, "54.54.66.234");
        }
    }
}
