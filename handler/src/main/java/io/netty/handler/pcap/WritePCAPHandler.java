package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.pcap.packet.EthernetPacket;
import io.netty.handler.pcap.packet.IPPacket;
import io.netty.handler.pcap.packet.TCPPacket;
import io.netty.handler.pcap.packet.UDPPacket;

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
            // Copy the ByteBuf
            ByteBuf packet = ((ByteBuf) msg).copy();
            InetSocketAddress dstAddr;
            InetSocketAddress srcAddr;

            if (isWrite) {
                srcAddr = (InetSocketAddress) ctx.channel().localAddress();
                dstAddr = (InetSocketAddress) ctx.channel().remoteAddress();
            } else {
                srcAddr = (InetSocketAddress) ctx.channel().remoteAddress();
                dstAddr = (InetSocketAddress) ctx.channel().localAddress();
            }

            if (protocol == Protocol.TCP) {
                ByteBuf tcpBuf = TCPPacket.createPacket(ctx.alloc().buffer(), packet, dstAddr.getPort(), srcAddr.getPort());

                ByteBuf ipBuf;
                if (dstAddr.getAddress() instanceof Inet4Address) {
                    ipBuf = IPPacket.createTCP4(ctx.alloc().buffer(),
                            tcpBuf, ipv4ToInt(srcAddr.getAddress()),
                            ipv4ToInt(dstAddr.getAddress()));
                } else {
                    ipBuf = IPPacket.createTCP6(ctx.alloc().buffer(),
                            tcpBuf, srcAddr.getAddress().getAddress(),
                            dstAddr.getAddress().getAddress());
                }

                ByteBuf ethernetBuf = EthernetPacket.createIPv4(ctx.alloc().buffer(),
                        ipBuf,
                        EthernetPacket.DUMMY_ADDRESS,
                        EthernetPacket.DUMMY_ADDRESS);

                pCapFileWriter.writePacket(ethernetBuf);
            } else {
                ByteBuf udpBuf = UDPPacket.createPacket(ctx.alloc().buffer(),
                        packet,
                        dstAddr.getPort(),
                        srcAddr.getPort());

                ByteBuf ipBuf;
                if (dstAddr.getAddress() instanceof Inet4Address) {
                    ipBuf = IPPacket.createUDPv4(ctx.alloc().buffer(),
                            udpBuf,
                            ipv4ToInt(srcAddr.getAddress()),
                            ipv4ToInt(dstAddr.getAddress()));
                } else {
                    ipBuf = IPPacket.createUDPv6(ctx.alloc().buffer(),
                            udpBuf,
                            srcAddr.getAddress().getAddress(),
                            dstAddr.getAddress().getAddress());
                }

                ByteBuf ethernetBuf = EthernetPacket.createIPv4(ctx.alloc().buffer(),
                        ipBuf,
                        EthernetPacket.DUMMY_ADDRESS,
                        EthernetPacket.DUMMY_ADDRESS);

                pCapFileWriter.writePacket(ethernetBuf);
            }
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
