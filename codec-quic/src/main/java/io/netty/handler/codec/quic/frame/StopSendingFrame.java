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

public class StopSendingFrame extends QuicFrame {

    protected StreamID streamID;
    protected short errorCode;

    StopSendingFrame() {
        super(FrameType.STOP_SENDING);
    }

    public StopSendingFrame(StreamID streamID, short errorCode) {
        this();
        this.streamID = streamID;
        this.errorCode = errorCode;
    }

    @Override
    public void read(ByteBuf buf) {
        streamID = StreamID.read(buf);
        //TODO should we read this with VarInt ieft unclear
        errorCode = buf.readShort();
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        streamID.write(buf);
        buf.writeShort(errorCode);
    }

    public StreamID streamID() {
        return streamID;
    }

    public void streamID(StreamID streamID) {
        this.streamID = streamID;
    }

    public short errorCode() {
        return errorCode;
    }

    public void errorCode(short errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        return "StopSendingFrame{" +
                "streamID=" + streamID +
                ", errorCode=" + errorCode +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        StopSendingFrame that = (StopSendingFrame) o;

        if (errorCode != that.errorCode) return false;
        return streamID != null ? streamID.equals(that.streamID) : that.streamID == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (streamID != null ? streamID.hashCode() : 0);
        result = 31 * result + (int) errorCode;
        return result;
    }
}
