/*
 * Copyright 2014 The Netty Project
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
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.quic.packet.HeaderUtil;
import io.netty.handler.codec.quic.packet.IPacket;
import io.netty.handler.codec.quic.packet.Packet;

import java.util.List;

public class QuicDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private Status status;

    public static String decodeString(ByteBuf buf) {
        int length = VarInt.read(buf).asInt();
        return new String(HeaderUtil.read(buf, length));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        while (msg.content().isReadable()) {
            out.add(Packet.readPacket(msg.content()));
        }
    }
}
