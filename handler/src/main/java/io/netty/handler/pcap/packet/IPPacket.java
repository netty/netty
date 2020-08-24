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
package io.netty.handler.pcap.packet;

import io.netty.buffer.ByteBuf;

public final class IPPacket {

    /**
     * Create IPv4 Packet for UDP Packet
     *
     * @param byteBuf    ByteBuf where IP Packet data will be set
     * @param payload    Payload of UDP
     * @param srcAddress Source IPv4 Address
     * @param dstAddress Destination IPv4 Address
     */
    public static ByteBuf createUDPv4(ByteBuf byteBuf, ByteBuf payload, int srcAddress, int dstAddress) {
        return createPacketV4(byteBuf, payload, 17, srcAddress, dstAddress);
    }

    /**
     * Create IPv6 Packet for UDP Packet
     *
     * @param byteBuf    ByteBuf where IP Packet data will be set
     * @param payload    Payload of UDP
     * @param srcAddress Source IPv6 Address
     * @param dstAddress Destination IPv6 Address
     */
    public static ByteBuf createUDPv6(ByteBuf byteBuf, ByteBuf payload, byte[] srcAddress, byte[] dstAddress) {
        return createPacketV6(byteBuf, payload, 17, srcAddress, dstAddress);
    }

    /**
     * Create IPv4 Packet for TCP Packet
     *
     * @param byteBuf    ByteBuf where IP Packet data will be set
     * @param payload    Payload of TCP
     * @param srcAddress Source IPv4 Address
     * @param dstAddress Destination IPv4 Address
     */
    public static ByteBuf createTCP4(ByteBuf byteBuf, ByteBuf payload, int srcAddress, int dstAddress) {
        return createPacketV4(byteBuf, payload, 6, srcAddress, dstAddress);
    }

    /**
     * Create IPv6 Packet for TCP Packet
     *
     * @param byteBuf    ByteBuf where IP Packet data will be set
     * @param payload    Payload of TCP
     * @param srcAddress Source IPv6 Address
     * @param dstAddress Destination IPv6 Address
     */
    public static ByteBuf createTCP6(ByteBuf byteBuf, ByteBuf payload, byte[] srcAddress, byte[] dstAddress) {
        return createPacketV6(byteBuf, payload, 6, srcAddress, dstAddress);
    }

    private static ByteBuf createPacketV4(ByteBuf byteBuf, ByteBuf payload, int protocol, int srcAddress,
                                          int dstAddress) {
        byteBuf.writeByte(0x45);      //  Version + IHL
        byteBuf.writeByte(0x00);      //  DSCP
        byteBuf.writeShort(payload.readableBytes() + 20); // Length
        byteBuf.writeShort(0x0000);   // Identification
        byteBuf.writeShort(0x0000);   // Fragment
        byteBuf.writeByte(0xff);      // TTL
        byteBuf.writeByte(protocol);  // Protocol
        byteBuf.writeShort(0);        // Checksum
        byteBuf.writeInt(srcAddress); // Source IPv4 Address
        byteBuf.writeInt(dstAddress); // Destination IPv4 Address
        byteBuf.writeBytes(payload);  // Payload of L4
        return byteBuf;
    }

    private static ByteBuf createPacketV6(ByteBuf byteBuf, ByteBuf payload, int protocol, byte[] srcAddress,
                                          byte[] dstAddress) {
        byteBuf.writeInt(6 << 28);          // Version  + Traffic class + Flow label
        byteBuf.writeShort(payload.readableBytes()); // Payload length
        byteBuf.writeByte(protocol & 0xff); // Next header
        byteBuf.writeByte(255);             // Hop limit
        byteBuf.writeBytes(srcAddress);     // Source IPv6 Address
        byteBuf.writeBytes(dstAddress);     // Destination IPv6 Address
        byteBuf.writeBytes(payload);        // Payload of L4
        return byteBuf;
    }
}
