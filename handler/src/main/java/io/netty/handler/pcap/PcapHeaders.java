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

final class PcapHeaders {

    /**
     * Pcap Global Header built from:
     * <ol>
     *      <li> magic_number </li>
     *      <li> version_major </li>
     *      <li> version_minor </li>
     *      <li> thiszone </li>
     *      <li> sigfigs </li>
     *      <li> snaplen </li>
     *      <li> network </li>
     * </ol>
     */
    private static final byte[] GLOBAL_HEADER = new byte[]{-95, -78, -61, -44, 0, 2, 0, 4, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, -1, -1, 0, 0, 0, 1};

    private PcapHeaders() {
        // Prevent outside initialization
    }

    /**
     * Write Pcap Global Header
     *
     * @param byteBuf byteBuf ByteBuf where we'll write header data
     */
    public static void writeGlobalHeader(ByteBuf byteBuf) {
        byteBuf.writeBytes(GLOBAL_HEADER);
    }

    /**
     * Write Pcap Packet Header
     *
     * @param byteBuf  ByteBuf where we'll write header data
     * @param ts_sec   timestamp seconds
     * @param ts_usec  timestamp microseconds
     * @param incl_len number of octets of packet saved in file
     * @param orig_len actual length of packet
     */
    static void writePacketHeader(ByteBuf byteBuf, int ts_sec, int ts_usec, int incl_len, int orig_len) {
        byteBuf.writeInt(ts_sec);
        byteBuf.writeInt(ts_usec);
        byteBuf.writeInt(incl_len);
        byteBuf.writeInt(orig_len);
    }
}
