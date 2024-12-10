/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;

final class TCPPacket {

    /**
     * Data Offset + Reserved Bits.
     */
    private static final short OFFSET = 0x5000;

    private TCPPacket() {
        // Prevent outside initialization
    }

    /**
     * Write TCP Packet
     *
     * @param byteBuf ByteBuf where Packet data will be set
     * @param payload Payload of this Packet
     * @param srcPort Source Port
     * @param dstPort Destination Port
     */
    static void writePacket(ByteBuf byteBuf, ByteBuf payload, long segmentNumber, long ackNumber, int srcPort,
                            int dstPort, TCPFlag... tcpFlags) {

        byteBuf.writeShort(srcPort);     // Source Port
        byteBuf.writeShort(dstPort);     // Destination Port
        byteBuf.writeInt((int) segmentNumber); // Segment Number
        byteBuf.writeInt((int) ackNumber);     // Acknowledgment Number
        byteBuf.writeShort(OFFSET | TCPFlag.getFlag(tcpFlags)); // Flags
        byteBuf.writeShort(65535);       // Window Size
        byteBuf.writeShort(0x0001);      // Checksum
        byteBuf.writeShort(0);           // Urgent Pointer

        if (payload != null) {
            byteBuf.writeBytes(payload); //  Payload of Data
        }
    }

    enum TCPFlag {
        FIN(1),
        SYN(1 << 1),
        RST(1 << 2),
        PSH(1 << 3),
        ACK(1 << 4),
        URG(1 << 5),
        ECE(1 << 6),
        CWR(1 << 7);

        private final int value;

        TCPFlag(int value) {
            this.value = value;
        }

        static int getFlag(TCPFlag... tcpFlags) {
            int flags = 0;

            for (TCPFlag tcpFlag : tcpFlags) {
                flags |= tcpFlag.value;
            }

            return  flags;
        }
    }
}
