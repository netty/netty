/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;

final class IPPacket {

    private static final byte MAX_TTL = (byte) 255;
    private static final short V4_HEADER_SIZE = 20;
    private static final byte TCP = 6 & 0xff;
    private static final byte UDP = 17 & 0xff;

    /**
     * Version + Traffic class + Flow label
     */
    private static final int IPV6_VERSION_TRAFFIC_FLOW = 60000000;

    private IPPacket() {
        // Prevent outside initialization
    }

    /**
     * Write IPv4 Packet for UDP Packet
     *
     * @param byteBuf    ByteBuf where IP Packet data will be set
     * @param payload    Payload of UDP
     * @param srcAddress Source IPv4 Address
     * @param dstAddress Destination IPv4 Address
     */
    static void writeUDPv4(ByteBuf byteBuf, ByteBuf payload, int srcAddress, int dstAddress) {
        writePacketv4(byteBuf, payload, UDP, srcAddress, dstAddress);
    }

    /**
     * Write IPv6 Packet for UDP Packet
     *
     * @param byteBuf    ByteBuf where IP Packet data will be set
     * @param payload    Payload of UDP
     * @param srcAddress Source IPv6 Address
     * @param dstAddress Destination IPv6 Address
     */
    static void writeUDPv6(ByteBuf byteBuf, ByteBuf payload, byte[] srcAddress, byte[] dstAddress) {
        writePacketv6(byteBuf, payload, UDP, srcAddress, dstAddress);
    }

    /**
     * Write IPv4 Packet for TCP Packet
     *
     * @param byteBuf    ByteBuf where IP Packet data will be set
     * @param payload    Payload of TCP
     * @param srcAddress Source IPv4 Address
     * @param dstAddress Destination IPv4 Address
     */
    static void writeTCPv4(ByteBuf byteBuf, ByteBuf payload, int srcAddress, int dstAddress) {
        writePacketv4(byteBuf, payload, TCP, srcAddress, dstAddress);
    }

    /**
     * Write IPv6 Packet for TCP Packet
     *
     * @param byteBuf    ByteBuf where IP Packet data will be set
     * @param payload    Payload of TCP
     * @param srcAddress Source IPv6 Address
     * @param dstAddress Destination IPv6 Address
     */
    static void writeTCPv6(ByteBuf byteBuf, ByteBuf payload, byte[] srcAddress, byte[] dstAddress) {
        writePacketv6(byteBuf, payload, TCP, srcAddress, dstAddress);
    }

    private static void writePacketv4(ByteBuf byteBuf, ByteBuf payload, int protocol, int srcAddress,
                                      int dstAddress) {

        byteBuf.writeByte(0x45);      //  Version + IHL
        byteBuf.writeByte(0x00);      //  DSCP
        byteBuf.writeShort(V4_HEADER_SIZE + payload.readableBytes()); // Length
        byteBuf.writeShort(0x0000);   // Identification
        byteBuf.writeShort(0x0000);   // Fragment
        byteBuf.writeByte(MAX_TTL);   // TTL
        byteBuf.writeByte(protocol);  // Protocol
        byteBuf.writeShort(0);        // Checksum
        byteBuf.writeInt(srcAddress); // Source IPv4 Address
        byteBuf.writeInt(dstAddress); // Destination IPv4 Address
        byteBuf.writeBytes(payload);  // Payload of L4
    }

    private static void writePacketv6(ByteBuf byteBuf, ByteBuf payload, int protocol, byte[] srcAddress,
                                      byte[] dstAddress) {

        byteBuf.writeInt(IPV6_VERSION_TRAFFIC_FLOW); // Version  + Traffic class + Flow label
        byteBuf.writeShort(payload.readableBytes()); // Payload length
        byteBuf.writeByte(protocol & 0xff); // Next header
        byteBuf.writeByte(MAX_TTL);         // Hop limit
        byteBuf.writeBytes(srcAddress);     // Source IPv6 Address
        byteBuf.writeBytes(dstAddress);     // Destination IPv6 Address
        byteBuf.writeBytes(payload);        // Payload of L4
    }
}
