/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.tun;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.Tun4Packet;
import io.netty.channel.socket.TunPacket;

import static io.netty.channel.socket.Tun4Packet.INET4_DESTINATION_ADDRESS;
import static io.netty.channel.socket.Tun4Packet.INET4_SOURCE_ADDRESS;

/**
 * Echoes received IPv4 packets by swapping source and destination addresses.
 */
@Sharable
public class Echo4Handler extends SimpleChannelInboundHandler<Tun4Packet> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Tun4Packet packet) throws Exception {
        // swap source and destination addresses. Depending on the layer 4 protocol used, this may
        // require recalculation of existing checksums. However, UDP and TCP work without
        // recalculation.
        ByteBuf buf = packet.content();
        int sourceAddress = buf.getInt(INET4_SOURCE_ADDRESS);
        int destinationAddress = buf.getInt(INET4_DESTINATION_ADDRESS);
        buf.setInt(INET4_SOURCE_ADDRESS, destinationAddress);
        buf.setInt(INET4_DESTINATION_ADDRESS, sourceAddress);

        TunPacket response = new Tun4Packet(buf.retain());
        ctx.write(response);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
        ctx.flush();
    }
}
