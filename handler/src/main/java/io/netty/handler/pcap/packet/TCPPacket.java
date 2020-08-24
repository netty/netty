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

public final class TCPPacket {
    /**
     * Create TCP Packet
     *
     * @param byteBuf ByteBuf where Packet data will be set
     * @param payload Payload of this Packet
     * @param srcPort Source Port
     * @param dstPort Destination Port
     */
    public static ByteBuf createPacket(ByteBuf byteBuf, ByteBuf payload, int srcPort, int dstPort) {
        byteBuf.writeShort(dstPort); // Destination Port
        byteBuf.writeShort(srcPort); // Source Port
        byteBuf.writeInt(0);         // Sequence Number
        byteBuf.writeInt(0);         // Acknowledgment Number
        byteBuf.writeShort(5 << 12); // Flags
        byteBuf.writeShort(65535);   // Window Size
        byteBuf.writeShort(0x0001);  // Checksum
        byteBuf.writeShort(0);       // Urgent Pointer
        byteBuf.writeBytes(payload); //  Payload of Data
        return byteBuf;
    }
}
