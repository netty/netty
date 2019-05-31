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

import java.util.List;

public class InitialPacket extends DataPacket implements LongPacket {

    protected Version version;
    protected byte[] sourceConnectionID;
    protected byte[] token;

    InitialPacket() {}

    public InitialPacket(long packetNumber, Version version, byte[] sourceConnectionID, byte[] token, List<QuicFrame> frames) {
        super(packetNumber, frames);
        this.version = version;
        this.sourceConnectionID = sourceConnectionID;
        this.token = token;
    }

    public InitialPacket(long packetNumber, Version version, byte[] sourceConnectionID, byte[] token, QuicFrame... frames) {
        super(packetNumber, frames);
        this.version = version;
        this.sourceConnectionID = sourceConnectionID;
        this.token = token;
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
    public FullPacketType packetType() {
        return FullPacketType.INIT;
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
    public void read(ByteBuf buf) {
        Cryptor cryptor = Cryptor.INITIAL;
        int offset = buf.readerIndex() - 1;

        byte[][] connectionInfo = HeaderUtil.readConnectionIDInfo(buf);
        connectionID = connectionInfo[0];
        sourceConnectionID = connectionInfo[1];

        token = HeaderUtil.read(buf, VarInt.read(buf).asInt());
        if (token.length == 0) {
            token = null;
        }

        int pnOffset = buf.readerIndex();
        byte[] header = cryptor.decryptHeader(readSample(buf), firstByte, readPN(buf, pnOffset), false);
        processData(buf, header, pnOffset, offset, cryptor);
    }

    @Override
    public void write(ByteBuf buf) {

    }

    public byte[] token() {
        return token;
    }

    public void token(byte[] token) {
        this.token = token;
    }
}
