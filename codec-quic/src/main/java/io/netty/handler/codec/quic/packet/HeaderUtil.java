/*
 *
 *  * Copyright 2019 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License,
 *  * version 2.0 (the "License"); you may not use this file except in compliance
 *  * with the License. You may obtain a copy of the License at:
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *
 */

package io.netty.handler.codec.quic.packet;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.tls.Cryptor;

public final class HeaderUtil {

    private HeaderUtil() {}

    public static byte writeLongDataHeader(ByteBuf buf, LongPacket packet) {
        return writeLongHeader(buf, packet, 4 - 1);//TODO packet number length is not always 4
    }

    public static byte writeLongHeader(ByteBuf buf, LongPacket packet, int magic) {
        byte firstByte = writeLongHeaderFirstByte(buf, packet, magic);
        packet.version().write(buf);
        writeConnectionIDInfo(buf, packet.connectionID(), packet.sourceConnectionID());
        return firstByte;
    }

    public static byte writeLongDataHeaderFirstByte(ByteBuf buf, LongPacket packet){
        return writeLongHeaderFirstByte(buf, packet, 4 - 1); //TODO packet number length is not always 4
    }

    public static byte writeLongHeaderFirstByte(ByteBuf buf, LongPacket packet, int magic) {
        int firstByte = packet.packetType().getFirstByte();
        firstByte |= magic;
        buf.writeByte(firstByte);
        return (byte) firstByte;
    }

    public static byte[] read(ByteBuf buf, int length) {
        byte[] binary = new byte[length];
        buf.readBytes(binary);
        return binary;
    }

    public static void writeLongPacketContent(ByteBuf buf, byte[] pn, int offset, Cryptor cryptor, byte[] frames, byte firstByte) {
        int pnOffset = buf.writerIndex();
        int sampleOffset = pnOffset + 4;
        buf.writeBytes(pn);

        byte[] current = new byte[buf.writerIndex() - offset];
        buf.getBytes(offset, current);
        buf.writeBytes(frames);
        final byte[] sample = new byte[16];
        buf.getBytes(sampleOffset, sample);

        byte[] rawHeader = new byte[4 + 1];
        rawHeader[0] = firstByte;
        System.arraycopy(pn, 0, rawHeader, 1, pn.length);

        byte[] encryptedHeader = cryptor.encryptHeader(sample, rawHeader, true);
        buf.setByte(offset, encryptedHeader[0]);
        buf.setBytes(pnOffset, encryptedHeader, 1, encryptedHeader.length - 1);
    }

    public static void writeConnectionIDInfo(ByteBuf buf, byte[] destination, byte[] source) {
        int destLength = destination == null ? 0 : destination.length - 3;
        int sourceLength = source == null ? 0 : source.length - 3;

        buf.writeByte((destLength << 4 | sourceLength) & 0xFF);
        if (destination != null) {
            buf.writeBytes(destination);
        }
        if (source != null) {
            buf.writeBytes(source);
        }
    }

    public static byte[][] readConnectionIDInfo(ByteBuf buf) {
        byte[][] connectionIDS = new byte[2][];
        int cil = buf.readByte() & 0xFF;
        int firstLength = ((cil & 0xf0) >> 4);
        int lastLength = ((cil & 0xf));
        if (firstLength > 0) {
            connectionIDS[0] = read(buf, firstLength + 3);
        }
        if (lastLength > 0) {
            connectionIDS[1] = read(buf, lastLength + 3);
        }
        return connectionIDS;
    }

}
