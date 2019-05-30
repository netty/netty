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

public class MaxStreamDataFrame extends QuicFrame {

    private StreamID streamID;
    private VarInt maxData;

    public MaxStreamDataFrame() {
        super(FrameType.MAX_STREAM_DATA);
    }

    public MaxStreamDataFrame(StreamID streamID, VarInt maxData) {
        this();
        this.streamID = streamID;
        this.maxData = maxData;
    }

    public MaxStreamDataFrame(long streamID, long maxData) {
        this(StreamID.byLong(streamID), VarInt.byLong(maxData));
    }

    @Override
    public void read(ByteBuf buf) {
        streamID = StreamID.read(buf);
        maxData = VarInt.read(buf);
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        streamID.write(buf);
        maxData.write(buf);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        MaxStreamDataFrame that = (MaxStreamDataFrame) o;

        if (streamID != null ? !streamID.equals(that.streamID) : that.streamID != null) return false;
        return maxData != null ? maxData.equals(that.maxData) : that.maxData == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (streamID != null ? streamID.hashCode() : 0);
        result = 31 * result + (maxData != null ? maxData.hashCode() : 0);
        return result;
    }

    public StreamID streamID() {
        return streamID;
    }

    public void streamID(StreamID streamID) {
        this.streamID = streamID;
    }

    public VarInt maxData() {
        return maxData;
    }

    public void maxData(VarInt maxData) {
        this.maxData = maxData;
    }
}
