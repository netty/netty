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

public class RetireConnectionIdFrame extends QuicFrame {

    protected long sequence;

    RetireConnectionIdFrame() {
        super(FrameType.RETIRE_CONNECTION_ID);
    }

    public RetireConnectionIdFrame(long sequence) {
        super(FrameType.RETIRE_CONNECTION_ID);
        this.sequence = sequence;
    }

    @Override
    public void read(ByteBuf buf) {
        sequence = VarInt.read(buf).asLong();
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        VarInt.byLong(sequence).write(buf);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        RetireConnectionIdFrame that = (RetireConnectionIdFrame) o;

        return sequence == that.sequence;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (sequence ^ (sequence >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "RetireConnectionIdFrame{" +
                "sequence=" + sequence +
                '}';
    }

    public long sequence() {
        return sequence;
    }

    public void sequence(long sequence) {
        this.sequence = sequence;
    }
}
