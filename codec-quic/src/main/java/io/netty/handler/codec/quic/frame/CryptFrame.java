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
import io.netty.handler.codec.quic.packet.HeaderUtil;

import java.util.Arrays;

public class CryptFrame extends QuicFrame {

    private VarInt offset;
    private byte[] data;

    public CryptFrame() {
        super(FrameType.CRYPT);
    }

    public CryptFrame(VarInt offset, byte[] data) {
        this();
        this.offset = offset;
        this.data = data;
    }

    public CryptFrame(long offset, byte[] data) {
        this(VarInt.byLong(offset), data);
    }

    @Override
    public void read(ByteBuf buf) {
        offset = VarInt.read(buf);
        data = HeaderUtil.read(buf, VarInt.read(buf).asInt());
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        offset.write(buf);
        VarInt.byLong(data.length).write(buf);
        buf.writeBytes(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        CryptFrame that = (CryptFrame) o;

        if (offset != null ? !offset.equals(that.offset) : that.offset != null) return false;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (offset != null ? offset.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    public byte[] data() {
        return data;
    }

    public void data(byte[] data) {
        this.data = data;
    }

    public VarInt offset() {
        return offset;
    }

    public void offset(VarInt offset) {
        this.offset = offset;
    }
}
