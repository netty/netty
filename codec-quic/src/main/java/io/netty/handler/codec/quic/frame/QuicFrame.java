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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.quic.packet.DataPacket;
import io.netty.handler.codec.quic.tls.Cryptor;

public class QuicFrame {

    public static void readFrames(DataPacket packet, ByteBuf buf, byte[] encrypted, byte[] header, Cryptor cryptor) {
        ByteBuf decrypted = Unpooled.wrappedBuffer(cryptor.decryptContent(encrypted, packet.packetNumber(), header));
        try {
            while (decrypted.isReadable()) {
                packet.frames().add(FrameType.readFrame(buf));
            }
        } finally {
            decrypted.release();
        }
    }

    protected FrameType type;

    public QuicFrame(FrameType type) {
        this.type = type;
    }

    public void read(ByteBuf buf) {}

    public void write(ByteBuf buf) {
        buf.writeByte(type.getByte());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QuicFrame frame = (QuicFrame) o;

        return type == frame.type;
    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }

    public FrameType type() {
        return type;
    }
}
