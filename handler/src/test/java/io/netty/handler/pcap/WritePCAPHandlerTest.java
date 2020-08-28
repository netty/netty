package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class WritePCAPHandlerTest {

    @Test
    public void udpV4() throws IOException {

        ByteBuf byteBuf = Unpooled.buffer();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new WritePCAPHandler(
                new ByteBufOutputStream(byteBuf), true, false, false
        ));

        InetSocketAddress srcAddr = new InetSocketAddress("127.0.0.1", 1000);
        InetSocketAddress dstAddr = new InetSocketAddress("192.168.1.1", 50000);

        embeddedChannel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer("Meow".getBytes()), dstAddr, srcAddr));
        embeddedChannel.flushInbound();

        // Verify Pcap Global Headers
        Assert.assertEquals(0xa1b2c3d4, byteBuf.readInt()); // magic_number
        Assert.assertEquals(2, byteBuf.readShort());        // version_major
        Assert.assertEquals(4, byteBuf.readShort());        // version_minor
        Assert.assertEquals(0, byteBuf.readInt());          // thiszone
        Assert.assertEquals(0, byteBuf.readInt());          // sigfigs
        Assert.assertEquals(0xffff, byteBuf.readInt());     // snaplen
        Assert.assertEquals(1, byteBuf.readInt());          // network

        // Verify Pcap Packet Header
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        Assert.assertEquals(46, byteBuf.readInt()); // Length of Packet Saved In Pcap
        Assert.assertEquals(46, byteBuf.readInt()); // Actual Length of Packet

        // -------------------------------------------- Verify Packet --------------------------------------------
        // Verify Ethernet Packet
        ByteBuf ethernetPacket = byteBuf.readBytes(46);
        Assert.assertArrayEquals(new byte[]{-86, -69, -52, -35, -18, -1},
                ByteBufUtil.getBytes(ethernetPacket.readBytes(6)));
        Assert.assertArrayEquals(new byte[]{-86, -69, -52, -35, -18, -1},
                ByteBufUtil.getBytes(ethernetPacket.readBytes(6)));
        Assert.assertEquals(0x0800, ethernetPacket.readShort());

        // Verify IPv4 Packet
        ByteBuf ipv4Packet = ethernetPacket.readBytes(32);
        Assert.assertEquals(0x45, ipv4Packet.readByte());    // Version + IHL
        Assert.assertEquals(0x00, ipv4Packet.readByte());    // DSCP
        Assert.assertEquals(32, ipv4Packet.readShort());     // Length
        Assert.assertEquals(0x0000, ipv4Packet.readShort()); // Identification
        Assert.assertEquals(0x0000, ipv4Packet.readShort()); // Fragment
        Assert.assertEquals((byte) 0xff, ipv4Packet.readByte());      // TTL
        Assert.assertEquals((byte) 17, ipv4Packet.readByte());        // Protocol
        Assert.assertEquals(0, ipv4Packet.readShort());      // Checksum
        Assert.assertEquals(ipv4ToInt(srcAddr.getAddress()), ipv4Packet.readInt()); // Source IPv4 Address
        Assert.assertEquals(ipv4ToInt(dstAddr.getAddress()), ipv4Packet.readInt()); // Destination IPv4 Address

        // Verify UDP Packet
        ByteBuf udpPacket = ipv4Packet.readBytes(12);
        Assert.assertEquals(1000, udpPacket.readShort());                  // Source Port
        Assert.assertEquals(50000, udpPacket.readShort() & 0xffff); // Destination Port
        Assert.assertEquals(12, udpPacket.readShort());                    // Length
        Assert.assertEquals(0x0001, udpPacket.readShort());                // Checksum
        Assert.assertArrayEquals("Meow".getBytes(), ByteBufUtil.getBytes(udpPacket.readBytes(4))); // Payload

        Assert.assertTrue(embeddedChannel.close().isSuccess());
    }

    private int ipv4ToInt(InetAddress inetAddress) {
        byte[] octets = inetAddress.getAddress();
        assert octets.length == 4;

        return (octets[0] & 0xff) << 24 |
                (octets[1] & 0xff) << 16 |
                (octets[2] & 0xff) << 8 |
                (octets[3] & 0xff);
    }
}
