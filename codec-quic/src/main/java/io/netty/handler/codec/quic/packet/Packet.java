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

public abstract class Packet implements IPacket {

    public static IPacket readPacket(ByteBuf buf) {
        buf.markReaderIndex();
        byte firstType = buf.readByte();
        if ((0x80 & (firstType & 0xFF)) == 0x80) {
            return FullPacketType.readLongPacket(buf, firstType);
        }
        buf.resetReaderIndex();
        ShortPacket packet = new ShortPacket();
        packet.firstByte(firstType);
        packet.read(buf);
        return packet;
    }

    protected byte[] connectionID;
    protected byte firstByte;

    protected byte[] drain(ByteBuf buf) {
        byte[] array = new byte[buf.readableBytes()];
        buf.readBytes(array);
        return array;
    }

    @Override
    public byte[] connectionID() {
        return connectionID;
    }

    @Override
    public void connectionID(byte[] connectionID) {
        this.connectionID = connectionID;
    }

    @Override
    public byte firstByte() {
        return firstByte;
    }

    @Override
    public void firstByte(byte firstByte) {
        this.firstByte = firstByte;
    }
}
