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

public class PaddingFrame extends QuicFrame {

    private int padding;

    public PaddingFrame() {
        super(FrameType.PADDING);
    }

    public PaddingFrame(int padding) {
        this();
        this.padding = padding;
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        buf.writeBytes(new byte[padding]);
    }

    @Override
    public void read(ByteBuf buf) {
        while (buf.isReadable() && buf.readByte() == 0) {
            padding++;
        }
        if (padding != 0) {
            buf.readerIndex(buf.readerIndex() - 1);
        }
    }

    @Override
    public String toString() {
        return "PaddingFrame{" +
                "padding=" + padding +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        PaddingFrame that = (PaddingFrame) o;

        return padding == that.padding;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + padding;
        return result;
    }
}
