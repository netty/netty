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

public class StreamDataBlockedFrame extends QuicFrame {

    protected StreamID streamID;
    protected long dataLimit;

    StreamDataBlockedFrame() {
        super(FrameType.STREAM_DATA_BLOCKED);
    }

    public StreamDataBlockedFrame(StreamID streamID, long dataLimit) {
        super(FrameType.STREAM_DATA_BLOCKED);
        this.streamID = streamID;
        this.dataLimit = dataLimit;
    }

    @Override
    public void read(ByteBuf buf) {
        streamID = StreamID.read(buf);
        dataLimit = VarInt.read(buf).asLong();
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        streamID.write(buf);
        VarInt.byLong(dataLimit).write(buf);
    }

    public StreamID streamID() {
        return streamID;
    }

    public void streamID(StreamID streamID) {
        this.streamID = streamID;
    }

    public long dataLimit() {
        return dataLimit;
    }

    public void dataLimit(long dataLimit) {
        this.dataLimit = dataLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        StreamDataBlockedFrame that = (StreamDataBlockedFrame) o;

        if (dataLimit != that.dataLimit) return false;
        return streamID != null ? streamID.equals(that.streamID) : that.streamID == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (streamID != null ? streamID.hashCode() : 0);
        result = 31 * result + (int) (dataLimit ^ (dataLimit >>> 32));
        return result;
    }
}
