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
package io.netty.example.tunechoserver;

import java.net.InetAddress;

import io.netty.buffer.ByteBuf;

public final class InetUtils {

    public static int computeInetChecksum(byte[] buf, int offset, int len) {

        if (len > 65536) {
            throw new IllegalArgumentException("checksum input too large");
        }

        int sum = 0;

        // Sum each whole 2-byte word in the input.
        for (; len > 1; offset += 2, len -= 2) {
            sum += ((buf[offset] & 0xff) << 8) + (buf[offset + 1] & 0xff);
        }

        // If the input length is odd, treat the input as if it had an extra 0 byte at the end.
        if (len > 0) {
            sum += (buf[offset] & 0xff) << 8;
        }

        // Complete the one's compliment sum by performing end-around carry.
        sum = ((sum >> 16) & 0xFFFF) + (sum & 0xFFFF);
        sum = ((sum >> 16) & 0xFFFF) + (sum & 0xFFFF);

        // Negate the resultant sum to form the new checksum.
        sum = ~sum & 0xFFFF;

        return sum;
    }

    public static int updateInetChecksum(byte[] oldDataBuf, int oldDataOffset, byte[] newDataBuf,
            int newDataOffset, int len, int oldSum) {

        if (len > 65536) {
            throw new IllegalArgumentException("checksum input too large");
        }
        if ((len & 1) != 0) {
            throw new IllegalArgumentException("checksum input not multiple of two");
        }

        // NOTE: This algorithm is an adaptation of Equation 3 from RFC-1624.

        // Start with the one's compliment negative of the previous checksum value.
        int sum = ~oldSum & 0xFFFF;

        // For each 2-byte word being updated...
        for (; len > 1; oldDataOffset += 2, newDataOffset += 2, len -= 2) {

            // Sum the one's compliment negative of the old word value.
            sum += ~(((oldDataBuf[oldDataOffset] & 0xff) << 8) + (oldDataBuf[oldDataOffset + 1] & 0xff)) & 0xFFFF;

            // Sum the new word value.
            sum +=  ((newDataBuf[newDataOffset] & 0xff) << 8) + (newDataBuf[newDataOffset + 1] & 0xff);
        }

        // Complete the one's compliment sum by performing end-around carry.
        sum = (sum & 0xFFFF) + ((sum >> 16) & 0xFFFF);
        sum = (sum & 0xFFFF) + ((sum >> 16) & 0xFFFF);

        // Negate the resultant sum to form the new checksum.
        sum = ~sum & 0xFFFF;

        return sum;
    }

    public static String ipVersionToString(int ipVersion) {
        switch (ipVersion) {
        case IP_VERSION_4:
            return "IPv4";
        case IP_VERSION_6:
            return "IPv6";
        default:
            return Integer.toHexString(ipVersion).toUpperCase();
        }
    }

    public static String ipProtocolToString(int ipProtocol) {
        switch (ipProtocol) {
        case IP_PROTOCOL_ICMP:
            return "ICMP";
        case IP_PROTOCOL_TCP:
            return "TCP";
        case IP_PROTOCOL_UDP:
            return "UDP";
        case IP_PROTOCOL_ICMPV6:
            return "ICMPv6";
        default:
            return Integer.toHexString(ipProtocol).toUpperCase();
        }
    }

    public static String ipAddressToString(byte[] addr) {
        try {
            return InetAddress.getByAddress(addr).getHostAddress();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Not an IP address");
        }
    }

    public static final class IPHeader {
        public final int ipVersion;
        public final int protocol;
        public final byte[] sourceAddress;
        public final byte[] destAddress;
        public final int headerLength;
        public final int payloadLength;
        public final int checksum;

        public IPHeader(int ipVersion, int protocol, byte[] sourceAddress, byte[] destAddress, int headerLength,
                int payloadLength, int checksum) {
            this.ipVersion = ipVersion;
            this.protocol = protocol;
            this.sourceAddress = sourceAddress;
            this.destAddress = destAddress;
            this.headerLength = headerLength;
            this.payloadLength = payloadLength;
            this.checksum = checksum;
        }
    }

    public static IPHeader decodeIPHeader(ByteBuf packetBuf) {
        return decodeIPHeader(packetBuf, packetBuf.readerIndex());
    }

    public static IPHeader decodeIPHeader(ByteBuf packetBuf, int ipHeaderOffset) {

        // Fail if the packet is too small.
        if (packetBuf.readableBytes() < ipHeaderOffset + 1) {
            throw new IllegalArgumentException("IP packet too small");
        }

        int ipVersion;
        int protocol;
        byte[] sourceAddress;
        byte[] destAddress;
        int headerLength;
        int payloadLength;
        int checksum = 0;

        ipVersion = packetBuf.getUnsignedByte(ipHeaderOffset) & 0xF0;

        // If the packet is an IPv6 packet...
        if (ipVersion == IP_VERSION_6) {

            // Set the header length.
            headerLength = IPV6_HEADER_LENGTH;

            // Fail if the packet is too small to contain the IPv6 header.
            if (packetBuf.readableBytes() < ipHeaderOffset + IPV6_HEADER_LENGTH) {
                throw new IllegalArgumentException("IP packet too small");
            }

            // Get the IP payload length.
            payloadLength = packetBuf.getUnsignedShort(ipHeaderOffset + IPV6_HEADER_OFFSET_PAYLOAD_LENGTH);

            // Fail if the packet is too small, counting the payload.
            if (packetBuf.readableBytes() < ipHeaderOffset + IPV6_HEADER_LENGTH + payloadLength) {
                throw new IllegalArgumentException("IP packet too small");
            }

            // Get the protocol.
            protocol = packetBuf.getUnsignedByte(ipHeaderOffset + IPV6_HEADER_OFFSET_NEXT_HEADER);

            // Get the source and destination addresses.
            sourceAddress = new byte[16];
            destAddress = new byte[16];
            packetBuf.getBytes(ipHeaderOffset + InetUtils.IPV6_HEADER_OFFSET_SOURCE_ADDR, sourceAddress);
            packetBuf.getBytes(ipHeaderOffset + InetUtils.IPV6_HEADER_OFFSET_DEST_ADDR, destAddress);

        // Otherwise if the packet is an IPv6 packet...
        } else if (ipVersion == IP_VERSION_4) {

            // Get the length of the header.
            headerLength = (packetBuf.getUnsignedByte(ipHeaderOffset + IPV4_HEADER_OFFSET_HEADER_LENGTH) & 0x0F) * 4;

            // Fail if header length value is too small.
            if (headerLength < IPV4_MIN_HEADER_LENGTH) {
                throw new IllegalArgumentException("Invalid IPv4 header length");
            }

            // Fail if the packet is too small to contain the IPv4 header.
            if (packetBuf.readableBytes() < ipHeaderOffset + headerLength) {
                throw new IllegalArgumentException("IP packet too small");
            }

            // Get the total length of the packet.
            int totalLength = packetBuf.getUnsignedShort(ipHeaderOffset + IPV4_HEADER_OFFSET_TOTAL_LENGTH);

            // Fail if the packet is smaller than the total length.
            if (packetBuf.readableBytes() < ipHeaderOffset + totalLength) {
                throw new IllegalArgumentException("IP packet too small");
            }

            // Compute the payload length
            payloadLength = totalLength - headerLength;

            // Get the protocol.
            protocol = packetBuf.getUnsignedByte(ipHeaderOffset + IPV4_HEADER_OFFSET_PROTOCOL);

            // Get the source and destination addresses.
            sourceAddress = new byte[4];
            destAddress = new byte[4];
            packetBuf.getBytes(ipHeaderOffset + InetUtils.IPV4_HEADER_OFFSET_SOURCE_ADDR, sourceAddress);
            packetBuf.getBytes(ipHeaderOffset + InetUtils.IPV4_HEADER_OFFSET_DEST_ADDR, destAddress);

            // Get IP checksum.
            checksum = packetBuf.getUnsignedShort(ipHeaderOffset + IPV4_HEADER_OFFSET_CHECKSUM);

        // Otherwise the packet type is unknown.
        } else {
            throw new UnsupportedOperationException(
                    "IP version unsupported: " + ipVersionToString(ipVersion));
        }

        return new InetUtils.IPHeader(ipVersion, protocol, sourceAddress, destAddress, headerLength,
                payloadLength, checksum);
    }

    public static final class ICMPHeader {
        public final int type;
        public final int code;
        public final int checksum;

        public ICMPHeader(int type, int code, int checksum) {
            this.type = type;
            this.code = code;
            this.checksum = checksum;
        }
    }

    public static ICMPHeader decodeICMPHeader(ByteBuf packetBuf) {
        return decodeICMPHeader(packetBuf, packetBuf.readerIndex());
    }

    public static ICMPHeader decodeICMPHeader(ByteBuf packetBuf, int headerOffset) {
        // Fail if the packet is too small.
        if (packetBuf.readableBytes() < headerOffset + ICMP_HEADER_LENGTH) {
            throw new IllegalArgumentException("ICMP packet too small");
        }

        int type = packetBuf.getUnsignedByte(headerOffset + ICMP_HEADER_OFFSET_TYPE);
        int code = packetBuf.getUnsignedByte(headerOffset + ICMP_HEADER_OFFSET_CODE);
        int checksum = packetBuf.getUnsignedShort(headerOffset + ICMP_HEADER_OFFSET_CHECKSUM);

        return new InetUtils.ICMPHeader(type, code, checksum);
    }

    public static final class UDPHeader {
        public final int sourcePort;
        public final int destPort;
        public final int length;
        public final int checksum;

        public UDPHeader(int sourcePort, int destPort, int length, int checksum) {
            this.sourcePort = sourcePort;
            this.destPort = destPort;
            this.length = length;
            this.checksum = checksum;
        }
    }

    public static UDPHeader decodeUDPHeader(ByteBuf packetBuf, int headerOffset) {
        // Fail if the packet is too small.
        if (packetBuf.readableBytes() < headerOffset + UDP_HEADER_LENGTH) {
            throw new IllegalArgumentException("UDP packet too small");
        }

        int sourcePort = packetBuf.getUnsignedShort(headerOffset + UDP_HEADER_OFFSET_SOURCE_PORT);
        int destPort = packetBuf.getUnsignedShort(headerOffset + UDP_HEADER_OFFSET_DEST_PORT);
        int length = packetBuf.getUnsignedShort(headerOffset + UDP_HEADER_OFFSET_LENGTH);
        int checksum = packetBuf.getUnsignedShort(headerOffset + UDP_HEADER_OFFSET_CHECKSUM);

        return new InetUtils.UDPHeader(sourcePort, destPort, length, checksum);
    }

    public static final class TCPHeader {
        public final int sourcePort;
        public final int destPort;
        public final long sequenceNum;
        public final int flags;
        public final int dataLength;
        public final int checksum;

        public TCPHeader(int sourcePort, int destPort, long sequenceNum, int flags, int dataLength, int checksum) {
            this.sourcePort = sourcePort;
            this.destPort = destPort;
            this.sequenceNum = sequenceNum;
            this.flags = flags;
            this.dataLength = dataLength;
            this.checksum = checksum;
        }
    }

    public static TCPHeader decodeTCPHeader(ByteBuf packetBuf, int headerOffset) {
        // Fail if the packet is too small.
        if (packetBuf.readableBytes() < headerOffset + TCP_HEADER_LENGTH) {
            throw new IllegalArgumentException("TCP packet too small");
        }

        int sourcePort = packetBuf.getUnsignedShort(headerOffset + TCP_HEADER_OFFSET_SOURCE_PORT);
        int destPort = packetBuf.getUnsignedShort(headerOffset + TCP_HEADER_OFFSET_DEST_PORT);
        long sequenceNum = packetBuf.getUnsignedInt(headerOffset + TCP_HEADER_OFFSET_SEQUENCE_NUM);
        int dataOffsetAndFlags = packetBuf.getUnsignedShort(headerOffset + TCP_HEADER_OFFSET_DATA_OFFSET_AND_FLAGS);
        int flags = dataOffsetAndFlags & 0x1F;
        int dataOffset = (dataOffsetAndFlags & 0xF000) >> 12;
        int dataLength = packetBuf.readableBytes() - headerOffset - (dataOffset * 4);
        int checksum = packetBuf.getUnsignedShort(headerOffset + TCP_HEADER_OFFSET_CHECKSUM);

        return new InetUtils.TCPHeader(sourcePort, destPort, sequenceNum, flags, dataLength, checksum);
    }

    public static final int IP_VERSION_4                        = 0x40;
    public static final int IP_VERSION_6                        = 0x60;

    public static final int IP_PROTOCOL_TCP                     = 0x06;
    public static final int IP_PROTOCOL_UDP                     = 0x11;
    public static final int IP_PROTOCOL_ICMP                    = 0x01;
    public static final int IP_PROTOCOL_ICMPV6                  = 0x3A;

    public static final int IPV4_MIN_HEADER_LENGTH              = 20;
    public static final int IPV4_HEADER_OFFSET_VERSION          = 0;
    public static final int IPV4_HEADER_OFFSET_HEADER_LENGTH    = 0;
    public static final int IPV4_HEADER_OFFSET_TOTAL_LENGTH     = 2;
    public static final int IPV4_HEADER_OFFSET_PROTOCOL         = 9;
    public static final int IPV4_HEADER_OFFSET_CHECKSUM         = 10;
    public static final int IPV4_HEADER_OFFSET_SOURCE_ADDR      = 12;
    public static final int IPV4_HEADER_OFFSET_DEST_ADDR        = 16;

    public static final int IPV6_HEADER_LENGTH                  = 40;
    public static final int IPV6_HEADER_OFFSET_VERSION          = 0;
    public static final int IPV6_HEADER_OFFSET_PAYLOAD_LENGTH   = 4;
    public static final int IPV6_HEADER_OFFSET_NEXT_HEADER      = 6;
    public static final int IPV6_HEADER_OFFSET_SOURCE_ADDR      = 8;
    public static final int IPV6_HEADER_OFFSET_DEST_ADDR        = 24;

    public static final int TCP_HEADER_LENGTH                   = 20;
    public static final int TCP_HEADER_OFFSET_SOURCE_PORT       = 0;
    public static final int TCP_HEADER_OFFSET_DEST_PORT         = 2;
    public static final int TCP_HEADER_OFFSET_SEQUENCE_NUM      = 4;
    public static final int TCP_HEADER_OFFSET_DATA_OFFSET_AND_FLAGS = 12;
    public static final int TCP_HEADER_OFFSET_CHECKSUM          = 16;

    public static final int UDP_HEADER_LENGTH                   = 8;
    public static final int UDP_HEADER_OFFSET_SOURCE_PORT       = 0;
    public static final int UDP_HEADER_OFFSET_DEST_PORT         = 2;
    public static final int UDP_HEADER_OFFSET_LENGTH            = 4;
    public static final int UDP_HEADER_OFFSET_CHECKSUM          = 6;

    public static final int ICMP_HEADER_LENGTH                  = 8;
    public static final int ICMP_HEADER_OFFSET_TYPE             = 0;
    public static final int ICMP_HEADER_OFFSET_CODE             = 1;
    public static final int ICMP_HEADER_OFFSET_CHECKSUM         = 2;
    public static final int ICMP_HEADER_REST_OF_HEADER          = 4;

    public static final int ICMPV6_HEADER_LENGTH                = 4;
    public static final int ICMPV6_HEADER_OFFSET_TYPE           = 0;
    public static final int ICMPV6_HEADER_OFFSET_CODE           = 1;
    public static final int ICMPV6_HEADER_OFFSET_CHECKSUM       = 2;

    public static final int TCP_FLAG_URG                        = 0x20;
    public static final int TCP_FLAG_ACK                        = 0x10;
    public static final int TCP_FLAG_PSH                        = 0x08;
    public static final int TCP_FLAG_RST                        = 0x04;
    public static final int TCP_FLAG_SYN                        = 0x02;
    public static final int TCP_FLAG_FIN                        = 0x01;

    public static final int ICMP_MESSAGE_TYPE_ECHO_REQUEST      = 8;
    public static final int ICMP_MESSAGE_TYPE_ECHO_REPLY        = 0;

    public static final int ICMPV6_MESSAGE_TYPE_ECHO_REQUEST    = 128;
    public static final int ICMPV6_MESSAGE_TYPE_ECHO_REPLY      = 129;

    private InetUtils() {
        // Not accessible.
    }
}
