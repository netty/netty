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
import io.netty.handler.codec.quic.VarInt;

public class StreamBlockedFrame extends QuicFrame {

    protected long streamsLimit;

    StreamBlockedFrame(byte typeByte) {
        super(FrameType.STREAMS_BLOCKED, typeByte);
    }

    public StreamBlockedFrame(boolean bidi, long streamsLimit) {
        super(FrameType.STREAMS_BLOCKED);
        this.streamsLimit = streamsLimit;
        bidi(bidi);
    }

    @Override
    public void read(ByteBuf buf) {
        streamsLimit = VarInt.read(buf).asLong();
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        VarInt.byLong(streamsLimit).write(buf);
    }

    public boolean bidi() {
        return typeByte == 0x16;
    }

    public void bidi(boolean bidi) {
        typeByte = (byte) (bidi ? 0x16 : 0x17);
    }

    public long streamsLimit() {
        return streamsLimit;
    }

    public void streamsLimit(long streamsLimit) {
        this.streamsLimit = streamsLimit;
    }

    @Override
    public String toString() {
        return "StreamBlockedFrame{" +
                "streamsLimit=" + streamsLimit +
                ", typeByte=" + typeByte +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        StreamBlockedFrame that = (StreamBlockedFrame) o;

        return streamsLimit == that.streamsLimit;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (streamsLimit ^ (streamsLimit >>> 32));
        return result;
    }
}
