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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class PCAPWriteHandler extends ChannelDuplexHandler {

    private final InternalLogger logger = InternalLoggerFactory.getInstance(PCAPWriteHandler.class);

    /**
     * TCP Sender Segment Number.
     */
    private int sendSegmentNumber = 1;

    /**
     * TCP Receiver Segment Number
     */
    private int receiveSegmentNumber = 1;

    /**
     * Source Address [TCP ONLY]
     */
    private InetSocketAddress srcAddr;

    /**
     * Destination Address [TCP ONLY]
     */
    private InetSocketAddress dstAddr;

    private final OutputStream outputStream;
    private PCapWriter pCapWriter;
    private final boolean isTCP;
    private final boolean isServer;
    private final boolean captureZeroByte;

    /**
     * Create new {@link PCAPWriteHandler} Instance.
     * {@code captureZeroByte} is set to {@code false}.
     *
     * @param outputStream OutputStream where Pcap data will be written
     * @param isTCP        {@code true} to capture TCP packets
     * @param isServer     {@code true} if we'll capture packet as server
     * @throws IOException If {@link OutputStream#write(byte[])} throws an exception
     */
    public PCAPWriteHandler(OutputStream outputStream, boolean isTCP, boolean isServer) throws IOException {
        this(outputStream, isTCP, isServer, false);
    }

    /**
     * Create new {@link PCAPWriteHandler} Instance
     *
     * @param outputStream    OutputStream where Pcap data will be written
     * @param isTCP           {@code true} to capture TCP packets
     * @param isServer        {@code true} if we'll capture packet as server
     * @param captureZeroByte {@code true} if we'll capture packets with 0 bytes
     * @throws IOException If {@link OutputStream#write(byte[])} throws an exception
     */
    public PCAPWriteHandler(OutputStream outputStream, boolean isTCP, boolean isServer, boolean captureZeroByte) throws IOException {
        this.outputStream = outputStream;
        this.isTCP = isTCP;
        this.isServer = isServer;
        this.captureZeroByte = captureZeroByte;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.pCapWriter = new PCapWriter(this.outputStream, ctx.alloc().buffer());

        /*
         * If `isServer` is set to true, it means we'll be receiving data from client.
         * In this case, Source Address will be `remoteAddress` and Destination Address
         * will be `localAddress`.
         *
         * If `isServer` is set to false, it means we'll be sending data to server.
         * In this case, Source Address will be `localAddress` and Destination Address
         * will be `remoteAddress`.
         */
        if (isTCP) {
            if (isServer) {
                srcAddr = (InetSocketAddress) ctx.channel().remoteAddress();
                dstAddr = (InetSocketAddress) ctx.channel().localAddress();
            } else {
                srcAddr = (InetSocketAddress) ctx.channel().localAddress();
                dstAddr = (InetSocketAddress) ctx.channel().remoteAddress();
            }
        }

        // If `isTCP` is true, then we'll simulate a fake handshake.
        if (isTCP) {
            logger.debug("Starting Fake TCP 3-Way Handshake");

            ByteBuf tcpBuf = ctx.alloc().buffer();

            // Write SYN with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, 0, 0, srcAddr.getPort(), dstAddr.getPort(), TCPPacket.Flag.SYN);
            completeTCPWrite(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            // Write SYN+ACK with Reversed Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, 0, 1, dstAddr.getPort(), srcAddr.getPort(), TCPPacket.Flag.SYN,
                    TCPPacket.Flag.ACK);
            completeTCPWrite(dstAddr, srcAddr, tcpBuf, ctx.alloc());

            // Write ACK with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, 1, 1, srcAddr.getPort(), dstAddr.getPort(), TCPPacket.Flag.ACK);
            completeTCPWrite(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            tcpBuf.release();

            logger.debug("Finished Fake TCP 3-Way Handshake");
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        writePacket(ctx, msg, false);
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        writePacket(ctx, msg, true);
        super.write(ctx, msg, promise);
    }

    private void writePacket(ChannelHandlerContext ctx, Object msg, boolean isWriteOperation) throws Exception {
        if (msg instanceof ByteBuf) {

            // If bytes are 0 and `captureZeroByte` is false, we won't capture this.
            if (((ByteBuf) msg).readableBytes() == 0 && !captureZeroByte) {
                logger.debug("Discarding Zero Byte TCP Packet. isWriteOperation {}", isWriteOperation);
                return;
            }

            ByteBuf packet = ((ByteBuf) msg).duplicate();
            int bytes = packet.readableBytes();

            ByteBuf tcpBuf = ctx.alloc().buffer();

            if (isWriteOperation) {
                TCPPacket.writePacket(tcpBuf, packet, sendSegmentNumber, receiveSegmentNumber, srcAddr.getPort(),
                        dstAddr.getPort(), TCPPacket.Flag.ACK);
                completeTCPWrite(srcAddr, dstAddr, tcpBuf, ctx.alloc());
                logTCP(true, bytes, sendSegmentNumber, receiveSegmentNumber, srcAddr, dstAddr, false);

                sendSegmentNumber += bytes;

                TCPPacket.writePacket(tcpBuf, null, receiveSegmentNumber, sendSegmentNumber, dstAddr.getPort(),
                        srcAddr.getPort(), TCPPacket.Flag.ACK);
                completeTCPWrite(dstAddr, srcAddr, tcpBuf, ctx.alloc());
                logTCP(true, bytes, sendSegmentNumber, receiveSegmentNumber, dstAddr, srcAddr, true);
            } else {
                TCPPacket.writePacket(tcpBuf, packet, receiveSegmentNumber, sendSegmentNumber, dstAddr.getPort(),
                        srcAddr.getPort(), TCPPacket.Flag.ACK);
                completeTCPWrite(dstAddr, srcAddr, tcpBuf, ctx.alloc());
                logTCP(false, bytes, receiveSegmentNumber, sendSegmentNumber, dstAddr, srcAddr, false);

                receiveSegmentNumber += bytes;

                TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber, receiveSegmentNumber, srcAddr.getPort(),
                        dstAddr.getPort(), TCPPacket.Flag.ACK);
                completeTCPWrite(srcAddr, dstAddr, tcpBuf, ctx.alloc());
                logTCP(false, bytes, sendSegmentNumber, receiveSegmentNumber, srcAddr, dstAddr, true);
            }

            tcpBuf.release();
        } else if (msg instanceof DatagramPacket) {
            handleUDP(ctx, ((DatagramPacket) msg).duplicate());
        } else {
            logger.error("Discarding Pcap Write for Object {}", msg);
        }
    }

    private void logTCP(boolean isWriteOperation, int bytes, int sendSegmentNumber, int receiveSegmentNumber,
                        InetSocketAddress srcAddr, InetSocketAddress dstAddr, boolean AckOnly) {
        if (AckOnly) {
            logger.debug("Writing TCP ACK, isWriteOperation {}, Segment Number {}, Ack Number {}, Src Addr {}, "
                    + "Dst Addr {}", isWriteOperation, sendSegmentNumber, receiveSegmentNumber, dstAddr, srcAddr);
        } else {
            logger.debug("Writing TCP Data of {} Bytes, isWriteOperation {}, Segment Number {}, Ack Number {}, " +
                            "Src Addr {}, Dst Addr {}", bytes, isWriteOperation, sendSegmentNumber,
                    receiveSegmentNumber, srcAddr, dstAddr);
        }
    }

    private void handleUDP(ChannelHandlerContext ctx, DatagramPacket datagramPacket) throws IOException {
        ByteBuf udpBuf = ctx.alloc().buffer();

        InetSocketAddress srcAddr = datagramPacket.sender();
        InetSocketAddress dstAddr = datagramPacket.recipient();

        logger.debug("Writing UDP Data of {} Bytes, Src Addr {}, Dst Addr {}", datagramPacket.content().readableBytes()
                ,srcAddr, dstAddr);

        UDPPacket.writePacket(udpBuf,
                datagramPacket.content(),
                srcAddr.getPort(),
                dstAddr.getPort());

        completeUDPWrite(srcAddr, dstAddr, udpBuf, ctx.alloc());

        udpBuf.release();
    }

    private void completeTCPWrite(InetSocketAddress srcAddr, InetSocketAddress dstAddr, ByteBuf tcpBuf,
                                  ByteBufAllocator byteBufAllocator) throws IOException {

        ByteBuf ipBuf = byteBufAllocator.buffer();
        ByteBuf ethernetBuf = byteBufAllocator.buffer();

        try {

            if (srcAddr.getAddress() instanceof Inet4Address) {
                IPPacket.writeTCPv4(ipBuf,
                        tcpBuf,
                        ipv4ToInt(srcAddr.getAddress()),
                        ipv4ToInt(dstAddr.getAddress()));

                EthernetPacket.writeIPv4(ethernetBuf, ipBuf);
            } else {
                IPPacket.writeTCPv6(ipBuf,
                        tcpBuf,
                        srcAddr.getAddress().getAddress(),
                        dstAddr.getAddress().getAddress());

                EthernetPacket.writeIPv6(ethernetBuf, ipBuf);
            }

            pCapWriter.writePacket(byteBufAllocator.buffer(), ethernetBuf);
        } catch (IOException ex) {
            tcpBuf.release();
            throw ex;
        } finally {
            ipBuf.release();
            ethernetBuf.release();
        }
    }

    private void completeUDPWrite(InetSocketAddress srcAddr, InetSocketAddress dstAddr, ByteBuf udpBuf,
                                  ByteBufAllocator byteBufAllocator) throws IOException {

        ByteBuf ipBuf = byteBufAllocator.buffer();
        ByteBuf ethernetBuf = byteBufAllocator.buffer();

        try {

            if (srcAddr.getAddress() instanceof Inet4Address) {
                IPPacket.writeUDPv4(ipBuf,
                        udpBuf,
                        ipv4ToInt(srcAddr.getAddress()),
                        ipv4ToInt(dstAddr.getAddress()));

                EthernetPacket.writeIPv4(ethernetBuf, ipBuf);
            } else {
                IPPacket.writeUDPv6(ipBuf,
                        udpBuf,
                        srcAddr.getAddress().getAddress(),
                        dstAddr.getAddress().getAddress());

                EthernetPacket.writeIPv6(ethernetBuf, ipBuf);
            }

            pCapWriter.writePacket(byteBufAllocator.buffer(), ethernetBuf);
        } catch (IOException ex) {
            udpBuf.release();
            throw ex;
        } finally {
            ipBuf.release();
            ethernetBuf.release();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

        // If `isTCP` is true, then we'll simulate a `FIN` flow.
        if (isTCP) {
            logger.debug("Starting Fake TCP FIN+ACK Flow to close connection");

            ByteBuf tcpBuf = ctx.alloc().buffer();

            // Write FIN+ACK with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber, receiveSegmentNumber, srcAddr.getPort(),
                    dstAddr.getPort(), TCPPacket.Flag.FIN, TCPPacket.Flag.ACK);
            completeTCPWrite(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            // Write FIN+ACK with Reversed Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, receiveSegmentNumber, sendSegmentNumber, dstAddr.getPort(),
                    srcAddr.getPort(), TCPPacket.Flag.FIN, TCPPacket.Flag.ACK);
            completeTCPWrite(dstAddr, srcAddr, tcpBuf, ctx.alloc());

            // Write ACK with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber + 1, receiveSegmentNumber + 1,
                    srcAddr.getPort(), dstAddr.getPort(), TCPPacket.Flag.ACK);
            completeTCPWrite(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            tcpBuf.release();

            logger.debug("Finished Fake TCP FIN+ACK Flow to close connection");
        }

        this.pCapWriter.close();
        super.handlerRemoved(ctx);
    }

    private int ipv4ToInt(InetAddress inetAddress) {
        byte[] octets = inetAddress.getAddress();
        assert octets.length == 4;

        return (octets[0] & 0xff) << 24 |
                (octets[1] & 0xff) << 16 |
                (octets[2] & 0xff) << 8 |
                (octets[3] & 0xff);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

        // If `isTCP` is true, then we'll simulate a `RST` flow.
        if (isTCP) {
            ByteBuf tcpBuf = ctx.alloc().buffer();

            // Write RST+ACK with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber, receiveSegmentNumber, srcAddr.getPort(),
                    dstAddr.getPort(), TCPPacket.Flag.RST, TCPPacket.Flag.ACK);
            completeTCPWrite(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            tcpBuf.release();

            logger.debug("Sent Fake TCP RST to close connection");
        }

        this.pCapWriter.close();
        cause.printStackTrace();
    }
}
