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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShortPacket extends Packet implements DataPacket {

    protected boolean keyPhase;
    protected long packetNumber;
    protected List<QuicFrame> frames = new ArrayList<QuicFrame>();

    @Override
    public void read(ByteBuf buf) {
        int offset = buf.readerIndex();
        byte firstByte = buf.readByte();
        keyPhase = (firstByte & 0x4) == 0x4;
        readConnectionID(buf);

        int pnOffset = buf.readerIndex();
        byte[] header = Cryptor.ONE_RTT.decryptHeader(readSample(buf), firstByte, readPN(buf, pnOffset), true);
        byte firstHeaderByte = header[0];
        int length = (firstHeaderByte & 0x3) + 1;

        byte[] pn = Arrays.copyOfRange(header, 1, 1 + length);
        readPacketNumber(pn);
        buf.readerIndex(buf.readerIndex() + length);

        byte[] content = new byte[buf.readerIndex() - offset];
        buf.getBytes(offset, content);

        /* replace old header */
        content[0] = firstHeaderByte;
        System.arraycopy(pn, 0, content, pnOffset - offset, pn.length);

        QuicFrame.readFrames(this, buf, content, Cryptor.ONE_RTT);
    }

    protected void readPacketNumber(byte[] buf) {
        byte[] pad = new byte[4 - buf.length];

        int length = pad.length + buf.length;
        byte[] bs = new byte[length];
        System.arraycopy(pad, 0, bs, 0, pad.length);
        System.arraycopy(buf, 0, bs, pad.length, buf.length);

        packetNumber = bs[0] << 24 | (bs[1] & 255) << 16 | (bs[2] & 255) << 8 | bs[3] & 255;
    }

    @Override
    public long packetNumber() {
        return packetNumber;
    }

    @Override
    public List<QuicFrame> frames() {
        return frames;
    }
}
