/*
 * Copyright 2020 The Netty Project
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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyTLV.Type;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.haproxy.HAProxyConstants.*;
import static io.netty.handler.codec.haproxy.HAProxyMessageEncoder.*;
import static org.junit.Assert.*;

public class HaProxyMessageEncoderTest {

    private static final int V2_HEADER_BYTES_LENGTH = 16;
    private static final int IPv4_ADDRESS_BYTES_LENGTH = 12;
    private static final int IPv6_ADDRESS_BYTES_LENGTH = 36;

    @Test
    public void testIPV4EncodeProxyV1() {
        EmbeddedChannel ch = new EmbeddedChannel(INSTANCE);

        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V1, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP4,
                "192.168.0.1", "192.168.0.11", 56324, 443);
        assertTrue(ch.writeOutbound(message));

        ByteBuf byteBuf = ch.readOutbound();

        assertEquals("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n",
                     byteBuf.toString(CharsetUtil.US_ASCII));

        byteBuf.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testIPV6EncodeProxyV1() {
        EmbeddedChannel ch = new EmbeddedChannel(INSTANCE);

        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V1, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP6,
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "1050:0:0:0:5:600:300c:326b", 56324, 443);
        assertTrue(ch.writeOutbound(message));

        ByteBuf byteBuf = ch.readOutbound();

        assertEquals("PROXY TCP6 2001:0db8:85a3:0000:0000:8a2e:0370:7334 1050:0:0:0:5:600:300c:326b 56324 443\r\n",
                     byteBuf.toString(CharsetUtil.US_ASCII));

        byteBuf.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testIPv4EncodeProxyV2() {
        EmbeddedChannel ch = new EmbeddedChannel(INSTANCE);

        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP4,
                "192.168.0.1", "192.168.0.11", 56324, 443);
        assertTrue(ch.writeOutbound(message));

        ByteBuf byteBuf = ch.readOutbound();

        // header
        byte[] headerBytes = ByteBufUtil.getBytes(byteBuf, 0, 12);
        assertArrayEquals(BINARY_PREFIX, headerBytes);

        // command
        byte commandByte = byteBuf.getByte(12);
        assertEquals(0x02, (commandByte & 0xf0) >> 4);
        assertEquals(0x01, commandByte & 0x0f);

        // transport protocol, address family
        byte transportByte = byteBuf.getByte(13);
        assertEquals(0x01, (transportByte & 0xf0) >> 4);
        assertEquals(0x01, transportByte & 0x0f);

        // source address length
        int sourceAddrLength = byteBuf.getUnsignedShort(14);
        assertEquals(12, sourceAddrLength);

        // source address
        byte[] sourceAddr = ByteBufUtil.getBytes(byteBuf, 16, 4);
        assertArrayEquals(new byte[] { (byte) 0xc0, (byte) 0xa8, 0x00, 0x01 }, sourceAddr);

        // destination address
        byte[] destAddr = ByteBufUtil.getBytes(byteBuf, 20, 4);
        assertArrayEquals(new byte[] { (byte) 0xc0, (byte) 0xa8, 0x00, 0x0b }, destAddr);

        // source port
        int sourcePort = byteBuf.getUnsignedShort(24);
        assertEquals(56324, sourcePort);

        // destination port
        int destPort = byteBuf.getUnsignedShort(26);
        assertEquals(443, destPort);

        byteBuf.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testIPv6EncodeProxyV2() {
        EmbeddedChannel ch = new EmbeddedChannel(INSTANCE);

        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP6,
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "1050:0:0:0:5:600:300c:326b", 56324, 443);
        assertTrue(ch.writeOutbound(message));

        ByteBuf byteBuf = ch.readOutbound();

        // header
        byte[] headerBytes = ByteBufUtil.getBytes(byteBuf, 0, 12);
        assertArrayEquals(BINARY_PREFIX, headerBytes);

        // command
        byte commandByte = byteBuf.getByte(12);
        assertEquals(0x02, (commandByte & 0xf0) >> 4);
        assertEquals(0x01, commandByte & 0x0f);

        // transport protocol, address family
        byte transportByte = byteBuf.getByte(13);
        assertEquals(0x02, (transportByte & 0xf0) >> 4);
        assertEquals(0x01, transportByte & 0x0f);

        // source address length
        int sourceAddrLength = byteBuf.getUnsignedShort(14);
        assertEquals(IPv6_ADDRESS_BYTES_LENGTH, sourceAddrLength);

        // source address
        byte[] sourceAddr = ByteBufUtil.getBytes(byteBuf, 16, 16);
        assertArrayEquals(new byte[] {
                (byte) 0x20, (byte) 0x01, 0x0d, (byte) 0xb8,
                (byte) 0x85, (byte) 0xa3, 0x00, 0x00, 0x00, 0x00, (byte) 0x8a, 0x2e,
                0x03, 0x70, 0x73, 0x34
        }, sourceAddr);

        // destination address
        byte[] destAddr = ByteBufUtil.getBytes(byteBuf, 32, 16);
        assertArrayEquals(new byte[] {
                (byte) 0x10, (byte) 0x50, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x05, 0x06, 0x00, 0x30, 0x0c, 0x32, 0x6b
        }, destAddr);

        // source port
        int sourcePort = byteBuf.getUnsignedShort(48);
        assertEquals(56324, sourcePort);

        // destination port
        int destPort = byteBuf.getUnsignedShort(50);
        assertEquals(443, destPort);

        byteBuf.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testUnixEncodeProxyV2() {
        EmbeddedChannel ch = new EmbeddedChannel(INSTANCE);

        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.PROXY, HAProxyProxiedProtocol.UNIX_STREAM,
                "/var/run/src.sock", "/var/run/dst.sock", 0, 0);
        assertTrue(ch.writeOutbound(message));

        ByteBuf byteBuf = ch.readOutbound();

        // header
        byte[] headerBytes = ByteBufUtil.getBytes(byteBuf, 0, 12);
        assertArrayEquals(BINARY_PREFIX, headerBytes);

        // command
        byte commandByte = byteBuf.getByte(12);
        assertEquals(0x02, (commandByte & 0xf0) >> 4);
        assertEquals(0x01, commandByte & 0x0f);

        // transport protocol, address family
        byte transportByte = byteBuf.getByte(13);
        assertEquals(0x03, (transportByte & 0xf0) >> 4);
        assertEquals(0x01, transportByte & 0x0f);

        // address length
        int addrLength = byteBuf.getUnsignedShort(14);
        assertEquals(TOTAL_UNIX_ADDRESS_BYTES_LENGTH, addrLength);

        // source address
        int srcAddrEnd = byteBuf.forEachByte(16, 108, ByteProcessor.FIND_NUL);
        assertEquals("/var/run/src.sock",
                     byteBuf.slice(16, srcAddrEnd - 16).toString(CharsetUtil.US_ASCII));

        // destination address
        int dstAddrEnd = byteBuf.forEachByte(124, 108, ByteProcessor.FIND_NUL);
        assertEquals("/var/run/dst.sock",
                     byteBuf.slice(124, dstAddrEnd - 124).toString(CharsetUtil.US_ASCII));

        byteBuf.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testTLVEncodeProxy() {
        EmbeddedChannel ch = new EmbeddedChannel(INSTANCE);

        List<HAProxyTLV> tlvs = new ArrayList<HAProxyTLV>();

        ByteBuf helloWorld = Unpooled.copiedBuffer("hello world", CharsetUtil.US_ASCII);
        HAProxyTLV alpnTlv = new HAProxyTLV(Type.PP2_TYPE_ALPN, (byte) 0x01, helloWorld.copy());
        tlvs.add(alpnTlv);

        ByteBuf arbitrary = Unpooled.copiedBuffer("an arbitrary string", CharsetUtil.US_ASCII);
        HAProxyTLV authorityTlv = new HAProxyTLV(Type.PP2_TYPE_AUTHORITY, (byte) 0x01, arbitrary.copy());
        tlvs.add(authorityTlv);

        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP4,
                "192.168.0.1", "192.168.0.11", 56324, 443, tlvs);
        assertTrue(ch.writeOutbound(message));

        ByteBuf byteBuf = ch.readOutbound();

        // length
        assertEquals(byteBuf.getUnsignedShort(14), byteBuf.readableBytes() - V2_HEADER_BYTES_LENGTH);

        // skip to tlv section
        ByteBuf tlv = byteBuf.skipBytes(V2_HEADER_BYTES_LENGTH + IPv4_ADDRESS_BYTES_LENGTH);

        // alpn tlv
        assertEquals(alpnTlv.typeByteValue(), tlv.readByte());
        short bufLength = tlv.readShort();
        assertEquals(helloWorld.array().length, bufLength);
        assertEquals(helloWorld, tlv.readBytes(bufLength));

        // authority tlv
        assertEquals(authorityTlv.typeByteValue(), tlv.readByte());
        bufLength = tlv.readShort();
        assertEquals(arbitrary.array().length, bufLength);
        assertEquals(arbitrary, tlv.readBytes(bufLength));

        byteBuf.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testSslTLVEncodeProxy() {
        EmbeddedChannel ch = new EmbeddedChannel(INSTANCE);

        List<HAProxyTLV> tlvs = new ArrayList<HAProxyTLV>();

        ByteBuf helloWorld = Unpooled.copiedBuffer("hello world", CharsetUtil.US_ASCII);
        HAProxyTLV alpnTlv = new HAProxyTLV(Type.PP2_TYPE_ALPN, (byte) 0x01, helloWorld.copy());
        tlvs.add(alpnTlv);

        ByteBuf arbitrary = Unpooled.copiedBuffer("an arbitrary string", CharsetUtil.US_ASCII);
        HAProxyTLV authorityTlv = new HAProxyTLV(Type.PP2_TYPE_AUTHORITY, (byte) 0x01, arbitrary.copy());
        tlvs.add(authorityTlv);

        ByteBuf sslContent = Unpooled.copiedBuffer("some ssl content", CharsetUtil.US_ASCII);
        HAProxySSLTLV haProxySSLTLV = new HAProxySSLTLV(1, (byte) 0x01, tlvs, sslContent.copy());

        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP4,
                "192.168.0.1", "192.168.0.11", 56324, 443,
                Collections.<HAProxyTLV>singletonList(haProxySSLTLV));
        assertTrue(ch.writeOutbound(message));

        ByteBuf byteBuf = ch.readOutbound();

        assertEquals(byteBuf.getUnsignedShort(14), byteBuf.readableBytes() - V2_HEADER_BYTES_LENGTH);
        ByteBuf tlv = byteBuf.skipBytes(V2_HEADER_BYTES_LENGTH + IPv4_ADDRESS_BYTES_LENGTH);

        // ssl tlv type
        assertEquals(haProxySSLTLV.typeByteValue(), tlv.readByte());

        // length
        int bufLength = tlv.readUnsignedShort();
        assertEquals(bufLength, tlv.readableBytes());

        // client, verify
        assertEquals(0x01, byteBuf.readByte());
        assertEquals(1, byteBuf.readInt());

        // alpn tlv
        assertEquals(alpnTlv.typeByteValue(), tlv.readByte());
        bufLength = tlv.readShort();
        assertEquals(helloWorld.array().length, bufLength);
        assertEquals(helloWorld, tlv.readBytes(bufLength));

        // authority tlv
        assertEquals(authorityTlv.typeByteValue(), tlv.readByte());
        bufLength = tlv.readShort();
        assertEquals(arbitrary.array().length, bufLength);
        assertEquals(arbitrary, tlv.readBytes(bufLength));

        byteBuf.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeLocalProxyV2() {
        EmbeddedChannel ch = new EmbeddedChannel(INSTANCE);

        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.LOCAL, HAProxyProxiedProtocol.UNKNOWN,
                null, null, 0, 0);
        assertTrue(ch.writeOutbound(message));

        ByteBuf byteBuf = ch.readOutbound();

        // header
        byte[] headerBytes = new byte[12];
        byteBuf.readBytes(headerBytes);
        assertArrayEquals(BINARY_PREFIX, headerBytes);

        // command
        byte commandByte = byteBuf.readByte();
        assertEquals(0x02, (commandByte & 0xf0) >> 4);
        assertEquals(0x00, commandByte & 0x0f);

        // transport protocol, address family
        byte transportByte = byteBuf.readByte();
        assertEquals(0x00, transportByte);

        // source address length
        int sourceAddrLength = byteBuf.readUnsignedShort();
        assertEquals(0, sourceAddrLength);

        assertFalse(byteBuf.isReadable());

        byteBuf.release();
        assertFalse(ch.finish());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidIpV4Address() {
        String invalidIpv4Address = "192.168.0.1234";
        new HAProxyMessage(
                HAProxyProtocolVersion.V1, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP4,
                invalidIpv4Address, "192.168.0.11", 56324, 443);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidIpV6Address() {
        String invalidIpv6Address = "2001:0db8:85a3:0000:0000:8a2e:0370:73345";
        new HAProxyMessage(
                HAProxyProtocolVersion.V1, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP6,
                invalidIpv6Address, "1050:0:0:0:5:600:300c:326b", 56324, 443);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidUnixAddress() {
        String invalidUnixAddress = new String(new byte[UNIX_ADDRESS_BYTES_LENGTH + 1]);
        new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.PROXY, HAProxyProxiedProtocol.UNIX_STREAM,
                invalidUnixAddress, "/var/run/dst.sock", 0, 0);
    }

    @Test(expected = NullPointerException.class)
    public void testNullUnixAddress() {
        new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.PROXY, HAProxyProxiedProtocol.UNIX_STREAM,
                null, null, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLongUnixAddress() {
        String longUnixAddress = new String(new char[109]).replace("\0", "a");
        new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.PROXY, HAProxyProxiedProtocol.UNIX_STREAM,
                "source", longUnixAddress, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidUnixPort() {
        new HAProxyMessage(
                HAProxyProtocolVersion.V2, HAProxyCommand.PROXY, HAProxyProxiedProtocol.UNIX_STREAM,
                "/var/run/src.sock", "/var/run/dst.sock", 80, 443);
    }
}
