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
package org.jboss.netty.handler.codec.socks;

import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.junit.Test;
import sun.net.util.IPAddressUtil;

import static org.junit.Assert.*;

public class SocksCmdRequestDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksCmdRequestDecoderTest.class);

    private static void testSocksCmdRequestDecoderWithDifferentParams(SocksMessage.CmdType cmdType,
                                      SocksMessage.AddressType addressType, String host, int port) throws Exception {
        logger.debug("Testing cmdType: " + cmdType + " addressType: " + addressType + " host: "
                + host + " port: " + port);
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        DecoderEmbedder<SocksRequest> embedder = new DecoderEmbedder<SocksRequest>(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (msg.getAddressType() == SocksMessage.AddressType.UNKNOWN) {
            assertTrue(embedder.poll() instanceof UnknownSocksRequest);
        } else {
            msg = (SocksCmdRequest) embedder.poll();
            assertSame(msg.getCmdType(), cmdType);
            assertSame(msg.getAddressType(), addressType);
            assertEquals(msg.getHost(), host);
            assertEquals(msg.getPort(), port);
        }
        assertNull(embedder.poll());
    }

    @Test
    public void testCmdRequestDecoderIPv4() throws Exception{
        String[] hosts = { "127.0.0.1" };
        int[] ports = {1, 32769, 65535 };
        for (SocksMessage.CmdType cmdType : SocksMessage.CmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksMessage.AddressType.IPv4, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderIPv6() throws Exception{
        String[] hosts = { SocksCommonUtils.ipv6toStr(IPAddressUtil.textToNumericFormatV6("::1")) };
        int[] ports = {1, 32769, 65535};
        for (SocksMessage.CmdType cmdType : SocksMessage.CmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksMessage.AddressType.IPv6, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderDomain()  throws Exception{
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
        for (SocksMessage.CmdType cmdType : SocksMessage.CmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksMessage.AddressType.DOMAIN, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderUnknown() throws Exception{
        String host = "google.com";
        int port = 80;
        for (SocksMessage.CmdType cmdType : SocksMessage.CmdType.values()) {
            testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksMessage.AddressType.UNKNOWN, host, port);
        }
    }
}
