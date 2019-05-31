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
import io.netty.handler.codec.quic.VarInt;
import io.netty.handler.codec.quic.Version;
import io.netty.handler.codec.quic.frame.QuicFrame;
import io.netty.handler.codec.quic.tls.Cryptor;

import java.util.Arrays;
import java.util.List;

public class HandshakePacket extends DataPacket implements LongPacket {

    protected Version version;
    protected byte[] sourceConnectionID;

    HandshakePacket() {}

    public HandshakePacket(long packetNumber, Version version, byte[] sourceConnectionID, List<QuicFrame> frames) {
        super(packetNumber, frames);
        this.version = version;
        this.sourceConnectionID = sourceConnectionID;
    }

    public HandshakePacket(long packetNumber, Version version, byte[] sourceConnectionID, QuicFrame... frames) {
        super(packetNumber, frames);
        this.version = version;
        this.sourceConnectionID = sourceConnectionID;
    }

    @Override
    public FullPacketType packetType() {
        return FullPacketType.HANDSHAKE;
    }

    @Override
    public byte[] sourceConnectionID() {
        return sourceConnectionID;
    }

    @Override
    public void sourceConnectionID(byte[] sourceConnectionID) {
        this.sourceConnectionID = sourceConnectionID;
    }

    @Override
    public Version version() {
        return version;
    }

    @Override
    public void version(Version version) {
        this.version = version;
    }

    @Override
    public void read(ByteBuf buf) {
        Cryptor cryptor = Cryptor.HANDSHAKE;
        int offset = buf.readerIndex() - 1;

        byte[][] connectionInfo = HeaderUtil.readConnectionIDInfo(buf);
        connectionID = connectionInfo[0];
        sourceConnectionID = connectionInfo[1];

        int pnOffset = buf.readerIndex();
        byte[] header = cryptor.decryptHeader(readSample(buf), firstByte, readPN(buf, pnOffset), false);
        processData(buf, header, pnOffset, offset, cryptor);
    }

    @Override
    public void write(ByteBuf buf) {
        int offset = buf.writerIndex();
        firstByte = HeaderUtil.writeLongDataHeader(buf, this);

        byte[] pn = getPN(buf);
        byte[] frames = framesEncoded();
        VarInt.byLong(frames.length + pn.length).write(buf);

        HeaderUtil.writeLongPacketContent(buf, pn, offset, Cryptor.HANDSHAKE, frames, firstByte);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        HandshakePacket that = (HandshakePacket) o;

        if (version != that.version) return false;
        return Arrays.equals(sourceConnectionID, that.sourceConnectionID);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(sourceConnectionID);
        return result;
    }
}
