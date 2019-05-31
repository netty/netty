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
import io.netty.handler.codec.quic.frame.QuicFrame;
import io.netty.handler.codec.quic.tls.Cryptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DataPacket extends Packet {

    protected long packetNumber;
    protected List<QuicFrame> frames = new ArrayList<QuicFrame>();

    DataPacket() {}

    public DataPacket(long packetNumber, List<QuicFrame> frames) {
        this.packetNumber = packetNumber;
        this.frames = frames;
    }

    public DataPacket(long packetNumber, QuicFrame... frames) {
        this.packetNumber = packetNumber;
        this.frames = Arrays.asList(frames);
    }

    public List<QuicFrame> frames() {
        return frames;
    }

    public long packetNumber() {
        return packetNumber;
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

    protected byte[] getPN(ByteBuf buf) {
        final byte[] bin = new byte[4];
        for (int j = 4; j > 0; j--) {
            bin[4 - j] = (byte) ((packetNumber >> (8 * (j - 1))) & 0xFF);
        }
        return bin;
    }

    protected void processData(ByteBuf buf, byte[] header, int pnStart, int packetStart, Cryptor cryptor) {
        byte firstHeaderByte = header[0];

        int length = (firstHeaderByte & 0x3) + 1;
        byte[] pn = Arrays.copyOfRange(header, 1, 1 + length);
        readPacketNumber(pn);
        buf.readerIndex(buf.readerIndex() + length);

        byte[] content = new byte[buf.readerIndex() - packetStart];
        buf.getBytes(packetStart, content);

        /* replace old header */
        content[0] = firstHeaderByte;
        System.arraycopy(pn, 0, content, pnStart - packetStart, pn.length);

        QuicFrame.readFrames(this, buf, drain(buf), content, cryptor);
    }

    protected byte[] framesEncoded() {
        ByteBuf raw = Unpooled.buffer();
        try {
            for (QuicFrame frame : frames) {
                frame.write(raw);
            }
            return drain(raw);
        } finally {
            raw.release();
        }
    }

    protected void writeFrames(Cryptor cryptor, ByteBuf buf, byte[] header) {
        writeFrames(cryptor, buf, framesEncoded(), header);
    }

    protected void writeFrames(Cryptor cryptor, ByteBuf buf, byte[] encoded, byte[] header) {
        buf.writeBytes(cryptor.seal(encoded, packetNumber, header));
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataPacket that = (DataPacket) o;

        if (packetNumber != that.packetNumber) return false;
        return frames != null ? frames.equals(that.frames) : that.frames == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (packetNumber ^ (packetNumber >>> 32));
        result = 31 * result + (frames != null ? frames.hashCode() : 0);
        return result;
    }
}
