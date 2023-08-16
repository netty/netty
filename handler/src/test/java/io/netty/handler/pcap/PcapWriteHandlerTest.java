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
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.DefaultDatagramChannelConfig;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;

import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PcapWriteHandlerTest {

    @Test
    public void udpV4SharedOutputStreamTest() throws InterruptedException {
        udpV4(true);
    }

    @Test
    public void udpV4NonOutputStream() throws InterruptedException {
        udpV4(false);
    }

    private static void udpV4(boolean sharedOutputStream) throws InterruptedException {
        ByteBuf byteBuf = Unpooled.buffer();

        InetSocketAddress serverAddr = new InetSocketAddress("127.0.0.1", 0);
        InetSocketAddress clientAddr = new InetSocketAddress("127.0.0.1", 0);

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

        ChannelFuture channelFutureServer = server.bind(serverAddr).sync();
        assertTrue(channelFutureServer.isSuccess());

        CloseDetectingByteBufOutputStream outputStream = new CloseDetectingByteBufOutputStream(byteBuf);

        // We'll bootstrap a UDP Client for sending UDP Packets to UDP Server.
        Bootstrap client = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(PcapWriteHandler.builder()
                        .sharedOutputStream(sharedOutputStream)
                        .build(outputStream));

        ChannelFuture channelFutureClient =
                client.connect(channelFutureServer.channel().localAddress(), clientAddr).sync();
        assertTrue(channelFutureClient.isSuccess());

        Channel clientChannel = channelFutureClient.channel();
        assertTrue(clientChannel.writeAndFlush(Unpooled.wrappedBuffer("Meow".getBytes())).sync().isSuccess());
        assertTrue(eventLoopGroup.shutdownGracefully().sync().isSuccess());

        verifyUdpCapture(!sharedOutputStream, // if sharedOutputStream is true, we don't verify the global headers.
                byteBuf,
                (InetSocketAddress) clientChannel.remoteAddress(),
                (InetSocketAddress) clientChannel.localAddress()
        );

        // If sharedOutputStream is true, we don't close the outputStream.
        // If sharedOutputStream is false, we close the outputStream.
        assertEquals(!sharedOutputStream, outputStream.closeCalled());
    }

    @Test
    public void embeddedUdp() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.wrappedBuffer("Meow".getBytes());

        InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

        // We fake a client
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                PcapWriteHandler.builder()
                        .forceUdpChannel(clientAddr, serverAddr)
                        .build(new ByteBufOutputStream(pcapBuffer))
        );

        assertTrue(embeddedChannel.writeOutbound(payload));
        assertEquals(payload, embeddedChannel.readOutbound());

        // Verify the capture data
        verifyUdpCapture(true, pcapBuffer, serverAddr, clientAddr);

        assertFalse(embeddedChannel.finishAndReleaseAll());
    }

    @Test
    public void udpMixedAddress() throws SocketException {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.wrappedBuffer("Meow".getBytes());

        InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        // for ipv6 ::, it's allowed to connect to ipv4 on some systems
        InetSocketAddress clientAddr = new InetSocketAddress("::", 3456);

        // We fake a client
        EmbeddedChannel embeddedChannel = new EmbeddedDatagramChannel(clientAddr, serverAddr);
        embeddedChannel.pipeline().addLast(PcapWriteHandler.builder()
                .build(new ByteBufOutputStream(pcapBuffer)));

        assertTrue(embeddedChannel.writeOutbound(payload));
        assertEquals(payload, embeddedChannel.readOutbound());

        // Verify the capture data
        verifyUdpCapture(true, pcapBuffer, serverAddr, new InetSocketAddress("0.0.0.0", 3456));

        assertFalse(embeddedChannel.finishAndReleaseAll());
    }

    @Test
    public void tcpV4SharedOutputStreamTest() throws Exception {
        tcpV4(true);
    }

    @Test
    public void tcpV4NonOutputStream() throws Exception {
        tcpV4(false);
    }

    private static void tcpV4(final boolean sharedOutputStream) throws Exception {
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
                        p.addLast(PcapWriteHandler.builder().sharedOutputStream(sharedOutputStream)
                                .build(new ByteBufOutputStream(byteBuf)));
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

        verifyTcpCapture(
                !sharedOutputStream, // if sharedOutputStream is true, we don't verify the global headers.
                byteBuf,
                (InetSocketAddress) serverChannelFuture.channel().localAddress(),
                (InetSocketAddress) clientChannelFuture.channel().localAddress()
        );
    }

    @Test
    public void embeddedTcp() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.wrappedBuffer("Meow".getBytes());

        InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

        EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                PcapWriteHandler.builder()
                        .forceTcpChannel(serverAddr, clientAddr, true)
                        .build(new ByteBufOutputStream(pcapBuffer))
        );

        assertTrue(embeddedChannel.writeInbound(payload));
        assertEquals(payload, embeddedChannel.readInbound());

        assertTrue(embeddedChannel.writeOutbound(payload));
        assertEquals(payload, embeddedChannel.readOutbound());

        // Verify the capture data
        verifyTcpCapture(true, pcapBuffer, serverAddr, clientAddr);

        assertFalse(embeddedChannel.finishAndReleaseAll());
    }

    @Test
    public void writerStateTest() throws Exception {
        final ByteBuf payload = Unpooled.wrappedBuffer("Meow".getBytes());
        final InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        final InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

        PcapWriteHandler pcapWriteHandler = PcapWriteHandler.builder()
                .forceTcpChannel(serverAddr, clientAddr, true)
                .build(new OutputStream() {
                    @Override
                    public void write(int b) {
                        // Discard everything
                    }
                });

        // State is INIT because we haven't written anything yet
        // and 'channelActive' is not called yet as this Handler
        // is yet to be attached to `EmbeddedChannel`.
        assertEquals(State.INIT, pcapWriteHandler.state());

        // Create a new 'EmbeddedChannel' and add the 'PcapWriteHandler'
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(pcapWriteHandler);

        // Write and read some data and verify it.
        assertTrue(embeddedChannel.writeInbound(payload));
        assertEquals(payload, embeddedChannel.readInbound());

        assertTrue(embeddedChannel.writeOutbound(payload));
        assertEquals(payload, embeddedChannel.readOutbound());

        // State is now STARTED because we attached Handler to 'EmbeddedChannel'.
        assertEquals(State.WRITING, pcapWriteHandler.state());

        // Close the PcapWriter. This should trigger closure of PcapWriteHandler too.
        pcapWriteHandler.pCapWriter().close();

        // State should be changed to closed by now
        assertEquals(State.CLOSED, pcapWriteHandler.state());

        // Close PcapWriteHandler again. This should be a no-op.
        pcapWriteHandler.close();

        // State should still be CLOSED. No change.
        assertEquals(State.CLOSED, pcapWriteHandler.state());

        // Close the 'EmbeddedChannel'.
        assertFalse(embeddedChannel.finishAndReleaseAll());
    }

    @Test
    public void pauseResumeTest() throws Exception {
        final byte[] payload = "Meow".getBytes();
        final InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        final InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

        DiscardingStatsOutputStream discardingStatsOutputStream = new DiscardingStatsOutputStream();
        PcapWriteHandler pcapWriteHandler = PcapWriteHandler.builder()
                .forceTcpChannel(serverAddr, clientAddr, true)
                .build(discardingStatsOutputStream);

        // Verify that no writes have been called yet.
        assertEquals(0, discardingStatsOutputStream.writesCalled());

        // Create a new 'EmbeddedChannel' and add the 'PcapWriteHandler'
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(pcapWriteHandler);

        for (int i = 0; i < 10; i++) {
            assertTrue(embeddedChannel.writeInbound(Unpooled.wrappedBuffer(payload)));
        }

        // Since we have written 10 times, we should have a value greater than 0.
        // We can't say it will be 10 exactly because there will be pcap headers also.
        final int initialWritesCalled = discardingStatsOutputStream.writesCalled();
        assertThat(initialWritesCalled).isGreaterThan(0);

        // Pause the Pcap
        pcapWriteHandler.pause();
        assertEquals(State.PAUSED, pcapWriteHandler.state());

        // Write 100 times. No writes should be called in OutputStream.
        for (int i = 0; i < 100; i++) {
            assertTrue(embeddedChannel.writeInbound(Unpooled.wrappedBuffer(payload)));
        }

        // Current stats and previous stats should be same.
        assertEquals(initialWritesCalled, discardingStatsOutputStream.writesCalled());

        // Let's resume the Pcap now.
        pcapWriteHandler.resume();
        assertEquals(State.WRITING, pcapWriteHandler.state());

        // Write 100 times. Writes should be called in OutputStream now.
        for (int i = 0; i < 100; i++) {
            assertTrue(embeddedChannel.writeInbound(Unpooled.wrappedBuffer(payload)));
        }

        // Verify we have written more than before.
        assertThat(discardingStatsOutputStream.writesCalled()).isGreaterThan(initialWritesCalled);

        // Close PcapWriteHandler again. This should be a no-op.
        pcapWriteHandler.close();

        // State should still be CLOSED. No change.
        assertEquals(State.CLOSED, pcapWriteHandler.state());

        // Close the 'EmbeddedChannel'.
        assertTrue(embeddedChannel.finishAndReleaseAll());
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

    private static void verifyTcpCapture(boolean verifyGlobalHeaders, ByteBuf byteBuf,
                                         InetSocketAddress serverAddr, InetSocketAddress clientAddr) {
        // note: right now, this method only checks the first packet, which is part of the fake three-way handshake.

        if (verifyGlobalHeaders) {
            verifyGlobalHeaders(byteBuf);
        }

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
        // Source IPv4 Address
        ipv4Packet.readInt();
        // Destination IPv4 Address
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) serverAddr.getAddress()), ipv4Packet.readInt());

        // Verify ports
        ByteBuf tcpPacket = ipv4Packet.readSlice(12);
        assertEquals(clientAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(serverAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
    }

    private static void verifyUdpCapture(boolean verifyGlobalHeaders, ByteBuf byteBuf,
                                         InetSocketAddress remoteAddress, InetSocketAddress localAddress) {
        if (verifyGlobalHeaders) {
            verifyGlobalHeaders(byteBuf);
        }

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
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) localAddress.getAddress()), ipv4Packet.readInt());

        // Destination IPv4 Address
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) remoteAddress.getAddress()), ipv4Packet.readInt());

        // Verify UDP Packet
        ByteBuf udpPacket = ipv4Packet.readBytes(12);
        assertEquals(localAddress.getPort() & 0xffff, udpPacket.readUnsignedShort()); // Source Port
        assertEquals(remoteAddress.getPort() & 0xffff, udpPacket.readUnsignedShort()); // Destination Port
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

    private static class EmbeddedDatagramChannel extends EmbeddedChannel implements DatagramChannel {
        private final InetSocketAddress local;
        private final InetSocketAddress remote;
        private DatagramChannelConfig config;

        EmbeddedDatagramChannel(InetSocketAddress local, InetSocketAddress remote) {
            super(DefaultChannelId.newInstance(), false);
            this.local = local;
            this.remote = remote;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public DatagramChannelConfig config() {
            if (config == null) {
                // ick! config() is called by the super constructor, so we need to do this.
                try {
                    config = new DefaultDatagramChannelConfig(this, new DatagramSocket());
                } catch (SocketException e) {
                    throw new RuntimeException(e);
                }
            }
            return config;
        }

        @Override
        public InetSocketAddress localAddress() {
            return (InetSocketAddress) super.localAddress();
        }

        @Override
        public InetSocketAddress remoteAddress() {
            return (InetSocketAddress) super.remoteAddress();
        }

        @Override
        protected SocketAddress localAddress0() {
            return local;
        }

        @Override
        protected SocketAddress remoteAddress0() {
            return remote;
        }

        @Override
        public ChannelFuture joinGroup(InetAddress multicastAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise future) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture joinGroup(
                InetSocketAddress multicastAddress,
                NetworkInterface networkInterface,
                ChannelPromise future) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture joinGroup(
                InetAddress multicastAddress,
                NetworkInterface networkInterface,
                InetAddress source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture joinGroup(
                InetAddress multicastAddress,
                NetworkInterface networkInterface,
                InetAddress source,
                ChannelPromise future) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture leaveGroup(InetAddress multicastAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelPromise future) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture leaveGroup(
                InetSocketAddress multicastAddress,
                NetworkInterface networkInterface,
                ChannelPromise future) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture leaveGroup(
                InetAddress multicastAddress,
                NetworkInterface networkInterface,
                InetAddress source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture leaveGroup(
                InetAddress multicastAddress,
                NetworkInterface networkInterface,
                InetAddress source,
                ChannelPromise future) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture block(
                InetAddress multicastAddress,
                NetworkInterface networkInterface,
                InetAddress sourceToBlock) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture block(
                InetAddress multicastAddress,
                NetworkInterface networkInterface,
                InetAddress sourceToBlock,
                ChannelPromise future) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock, ChannelPromise future) {
            throw new UnsupportedOperationException();
        }
    }
}
