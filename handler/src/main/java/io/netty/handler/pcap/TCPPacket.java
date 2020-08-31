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

final class TCPPacket {

    /**
     * <p> Data Offset + Reserved Bits. </p>
     *
     * Equivalent to: {@code 5 << 12}
     */
    private static final short OFFSET = 20480;

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
    static void writePacket(ByteBuf byteBuf, ByteBuf payload, int segmentNumber, int ackNumber, int srcPort,
                            int dstPort, TCPFlag... tcpFlags) {

        byteBuf.writeShort(srcPort);     // Source Port
        byteBuf.writeShort(dstPort);     // Destination Port
        byteBuf.writeInt(segmentNumber); // Segment Number
        byteBuf.writeInt(ackNumber);     // Acknowledgment Number
        byteBuf.writeShort(OFFSET | TCPFlag.getFlag(tcpFlags)); // Flags
        byteBuf.writeShort(65535);       // Window Size
        byteBuf.writeShort(0x0001);      // Checksum
        byteBuf.writeShort(0);           // Urgent Pointer

        if (payload != null) {
            byteBuf.writeBytes(payload); //  Payload of Data
        }
    }

    enum TCPFlag {
        FIN,
        SYN,
        RST,
        PSH,
        ACK,
        URG,
        ECE,
        CWR;

        static int getFlag(TCPFlag... tcpFlags) {
            int fin = 0;
            int syn = 0;
            int rst = 0;
            int psh = 0;
            int ack = 0;
            int urg = 0;
            int ece = 0;
            int cwr = 0;

            for (TCPFlag tcpFlag : tcpFlags) {
                if (tcpFlag == TCPFlag.FIN) {
                    fin = 1;
                } else if (tcpFlag == TCPFlag.SYN) {
                    syn = 1;
                } else if (tcpFlag == TCPFlag.RST) {
                    rst = 1;
                } else if (tcpFlag == TCPFlag.PSH) {
                    psh = 1;
                } else if (tcpFlag == TCPFlag.ACK) {
                    ack = 1;
                } else if (tcpFlag == TCPFlag.URG) {
                    urg = 1;
                } else if (tcpFlag == TCPFlag.ECE) {
                    ece = 1;
                } else if (tcpFlag == TCPFlag.CWR) {
                    cwr = 1;
                }
            }

            return  fin << 0 |
                    syn << 1 |
                    rst << 2 |
                    psh << 3 |
                    ack << 4 |
                    urg << 5 |
                    ece << 6 |
                    cwr << 7;
        }
    }
}
