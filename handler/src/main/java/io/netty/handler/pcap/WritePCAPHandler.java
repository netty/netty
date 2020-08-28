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

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class WritePCAPHandler extends ChannelDuplexHandler {

    /**
     * TCP Sender Segment Number.
     */
    private int sendSegmentNumber = 1;

    /**
     * TCP Receiver Segment Number
     */
    private int receiveSegmentNumber = 1;

    /**
     * Source Address [TCP only]
     */
    private InetSocketAddress srcAddr;

    /**
     * Destination Address [TCP only]
     */
    private InetSocketAddress dstAddr;

    private final PCapWriter pCapWriter;
    private final boolean isTCP;
    private final boolean isServer;
    private final boolean captureZeroBytes;

    /**
     * Create new {@link WritePCAPHandler} Instance.
     * {@code captureZeroBytes} is set to false.
     *
     * @param outputStream OutputStream where Pcap data will be written
     * @param isTCP        {@code true} to capture TCP packets
     * @param isServer     {@code true} if we'll capture packet as server
     * @throws IOException If {@link OutputStream#write(byte[])} throws an exception
     */
    public WritePCAPHandler(OutputStream outputStream, boolean isTCP, boolean isServer) throws IOException {
        this(outputStream, isTCP, isServer, false);
    }

    /**
     * Create new {@link WritePCAPHandler} Instance
     *
     * @param outputStream     OutputStream where Pcap data will be written
     * @param isTCP            {@code true} to capture TCP packets
     * @param isServer         {@code true} if we'll capture packet as server
     * @param captureZeroBytes {@code true} if we'll capture packets with 0 bytes
     * @throws IOException If {@link OutputStream#write(byte[])} throws an exception
     */
    public WritePCAPHandler(OutputStream outputStream, boolean isTCP, boolean isServer, boolean captureZeroBytes) throws IOException {
        this.pCapWriter = new PCapWriter(outputStream);
        this.isTCP = isTCP;
        this.isServer = isServer;
        this.captureZeroBytes = captureZeroBytes;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        /*
         * If `isServer` is set to true, it means we'll be receiving data from client.
         * In this case, Source Address will be `remoteAddress` and Destination Address
         * will be `localAddress`.
         *
         * If `isServer` is set to false, it means we'll be sending data to server.
         * In this case, Source Address will be `localAddress` and Destination Address
         * will be `remoteAddress`.
         */
        if (isServer) {
            srcAddr = (InetSocketAddress) ctx.channel().remoteAddress();
            dstAddr = (InetSocketAddress) ctx.channel().localAddress();
        } else {
            srcAddr = (InetSocketAddress) ctx.channel().localAddress();
            dstAddr = (InetSocketAddress) ctx.channel().remoteAddress();
        }

        // If `isTCP` is true, then we'll simulate a fake handshake.
        if (isTCP) {
            ByteBuf tcpBuf = ctx.alloc().buffer();

            // Write SYN with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, 0, 0, srcAddr.getPort(), dstAddr.getPort(), TCPPacket.Flag.SYN);
            finalizeTCP(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            // Write SYN+ACK with Reversed Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, 0, 1, dstAddr.getPort(), srcAddr.getPort(), TCPPacket.Flag.SYN, TCPPacket.Flag.ACK);
            finalizeTCP(dstAddr, srcAddr, tcpBuf, ctx.alloc());

            // Write ACK with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, 1, 1, srcAddr.getPort(), dstAddr.getPort(), TCPPacket.Flag.ACK);
            finalizeTCP(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            tcpBuf.release();
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

    private void writePacket(ChannelHandlerContext ctx, Object msg, boolean isWrite) throws Exception {
        if (msg instanceof ByteBuf) {

            // If bytes are 0 and `captureZeroBytes` is false, we won't capture this.
            if (((ByteBuf) msg).readableBytes() == 0 && !captureZeroBytes) {
                return;
            }

            ByteBuf packet = ((ByteBuf) msg).duplicate();
            int bytes = packet.readableBytes();

            ByteBuf tcpBuf = ctx.alloc().buffer();

            if (isWrite) {
                TCPPacket.writePacket(tcpBuf, packet, sendSegmentNumber, receiveSegmentNumber, srcAddr.getPort(),
                        dstAddr.getPort(), TCPPacket.Flag.ACK);
                finalizeTCP(srcAddr, dstAddr, tcpBuf, ctx.alloc());

                sendSegmentNumber += bytes;

                TCPPacket.writePacket(tcpBuf, null, receiveSegmentNumber, sendSegmentNumber, dstAddr.getPort(),
                        srcAddr.getPort(), TCPPacket.Flag.ACK);
                finalizeTCP(dstAddr, srcAddr, tcpBuf, ctx.alloc());
            } else {
                TCPPacket.writePacket(tcpBuf, packet, receiveSegmentNumber, sendSegmentNumber, dstAddr.getPort(),
                        srcAddr.getPort(), TCPPacket.Flag.ACK);
                finalizeTCP(dstAddr, srcAddr, tcpBuf, ctx.alloc());

                receiveSegmentNumber += bytes;

                TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber, receiveSegmentNumber, srcAddr.getPort(),
                        dstAddr.getPort(), TCPPacket.Flag.ACK);
                finalizeTCP(srcAddr, dstAddr, tcpBuf, ctx.alloc());
            }

            tcpBuf.release();

        } else if (msg instanceof DatagramPacket) {
            handleUDP(ctx, ((DatagramPacket) msg).duplicate());
        }
    }

    private void handleUDP(ChannelHandlerContext ctx, DatagramPacket datagramPacket) throws IOException {
        ByteBuf udpBuf = ctx.alloc().buffer();
        ByteBuf ipBuf = ctx.alloc().buffer();
        ByteBuf ethernetBuf = ctx.alloc().buffer();

        try {

            InetSocketAddress srcAddr = datagramPacket.sender();
            InetSocketAddress dstAddr = datagramPacket.recipient();

            UDPPacket.writePacket(udpBuf,
                    datagramPacket.content(),
                    srcAddr.getPort(),
                    dstAddr.getPort());

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

            pCapWriter.writePacket(ctx.alloc().buffer(), ethernetBuf);
        } finally {
            udpBuf.release();
            ipBuf.release();
            ethernetBuf.release();
        }
    }

    private void finalizeTCP(InetSocketAddress srcAddr, InetSocketAddress dstAddr, ByteBuf tcpBuf,
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

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

        // If `isTCP` is true, then we'll simulate a `FIN` flow.
        if (isTCP) {
            ByteBuf tcpBuf = ctx.alloc().buffer();

            // Write FIN+ACK with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber, receiveSegmentNumber, srcAddr.getPort(),
                    dstAddr.getPort(), TCPPacket.Flag.FIN, TCPPacket.Flag.ACK);
            finalizeTCP(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            // Write FIN+ACK with Reversed Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, receiveSegmentNumber, sendSegmentNumber, dstAddr.getPort(),
                    srcAddr.getPort(), TCPPacket.Flag.FIN, TCPPacket.Flag.ACK);
            finalizeTCP(dstAddr, srcAddr, tcpBuf, ctx.alloc());

            // Write ACK with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber + 1, receiveSegmentNumber + 1, srcAddr.getPort(),
                    dstAddr.getPort(), TCPPacket.Flag.ACK);
            finalizeTCP(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            tcpBuf.release();
        }

        this.pCapWriter.close();
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

        // If `isTCP` is true, then we'll simulate a `RST` flow.
        if (isTCP) {
            ByteBuf tcpBuf = ctx.alloc().buffer();

            // Write RST+ACK with Normal Source and Destination Address
            TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber, receiveSegmentNumber, srcAddr.getPort(),
                    dstAddr.getPort(), TCPPacket.Flag.RST, TCPPacket.Flag.ACK);
            finalizeTCP(srcAddr, dstAddr, tcpBuf, ctx.alloc());

            tcpBuf.release();
        }

        this.pCapWriter.close();
        cause.printStackTrace();
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
