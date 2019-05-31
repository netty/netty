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
import io.netty.handler.codec.quic.Version;

import java.util.HashMap;

public enum FullPacketType {

    INIT(0x0) {
        @Override
        public LongPacket constructPacket() {
            return new InitialPacket();
        }
    },
    RTT_PROTECTED(0x01) {
        //TODO
        @Override
        public LongPacket constructPacket() {
            return null;
        }
    },
    HANDSHAKE(0x02) {
        @Override
        public LongPacket constructPacket() {
            return new HandshakePacket();
        }
    },
    RETRY(0x03) {
        @Override
        public LongPacket constructPacket() {
            return new RetryPacket();
        }
    };

    private byte id;

    public byte id() {
        return id;
    }

    public abstract LongPacket constructPacket();

    FullPacketType(int id) {
        this.id = (byte) id;
    }

    private static final HashMap<Byte, FullPacketType> TYPES = new HashMap<Byte, FullPacketType>(FullPacketType.values().length, 1);

    static {
        for (FullPacketType packetType : FullPacketType.values()) {
            TYPES.put(packetType.id(), packetType);
        }
    }

    public static LongPacket readLongPacket(ByteBuf buf, byte first) {
        int rawType = first & 0xFF;
        FullPacketType type = TYPES.get((byte) ((rawType & 0x30) >> 4));
        LongPacket packet = type.constructPacket();
        packet.firstByte(first);
        packet.version(Version.readVersion(buf));
        packet.read(buf);
        return packet;
    }
}
