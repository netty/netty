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

package io.netty.handler.codec.quic.frame;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.StreamID;
import io.netty.handler.codec.quic.VarInt;
import io.netty.handler.codec.quic.packet.HeaderUtil;

public class StreamFrame extends QuicFrame {

    protected long offset;
    protected int length;
    protected StreamID streamID;
    protected byte[] data;

    StreamFrame(byte typeByte) {
        super(FrameType.STREAM, typeByte);
    }

    public StreamFrame(boolean fin, StreamID streamID, byte[] data) {
        this(0, 0, fin, streamID, data);
    }

    public StreamFrame(long offset, int length, boolean fin, StreamID streamID, byte[] data) {
        super(FrameType.STREAM);
        offset(offset);
        length(length);
        fin(fin);
        this.streamID = streamID;
        this.data = data;
    }

    @Override
    public void read(ByteBuf buf) {
        streamID = StreamID.read(buf);
        if (hasOffset()) {
            offset = VarInt.read(buf).asLong();
        }
        length = hasLength() ? VarInt.read(buf).asInt() : buf.readableBytes();
        data = HeaderUtil.read(buf, length);
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        streamID.write(buf);
        if (hasOffset()) {
            VarInt.byLong(offset).write(buf);
        }
        VarInt.byLong(hasLength() ? length : data.length).write(buf);
        buf.writeBytes(data);
    }

    public StreamID streamID() {
        return streamID;
    }

    public void streamID(StreamID streamID) {
        this.streamID = streamID;
    }

    public long offset() {
        return offset;
    }

    public void offset(long offset) {
        this.offset = offset;
        hasOffset(offset != 0);
    }

    public int length() {
        return hasLength() ? length : data.length;
    }

    public void length(int length) {
        this.length = length;
        hasLength(length != 0);
    }

    public byte[] data() {
        return data;
    }

    public void data(byte[] data) {
        this.data = data;
    }

    public boolean fin() {
        return (typeByte & 0x01) == 0x01;
    }

    public boolean hasLength() {
        return (typeByte & 0x02) == 0x02;
    }

    public boolean hasOffset() {
        return (typeByte & 0x02) == 0x02;
    }

    private void constructTypeByte(boolean offset, boolean fin, boolean length) {
        typeByte = type.firstIdentifier();
        if (offset) {
            typeByte = (byte) (typeByte | 0x04);
        }
        if (fin) {
            typeByte = (byte) (typeByte | 0x04);
        }
        if (length) {
            typeByte = (byte) (typeByte | 0x04);
        }
    }

    public void fin(boolean fin) {
        constructTypeByte(hasOffset(), fin, hasLength());
    }

    public void hasLength(boolean length) {
        constructTypeByte(hasOffset(), fin(), length);
    }

    public void hasOffset(boolean offset) {
        constructTypeByte(offset, fin(), hasLength());
    }

}
