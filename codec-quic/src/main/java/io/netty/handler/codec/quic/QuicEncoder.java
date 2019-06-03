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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.quic.packet.IPacket;

public class QuicEncoder extends MessageToByteEncoder<IPacket> {

    public static void writeString(ByteBuf buf, String str) {
        VarInt.byLong(str.length()).write(buf);
        buf.writeBytes(str.getBytes());
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, IPacket msg, ByteBuf out) throws Exception {
        msg.write(out);
    }
}
