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

public class StreamResetFrame extends QuicFrame {

    private StreamID id;
    private short error;
    private VarInt offset;

    public StreamResetFrame() {
        super(FrameType.RESET_STREAM);
    }

    public StreamResetFrame(StreamID id, short error, VarInt offset) {
        this();
        this.id = id;
        this.error = error;
        this.offset = offset;
    }

    public StreamResetFrame(long id, short error, long offset) {
        this(StreamID.byLong(id), error, VarInt.byLong(offset));
    }

    @Override
    public void read(ByteBuf buf) {
        id = StreamID.read(buf);
        error = buf.readShort();
        offset = VarInt.read(buf);
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        id.write(buf);
        buf.writeShort(error);
        offset.write(buf);
    }

    @Override
    public String toString() {
        return "StreamResetFrame{" +
                "id=" + id +
                ", error=" + error +
                ", offset=" + offset +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        StreamResetFrame that = (StreamResetFrame) o;

        if (error != that.error) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        return offset != null ? offset.equals(that.offset) : that.offset == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (int) error;
        result = 31 * result + (offset != null ? offset.hashCode() : 0);
        return result;
    }

    public StreamID id() {
        return id;
    }

    public void id(StreamID id) {
        this.id = id;
    }

    public short error() {
        return error;
    }

    public void error(short error) {
        this.error = error;
    }

    public VarInt offset() {
        return offset;
    }

    public void offset(VarInt offset) {
        this.offset = offset;
    }
}
