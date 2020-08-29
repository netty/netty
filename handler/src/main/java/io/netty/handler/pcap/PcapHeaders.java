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

import java.util.concurrent.TimeUnit;

final class PcapHeaders {

    private PcapHeaders() {
        // Prevent outside initialization
    }

    /**
     * Write Pcap Global Header
     * @param byteBuf byteBuf ByteBuf where we'll write header data
     */
    static void writeGlobalHeader(ByteBuf byteBuf) {
        byteBuf.writeInt(0xa1b2c3d4); // magic_number
        byteBuf.writeShort(2);        // version_major
        byteBuf.writeShort(4);        // version_minor
        byteBuf.writeInt(0);          // thiszone
        byteBuf.writeInt(0);          // sigfigs
        byteBuf.writeInt(0xffff);     // snaplen
        byteBuf.writeInt(1);          // network
    }

    /**
     * Write Pcap Packet Header
     *
     * @param byteBuf ByteBuf where we'll write header data
     * @param ts_sec   timestamp seconds
     * @param ts_usec  timestamp microseconds
     * @param incl_len number of octets of packet saved in file
     * @param orig_len actual length of packet
     */
    static void writePacketHeader(ByteBuf byteBuf, long ts_sec, int ts_usec, int incl_len, int orig_len) {
        byteBuf.writeInt((int) TimeUnit.MILLISECONDS.toSeconds(ts_sec));
        byteBuf.writeInt(ts_usec);
        byteBuf.writeInt(incl_len);
        byteBuf.writeInt(orig_len);
    }
}
