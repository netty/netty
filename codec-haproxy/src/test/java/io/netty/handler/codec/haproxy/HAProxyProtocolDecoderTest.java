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
    public void testV1NoUDP() {
        String header = "PROXY UDP4 192.168.0.1 192.168.0.11 56324 443\r\n";
        ch.writeInbound(copiedBuffer(header, CharsetUtil.US_ASCII));
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

    @Test
    public void testV2IPV4Decode() {
        byte[] header = new byte[28];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY
        header[13] = (byte) 0x11; // TCP over IPv4

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0x0c; // -----

        header[16] = (byte) 0xc0; // Source Address
        header[17] = (byte) 0xa8; // -----
        header[18] = (byte) 0x00; // -----
        header[19] = (byte) 0x01; // -----

        header[20] = (byte) 0xc0; // Destination Address
        header[21] = (byte) 0xa8; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x0b; // -----

        header[24] = (byte) 0xdc; // Source Port
        header[25] = (byte) 0x04; // -----

        header[26] = (byte) 0x01; // Destination Port
        header[27] = (byte) 0xbb; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.TWO, msg.version());
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
    public void testV2UDPDecode() {
        byte[] header = new byte[28];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY
        header[13] = (byte) 0x12; // UDP over IPv4

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0x0c; // -----

        header[16] = (byte) 0xc0; // Source Address
        header[17] = (byte) 0xa8; // -----
        header[18] = (byte) 0x00; // -----
        header[19] = (byte) 0x01; // -----

        header[20] = (byte) 0xc0; // Destination Address
        header[21] = (byte) 0xa8; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x0b; // -----

        header[24] = (byte) 0xdc; // Source Port
        header[25] = (byte) 0x04; // -----

        header[26] = (byte) 0x01; // Destination Port
        header[27] = (byte) 0xbb; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.TWO, msg.version());
        assertEquals(HAProxyProtocolCommand.PROXY, msg.command());
        assertEquals(ProxiedProtocolAndFamily.UDP4, msg.protocolAndFamily());
        assertEquals("192.168.0.1", msg.sourceAddress());
        assertEquals("192.168.0.11", msg.destinationAddress());
        assertEquals(56324, msg.sourcePort());
        assertEquals(443, msg.destinationPort());
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testv2IPV6Decode() {
        byte[] header = new byte[52];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY
        header[13] = (byte) 0x21; // TCP over IPv6

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0x24; // -----

        header[16] = (byte) 0x20; // Source Address
        header[17] = (byte) 0x01; // -----
        header[18] = (byte) 0x0d; // -----
        header[19] = (byte) 0xb8; // -----
        header[20] = (byte) 0x85; // -----
        header[21] = (byte) 0xa3; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x00; // -----
        header[24] = (byte) 0x00; // -----
        header[25] = (byte) 0x00; // -----
        header[26] = (byte) 0x8a; // -----
        header[27] = (byte) 0x2e; // -----
        header[28] = (byte) 0x03; // -----
        header[29] = (byte) 0x70; // -----
        header[30] = (byte) 0x73; // -----
        header[31] = (byte) 0x34; // -----

        header[32] = (byte) 0x10; // Destination Address
        header[33] = (byte) 0x50; // -----
        header[34] = (byte) 0x00; // -----
        header[35] = (byte) 0x00; // -----
        header[36] = (byte) 0x00; // -----
        header[37] = (byte) 0x00; // -----
        header[38] = (byte) 0x00; // -----
        header[39] = (byte) 0x00; // -----
        header[40] = (byte) 0x00; // -----
        header[41] = (byte) 0x05; // -----
        header[42] = (byte) 0x06; // -----
        header[43] = (byte) 0x00; // -----
        header[44] = (byte) 0x30; // -----
        header[45] = (byte) 0x0c; // -----
        header[46] = (byte) 0x32; // -----
        header[47] = (byte) 0x6b; // -----

        header[48] = (byte) 0xdc; // Source Port
        header[49] = (byte) 0x04; // -----

        header[50] = (byte) 0x01; // Destination Port
        header[51] = (byte) 0xbb; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.TWO, msg.version());
        assertEquals(HAProxyProtocolCommand.PROXY, msg.command());
        assertEquals(ProxiedProtocolAndFamily.TCP6, msg.protocolAndFamily());
        assertEquals("2001:db8:85a3:0:0:8a2e:370:7334", msg.sourceAddress());
        assertEquals("1050:0:0:0:5:600:300c:326b", msg.destinationAddress());
        assertEquals(56324, msg.sourcePort());
        assertEquals(443, msg.destinationPort());
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testv2UnixDecode() {
        byte[] header = new byte[232];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY
        header[13] = (byte) 0x31; // UNIX_STREAM

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0xd8; // -----

        header[16] = (byte) 0x2f; // Source Address
        header[17] = (byte) 0x76; // -----
        header[18] = (byte) 0x61; // -----
        header[19] = (byte) 0x72; // -----
        header[20] = (byte) 0x2f; // -----
        header[21] = (byte) 0x72; // -----
        header[22] = (byte) 0x75; // -----
        header[23] = (byte) 0x6e; // -----
        header[24] = (byte) 0x2f; // -----
        header[25] = (byte) 0x73; // -----
        header[26] = (byte) 0x72; // -----
        header[27] = (byte) 0x63; // -----
        header[28] = (byte) 0x2e; // -----
        header[29] = (byte) 0x73; // -----
        header[30] = (byte) 0x6f; // -----
        header[31] = (byte) 0x63; // -----
        header[32] = (byte) 0x6b; // -----
        header[33] = (byte) 0x00; // -----

        header[124] = (byte) 0x2f; // Destination Address
        header[125] = (byte) 0x76; // -----
        header[126] = (byte) 0x61; // -----
        header[127] = (byte) 0x72; // -----
        header[128] = (byte) 0x2f; // -----
        header[129] = (byte) 0x72; // -----
        header[130] = (byte) 0x75; // -----
        header[131] = (byte) 0x6e; // -----
        header[132] = (byte) 0x2f; // -----
        header[133] = (byte) 0x64; // -----
        header[134] = (byte) 0x65; // -----
        header[135] = (byte) 0x73; // -----
        header[136] = (byte) 0x74; // -----
        header[137] = (byte) 0x2e; // -----
        header[138] = (byte) 0x73; // -----
        header[139] = (byte) 0x6f; // -----
        header[140] = (byte) 0x63; // -----
        header[141] = (byte) 0x6b; // -----
        header[142] = (byte) 0x00; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.TWO, msg.version());
        assertEquals(HAProxyProtocolCommand.PROXY, msg.command());
        assertEquals(ProxiedProtocolAndFamily.UNIX_STREAM, msg.protocolAndFamily());
        assertEquals("/var/run/src.sock", msg.sourceAddress());
        assertEquals("/var/run/dest.sock", msg.destinationAddress());
        assertEquals(0, msg.sourcePort());
        assertEquals(0, msg.destinationPort());
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testV2LocalProtocolDecode() {
        byte[] header = new byte[28];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x20; // v2, cmd=LOCAL
        header[13] = (byte) 0x00; // Unspecified transport protocol and address family

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0x0c; // -----

        header[16] = (byte) 0xc0; // Source Address
        header[17] = (byte) 0xa8; // -----
        header[18] = (byte) 0x00; // -----
        header[19] = (byte) 0x01; // -----

        header[20] = (byte) 0xc0; // Destination Address
        header[21] = (byte) 0xa8; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x0b; // -----

        header[24] = (byte) 0xdc; // Source Port
        header[25] = (byte) 0x04; // -----

        header[26] = (byte) 0x01; // Destination Port
        header[27] = (byte) 0xbb; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.TWO, msg.version());
        assertEquals(HAProxyProtocolCommand.LOCAL, msg.command());
        assertEquals(ProxiedProtocolAndFamily.UNKNOWN, msg.protocolAndFamily());
        assertNull(msg.sourceAddress());
        assertNull(msg.destinationAddress());
        assertEquals(0, msg.sourcePort());
        assertEquals(0, msg.destinationPort());
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testV2UnknownProtocolDecode() {
        byte[] header = new byte[28];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY
        header[13] = (byte) 0x00; // Unspecified transport protocol and address family

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0x0c; // -----

        header[16] = (byte) 0xc0; // Source Address
        header[17] = (byte) 0xa8; // -----
        header[18] = (byte) 0x00; // -----
        header[19] = (byte) 0x01; // -----

        header[20] = (byte) 0xc0; // Destination Address
        header[21] = (byte) 0xa8; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x0b; // -----

        header[24] = (byte) 0xdc; // Source Port
        header[25] = (byte) 0x04; // -----

        header[26] = (byte) 0x01; // Destination Port
        header[27] = (byte) 0xbb; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.TWO, msg.version());
        assertEquals(HAProxyProtocolCommand.PROXY, msg.command());
        assertEquals(ProxiedProtocolAndFamily.UNKNOWN, msg.protocolAndFamily());
        assertNull(msg.sourceAddress());
        assertNull(msg.destinationAddress());
        assertEquals(0, msg.sourcePort());
        assertEquals(0, msg.destinationPort());
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testV2WithTLV() {
        ch = new EmbeddedChannel(new HAProxyProtocolDecoder(4));

        byte[] header = new byte[236];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY
        header[13] = (byte) 0x31; // UNIX_STREAM

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0xdc; // -----

        header[16] = (byte) 0x2f; // Source Address
        header[17] = (byte) 0x76; // -----
        header[18] = (byte) 0x61; // -----
        header[19] = (byte) 0x72; // -----
        header[20] = (byte) 0x2f; // -----
        header[21] = (byte) 0x72; // -----
        header[22] = (byte) 0x75; // -----
        header[23] = (byte) 0x6e; // -----
        header[24] = (byte) 0x2f; // -----
        header[25] = (byte) 0x73; // -----
        header[26] = (byte) 0x72; // -----
        header[27] = (byte) 0x63; // -----
        header[28] = (byte) 0x2e; // -----
        header[29] = (byte) 0x73; // -----
        header[30] = (byte) 0x6f; // -----
        header[31] = (byte) 0x63; // -----
        header[32] = (byte) 0x6b; // -----
        header[33] = (byte) 0x00; // -----

        header[124] = (byte) 0x2f; // Destination Address
        header[125] = (byte) 0x76; // -----
        header[126] = (byte) 0x61; // -----
        header[127] = (byte) 0x72; // -----
        header[128] = (byte) 0x2f; // -----
        header[129] = (byte) 0x72; // -----
        header[130] = (byte) 0x75; // -----
        header[131] = (byte) 0x6e; // -----
        header[132] = (byte) 0x2f; // -----
        header[133] = (byte) 0x64; // -----
        header[134] = (byte) 0x65; // -----
        header[135] = (byte) 0x73; // -----
        header[136] = (byte) 0x74; // -----
        header[137] = (byte) 0x2e; // -----
        header[138] = (byte) 0x73; // -----
        header[139] = (byte) 0x6f; // -----
        header[140] = (byte) 0x63; // -----
        header[141] = (byte) 0x6b; // -----
        header[142] = (byte) 0x00; // -----

        // ---- Additional data (TLV) ---- \\

        header[232] = (byte) 0x01; // Type
        header[233] = (byte) 0x00; // Remaining bytes
        header[234] = (byte) 0x01; // -----
        header[235] = (byte) 0x01; // Payload

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
        Object msgObj = ch.readInbound();
        assertEquals(startChannels - 1, ch.pipeline().names().size());
        assertTrue(msgObj instanceof HAProxyProtocolMessage);
        HAProxyProtocolMessage msg = (HAProxyProtocolMessage) msgObj;
        assertEquals(HAProxyProtocolVersion.TWO, msg.version());
        assertEquals(HAProxyProtocolCommand.PROXY, msg.command());
        assertEquals(ProxiedProtocolAndFamily.UNIX_STREAM, msg.protocolAndFamily());
        assertEquals("/var/run/src.sock", msg.sourceAddress());
        assertEquals("/var/run/dest.sock", msg.destinationAddress());
        assertEquals(0, msg.sourcePort());
        assertEquals(0, msg.destinationPort());
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testV2InvalidProtocol() {
        byte[] header = new byte[28];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY
        header[13] = (byte) 0x41; // Bogus transport protocol

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0x0c; // -----

        header[16] = (byte) 0xc0; // Source Address
        header[17] = (byte) 0xa8; // -----
        header[18] = (byte) 0x00; // -----
        header[19] = (byte) 0x01; // -----

        header[20] = (byte) 0xc0; // Destination Address
        header[21] = (byte) 0xa8; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x0b; // -----

        header[24] = (byte) 0xdc; // Source Port
        header[25] = (byte) 0x04; // -----

        header[26] = (byte) 0x01; // Destination Port
        header[27] = (byte) 0xbb; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testV2MissingParams() {
        byte[] header = new byte[26];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY
        header[13] = (byte) 0x11; // TCP over IPv4

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0x0a; // -----

        header[16] = (byte) 0xc0; // Source Address
        header[17] = (byte) 0xa8; // -----
        header[18] = (byte) 0x00; // -----
        header[19] = (byte) 0x01; // -----

        header[20] = (byte) 0xc0; // Destination Address
        header[21] = (byte) 0xa8; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x0b; // -----

        header[24] = (byte) 0xdc; // Source Port
        header[25] = (byte) 0x04; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testV2InvalidCommand() {
        byte[] header = new byte[28];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x22; // v2, Bogus command
        header[13] = (byte) 0x11; // TCP over IPv4

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0x0c; // -----

        header[16] = (byte) 0xc0; // Source Address
        header[17] = (byte) 0xa8; // -----
        header[18] = (byte) 0x00; // -----
        header[19] = (byte) 0x01; // -----

        header[20] = (byte) 0xc0; // Destination Address
        header[21] = (byte) 0xa8; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x0b; // -----

        header[24] = (byte) 0xdc; // Source Port
        header[25] = (byte) 0x04; // -----

        header[26] = (byte) 0x01; // Destination Port
        header[27] = (byte) 0xbb; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testV2InvalidVersion() {
        byte[] header = new byte[28];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x31; // Bogus version, cmd=PROXY
        header[13] = (byte) 0x11; // TCP over IPv4

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0x0c; // -----

        header[16] = (byte) 0xc0; // Source Address
        header[17] = (byte) 0xa8; // -----
        header[18] = (byte) 0x00; // -----
        header[19] = (byte) 0x01; // -----

        header[20] = (byte) 0xc0; // Destination Address
        header[21] = (byte) 0xa8; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x0b; // -----

        header[24] = (byte) 0xdc; // Source Port
        header[25] = (byte) 0x04; // -----

        header[26] = (byte) 0x01; // Destination Port
        header[27] = (byte) 0xbb; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
    }

    @Test(expected = HAProxyProtocolException.class)
    public void testV2HeaderTooLong() {
        ch = new EmbeddedChannel(new HAProxyProtocolDecoder(0));

        byte[] header = new byte[248];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY
        header[13] = (byte) 0x11; // TCP over IPv4

        header[14] = (byte) 0x00; // Remaining Bytes
        header[15] = (byte) 0xe8; // -----

        header[16] = (byte) 0xc0; // Source Address
        header[17] = (byte) 0xa8; // -----
        header[18] = (byte) 0x00; // -----
        header[19] = (byte) 0x01; // -----

        header[20] = (byte) 0xc0; // Destination Address
        header[21] = (byte) 0xa8; // -----
        header[22] = (byte) 0x00; // -----
        header[23] = (byte) 0x0b; // -----

        header[24] = (byte) 0xdc; // Source Port
        header[25] = (byte) 0x04; // -----

        header[26] = (byte) 0x01; // Destination Port
        header[27] = (byte) 0xbb; // -----

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
    }

    @Test
    public void testV2IncompleteHeader() {
        byte[] header = new byte[13];
        header[0]  = (byte) 0x0D; // Binary Prefix
        header[1]  = (byte) 0x0A; // -----
        header[2]  = (byte) 0x0D; // -----
        header[3]  = (byte) 0x0A; // -----
        header[4]  = (byte) 0x00; // -----
        header[5]  = (byte) 0x0D; // -----
        header[6]  = (byte) 0x0A; // -----
        header[7]  = (byte) 0x51; // -----
        header[8]  = (byte) 0x55; // -----
        header[9]  = (byte) 0x49; // -----
        header[10] = (byte) 0x54; // -----
        header[11] = (byte) 0x0A; // -----

        header[12] = (byte) 0x21; // v2, cmd=PROXY

        int startChannels = ch.pipeline().names().size();
        ch.writeInbound(copiedBuffer(header));
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }
}
