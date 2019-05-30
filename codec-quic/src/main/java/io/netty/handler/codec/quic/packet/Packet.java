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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.quic.frame.FrameType;
import io.netty.handler.codec.quic.frame.QuicFrame;
import io.netty.handler.codec.quic.tls.Cryptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class Packet {

    private static final HashMap<Byte, PacketType> TYPRES = new HashMap<Byte, PacketType>(PacketType.values().length, 1);

    static {
        for (PacketType packetType : PacketType.values()) {
            TYPRES.put(packetType.id(), packetType);
        }
    }

    public static Packet readPacket(ByteBuf buf) {
        buf.markReaderIndex();
        int rawType = buf.readByte() & 0xFF;
        if ((0x80 & rawType) == 0x80) {
            PacketType type = TYPRES.get((byte) ((rawType & 0x30) >> 4));
            Packet packet = type.constructPacket();
            packet.read(buf);
            return packet;
        }
        buf.resetReaderIndex();
        ShortPacket packet = new ShortPacket();
        packet.read(buf);
        return packet;
    }

    protected PacketType type;
    protected byte[] connectionID;


    protected void readConnectionID(ByteBuf buf) {
        connectionID = new byte[18];
        buf.readBytes(connectionID);
    }

    protected byte[] readSample(ByteBuf buf) {
        final int sampleOffset = buf.readerIndex() + 4;
        final byte[] sample = new byte[16];
        buf.getBytes(sampleOffset, sample);
        return sample;
    }

    protected byte[] readPN(ByteBuf buf, int pnOffset) {
        byte[] pn = new byte[4];
        buf.getBytes(pnOffset, pn);
        return pn;
    }

    public static byte[] drain(ByteBuf buf) {
        byte[] array = new byte[buf.readableBytes()];
        buf.readBytes(array);
        return array;
    }

    public abstract void read(ByteBuf buf);

    public void write(ByteBuf buf) {
        buf.writeByte(type.id());
    }

}
