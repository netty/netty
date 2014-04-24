/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.haproxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;

import org.junit.Before;
import org.junit.Test;

import static io.netty.buffer.Unpooled.*;
import static org.junit.Assert.*;

public class HAProxyProtocolDecoderTest {

    private EmbeddedChannel ch;

    @Before
    public void setUp() {
        ch = new EmbeddedChannel(new HAProxyProtocolDecoder());
    }

    @Test
    public void testIPV4Decode() {
        int startChannels = ch.pipeline().names().size();
        String header = "PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.ONE, msg.version());
        assertEquals(HAProxyProtocolCommand.PROXY, msg.command());
        assertEquals(ProxiedProtocolAndFamily.TCP4, msg.protocolAndFamily());
        assertEquals("192.168.0.1", msg.sourceAddress());
        assertEquals("192.168.0.11", msg.destinationAddress());
        assertEquals(56324, msg.sourcePort());
        assertEquals(443, msg.destinationPort());
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testIPV6Decode() {
        int startChannels = ch.pipeline().names().size();
        String header = "PROXY TCP6 2001:0db8:85a3:0000:0000:8a2e:0370:7334 1050:0:0:0:5:600:300c:326b 56324 443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.ONE, msg.version());
        assertEquals(HAProxyProtocolCommand.PROXY, msg.command());
        assertEquals(ProxiedProtocolAndFamily.TCP6, msg.protocolAndFamily());
        assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", msg.sourceAddress());
        assertEquals("1050:0:0:0:5:600:300c:326b", msg.destinationAddress());
        assertEquals(56324, msg.sourcePort());
        assertEquals(443, msg.destinationPort());
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testUnknownProtocolDecode() {
        int startChannels = ch.pipeline().names().size();
        String header = "PROXY UNKNOWN 192.168.0.1 192.168.0.11 56324 443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.ONE, msg.version());
        assertEquals(HAProxyProtocolCommand.PROXY, msg.command());
        assertEquals(ProxiedProtocolAndFamily.UNKNOWN, msg.protocolAndFamily());
        assertNull(msg.sourceAddress());
        assertNull(msg.destinationAddress());
        assertEquals(0, msg.sourcePort());
        assertEquals(0, msg.destinationPort());
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testInvalidPort() {
        String header = "PROXY TCP4 192.168.0.1 192.168.0.11 80000 443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testInvalidIPV4Address() {
        String header = "PROXY TCP4 299.168.0.1 192.168.0.11 56324 443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testInvalidIPV6Address() {
        String header = "PROXY TCP6 r001:0db8:85a3:0000:0000:8a2e:0370:7334 1050:0:0:0:5:600:300c:326b 56324 443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testInvalidProtocol() {
        String header = "PROXY TCP7 192.168.0.1 192.168.0.11 56324 443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testMissingParams() {
        String header = "PROXY TCP4 192.168.0.1 192.168.0.11 56324\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testTooManyParams() {
        String header = "PROXY TCP4 192.168.0.1 192.168.0.11 56324 443 123\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testInvalidCommand() {
        String header = "PING TCP4 192.168.0.1 192.168.0.11 56324 443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testInvalidEOL() {
        String header = "PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\nGET / HTTP/1.1\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testHeaderTooLong() {
        String header = "PROXY TCP4 192.168.0.1 192.168.0.11 56324 " +
                "00000000000000000000000000000000000000000000000000000000000000000443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
    }

    @Test
    public void testIncompleteHeader() {
        String header = "PROXY TCP4 192.168.0.1 192.168.0.11 56324";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testCloseOnInvalid() {
        ChannelFuture closeFuture = ch.closeFuture();
        String header = "GET / HTTP/1.1\r\n";
        try {
            ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
        } catch (HAProxyProtocolException ppex) {
            // swallow this exception since we're just testing to be sure the channel was closed
        }
        boolean isComplete = closeFuture.awaitUninterruptibly(5000);
        if (!isComplete || !closeFuture.isDone() || !closeFuture.isSuccess()) {
            fail("Expected channel close");
        }
    }

    @Test
    public void testTransportProtocolAndAddressFamily() {
        final byte unkown = ProxiedProtocolAndFamily.UNKNOWN.byteValue();
        final byte tcp4 = ProxiedProtocolAndFamily.TCP4.byteValue();
        final byte tcp6 = ProxiedProtocolAndFamily.TCP6.byteValue();
        final byte udp4 = ProxiedProtocolAndFamily.UDP4.byteValue();
        final byte udp6 = ProxiedProtocolAndFamily.UDP6.byteValue();
        final byte unix_stream = ProxiedProtocolAndFamily.UNIX_STREAM.byteValue();
        final byte unix_dgram = ProxiedProtocolAndFamily.UNIX_DGRAM.byteValue();

        assertEquals(ProxiedTransportProtocol.UNSPECIFIED, ProxiedTransportProtocol.valueOf(unkown));
        assertEquals(ProxiedTransportProtocol.STREAM, ProxiedTransportProtocol.valueOf(tcp4));
        assertEquals(ProxiedTransportProtocol.STREAM, ProxiedTransportProtocol.valueOf(tcp6));
        assertEquals(ProxiedTransportProtocol.STREAM, ProxiedTransportProtocol.valueOf(unix_stream));
        assertEquals(ProxiedTransportProtocol.DGRAM, ProxiedTransportProtocol.valueOf(udp4));
        assertEquals(ProxiedTransportProtocol.DGRAM, ProxiedTransportProtocol.valueOf(udp6));
        assertEquals(ProxiedTransportProtocol.DGRAM, ProxiedTransportProtocol.valueOf(unix_dgram));

        assertEquals(ProxiedAddressFamily.UNSPECIFIED, ProxiedAddressFamily.valueOf(unkown));
        assertEquals(ProxiedAddressFamily.IPV4, ProxiedAddressFamily.valueOf(tcp4));
        assertEquals(ProxiedAddressFamily.IPV4, ProxiedAddressFamily.valueOf(udp4));
        assertEquals(ProxiedAddressFamily.IPV6, ProxiedAddressFamily.valueOf(tcp6));
        assertEquals(ProxiedAddressFamily.IPV6, ProxiedAddressFamily.valueOf(udp6));
        assertEquals(ProxiedAddressFamily.UNIX, ProxiedAddressFamily.valueOf(unix_stream));
        assertEquals(ProxiedAddressFamily.UNIX, ProxiedAddressFamily.valueOf(unix_dgram));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testV2Unsupported() {
        byte[] header = new byte[] {
                (byte) 0x0D, // Binary Prefix
                (byte) 0x0A, // -----
                (byte) 0x0D, // -----
                (byte) 0x0A, // -----
                (byte) 0x00, // -----
                (byte) 0x0D, // -----
                (byte) 0x0A, // -----
                (byte) 0x51, // -----
                (byte) 0x55, // -----
                (byte) 0x49, // -----
                (byte) 0x54, // -----
                (byte) 0x0A, // -----
                (byte) 0x02, // v2
                (byte) 0x01, // PROXY
                (byte) 0x11, // TCP over IPv4
                (byte) 0x0c, // Remaining Bytes
                (byte) 0xc0, // Source Address
                (byte) 0xa8, // -----
                (byte) 0x00, // -----
                (byte) 0x01, // -----
                (byte) 0xc0, // Destination Address
                (byte) 0xa8, // -----
                (byte) 0x00, // -----
                (byte) 0x0b, // -----
                (byte) 0xdc, // Source Port
                (byte) 0x04, // -----
                (byte) 0x01, // Destination Port
                (byte) 0xbb  // -----
        };
        ch.writeInbound(copiedBuffer(header));
        assertFalse(ch.finish());
    }
}
