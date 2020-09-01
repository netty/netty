/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import org.junit.Test;
import sun.nio.cs.StandardCharsets;

import static org.junit.Assert.*;

import java.net.Inet4Address;
import java.net.InetSocketAddress;

public class PCAPWriteHandlerTest {

    @Test
    public void udpV4() {

        ByteBuf byteBuf = Unpooled.buffer();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new PCAPWriteHandler(new ByteBufOutputStream(byteBuf)));

        InetSocketAddress srcAddr = new InetSocketAddress("127.0.0.1", 1000);
        InetSocketAddress dstAddr = new InetSocketAddress("192.168.1.1", 50000);

        assertTrue(embeddedChannel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer("Meow".getBytes()),
                dstAddr, srcAddr)));
        embeddedChannel.flushInbound();

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
        assertArrayEquals(new byte[]{0, 0, 94, 0, 83, -1},
                ByteBufUtil.getBytes(ethernetPacket.readBytes(6)));
        assertArrayEquals(new byte[]{0, 0, 94, 0, 83, 0},
                ByteBufUtil.getBytes(ethernetPacket.readBytes(6)));
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
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) srcAddr.getAddress()),
                ipv4Packet.readInt()); // Source IPv4 Address
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) dstAddr.getAddress()),
                ipv4Packet.readInt()); // Destination IPv4 Address

        // Verify UDP Packet
        ByteBuf udpPacket = ipv4Packet.readBytes(12);
        assertEquals(1000, udpPacket.readShort());                  // Source Port
        assertEquals(50000, udpPacket.readShort() & 0xffff); // Destination Port
        assertEquals(12, udpPacket.readShort());                    // Length
        assertEquals(0x0001, udpPacket.readShort());                // Checksum
        assertArrayEquals("Meow".getBytes(CharsetUtil.UTF_8), ByteBufUtil.getBytes(udpPacket.readBytes(4))); // Payload

        assertTrue(ethernetPacket.release());
        assertTrue(ipv4Packet.release());
        assertTrue(udpPacket.release());
        assertTrue(embeddedChannel.close().isSuccess());
    }
}
