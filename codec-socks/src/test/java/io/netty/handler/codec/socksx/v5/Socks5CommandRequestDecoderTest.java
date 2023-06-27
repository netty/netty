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
import io.netty.util.NetUtil;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Test;

import java.net.IDN;
import java.net.UnknownHostException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class Socks5CommandRequestDecoderTest {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(Socks5CommandRequestDecoderTest.class);

    private static void test(
            Socks5CommandType type, Socks5AddressType dstAddrType, String dstAddr, int dstPort) {
        logger.debug(
                "Testing type: " + type + " dstAddrType: " + dstAddrType +
                " dstAddr: " + dstAddr + " dstPort: " + dstPort);

        Socks5CommandRequest msg =
                new DefaultSocks5CommandRequest(type, dstAddrType, dstAddr, dstPort);
        EmbeddedChannel embedder = new EmbeddedChannel(new Socks5CommandRequestDecoder());
        Socks5CommonTestUtils.writeFromClientToServer(embedder, msg);
        msg = embedder.readInbound();
        assertSame(msg.type(), type);
        assertSame(msg.dstAddrType(), dstAddrType);
        assertEquals(msg.dstAddr(), IDN.toASCII(dstAddr));
        assertEquals(msg.dstPort(), dstPort);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderIPv4() {
        String[] hosts = {"127.0.0.1", };
        int[] ports = {1, 32769, 65535 };
        for (Socks5CommandType cmdType: Arrays.asList(Socks5CommandType.BIND,
                                                      Socks5CommandType.CONNECT,
                                                      Socks5CommandType.UDP_ASSOCIATE)) {
            for (String host : hosts) {
                for (int port : ports) {
                    test(cmdType, Socks5AddressType.IPv4, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderIPv6() throws UnknownHostException {
        String[] hosts = {
                NetUtil.bytesToIpAddress(SocketUtils.addressByName("::1").getAddress()) };
        int[] ports = {1, 32769, 65535};
        for (Socks5CommandType cmdType: Arrays.asList(Socks5CommandType.BIND,
                                                      Socks5CommandType.CONNECT,
                                                      Socks5CommandType.UDP_ASSOCIATE)) {
            for (String host : hosts) {
                for (int port : ports) {
                    test(cmdType, Socks5AddressType.IPv6, host, port);
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
        for (Socks5CommandType cmdType: Arrays.asList(Socks5CommandType.BIND,
                                                      Socks5CommandType.CONNECT,
                                                      Socks5CommandType.UDP_ASSOCIATE)) {
            for (String host : hosts) {
                for (int port : ports) {
                    test(cmdType, Socks5AddressType.DOMAIN, host, port);
                }
            }
        }
    }
}
