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
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.DefaultDatagramChannelConfig;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PcapWriteHandlerTest {

    @Test
    public void udpV4SharedOutputStreamTest() throws InterruptedException {
        udpV4(true, true);
    }

    @Test
    public void udpV4NonOutputStream() throws InterruptedException {
        udpV4(false, true);
    }

    @Test
    public void udpV4NoGlobalHeaderOutputStream() throws InterruptedException {
        udpV4(false, false);
    }

    private static void udpV4(boolean sharedOutputStream, boolean writeGlobalHeaders)
            throws InterruptedException {
        ByteBuf byteBuf = Unpooled.buffer();
        ByteBuf payload = Unpooled.wrappedBuffer("Meow".getBytes());

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("127.0.0.1", 0);
            InetSocketAddress clientAddr = new InetSocketAddress("127.0.0.1", 0);

            EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

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
                            .writePcapGlobalHeader(writeGlobalHeaders)
                            .build(outputStream));

            ChannelFuture channelFutureClient =
                    client.bind(clientAddr).sync();
            assertTrue(channelFutureClient.isSuccess());

            Channel clientChannel = channelFutureClient.channel();
            DatagramPacket datagram = new DatagramPacket(payload.copy(),
                    (InetSocketAddress) channelFutureServer.channel().localAddress());
            assertTrue(clientChannel.writeAndFlush(datagram).sync().isSuccess());
            assertTrue(eventLoopGroup.shutdownGracefully().sync().isSuccess());

            // if sharedOutputStream is true or writeGlobalHeaders is false, we don't verify the global headers.
            verifyUdpCapture(!sharedOutputStream && writeGlobalHeaders,
                    byteBuf, payload,
                    (InetSocketAddress) channelFutureServer.channel().localAddress(),
                    (InetSocketAddress) clientChannel.localAddress()
            );

            // If sharedOutputStream is true, we don't close the outputStream.
            // If sharedOutputStream is false, we close the outputStream.
            assertEquals(!sharedOutputStream, outputStream.closeCalled());
        } finally {
            byteBuf.release();
            payload.release();
        }
    }

    @Test
    public void embeddedUdp() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.wrappedBuffer("Meow".getBytes());

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            // We fake a client
            EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                    PcapWriteHandler.builder()
                            .forceUdpChannel(clientAddr, serverAddr)
                            .build(new ByteBufOutputStream(pcapBuffer))
            );

            assertTrue(embeddedChannel.writeOutbound(payload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readOutbound();
            assertEquals(payload, read);
            read.release();

            assertTrue(embeddedChannel.writeInbound(payload.retainedDuplicate()));
            read = embeddedChannel.readInbound();
            assertEquals(payload, read);
            read.release();

            // Verify the capture data
            verifyUdpCapture(true, pcapBuffer, payload, serverAddr, clientAddr);

            verifyUdpCapture(false, pcapBuffer, payload, clientAddr, serverAddr);

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            pcapBuffer.release();
            payload.release();
        }
    }

    @Test
    public void udpMixedAddress() throws SocketException {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.wrappedBuffer("Meow".getBytes());

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            // for ipv6 ::, it's allowed to connect to ipv4 on some systems
            InetSocketAddress clientAddr = new InetSocketAddress("::", 3456);

            // We fake a client
            EmbeddedChannel embeddedChannel = new EmbeddedDatagramChannel(clientAddr, serverAddr);
            embeddedChannel.pipeline().addLast(PcapWriteHandler.builder()
                    .build(new ByteBufOutputStream(pcapBuffer)));

            assertTrue(embeddedChannel.writeOutbound(payload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readOutbound();
            assertEquals(payload, read);
            read.release();

            // Verify the capture data
            verifyUdpCapture(true, pcapBuffer, payload, serverAddr, new InetSocketAddress("0.0.0.0", 3456));

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            pcapBuffer.release();
            payload.release();
        }
    }

    @Test
    public void udpLargeByteBufPayload() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        //Create payload 1 byte larger than maximum
        String payloadString = new String(new char[65507 + 1]).replace('\0', 'X');
        final ByteBuf payload = Unpooled.wrappedBuffer(payloadString.getBytes());

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            // We fake a client
            EmbeddedChannel embeddedChannel = new EmbeddedDatagramChannel(clientAddr, serverAddr);
            embeddedChannel.pipeline().addLast(PcapWriteHandler.builder()
                    .build(new ByteBufOutputStream(pcapBuffer)));

            assertTrue(embeddedChannel.writeOutbound(payload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readOutbound();
            assertEquals(payload, read);
            read.release();

            // Verify only the PCAP global header was written. Large UDP payload should be discarded
            assertEquals(24, pcapBuffer.readableBytes());

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            pcapBuffer.release();
            payload.release();
        }
    }

    @Test
    public void udpLargeDatagramPayload() {
        InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

        final ByteBuf pcapBuffer = Unpooled.buffer();
        //Create payload 1 byte larger than maximum
        String payloadString = new String(new char[65507 + 1]).replace('\0', 'X');
        final DatagramPacket datagram =
                new DatagramPacket(Unpooled.wrappedBuffer(payloadString.getBytes()), serverAddr);
        try {
            // We fake a client
            EmbeddedChannel embeddedChannel = new EmbeddedDatagramChannel(clientAddr, serverAddr);
            embeddedChannel.pipeline().addLast(PcapWriteHandler.builder()
                    .build(new ByteBufOutputStream(pcapBuffer)));

            assertTrue(embeddedChannel.writeOutbound(datagram.retainedDuplicate()));
            DatagramPacket read = embeddedChannel.readOutbound();
            assertEquals(datagram.content(), read.content());
            read.release();

            // Verify only the PCAP global header was written. Large UDP payload should be discarded
            assertEquals(24, pcapBuffer.readableBytes());

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            pcapBuffer.release();
            datagram.release();
        }
    }

    @Test
    public void udpZeroLengthByteBufCaptured() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.buffer();

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            // We fake a client
            EmbeddedChannel embeddedChannel = new EmbeddedDatagramChannel(clientAddr, serverAddr);
            embeddedChannel.pipeline().addLast(PcapWriteHandler.builder()
                    .captureZeroByte(true)
                    .build(new ByteBufOutputStream(pcapBuffer)));

            assertTrue(embeddedChannel.writeOutbound(payload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readOutbound();
            assertEquals(payload, read);
            read.release();

            assertTrue(embeddedChannel.writeInbound(payload.retainedDuplicate()));
            read = embeddedChannel.readInbound();
            assertEquals(payload, read);
            read.release();

            verifyUdpCapture(true, pcapBuffer, payload, serverAddr, clientAddr);

            verifyUdpCapture(false, pcapBuffer, payload, clientAddr, serverAddr);

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            pcapBuffer.release();
            payload.release();
        }
    }

    @Test
    public void udpZeroLengthByteBufDiscarded() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.buffer();

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            // We fake a client
            EmbeddedChannel embeddedChannel = new EmbeddedDatagramChannel(clientAddr, serverAddr);
            embeddedChannel.pipeline().addLast(PcapWriteHandler.builder()
                    .build(new ByteBufOutputStream(pcapBuffer)));

            assertTrue(embeddedChannel.writeOutbound(payload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readOutbound();
            assertEquals(payload, read);
            read.release();

            assertTrue(embeddedChannel.writeInbound(payload.retainedDuplicate()));
            read = embeddedChannel.readInbound();
            assertEquals(payload, read);
            read.release();

            // Verify only the PCAP global header was written. Large UDP payload should be discarded
            assertEquals(24, pcapBuffer.readableBytes());

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            pcapBuffer.release();
            payload.release();
        }
    }

    @Test
    public void udpZeroLengthDatagramCaptured() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.buffer();
        InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);
        final DatagramPacket outbound = new DatagramPacket(payload.retainedDuplicate(), serverAddr);
        final DatagramPacket inbound = new DatagramPacket(payload.retainedDuplicate(), clientAddr, serverAddr);
        try {
            // We fake a client
            EmbeddedChannel embeddedChannel = new EmbeddedDatagramChannel(clientAddr, serverAddr);
            embeddedChannel.pipeline().addLast(PcapWriteHandler.builder()
                    .captureZeroByte(true)
                    .build(new ByteBufOutputStream(pcapBuffer)));

            assertTrue(embeddedChannel.writeOutbound(outbound.retainedDuplicate()));
            DatagramPacket read = embeddedChannel.readOutbound();
            assertEquals(outbound.content(), read.content());
            read.release();

            assertTrue(embeddedChannel.writeInbound(inbound.retainedDuplicate()));
            read = embeddedChannel.readInbound();
            assertEquals(inbound.content(), read.content());
            read.release();

            verifyUdpCapture(true, pcapBuffer, payload, serverAddr, clientAddr);

            verifyUdpCapture(false, pcapBuffer, payload, clientAddr, serverAddr);

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            pcapBuffer.release();
            payload.release();
            outbound.release();
            inbound.release();
        }
    }

    @Test
    public void udpZeroLengthDatagramDiscarded() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.buffer();
        InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);
        final DatagramPacket outbound = new DatagramPacket(payload.retainedDuplicate(), serverAddr);
        final DatagramPacket inbound = new DatagramPacket(payload.retainedDuplicate(), clientAddr, serverAddr);
        try {
            // We fake a client
            EmbeddedChannel embeddedChannel = new EmbeddedDatagramChannel(clientAddr, serverAddr);
            embeddedChannel.pipeline().addLast(PcapWriteHandler.builder()
                    .build(new ByteBufOutputStream(pcapBuffer)));

            assertTrue(embeddedChannel.writeOutbound(outbound.retainedDuplicate()));
            DatagramPacket read = embeddedChannel.readOutbound();
            assertEquals(outbound.content(), read.content());
            read.release();

            assertTrue(embeddedChannel.writeInbound(inbound.retainedDuplicate()));
            read = embeddedChannel.readInbound();
            assertEquals(inbound.content(), read.content());
            read.release();

            // Verify only the PCAP global header was written. Large UDP payload should be discarded
            assertEquals(24, pcapBuffer.readableBytes());

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            pcapBuffer.release();
            payload.release();
            outbound.release();
            inbound.release();
        }
    }

    @Test
    public void udpExceptionCaught() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        try {
            final RuntimeException exception = new RuntimeException();

            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            CloseDetectingByteBufOutputStream outputStream = new CloseDetectingByteBufOutputStream(pcapBuffer);
            // We fake a client
            EmbeddedChannel embeddedChannel = new EmbeddedDatagramChannel(clientAddr, serverAddr);
            embeddedChannel.pipeline().addLast(PcapWriteHandler.builder()
                    .build(outputStream));
            embeddedChannel.pipeline().fireExceptionCaught(exception);

            assertTrue(outputStream.closeCalled());

            // Verify only the PCAP global header was written.
            assertEquals(24, pcapBuffer.readableBytes());

            // Verify thrown exception
            try {
                embeddedChannel.checkException();
                fail();
            } catch (Throwable t) {
                assertSame(exception, t);
            }

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            pcapBuffer.release();
        }
    }

    @Test
    public void tcpV4SharedOutputStreamTest() throws Exception {
        tcpV4(true, true);
    }

    @Test
    public void tcpV4NoGlobalHeaderOutputStream() throws Exception {
        tcpV4(false, false);
    }

    @Test
    public void tcpV4NonOutputStream() throws Exception {
        tcpV4(false, true);
    }

    private static void tcpV4(final boolean sharedOutputStream, final boolean writeGlobalHeaders) throws Exception {
        final ByteBuf byteBuf = Unpooled.buffer();
        final ByteBuf payload = Unpooled.wrappedBuffer("Meow".getBytes());
        try {
            EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

            // Configure the echo server
            ServerBootstrap sb = new ServerBootstrap();
            final CountDownLatch serverLatch = new CountDownLatch(1);
            sb.group(group)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(PcapWriteHandler.builder().sharedOutputStream(sharedOutputStream)
                                    .writePcapGlobalHeader(writeGlobalHeaders)
                                    .build(new ByteBufOutputStream(byteBuf)));
                            p.addLast(new ChannelInboundHandlerAdapter() {
                                private int read;
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf buf = (ByteBuf) msg;
                                    read += buf.readableBytes();
                                    ctx.writeAndFlush(buf);
                                    if (read == payload.readableBytes()) {
                                        serverLatch.countDown();
                                    }
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
            final CountDownLatch clientLatch = new CountDownLatch(1);
            cb.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new ChannelInboundHandlerAdapter() {
                                private int read;
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf buf = (ByteBuf) msg;
                                    read += buf.readableBytes();
                                    buf.release();
                                    if (read == payload.readableBytes()) {
                                        clientLatch.countDown();
                                    }
                                }

                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    ctx.writeAndFlush(payload.copy());
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    ctx.close();
                                }
                            });
                        }
                    });

            InetSocketAddress serverAddress = (InetSocketAddress) serverChannelFuture.channel().localAddress();
            // Start the client.
            ChannelFuture clientChannelFuture = cb.connect(serverAddress).sync();
            assertTrue(clientChannelFuture.isSuccess());

            InetSocketAddress clientAddress = (InetSocketAddress) clientChannelFuture.channel().localAddress();

            assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
            assertTrue(clientLatch.await(5, TimeUnit.SECONDS));

            clientChannelFuture.channel().close().sync();
            serverChannelFuture.channel().close().sync();

            // Shut down all event loops to terminate all threads.
            assertTrue(group.shutdownGracefully().sync().isSuccess());

            // if sharedOutputStream is true or writeGlobalHeaders is false, we don't verify the global headers.
            verifyTcpHandshakeCapture(
                    writeGlobalHeaders && !sharedOutputStream,
                    byteBuf,
                    serverAddress,
                    clientAddress);

            verifyTcpCapture(
                    byteBuf, payload,
                    serverAddress,
                    clientAddress,
                    1, 1); //After handshake, sequence and ack are 1

            verifyTcpCapture(
                    byteBuf, payload,
                    clientAddress,
                    serverAddress,
                    1, 5); //Server has read 4 bytes so its ack is now 5

            verifyTcpCloseCapture(byteBuf,
                    serverAddress,
                    clientAddress,
                    5, 5); //Server and client have both read 4 bytes
        } finally {
            byteBuf.release();
            payload.release();
        }
    }

    @Test
    public void embeddedTcpClient() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        //Differing size payloads so that sequence numbers and ack numbers are different
        final ByteBuf readPayload = Unpooled.wrappedBuffer("Read".getBytes());
        final ByteBuf writePayload = Unpooled.wrappedBuffer("Write".getBytes());

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                    PcapWriteHandler.builder()
                            .forceTcpChannel(serverAddr, clientAddr, false)
                            .build(new ByteBufOutputStream(pcapBuffer))
            );

            assertTrue(embeddedChannel.writeInbound(readPayload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readInbound();
            assertEquals(readPayload, read);
            read.release();

            assertTrue(embeddedChannel.writeOutbound(writePayload.retainedDuplicate()));
            read = embeddedChannel.readOutbound();
            assertEquals(writePayload, read);
            read.release();

            assertFalse(embeddedChannel.finishAndReleaseAll());

            // Verify the capture data
            verifyTcpHandshakeCapture(true, pcapBuffer, serverAddr, clientAddr);

            //Verify client read
            verifyTcpCapture(pcapBuffer, readPayload,
                    clientAddr, serverAddr,
                    1, 1);

            //Verify client write
            verifyTcpCapture(pcapBuffer, writePayload,
                    serverAddr, clientAddr,
                    1, 5);

            verifyTcpCloseCapture(pcapBuffer, serverAddr, clientAddr,
                    6, 5); //Client has received 4 bytes and sent 5 bytes
        } finally {
            pcapBuffer.release();
            readPayload.release();
            writePayload.release();
        }
    }

    @Test
    public void embeddedTcpServer() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        //Differing size payloads so that sequence numbers and ack numbers are different
        final ByteBuf readPayload = Unpooled.wrappedBuffer("Read".getBytes());
        final ByteBuf writePayload = Unpooled.wrappedBuffer("Write".getBytes());

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                    PcapWriteHandler.builder()
                            .forceTcpChannel(serverAddr, clientAddr, true)
                            .build(new ByteBufOutputStream(pcapBuffer))
            );

            assertTrue(embeddedChannel.writeInbound(readPayload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readInbound();
            assertEquals(readPayload, read);
            read.release();

            assertTrue(embeddedChannel.writeOutbound(writePayload.retainedDuplicate()));
            read = embeddedChannel.readOutbound();
            assertEquals(writePayload, read);
            read.release();

            assertFalse(embeddedChannel.finishAndReleaseAll());

            // Verify the capture data
            verifyTcpHandshakeCapture(true, pcapBuffer, serverAddr, clientAddr);

            //Verify server read
            verifyTcpCapture(pcapBuffer, readPayload,
                    serverAddr, clientAddr,
                    1, 1);

            //Verify server write
            verifyTcpCapture(pcapBuffer, writePayload,
                    clientAddr, serverAddr,
                    1, 5);

            verifyTcpCloseCapture(pcapBuffer, serverAddr, clientAddr,
                    5, 6); //Client has received 5 bytes and sent 4
        } finally {
            pcapBuffer.release();
            readPayload.release();
            writePayload.release();
        }
    }

    @Test
    public void tcpLargePayload() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        String payloadString = new String(new char[65495 + 1]).replace('\0', 'X');
        final ByteBuf payload = Unpooled.wrappedBuffer(payloadString.getBytes());
        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                    PcapWriteHandler.builder()
                            .forceTcpChannel(serverAddr, clientAddr, false)
                            .build(new ByteBufOutputStream(pcapBuffer))
            );

            assertTrue(embeddedChannel.writeOutbound(payload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readOutbound();
            assertEquals(payload, read);
            read.release();

            assertFalse(embeddedChannel.finishAndReleaseAll());

            // Verify the capture data
            verifyTcpHandshakeCapture(true, pcapBuffer, serverAddr, clientAddr);
            //Verify client write
            verifyTcpCapture(pcapBuffer, payload.slice(0, 65495),
                    serverAddr, clientAddr,
                    1, 1);
            //Verify client write
            verifyTcpCapture(pcapBuffer, payload.slice(65495, 1),
                    serverAddr, clientAddr,
                    65496, 1);

            verifyTcpCloseCapture(pcapBuffer, serverAddr, clientAddr,
                    65497, 1); //Client has received 4 bytes and sent 5 bytes
        } finally {
            pcapBuffer.release();
            payload.release();
        }
    }

    @Test
    public void tcpZeroByteCaptured() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.buffer();

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                    PcapWriteHandler.builder()
                            .forceTcpChannel(serverAddr, clientAddr, false)
                            .captureZeroByte(true) //Enable zero byte capture
                            .build(new ByteBufOutputStream(pcapBuffer))
            );

            assertTrue(embeddedChannel.writeOutbound(payload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readOutbound();
            assertEquals(payload, read);
            read.release();

            assertTrue(embeddedChannel.writeInbound(payload.retainedDuplicate()));
            read = embeddedChannel.readInbound();
            assertEquals(payload, read);

            assertFalse(embeddedChannel.finishAndReleaseAll());

            // Verify the capture data
            verifyTcpHandshakeCapture(true, pcapBuffer, serverAddr, clientAddr);

            //Verify client write empty payload
            verifyTcpCapture(pcapBuffer, payload,
                    serverAddr, clientAddr,
                    1, 1);

            //Verify client read empty payload
            verifyTcpCapture(pcapBuffer, payload,
                    clientAddr, serverAddr,
                    1, 1);

            verifyTcpCloseCapture(pcapBuffer, serverAddr, clientAddr,
                    1, 1);
        } finally {
            pcapBuffer.release();
            payload.release();
        }
    }

    @Test
    public void tcpZeroByteDiscarded() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final ByteBuf payload = Unpooled.buffer();

        try {
            InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
            InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

            EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                    PcapWriteHandler.builder()
                            .forceTcpChannel(serverAddr, clientAddr, false)
                            .build(new ByteBufOutputStream(pcapBuffer))
            );

            assertTrue(embeddedChannel.writeOutbound(payload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readOutbound();
            assertEquals(payload, read);
            read.release();

            assertTrue(embeddedChannel.writeInbound(payload.retainedDuplicate()));
            read = embeddedChannel.readInbound();
            assertEquals(payload, read);
            read.release();

            assertFalse(embeddedChannel.finishAndReleaseAll());

            //Verify client discard empty payloads. Pcap should only contain header, syn, and fin messages

            verifyTcpHandshakeCapture(true, pcapBuffer, serverAddr, clientAddr);
            verifyTcpCloseCapture(pcapBuffer, serverAddr, clientAddr,
                    1, 1);
        } finally {
            pcapBuffer.release();
            payload.release();
        }
    }

    @Test
    public void tcpExceptionCaught() {
        final ByteBuf pcapBuffer = Unpooled.buffer();
        final RuntimeException exception = new RuntimeException();

        InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

        CloseDetectingByteBufOutputStream outputStream = new CloseDetectingByteBufOutputStream(pcapBuffer);
        final EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        super.channelActive(ctx);

                        ctx.fireExceptionCaught(exception);
                    }
                },
                PcapWriteHandler.builder()
                                .forceTcpChannel(serverAddr, clientAddr, false)
                                .build(outputStream)
        );

        // Verify tcp handshake, tcp rst, and stream close

        assertTrue(outputStream.closeCalled());

        verifyTcpHandshakeCapture(true, pcapBuffer, serverAddr, clientAddr);

        // Verify rst
        ByteBuf tcpPacket = verifyTcpBaseHeaders(pcapBuffer, serverAddr, 0);

        assertEquals(clientAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(serverAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
        assertEquals(1, tcpPacket.readUnsignedInt()); // Sequence number
        assertEquals(1, tcpPacket.readUnsignedInt()); // Ack number
        tcpPacket.readByte(); // Offset/Reserved
        assertEquals(20, tcpPacket.readByte()); // RST control bit
        assertEquals(65535, tcpPacket.readUnsignedShort()); // Window Size
        assertEquals(1, tcpPacket.readUnsignedShort()); // Checksum
        assertEquals(0, tcpPacket.readUnsignedShort()); // Urgent Pointer

        // Verify thrown exception
        try {
            embeddedChannel.checkException();
            fail();
        } catch (Throwable t) {
            assertSame(exception, t);
        } finally {
            pcapBuffer.release();
        }

        assertFalse(embeddedChannel.finishAndReleaseAll());
    }

    @Test
    public void writePcapGreaterThan4Gb() {
        InetSocketAddress serverAddr = new InetSocketAddress("1.1.1.1", 1234);
        InetSocketAddress clientAddr = new InetSocketAddress("2.2.2.2", 3456);

        class RecordingOutputStream extends OutputStream {
            private long bytesWritten;
            private ByteArrayOutputStream out;
            @Override
            public void write(int b) {
                bytesWritten++;
                if (out != null) {
                    out.write(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                bytesWritten += len;
                if (out != null) {
                    out.write(b, off, len);
                }
            }

            void record() {
                out = new ByteArrayOutputStream();
            }

            void reset() {
                out = null;
            }

            byte[] recordedBytes() {
                return out.toByteArray();
            }

            long bytesWritten() {
                return bytesWritten;
            }
        }

        RecordingOutputStream outputStream = new RecordingOutputStream();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                new DiscardingWritesAndFlushesHandler(), //Discard writes/flushes
                PcapWriteHandler.builder()
                                .forceTcpChannel(serverAddr, clientAddr, true)
                                .build(outputStream),
                new DiscardingReadsHandler() //Discard reads
        );

        int chunkSize = 0xFFFF - 40; // 40 bytes for header data
        String payloadString = new String(new char[chunkSize]).replace('\0', 'X');
        final ByteBuf payload = Unpooled.wrappedBuffer(payloadString.getBytes());

        try {
            long fourGB = 0xFFFFFFFFL;

            // Let's send 4 GiB inbound, ...
            long msgCount = (fourGB / chunkSize) + 2;
            for (int i = 0; i < msgCount; i++) {
                //Only store the last data/ack
                if (i == msgCount - 1) {
                    outputStream.record();
                }
                embeddedChannel.writeInbound(payload.retainedDuplicate());
            }

            //Validate the wrapped number
            long sequenceNumber = ((msgCount - 1) * chunkSize) - fourGB;
            long ackNumber = 1;
            ByteBuf buf = Unpooled.wrappedBuffer(outputStream.recordedBytes());
            verifyTcpCapture(buf, payload, serverAddr, clientAddr, sequenceNumber, ackNumber);
            buf.release();
            outputStream.reset();

            // ... and 4 GiB outbound.
            for (int i = 0; i < msgCount; i++) {
                //Only store the last data/ack
                if (i == msgCount - 1) {
                    outputStream.record();
                }
                embeddedChannel.writeOutbound(payload.retainedDuplicate());
            }

            //Validate the wrapped number
            ackNumber = sequenceNumber + chunkSize;
            buf = Unpooled.wrappedBuffer(outputStream.recordedBytes());
            verifyTcpCapture(buf, payload, clientAddr, serverAddr, sequenceNumber, ackNumber);
            buf.release();
            outputStream.reset();
            assertThat(outputStream.bytesWritten()).isGreaterThan(2 * (fourGB + 1000));

            assertFalse(embeddedChannel.finishAndReleaseAll());
        } finally {
            payload.release();
        }
    }

    @Test
    public void writerStateTest() throws Exception {
        final ByteBuf payload = Unpooled.wrappedBuffer("Meow".getBytes());
        try {
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
            assertTrue(embeddedChannel.writeInbound(payload.retainedDuplicate()));
            ByteBuf read = embeddedChannel.readInbound();
            assertEquals(payload, read);
            read.release();

            assertTrue(embeddedChannel.writeOutbound(payload.retainedDuplicate()));
            read = embeddedChannel.readOutbound();
            assertEquals(payload, read);
            read.release();

            // State is now WRITING because we attached Handler to 'EmbeddedChannel'.
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
        } finally {
            payload.release();
        }
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
        assertFalse(pcapWriteHandler.isWriting());

        // Write 100 times. No writes should be called in OutputStream.
        for (int i = 0; i < 100; i++) {
            assertTrue(embeddedChannel.writeInbound(Unpooled.wrappedBuffer(payload)));
        }

        // Current stats and previous stats should be same.
        assertEquals(initialWritesCalled, discardingStatsOutputStream.writesCalled());

        // Let's resume the Pcap now.
        pcapWriteHandler.resume();
        assertEquals(State.WRITING, pcapWriteHandler.state());
        assertTrue(pcapWriteHandler.isWriting());

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

    @Test
    public void globalHeadersStandalone() throws IOException {
        ByteBuf pcapBuffer = Unpooled.buffer();
        ByteBufOutputStream outputStream = new ByteBufOutputStream(pcapBuffer, true);
        PcapWriteHandler.writeGlobalHeader(outputStream);

        verifyGlobalHeaders(pcapBuffer);

        outputStream.close();
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

    private static void verifyTcpCapture(ByteBuf byteBuf, ByteBuf expectedPayload,
                                         InetSocketAddress destinationAddr, InetSocketAddress sourceAddr,
                                         long sequenceNumber,
                                         long ackNumber) {
        int expectedPayloadLength = expectedPayload.readableBytes();

        // Verify data
        ByteBuf tcpPacket = verifyTcpBaseHeaders(byteBuf, destinationAddr, expectedPayloadLength);

        assertEquals(sourceAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(destinationAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
        assertEquals(sequenceNumber, tcpPacket.readUnsignedInt()); // Sequence number
        assertEquals(ackNumber, tcpPacket.readUnsignedInt()); // Ack number
        tcpPacket.readByte(); // Offset/Reserved
        assertEquals(16, tcpPacket.readByte()); // ACK Control bit
        assertEquals(65535, tcpPacket.readUnsignedShort()); // Window Size
        assertEquals(1, tcpPacket.readUnsignedShort()); // Checksum
        assertEquals(0, tcpPacket.readUnsignedShort()); // Urgent Pointer
        assertArrayEquals(ByteBufUtil.getBytes(expectedPayload), ByteBufUtil.getBytes(tcpPacket));

        // Verify ACK
        tcpPacket = verifyTcpBaseHeaders(byteBuf, sourceAddr, 0);

        assertEquals(destinationAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(sourceAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
        assertEquals(ackNumber, tcpPacket.readUnsignedInt()); // Sequence number
        assertEquals(sequenceNumber + expectedPayloadLength, tcpPacket.readUnsignedInt()); // Ack number
        tcpPacket.readByte(); // Offset/Reserved
        assertEquals(16, tcpPacket.readByte()); // ACK Control bit
        assertEquals(65535, tcpPacket.readUnsignedShort()); // Window Size
        assertEquals(1, tcpPacket.readUnsignedShort()); // Checksum
        assertEquals(0, tcpPacket.readUnsignedShort()); // Urgent Pointer
    }

    private static void verifyTcpHandshakeCapture(boolean verifyGlobalHeaders, ByteBuf byteBuf,
                                                  InetSocketAddress serverAddr, InetSocketAddress clientAddr) {
        if (verifyGlobalHeaders) {
            verifyGlobalHeaders(byteBuf);
        }

        // Verify syn

        ByteBuf tcpPacket = verifyTcpBaseHeaders(byteBuf, serverAddr, 0);

        assertEquals(clientAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(serverAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
        assertEquals(0, tcpPacket.readUnsignedInt()); // Sequence number
        assertEquals(0, tcpPacket.readUnsignedInt()); // Ack number
        tcpPacket.readByte(); // Offset/Reserved
        assertEquals(2, tcpPacket.readByte()); // SYN control bit
        assertEquals(65535, tcpPacket.readUnsignedShort()); // Window Size
        assertEquals(1, tcpPacket.readUnsignedShort()); // Checksum
        assertEquals(0, tcpPacket.readUnsignedShort()); // Urgent Pointer

        // Verify syn+ack

        tcpPacket = verifyTcpBaseHeaders(byteBuf, clientAddr, 0);

        assertEquals(serverAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(clientAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
        assertEquals(0, tcpPacket.readUnsignedInt()); // Sequence number
        assertEquals(1, tcpPacket.readUnsignedInt()); // Ack number
        tcpPacket.readByte(); // Offset/Reserved
        assertEquals(18, tcpPacket.readByte()); // SYN+ACK control bit
        assertEquals(65535, tcpPacket.readUnsignedShort()); // Window Size
        assertEquals(1, tcpPacket.readUnsignedShort()); // Checksum
        assertEquals(0, tcpPacket.readUnsignedShort()); // Urgent Pointer

        // Verify ack
        tcpPacket = verifyTcpBaseHeaders(byteBuf, serverAddr, 0);

        assertEquals(clientAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(serverAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
        assertEquals(1, tcpPacket.readUnsignedInt()); // Sequence number
        assertEquals(1, tcpPacket.readUnsignedInt()); // Ack number
        tcpPacket.readByte(); // Offset/Reserved
        assertEquals(16, tcpPacket.readByte()); // ACK control bit
        assertEquals(65535, tcpPacket.readUnsignedShort()); // Window Size
        assertEquals(1, tcpPacket.readUnsignedShort()); // Checksum
        assertEquals(0, tcpPacket.readUnsignedShort()); // Urgent Pointer
    }

    private static void verifyTcpCloseCapture(ByteBuf byteBuf,
                                              InetSocketAddress serverAddr, InetSocketAddress clientAddr,
                                              int sequenceNumber, int ackNumber) {
        // Verify fin
        ByteBuf tcpPacket = verifyTcpBaseHeaders(byteBuf, serverAddr, 0);

        assertEquals(clientAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(serverAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
        assertEquals(sequenceNumber, tcpPacket.readUnsignedInt()); // Sequence number
        assertEquals(ackNumber, tcpPacket.readUnsignedInt()); // Ack number
        tcpPacket.readByte(); // Offset/Reserved
        assertEquals(17, tcpPacket.readByte()); // FIN+ACK control bit
        assertEquals(65535, tcpPacket.readUnsignedShort()); // Window Size
        assertEquals(1, tcpPacket.readUnsignedShort()); // Checksum
        assertEquals(0, tcpPacket.readUnsignedShort()); // Urgent Pointer

        // Verify fin+ack
        tcpPacket = verifyTcpBaseHeaders(byteBuf, clientAddr, 0);

        assertEquals(serverAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(clientAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
        assertEquals(ackNumber, tcpPacket.readUnsignedInt()); // Sequence number
        assertEquals(sequenceNumber, tcpPacket.readUnsignedInt()); // Ack number
        tcpPacket.readByte(); // Offset/Reserved
        assertEquals(17, tcpPacket.readByte()); // FIN+ACK control bit
        assertEquals(65535, tcpPacket.readUnsignedShort()); // Window Size
        assertEquals(1, tcpPacket.readUnsignedShort()); // Checksum
        assertEquals(0, tcpPacket.readUnsignedShort()); // Urgent Pointer

        // Verify ack
        tcpPacket = verifyTcpBaseHeaders(byteBuf, serverAddr, 0);

        assertEquals(clientAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Source Port
        assertEquals(serverAddr.getPort() & 0xffff, tcpPacket.readUnsignedShort()); // Destination Port
        assertEquals(sequenceNumber + 1, tcpPacket.readUnsignedInt()); // Sequence number
        assertEquals(ackNumber + 1, tcpPacket.readUnsignedInt()); // Ack number
        tcpPacket.readByte(); // Offset/Reserved
        assertEquals(16, tcpPacket.readByte()); // ACK control bit
        assertEquals(65535, tcpPacket.readUnsignedShort()); // Window Size
        assertEquals(1, tcpPacket.readUnsignedShort()); // Checksum
        assertEquals(0, tcpPacket.readUnsignedShort()); // Urgent Pointer
    }

    private static ByteBuf verifyTcpBaseHeaders(ByteBuf byteBuf, InetSocketAddress destinationAddress,
                                                int expectedPayloadLength) {
        // Verify Pcap Packet Header
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        assertEquals(54 + expectedPayloadLength, byteBuf.readInt()); // Length of Packet Saved In Pcap
        assertEquals(54 + expectedPayloadLength, byteBuf.readInt()); // Actual Length of Packet

        // -------------------------------------------- Verify Packet --------------------------------------------
        // Verify Ethernet Packet
        ByteBuf ethernetPacket = byteBuf.readSlice(54 + expectedPayloadLength);
        ByteBuf dstMac = ethernetPacket.readSlice(6);
        ByteBuf srcMac = ethernetPacket.readSlice(6);
        assertArrayEquals(new byte[] { 0, 0, 94, 0, 83, -1 }, ByteBufUtil.getBytes(dstMac));
        assertArrayEquals(new byte[] { 0, 0, 94, 0, 83, 0 }, ByteBufUtil.getBytes(srcMac));
        assertEquals(0x0800, ethernetPacket.readShort());

        // Verify IPv4 Packet
        ByteBuf ipv4Packet = ethernetPacket.readSlice(40 + expectedPayloadLength);
        assertEquals(0x45, ipv4Packet.readByte());    // Version + IHL
        assertEquals(0x00, ipv4Packet.readByte());    // DSCP
        assertEquals(40 + expectedPayloadLength, ipv4Packet.readUnsignedShort());     // Length
        assertEquals(0x0000, ipv4Packet.readShort()); // Identification
        assertEquals(0x0000, ipv4Packet.readShort()); // Fragment
        assertEquals((byte) 0xff, ipv4Packet.readByte());      // TTL
        assertEquals((byte) 6, ipv4Packet.readByte());        // Protocol
        assertEquals(0, ipv4Packet.readShort());      // Checksum
        // Source IPv4 Address
        ipv4Packet.readInt();
        // Destination IPv4 Address
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) destinationAddress.getAddress()), ipv4Packet.readInt());
        return ipv4Packet.readSlice(20 + expectedPayloadLength);
    }

    private static void verifyUdpCapture(boolean verifyGlobalHeaders, ByteBuf byteBuf, ByteBuf expectedPayload,
                                         InetSocketAddress remoteAddress, InetSocketAddress localAddress) {
        if (verifyGlobalHeaders) {
            verifyGlobalHeaders(byteBuf);
        }

        int expectedPayloadLength = expectedPayload.readableBytes();

        // Verify Pcap Packet Header
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        byteBuf.readInt(); // Just read, we don't care about timestamps for now
        assertEquals(42 + expectedPayloadLength, byteBuf.readInt()); // Length of Packet Saved In Pcap
        assertEquals(42 + expectedPayloadLength, byteBuf.readInt()); // Actual Length of Packet

        // -------------------------------------------- Verify Packet --------------------------------------------
        // Verify Ethernet Packet
        ByteBuf ethernetPacket = byteBuf.readBytes(42 + expectedPayloadLength);
        ByteBuf dstMac = ethernetPacket.readBytes(6);
        ByteBuf srcMac = ethernetPacket.readBytes(6);
        assertArrayEquals(new byte[] { 0, 0, 94, 0, 83, -1 }, ByteBufUtil.getBytes(dstMac));
        assertArrayEquals(new byte[] { 0, 0, 94, 0, 83, 0 }, ByteBufUtil.getBytes(srcMac));
        assertEquals(0x0800, ethernetPacket.readShort());

        // Verify IPv4 Packet
        ByteBuf ipv4Packet = ethernetPacket.readBytes(28 + expectedPayloadLength);
        assertEquals(0x45, ipv4Packet.readByte());    // Version + IHL
        assertEquals(0x00, ipv4Packet.readByte());    // DSCP
        assertEquals(28 + expectedPayloadLength, ipv4Packet.readShort());     // Length
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
        ByteBuf udpPacket = ipv4Packet.readBytes(8 + expectedPayloadLength);
        assertEquals(localAddress.getPort() & 0xffff, udpPacket.readUnsignedShort()); // Source Port
        assertEquals(remoteAddress.getPort() & 0xffff, udpPacket.readUnsignedShort()); // Destination Port
        assertEquals(8 + expectedPayloadLength, udpPacket.readShort());     // Length
        assertEquals(0x0001, udpPacket.readShort()); // Checksum

        // Verify Payload
        ByteBuf payload = udpPacket.readBytes(expectedPayloadLength);
        assertArrayEquals(ByteBufUtil.getBytes(expectedPayload), ByteBufUtil.getBytes(payload)); // Payload

        // Release all ByteBuf
        assertTrue(dstMac.release());
        assertTrue(srcMac.release());
        if (expectedPayloadLength > 0) {
            assertTrue(payload.release());
        }
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

    static final class DiscardingReadsHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            //Discard
            ReferenceCountUtil.release(msg);
        }
    }

    static class DiscardingWritesAndFlushesHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void flush(ChannelHandlerContext ctx) {
            //Discard
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            //Discard
            ReferenceCountUtil.release(msg);
            promise.setSuccess();
        }
    }
}
