/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.pcap;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PcapWriteHandlerTest {

    @Test
    public void udpV4() throws InterruptedException {

        ByteBuf byteBuf = Unpooled.buffer();

        InetSocketAddress srvAddr = new InetSocketAddress("127.0.0.1", 62001);
        InetSocketAddress cltAddr = new InetSocketAddress("127.0.0.1", 62002);

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);

        // We'll bootstrap a UDP Server to avoid "Network Unreachable errors" when sending UDP Packet.
        Bootstrap server = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                        // Discard
                    }
                });

        ChannelFuture channelFutureServer = server.bind(srvAddr).sync();
        assertTrue(channelFutureServer.isSuccess());

        // We'll bootstrap a UDP Client for sending UDP Packets to UDP Server.
        Bootstrap client = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(new PcapWriteHandler(new ByteBufOutputStream(byteBuf)));

        ChannelFuture channelFutureClient = client.connect(srvAddr, cltAddr).sync();
        assertTrue(channelFutureClient.isSuccess());
        assertTrue(channelFutureClient.channel().writeAndFlush(Unpooled.wrappedBuffer("Meow".getBytes()))
                .sync().isSuccess());
        assertTrue(eventLoopGroup.shutdownGracefully().sync().isSuccess());

        // Verify Pcap Global Headers
        assertEquals(0xa1b2c3d4, byteBuf.readInt()); // magic_number
        assertEquals(2, byteBuf.readShort());        // version_major
        assertEquals(4, byteBuf.readShort());        // version_minor
        assertEquals(0, byteBuf.readInt());          // thiszone
        assertEquals(0, byteBuf.readInt());          // sigfigs
        assertEquals(0xffff, byteBuf.readInt());     // snaplen
        assertEquals(1, byteBuf.readInt());          // network

        // Verify Pcap Packet Header
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        assertEquals(46, byteBuf.readInt()); // Length of Packet Saved In Pcap
        assertEquals(46, byteBuf.readInt()); // Actual Length of Packet

        // -------------------------------------------- Verify Packet --------------------------------------------
        // Verify Ethernet Packet
        ByteBuf ethernetPacket = byteBuf.readBytes(46);
        ByteBuf dstMac = ethernetPacket.readBytes(6);
        ByteBuf srcMac = ethernetPacket.readBytes(6);
        assertArrayEquals(new byte[]{0, 0, 94, 0, 83, -1}, ByteBufUtil.getBytes(dstMac));
        assertArrayEquals(new byte[]{0, 0, 94, 0, 83, 0}, ByteBufUtil.getBytes(srcMac));
        assertEquals(0x0800, ethernetPacket.readShort());

        // Verify IPv4 Packet
        ByteBuf ipv4Packet = ethernetPacket.readBytes(32);
        assertEquals(0x45, ipv4Packet.readByte());    // Version + IHL
        assertEquals(0x00, ipv4Packet.readByte());    // DSCP
        assertEquals(32, ipv4Packet.readShort());     // Length
        assertEquals(0x0000, ipv4Packet.readShort()); // Identification
        assertEquals(0x0000, ipv4Packet.readShort()); // Fragment
        assertEquals((byte) 0xff, ipv4Packet.readByte());      // TTL
        assertEquals((byte) 17, ipv4Packet.readByte());        // Protocol
        assertEquals(0, ipv4Packet.readShort());      // Checksum
        // Source IPv4 Address
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) srvAddr.getAddress()), ipv4Packet.readInt());
        // Destination IPv4 Address
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) cltAddr.getAddress()), ipv4Packet.readInt());

        // Verify UDP Packet
        ByteBuf udpPacket = ipv4Packet.readBytes(12);
        assertEquals(cltAddr.getPort() & 0xffff, udpPacket.readUnsignedShort()); // Source Port
        assertEquals(srvAddr.getPort() & 0xffff, udpPacket.readUnsignedShort()); // Destination Port
        assertEquals(12, udpPacket.readShort());     // Length
        assertEquals(0x0001, udpPacket.readShort()); // Checksum

        // Verify Payload
        ByteBuf payload = udpPacket.readBytes(4);
        assertArrayEquals("Meow".getBytes(CharsetUtil.UTF_8), ByteBufUtil.getBytes(payload)); // Payload

        // Release all ByteBuf
        assertTrue(dstMac.release());
        assertTrue(srcMac.release());
        assertTrue(payload.release());
        assertTrue(byteBuf.release());
        assertTrue(ethernetPacket.release());
        assertTrue(ipv4Packet.release());
        assertTrue(udpPacket.release());
    }
}
