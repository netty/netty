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

final class EthernetPacket {

    /**
     * AA:BB:CC:DD:EE:FF
     */
    private static final byte[] DUMMY_ADDRESS = new byte[]{-86, -69, -52, -35, -18, -1};

    private EthernetPacket() {
        // Prevent outside initialization
    }

    /**
     * Write IPv4 Ethernet Packet. It uses a dummy MAC address for both source and destination.
     *
     * @param byteBuf    ByteBuf where Ethernet Packet data will be set
     * @param payload    Payload of IPv4
     */
    static void writeIPv4(ByteBuf byteBuf, ByteBuf payload) {
        EthernetPacket.writePacket(byteBuf, payload, DUMMY_ADDRESS, DUMMY_ADDRESS, 0x0800);
    }

    /**
     * Write IPv6 Ethernet Packet. It uses a dummy MAC address for both source and destination.
     *
     * @param byteBuf    ByteBuf where Ethernet Packet data will be set
     * @param payload    Payload of IPv6
     */
    static void writeIPv6(ByteBuf byteBuf, ByteBuf payload) {
        EthernetPacket.writePacket(byteBuf, payload, DUMMY_ADDRESS, DUMMY_ADDRESS, 0x86dd);
    }

    /**
     * Write IPv6 Ethernet Packet
     *
     * @param byteBuf    ByteBuf where Ethernet Packet data will be set
     * @param payload    Payload of IPv6
     * @param srcAddress Source MAC Address
     * @param dstAddress Destination MAC Address
     * @param type Type of Frame
     */
    private static void writePacket(ByteBuf byteBuf, ByteBuf payload, byte[] srcAddress, byte[] dstAddress,
                                    int type) {
        byteBuf.writeBytes(dstAddress); // Destination MAC Address
        byteBuf.writeBytes(srcAddress); // Source MAC Address
        byteBuf.writeShort(type);       // Frame Type (IPv4 or IPv6)
        byteBuf.writeBytes(payload);    // Payload of L3
    }
}
