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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PcapWriteHandlerTest {

    @Test
    public void udpV4() throws InterruptedException {

        ByteBuf byteBuf = Unpooled.buffer();

        InetSocketAddress srvReqAddr = new InetSocketAddress("127.0.0.1", 0);
        InetSocketAddress cltReqAddr = new InetSocketAddress("127.0.0.1", 0);

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

        ChannelFuture channelFutureServer = server.bind(srvReqAddr).sync();
        assertTrue(channelFutureServer.isSuccess());

        // We'll bootstrap a UDP Client for sending UDP Packets to UDP Server.
        Bootstrap client = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(new PcapWriteHandler(new ByteBufOutputStream(byteBuf)));

        ChannelFuture channelFutureClient =
                client.connect(channelFutureServer.channel().localAddress(), cltReqAddr).sync();
        assertTrue(channelFutureClient.isSuccess());
        Channel clientChannel = channelFutureClient.channel();
        assertTrue(clientChannel.writeAndFlush(Unpooled.wrappedBuffer("Meow".getBytes())).sync().isSuccess());
        assertTrue(eventLoopGroup.shutdownGracefully().sync().isSuccess());

        // Verify Pcap Global Headers
        verifyGlobalHeaders(byteBuf);

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
        InetSocketAddress localAddr = (InetSocketAddress) clientChannel.remoteAddress();
        // Source IPv4 Address
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) localAddr.getAddress()), ipv4Packet.readInt());
        InetSocketAddress remoteAddr = (InetSocketAddress) clientChannel.localAddress();
        // Destination IPv4 Address
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) remoteAddr.getAddress()), ipv4Packet.readInt());

        // Verify UDP Packet
        ByteBuf udpPacket = ipv4Packet.readBytes(12);
        assertEquals(remoteAddr.getPort() & 0xffff, udpPacket.readUnsignedShort()); // Source Port
        assertEquals(localAddr.getPort() & 0xffff, udpPacket.readUnsignedShort()); // Destination Port
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

    @Test
    public void tcpV4() throws InterruptedException, ExecutionException {
        final ByteBuf byteBuf = Unpooled.buffer();

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup clientGroup = new NioEventLoopGroup();

        // Configure the echo server
        ServerBootstrap sb = new ServerBootstrap();
        final Promise<Boolean> dataReadPromise = bossGroup.next().newPromise();
        sb.group(bossGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new PcapWriteHandler(new ByteBufOutputStream(byteBuf)));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ctx.write(msg);
                            }

                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) {
                                ctx.flush();
                                dataReadPromise.setSuccess(true);
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                ctx.close();
                            }
                        });
                    }
                });

        // Start the server.
        ChannelFuture serverChannelFuture = sb.bind(new InetSocketAddress("127.0.0.1", 0)).sync();
        assertTrue(serverChannelFuture.isSuccess());

        // configure the client
        Bootstrap cb = new Bootstrap();
        final Promise<Boolean> dataWrittenPromise = clientGroup.next().newPromise();
        cb.group(clientGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ctx.writeAndFlush(Unpooled.wrappedBuffer("Meow".getBytes()));
                                dataWrittenPromise.setSuccess(true);
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                ctx.close();
                            }
                        });
                    }
                });

        // Start the client.
        ChannelFuture clientChannelFuture = cb.connect(serverChannelFuture.channel().localAddress()).sync();
        assertTrue(clientChannelFuture.isSuccess());

        assertTrue(dataWrittenPromise.await(5, TimeUnit.SECONDS));
        assertTrue(dataReadPromise.await(5, TimeUnit.SECONDS));

        clientChannelFuture.channel().close().sync();
        serverChannelFuture.channel().close().sync();

        // Shut down all event loops to terminate all threads.
        assertTrue(clientGroup.shutdownGracefully().sync().isSuccess());
        assertTrue(bossGroup.shutdownGracefully().sync().isSuccess());

        verifyGlobalHeaders(byteBuf);

        // Verify Pcap Packet Header
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        assertEquals(54, byteBuf.readInt()); // Length of Packet Saved In Pcap
        assertEquals(54, byteBuf.readInt()); // Actual Length of Packet

        // -------------------------------------------- Verify Packet --------------------------------------------
        // Verify Ethernet Packet
        ByteBuf ethernetPacket = byteBuf.readSlice(54);
        ByteBuf dstMac = ethernetPacket.readSlice(6);
        ByteBuf srcMac = ethernetPacket.readSlice(6);
        assertArrayEquals(new byte[]{0, 0, 94, 0, 83, -1}, ByteBufUtil.getBytes(dstMac));
        assertArrayEquals(new byte[]{0, 0, 94, 0, 83, 0}, ByteBufUtil.getBytes(srcMac));
        assertEquals(0x0800, ethernetPacket.readShort());

        // Verify IPv4 Packet
        ByteBuf ipv4Packet = ethernetPacket.readSlice(32);
        assertEquals(0x45, ipv4Packet.readByte());    // Version + IHL
        assertEquals(0x00, ipv4Packet.readByte());    // DSCP
        assertEquals(40, ipv4Packet.readShort());     // Length
        assertEquals(0x0000, ipv4Packet.readShort()); // Identification
        assertEquals(0x0000, ipv4Packet.readShort()); // Fragment
        assertEquals((byte) 0xff, ipv4Packet.readByte());      // TTL
        assertEquals((byte) 6, ipv4Packet.readByte());        // Protocol
        assertEquals(0, ipv4Packet.readShort());      // Checksum
        InetSocketAddress serverAddr = (InetSocketAddress) serverChannelFuture.channel().localAddress();
        // Source IPv4 Address
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) serverAddr.getAddress()), ipv4Packet.readInt());
        // Destination IPv4 Address
        ipv4Packet.readInt();

        InetSocketAddress clientAddr = (InetSocketAddress) clientChannelFuture.channel().localAddress();

        // Verify ports
        ByteBuf tcpPacket = ipv4Packet.readSlice(12);
        assertEquals(clientAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(serverAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
    }

    private static void verifyGlobalHeaders(ByteBuf byteBuf) {
        assertEquals(0xa1b2c3d4, byteBuf.readInt()); // magic_number
        assertEquals(2, byteBuf.readShort());        // version_major
        assertEquals(4, byteBuf.readShort());        // version_minor
        assertEquals(0, byteBuf.readInt());          // thiszone
        assertEquals(0, byteBuf.readInt());          // sigfigs
        assertEquals(0xffff, byteBuf.readInt());     // snaplen
        assertEquals(1, byteBuf.readInt());          // network
    }
}
