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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class WritePCAPHandler extends ChannelDuplexHandler {

    private final Protocol protocol;
    private final PCapFileWriter pCapFileWriter;

    public WritePCAPHandler(Protocol protocol, String destinationFile) throws IOException {
        this(protocol, new File(destinationFile));
    }

    public WritePCAPHandler(Protocol protocol, File destinationFile) throws IOException {
        this.protocol = protocol;
        this.pCapFileWriter = new PCapFileWriter(destinationFile);
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

    private void writePacket(ChannelHandlerContext ctx, Object msg, boolean isWrite) throws IOException {
        if (msg instanceof ByteBuf) {
            // Duplicate the ByteBuf
            ByteBuf packet = ((ByteBuf) msg).duplicate();

            InetSocketAddress srcAddr;
            InetSocketAddress dstAddr;
            /*
             * When `isWrite` it true, it means we're sending data from Netty to somewhere else.
             * In this case, source address will be `localAddress` and destination address will
             * be `remoteAddress`.
             *
             * When `isWrite` is false, it means we're reading data from somewhere else in Netty.
             * In this case, source address will be `remoteAddress` and destination address will
             * be `localAddress`.
             */
            if (isWrite) {
                srcAddr = (InetSocketAddress) ctx.channel().localAddress();
                dstAddr = (InetSocketAddress) ctx.channel().remoteAddress();
            } else {
                srcAddr = (InetSocketAddress) ctx.channel().remoteAddress();
                dstAddr = (InetSocketAddress) ctx.channel().localAddress();
            }

            if (protocol == Protocol.TCP) {
                handleTCP(ctx, srcAddr, dstAddr, packet);
            } else {
                handleUDP(ctx, srcAddr, dstAddr, packet);
            }
        }
    }

    private void handleTCP(ChannelHandlerContext ctx, InetSocketAddress srcAddr, InetSocketAddress dstAddr,
                           ByteBuf packet) throws IOException {
        ByteBuf tcpBuf = ctx.alloc().buffer();
        TCPPacket.createPacket(tcpBuf, packet, dstAddr.getPort(), srcAddr.getPort());

        ByteBuf ipBuf = ctx.alloc().buffer();
        if (dstAddr.getAddress() instanceof Inet4Address) {
            IPPacket.createTCPv4(ipBuf,
                    tcpBuf,
                    ipv4ToInt(srcAddr.getAddress()),
                    ipv4ToInt(dstAddr.getAddress()));

            ByteBuf ethernetBuf = ctx.alloc().buffer();
            EthernetPacket.createIPv4(ethernetBuf, ipBuf);
            pCapFileWriter.writePacket(ctx.alloc().buffer(), ethernetBuf);
        } else {
            IPPacket.createTCPv6(ipBuf,
                    tcpBuf,
                    srcAddr.getAddress().getAddress(),
                    dstAddr.getAddress().getAddress());

            ByteBuf ethernetBuf = ctx.alloc().buffer();
            EthernetPacket.createIPv6(ethernetBuf, ipBuf);
            pCapFileWriter.writePacket(ctx.alloc().buffer(), ethernetBuf);
        }
    }

    private void handleUDP(ChannelHandlerContext ctx, InetSocketAddress srcAddr, InetSocketAddress dstAddr,
                           ByteBuf packet) throws IOException {
        ByteBuf udpBuf = ctx.alloc().buffer();
        UDPPacket.createPacket(udpBuf,
                packet,
                dstAddr.getPort(),
                srcAddr.getPort());

        ByteBuf ipBuf = ctx.alloc().buffer();
        if (dstAddr.getAddress() instanceof Inet4Address) {
            IPPacket.createUDPv4(ipBuf,
                    udpBuf,
                    ipv4ToInt(srcAddr.getAddress()),
                    ipv4ToInt(dstAddr.getAddress()));

            ByteBuf ethernetBuf = ctx.alloc().buffer();
            EthernetPacket.createIPv4(ethernetBuf, ipBuf);
            pCapFileWriter.writePacket(ctx.alloc().buffer(), ethernetBuf);
        } else {
            IPPacket.createUDPv6(ipBuf,
                    udpBuf,
                    srcAddr.getAddress().getAddress(),
                    dstAddr.getAddress().getAddress());

            ByteBuf ethernetBuf = ctx.alloc().buffer();
            EthernetPacket.createIPv6(ethernetBuf, ipBuf);
            pCapFileWriter.writePacket(ctx.alloc().buffer(), ethernetBuf);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        this.pCapFileWriter.close();
        super.close(ctx, promise);
    }

    private int ipv4ToInt(InetAddress inetAddress) {
        byte[] octets = inetAddress.getAddress();
        assert octets.length == 4;

        return  (octets[0] & 0xff) << 24 |
                (octets[1] & 0xff) << 16 |
                (octets[2] & 0xff) << 8 |
                (octets[3] & 0xff);
    }
}
