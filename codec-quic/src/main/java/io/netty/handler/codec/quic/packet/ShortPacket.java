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
import io.netty.handler.codec.quic.frame.QuicFrame;
import io.netty.handler.codec.quic.tls.Cryptor;

import java.security.GeneralSecurityException;
import java.util.List;

public class ShortPacket extends DataPacket {

    protected boolean keyPhase;

    ShortPacket() {}

    public ShortPacket(long packetNumber, boolean keyPhase, List<QuicFrame> frames) {
        super(packetNumber, frames);
    }

    public ShortPacket(long packetNumber, boolean keyPhase, QuicFrame... frames) {
        super(packetNumber, frames);
    }

    @Override
    public void read(ByteBuf buf) {
        keyPhase = (firstByte & 0x4) == 0x4;

        Cryptor cryptor = Cryptor.ONE_RTT;
        int offset = buf.readerIndex() - 1;
        connectionID = HeaderUtil.read(buf, 16);

        int pnOffset = buf.readerIndex();
        byte[] header = cryptor.decryptHeader(readSample(buf), firstByte, readPN(buf, pnOffset), true);
        processData(buf, header, pnOffset, offset, cryptor);
    }

    @Override
    public void write(ByteBuf buf) {
        Cryptor cryptor = Cryptor.ONE_RTT;
        int offset = buf.writerIndex();
        firstByte = 0x40;
        if (keyPhase) {
            firstByte |= 0x4;
        }
        firstByte |= packetNumber;
        firstByte |= 4 - 1; //TODO size can be compressed
        buf.writeByte(firstByte);
        buf.writeBytes(connectionID);

        byte[] pn = getPN(buf);
        HeaderUtil.writeLongPacketContent(buf, pn, offset, cryptor, framesEncoded(), firstByte);
    }

    public boolean keyPhase() {
        return keyPhase;
    }

    public void keyPhase(boolean keyPhase) {
        this.keyPhase = keyPhase;
    }
}
