/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel.epoll;

import java.net.InetAddress;
import java.util.Formatter;
import java.util.Locale;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * A {@link ChannelLogger} that logs packets sent or received via a {@link TunTapChannel}.
 */
public class TunTapPacketLogger extends LoggingHandler {

    private boolean logPacketData = true;

    public TunTapPacketLogger() {
        super(LogLevel.INFO);
    }

    public TunTapPacketLogger(LogLevel level) {
        super(level);
    }

    public TunTapPacketLogger(String name) {
        super(name);
    }

    public TunTapPacketLogger(String name, LogLevel level) {
        super(name, level);
    }

    public boolean logPacketData() {
        return logPacketData;
    }

    public void setLogPacketData(boolean logPacketData) {
        this.logPacketData = logPacketData;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof TunTapPacket) {
            logPacket((TunTapPacket) msg, ((TunTapChannel) ctx.channel()).isTapChannel(), "TUN/TAP PACKET RECEIVED");
            ctx.fireChannelRead(msg);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof TunTapPacket) {
            logPacket((TunTapPacket) msg, ((TunTapChannel) ctx.channel()).isTapChannel(), "TUN/TAP PACKET SENT");
            ctx.write(msg, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private void logPacket(TunTapPacket packet, boolean isEtherFrame, String eventName) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, formatPacket(packet, isEtherFrame, eventName, logPacketData));
        }
    }

    public static String formatPacket(TunTapPacket packet, boolean isEtherFrame, String title, boolean logPacketData) {
        StringBuilder out = new StringBuilder();
        Formatter formatter = new Formatter(out, Locale.US);
        ByteBuf packetData = packet.packetData();
        int protocol = packet.protocol();
        int ipPacketOffset = 0;

        formatter.format("%s (protocol %s, len %d):%n",
                title,
                TunTapPacket.getProtocolName(packet.protocol()),
                packetData.readableBytes());

        // If the packet is an Ethernet frame...
        if (isEtherFrame) {

            // Generally a sender doesn't specify a protocol when sending an Ethernet frame, since
            // Linux doesn't require it.  Therefore extract the protocol from the Ethernet frame data.
            protocol = packetData.getUnsignedShort(packetData.readerIndex() + 12);

            // Print the Ethernet header
            formatEthernetHeader(packetData, 0, protocol, formatter);

            // Adjust where we look for the IP header within the packet.
            ipPacketOffset = 14;
        }

        // If the packet contains an IPv4 or IPv6 packet, print the appropriate header information.
        if (protocol == TunTapPacket.PROTOCOL_IPV6) {
            formatIPv6Header(packetData, ipPacketOffset, formatter);
        } else if (protocol == TunTapPacket.PROTOCOL_IPV4) {
            formatIPv4Header(packetData, ipPacketOffset, formatter);
        }

        // Print the full contents of the packet.
        if (logPacketData) {
            formatter.format("%s", ByteBufUtil.prettyHexDump(packetData));
        }

        formatter.close();

        return out.toString();
    }

    private static void formatEthernetHeader(ByteBuf packetData, int headerOffset, int protocol, Formatter formatter) {
        int readerIndex = packetData.readerIndex();
        byte[] srcMACAddr = new byte[6];
        byte[] destMACAddr = new byte[6];

        packetData.getBytes(readerIndex + headerOffset + 6, srcMACAddr);
        packetData.getBytes(readerIndex + headerOffset + 0, destMACAddr);

        formatter.format("%s", "  Ethernet: src ");
        formatMAC48(srcMACAddr, formatter);
        formatter.format("%s", ", dest ");
        formatMAC48(destMACAddr, formatter);
        formatter.format(", protocol %s%n", TunTapPacket.getProtocolName(protocol));
    }

    private static void formatIPv4Header(ByteBuf packetData, int headerOffset, Formatter formatter) {
        int readerIndex = packetData.readerIndex();
        String srcAddr, destAddr;

        try {
            byte[] srcAddrData = new byte[4];
            packetData.getBytes(readerIndex + headerOffset + 12, srcAddrData);
            srcAddr = InetAddress.getByAddress(srcAddrData).getHostAddress();
        } catch (Exception ex) {
            srcAddr = "(invalid)";
        }

        try {
            byte[] destAddrData = new byte[4];
            packetData.getBytes(readerIndex + headerOffset + 16, destAddrData);
            destAddr = InetAddress.getByAddress(destAddrData).getHostAddress();
        } catch (Exception ex) {
            destAddr = "(invalid)";
        }

        int transportProtocol = packetData.getUnsignedByte(readerIndex + headerOffset + 9);

        formatter.format("  IPv4: src %s, dest %s, protocol %s%n",
                srcAddr, destAddr, transportProtocolToString(transportProtocol));
    }

    private static void formatIPv6Header(ByteBuf packetData, int headerOffset, Formatter formatter) {
        int readerIndex = packetData.readerIndex();
        String srcAddr, destAddr;

        try {
            byte[] srcAddrData = new byte[16];
            packetData.getBytes(readerIndex + headerOffset + 8, srcAddrData);
            srcAddr = InetAddress.getByAddress(srcAddrData).getHostAddress();
        } catch (Exception ex) {
            srcAddr = "(invalid)";
        }

        try {
            byte[] destAddrData = new byte[16];
            packetData.getBytes(readerIndex + headerOffset + 24, destAddrData);
            destAddr = InetAddress.getByAddress(destAddrData).getHostAddress();
        } catch (Exception ex) {
            destAddr = "(invalid)";
        }

        int transportProtocol = packetData.getUnsignedByte(readerIndex + headerOffset + 6);

        formatter.format("  IPv6: src %s, dest %s, protocol %s%n",
                srcAddr, destAddr, transportProtocolToString(transportProtocol));
    }

    private static void formatMAC48(byte[] mac48, Formatter formatter) {
        for (int i = 0; i < 6; i++) {
            formatter.format("%s%02X", i != 0 ? ":" : "", mac48[i]);
        }
    }

    private static String transportProtocolToString(int transportProtocol) {
        switch (transportProtocol) {
        case 1:
            return "ICMP";
        case 6:
            return "TCP";
        case 0x11:
            return "UDP";
        case 0x3A:
            return "ICMPv6";
        default:
            return Integer.toHexString(transportProtocol).toUpperCase();
        }
    }
}
