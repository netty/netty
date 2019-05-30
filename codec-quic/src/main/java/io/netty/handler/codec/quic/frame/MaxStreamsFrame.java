/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.quic.frame;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.VarInt;

public class MaxStreamsFrame extends QuicFrame {

    private boolean bidi;
    private VarInt maxStreams;

    public MaxStreamsFrame() {
        super(FrameType.MAX_STREAMS);
    }

    public MaxStreamsFrame(boolean bidi, long maxStreams) {
        this(bidi, VarInt.byLong(maxStreams));
    }

    public MaxStreamsFrame(boolean bidi, VarInt maxStreams) {
        super(FrameType.MAX_STREAMS);
        this.bidi = bidi;
        this.maxStreams = maxStreams;
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        buf.writeByte(bidi ? 0x12 : 0x13);
        maxStreams.write(buf);
    }

    @Override
    public void read(ByteBuf buf) {
        bidi = buf.readByte() == 0x12;
        maxStreams = VarInt.read(buf);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        MaxStreamsFrame that = (MaxStreamsFrame) o;

        if (bidi != that.bidi) return false;
        return maxStreams != null ? maxStreams.equals(that.maxStreams) : that.maxStreams == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (bidi ? 1 : 0);
        result = 31 * result + (maxStreams != null ? maxStreams.hashCode() : 0);
        return result;
    }

    public boolean bidi() {
        return bidi;
    }

    public VarInt maxStreams() {
        return maxStreams;
    }

    public void bidi(boolean bidi) {
        this.bidi = bidi;
    }

    public void maxStreams(VarInt maxStreams) {
        this.maxStreams = maxStreams;
    }
}
