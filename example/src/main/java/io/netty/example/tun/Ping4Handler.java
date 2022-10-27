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

import java.net.InetAddress;

import static io.netty.channel.socket.Tun4Packet.INET4_DESTINATION_ADDRESS;
import static io.netty.channel.socket.Tun4Packet.INET4_SOURCE_ADDRESS;

/**
 * Replies to ICMP echo ping requests.
 */
@Sharable
public class Ping4Handler extends SimpleChannelInboundHandler<Tun4Packet> {
    // https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
    public static final int PROTOCOL = 1;
    // https://datatracker.ietf.org/doc/html/rfc792
    public static final int TYPE = 20;
    public static final int CHECKSUM = 22;
    public static final int ECHO = 8;
    public static final int ECHO_REPLY = 0;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Tun4Packet packet) {
        if (packet.protocol() == PROTOCOL) {
            short icmpType = packet.content().getUnsignedByte(TYPE);
            if (icmpType == ECHO) {
                InetAddress source = packet.sourceAddress();
                InetAddress destination = packet.destinationAddress();
                int checksum = packet.content().getUnsignedShort(CHECKSUM);

                // create response
                ByteBuf buf = packet.content();
                buf.setBytes(INET4_SOURCE_ADDRESS, destination.getAddress());
                buf.setBytes(INET4_DESTINATION_ADDRESS, source.getAddress());
                buf.setByte(TYPE, ECHO_REPLY);
                buf.setShort(CHECKSUM, (checksum + 0x0800) % 0xffff);

                TunPacket response = new Tun4Packet(buf.retain());
                ctx.writeAndFlush(response);
            }
        }
    }
}
