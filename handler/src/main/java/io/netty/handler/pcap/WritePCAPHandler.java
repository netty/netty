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
import io.netty.channel.socket.DatagramPacket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class WritePCAPHandler extends ChannelDuplexHandler {

    private final PCapWriter pCapWriter;

    /**
     * Create new {@link WritePCAPHandler} Instance
     *
     * @param outputStream OutputStream where Pcap data will be written
     * @throws IOException If {@link OutputStream#write(byte[])} throws an exception
     */
    public WritePCAPHandler(OutputStream outputStream) throws IOException {
        this.pCapWriter = new PCapWriter(outputStream);
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

            handleTCP(ctx, srcAddr, dstAddr, ((ByteBuf) msg).duplicate());
        } else if (msg instanceof DatagramPacket) {
            handleUDP(ctx, ((DatagramPacket) msg).duplicate());
        }
    }

    private void handleTCP(ChannelHandlerContext ctx, InetSocketAddress srcAddr, InetSocketAddress dstAddr,
                           ByteBuf packet) throws IOException {
        ByteBuf tcpBuf = ctx.alloc().buffer();
        TCPPacket.writePacket(tcpBuf, packet, dstAddr.getPort(), srcAddr.getPort());

        ByteBuf ipBuf = ctx.alloc().buffer();
        if (srcAddr.getAddress() instanceof Inet4Address) {
            IPPacket.writeTCPv4(ipBuf,
                    tcpBuf,
                    ipv4ToInt(srcAddr.getAddress()),
                    ipv4ToInt(dstAddr.getAddress()));

            ByteBuf ethernetBuf = ctx.alloc().buffer();
            EthernetPacket.writeIPv4(ethernetBuf, ipBuf);
            pCapWriter.writePacket(ctx.alloc().buffer(), ethernetBuf);
        } else {
            IPPacket.writeTCPv6(ipBuf,
                    tcpBuf,
                    srcAddr.getAddress().getAddress(),
                    dstAddr.getAddress().getAddress());

            ByteBuf ethernetBuf = ctx.alloc().buffer();
            EthernetPacket.writeIPv6(ethernetBuf, ipBuf);
            pCapWriter.writePacket(ctx.alloc().buffer(), ethernetBuf);
        }
    }

    private void handleUDP(ChannelHandlerContext ctx, DatagramPacket datagramPacket) throws IOException {
        InetSocketAddress srcAddr = datagramPacket.sender();
        InetSocketAddress dstAddr = datagramPacket.recipient();

        ByteBuf udpBuf = ctx.alloc().buffer();
        UDPPacket.writePacket(udpBuf,
                datagramPacket.content(),
                srcAddr.getPort(),
                dstAddr.getPort());

        ByteBuf ipBuf = ctx.alloc().buffer();
        if (srcAddr.getAddress() instanceof Inet4Address) {
            IPPacket.writeUDPv4(ipBuf,
                    udpBuf,
                    ipv4ToInt(srcAddr.getAddress()),
                    ipv4ToInt(dstAddr.getAddress()));

            ByteBuf ethernetBuf = ctx.alloc().buffer();
            EthernetPacket.writeIPv4(ethernetBuf, ipBuf);
            pCapWriter.writePacket(ctx.alloc().buffer(), ethernetBuf);
        } else {
            IPPacket.writeUDPv6(ipBuf,
                    udpBuf,
                    srcAddr.getAddress().getAddress(),
                    dstAddr.getAddress().getAddress());

            ByteBuf ethernetBuf = ctx.alloc().buffer();
            EthernetPacket.writeIPv6(ethernetBuf, ipBuf);
            pCapWriter.writePacket(ctx.alloc().buffer(), ethernetBuf);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        this.pCapWriter.close();
        super.handlerRemoved(ctx);
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
