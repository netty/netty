/*
 * Copyright 2017 The Netty Project
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
package io.netty.example.tunechoserver;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.TunAddress;
import io.netty.channel.epoll.TunTapChannel;
import io.netty.channel.epoll.TunTapChannelOption;
import io.netty.channel.epoll.TunTapPacket;
import io.netty.channel.epoll.TunTapPacketLogger;

/**
 * A simple TunTapChannel server that echos UDP and ICMP packets back to a sender.
 *
 * TunEchoServer is a Netty server designed to exercise Netty's TunTapChannel class.
 * TunEchoServer effectively emulates a very simple IPv4/IPv6 host system which echos
 * any UDP or ICMP packet it receives back to the sender.  TunEchoServer is designed
 * to be usable from the command line to facilitate testing, and accepts various system
 * properties to control its behavior (see below).
 *
 * Note that the TunEchoServer does not listen on a particular address or port in
 * the same sense as a traditional server would.  Rather the TunEchoServer listens
 * on a Linux TUN device, and reflects back any IP packet sent to it by reversing the
 * source and destination addresses in the packet.  It does this without regard to the
 * destination address in the original packet.  Thus the TunEchoServer effectively
 * "listens" on all IP addresses and port numbers that are reachable via the configured
 * interface.
 *
 *
 * <h3>Running TunEchoServer</h3>
 *
 * To run TunEchoServer, perform the following steps:
 *
 * 1) Initialize a TUN device over which the server will receive packets. Configure this
 * device with a set of private addresses and subnets.  (Note that the specific addresses
 * assigned here represent the local host machine, <b>not</b> the address of the TunEchoServer).
 *
 * <pre>
 *     sudo ip tuntap add dev tun-echo-server mode tun user $USER group $USER
 *     sudo ip link set tun-echo-server up
 *     sudo ip addr add 172.20.0.1/12 dev tun-echo-server
 *     sudo ip addr add fd00:0:1::1/64 dev tun-echo-server
 * </pre>
 *
 * 2) Start the server process, supplying paths to the necessary jar files.
 *
 * <pre>
 *     java -cp <path to netty-all-*.jar>:<path to netty-example-*.jar> io.netty.example.tunechoserver.TunEchoServer
 * </pre>
 *
 * 3) From a second shell, ping the server from the local host.
 *
 * <pre>
 *     ping -c 10 172.20.0.2
 *
 *     ping6 -c 10 fd00:0:1::2
 * </pre>
 *
 * 4) The ping command should indicate that responses are being received.
 *    Additionally you should see logging output from the server like this:
 *
 * <pre>
 *     Feb 01, 2017 7:26:16 PM io.netty.channel.epoll.TunTapPacketLogger logPacket
 *     INFO: TUN/TAP PACKET RECEIVED (protocol IPv6, len 104):
 *       IPv6: src fd00:0:1:0:0:0:0:1, dest fd00:0:1:0:0:0:0:2, protocol ICMPv6
 *              +-------------------------------------------------+
 *              |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 *     +--------+-------------------------------------------------+----------------+
 *     |00000000| 60 03 62 2f 00 40 3a 40 fd 00 00 00 00 01 00 00 |`.b/.@:@........|
 *     |00000010| 00 00 00 00 00 00 00 01 fd 00 00 00 00 01 00 00 |................|
 *     |00000020| 00 00 00 00 00 00 00 02 80 00 5b 5c 19 8a 00 01 |..........[\....|
 *     |00000030| d8 a6 92 58 00 00 00 00 da c4 0c 00 00 00 00 00 |...X............|
 *     |00000040| 10 11 12 13 14 15 16 17 18 19 1a 1b 1c 1d 1e 1f |................|
 *     |00000050| 20 21 22 23 24 25 26 27 28 29 2a 2b 2c 2d 2e 2f | !"#$%&'()*+,-./|
 *     |00000060| 30 31 32 33 34 35 36 37                         |01234567        |
 *     +--------+-------------------------------------------------+----------------+
 *
 *     Feb 01, 2017 7:26:16 PM io.netty.channel.epoll.TunTapPacketLogger logPacket
 *     INFO: TUN/TAP PACKET SENT (protocol IPv6, len 104):
 *       IPv6: src fd00:0:1:0:0:0:0:2, dest fd00:0:1:0:0:0:0:1, protocol ICMPv6
 *              +-------------------------------------------------+
 *              |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 *     +--------+-------------------------------------------------+----------------+
 *     |00000000| 60 03 62 2f 00 40 3a 40 fd 00 00 00 00 01 00 00 |`.b/.@:@........|
 *     |00000010| 00 00 00 00 00 00 00 02 fd 00 00 00 00 01 00 00 |................|
 *     |00000020| 00 00 00 00 00 00 00 01 81 00 5a 5c 19 8a 00 01 |..........Z\....|
 *     |00000030| d8 a6 92 58 00 00 00 00 da c4 0c 00 00 00 00 00 |...X............|
 *     |00000040| 10 11 12 13 14 15 16 17 18 19 1a 1b 1c 1d 1e 1f |................|
 *     |00000050| 20 21 22 23 24 25 26 27 28 29 2a 2b 2c 2d 2e 2f | !"#$%&'()*+,-./|
 *     |00000060| 30 31 32 33 34 35 36 37                         |01234567        |
 *     +--------+-------------------------------------------------+----------------+
 * </pre>
 *
 * 5) To test UDP, use the netcat (nc) tool to send and receive UDP packets from the server.
 *
 * <pre>
 *     echo "HELLO via UDP!" | nc -u 172.20.0.2 7
 *
 *     echo "HELLO via UDP!" | nc -u -6 fd00:0:1::2 7
 * </pre>
 *
 *
 * <h3>Configurable Properties</h3>
 *
 * TunEchoServer supports the following system properties for configuring its behavior:
 *
 * tun-device=<device-name>
 *
 * Open the specified linux TUN device. Defaults to 'tun-echo-server'.
 *
 * thread-pool-size=<int>
 *
 * Use a ThreadPoolExecutor with the specified number of threads to respond to incoming packets.
 * A value of 0 causes the responses to be sent on the Netty channel thread. Defaults to 0.
 *
 * use-unpooled=<true|false>
 *
 * Enable/disable use of Netty UnpooledByteBufAllocator for allocating buffers. Defaults to false.
 *
 * read-buf-size=<int>
 *
 * Set the TunTapChannel read buffer size.  Defaults to 2048.
 *
 * force-gc=<true|false>
 *
 * Enable/disable garbage collection and finalizers after each packet is processed. Defaults to false.
 *
 * debug=<true|false>
 *
 * Enable/disable verbose logging of server activity. Disabling debug logging is using running
 * performance tests.  Defaults to true.
 *
 */

public class TunEchoServer extends SimpleChannelInboundHandler<TunTapPacket> {

    private static AbstractByteBufAllocator bufAllocator = PooledByteBufAllocator.DEFAULT;
    private static ThreadPoolExecutor threadPool;
    private static Logger logger;
    private static boolean forceGC;
    private static boolean debug;
    private static String tunDeviceName;
    private static int threadPoolSize;
    private static int readBufSize;

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        // We don't close the channel because we can keep serving requests.
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, final TunTapPacket inPacket) throws Exception {
        final Channel channel = ctx.channel();

        // If a thread pool has been configured, use it to run the handlePacket().  Otherwise call
        // handlePacket() directly on the channel thread.
        if (threadPool != null) {
            inPacket.retain();
            threadPool.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        handlePacket(channel, inPacket);
                        inPacket.release();
                    }
                }
            );
        } else {
            handlePacket(channel, inPacket);
        }
    }

    private static void handlePacket(Channel channel, TunTapPacket inPacket) {
        ByteBuf inPacketBuf = inPacket.packetData();
        TunTapPacket outPacket = null;
        ByteBuf outPacketBuf = null;

        try {
            // Ignore anything that isn't marked as an IPv4 or IPv6 packet by the kernel.
            if (inPacket.protocol() != TunTapPacket.PROTOCOL_IPV4 &&
                inPacket.protocol() != TunTapPacket.PROTOCOL_IPV6) {
                return;
            }

            // Parse the IP header.
            InetUtils.IPHeader ipHeader = InetUtils.decodeIPHeader(inPacketBuf);

            // Ingore the packet if the version in the IP header doesn't match the kernel packet type.
            if ((inPacket.protocol() == TunTapPacket.PROTOCOL_IPV4 && ipHeader.ipVersion != InetUtils.IP_VERSION_4) ||
                (inPacket.protocol() == TunTapPacket.PROTOCOL_IPV6 && ipHeader.ipVersion != InetUtils.IP_VERSION_6)) {
                return;
            }

            // Create a copy of the inbound packet that will become the response packet.
            outPacketBuf = inPacketBuf.copy();
            outPacket = new TunTapPacket(inPacket.protocol(), outPacketBuf);

            // Swap the source and destination addresses in the response packet.
            swapSourceDestAddresses(outPacketBuf, ipHeader);

            // If the packet is an ICMP or ICMPv6 packet...
            if ((ipHeader.ipVersion == InetUtils.IP_VERSION_4 && ipHeader.protocol == InetUtils.IP_PROTOCOL_ICMP) ||
                (ipHeader.ipVersion == InetUtils.IP_VERSION_6 && ipHeader.protocol == InetUtils.IP_PROTOCOL_ICMPV6)) {

                // Decode ICMP header.
                InetUtils.ICMPHeader icmpHeader = InetUtils.decodeICMPHeader(inPacketBuf, ipHeader.headerLength);

                // Ignore anything other than an ICMP or ICMPv6 Echo Request
                if ((ipHeader.ipVersion == InetUtils.IP_VERSION_4 &&
                     icmpHeader.type != InetUtils.ICMP_MESSAGE_TYPE_ECHO_REQUEST) ||
                    (ipHeader.ipVersion == InetUtils.IP_VERSION_6 &&
                     icmpHeader.type != InetUtils.ICMPV6_MESSAGE_TYPE_ECHO_REQUEST) ||
                    icmpHeader.code != 0) {
                    return;
                }

                // Change the message type to Echo Reply.
                int newType = (ipHeader.ipVersion == InetUtils.IP_VERSION_4)
                    ? InetUtils.ICMP_MESSAGE_TYPE_ECHO_REPLY
                    : InetUtils.ICMPV6_MESSAGE_TYPE_ECHO_REPLY;
                changeICMPTypeAndCode(outPacketBuf, ipHeader, icmpHeader, newType, 0);

            // Otherwise if the packet is a UDP packet...
            } else if (ipHeader.protocol == InetUtils.IP_PROTOCOL_UDP) {

                // Decode the UDP header.
                InetUtils.UDPHeader udpHeader = InetUtils.decodeUDPHeader(inPacketBuf, ipHeader.headerLength);

                // Swap the source and destination port numbers in the response packet.
                swapSourceDestPorts(outPacketBuf, ipHeader, udpHeader);

            // Otherwise, not an ICMP or UDP packet, so ignore it.
            } else {
                return;
            }

            // Write the response packet to the TUN device.
            channel.writeAndFlush(outPacket);
            outPacket = null;
            outPacketBuf = null;
        } finally {
            if (outPacket != null) {
                outPacket.release();
            } else if (outPacketBuf != null) {
                outPacketBuf.release();
            }
        }

        // If requested, provoke GC as an aid to detecting buffer leaks.
        if (forceGC) {
            System.gc();
            System.runFinalization();
        }
    }

    private static void swapSourceDestAddresses(ByteBuf packetBuf, InetUtils.IPHeader ipHeader) {

        // Swap the source and destination addresses in the IP header.  Note that this does not affect the
        // packet's checksums values.
        if (ipHeader.ipVersion == InetUtils.IP_VERSION_4) {
            packetBuf.setBytes(InetUtils.IPV4_HEADER_OFFSET_SOURCE_ADDR, ipHeader.destAddress);
            packetBuf.setBytes(InetUtils.IPV4_HEADER_OFFSET_DEST_ADDR, ipHeader.sourceAddress);
        } else if (ipHeader.ipVersion == InetUtils.IP_VERSION_6) {
            packetBuf.setBytes(InetUtils.IPV6_HEADER_OFFSET_SOURCE_ADDR, ipHeader.destAddress);
            packetBuf.setBytes(InetUtils.IPV6_HEADER_OFFSET_DEST_ADDR, ipHeader.sourceAddress);
        }
    }

    private static void swapSourceDestPorts(ByteBuf packetBuf, InetUtils.IPHeader ipHeader,
            InetUtils.UDPHeader udpHeader) {

        // Swap the source and destination port numbers in the UDP header.  Note that this does not affect the
        // packet's checksums values.
        packetBuf.setShort(ipHeader.headerLength +
                InetUtils.UDP_HEADER_OFFSET_SOURCE_PORT, udpHeader.destPort);
        packetBuf.setShort(ipHeader.headerLength +
                InetUtils.UDP_HEADER_OFFSET_DEST_PORT, udpHeader.sourcePort);
    }

    private static void changeICMPTypeAndCode(ByteBuf packetBuf, InetUtils.IPHeader ipHeader,
            InetUtils.ICMPHeader icmpHeader, int newType, int newCode) {

        // Read the portion of the packet containing the ICMP type and code.
        byte[] oldTypeAndCodeBytes = new byte[2];
        packetBuf.getBytes(ipHeader.headerLength, oldTypeAndCodeBytes);

        // Read the portion of the packet containing the original ICMP checksum.
        byte[] oldICMPChecksumBytes = new byte[2];
        packetBuf.getBytes(ipHeader.headerLength + InetUtils.ICMP_HEADER_OFFSET_CHECKSUM, oldICMPChecksumBytes);

        // Construct a byte array containing the new type and code in the form these fields will appear in
        // updated the packet.
        byte[] newTypeAndCode = new byte[2];
        newTypeAndCode[0] = (byte) newType;
        newTypeAndCode[1] = (byte) newCode;

        // Update the packet with the new type and code.
        packetBuf.setBytes(ipHeader.headerLength, newTypeAndCode);

        // Compute a new ICMP checksum based on the updated type and code.
        int newICMPChecksum = InetUtils.updateInetChecksum(oldTypeAndCodeBytes, 0,
                newTypeAndCode, 0, 2, icmpHeader.checksum);

        // Update the checksum field in the ICMP header.
        packetBuf.setShort(ipHeader.headerLength + InetUtils.ICMP_HEADER_OFFSET_CHECKSUM, newICMPChecksum);

        // If the packet is an ICMP packet (vs ICMPv6)...
        if (ipHeader.ipVersion == InetUtils.IP_VERSION_4) {

            // Read the portion of the packet containing the new ICMP checksum bytes.
            byte[] newICMPChecksumBytes = new byte[2];
            packetBuf.getBytes(ipHeader.headerLength + InetUtils.ICMP_HEADER_OFFSET_CHECKSUM, newICMPChecksumBytes);

            // Compute an updated IP checksum.
            int newIPChecksum = InetUtils.updateInetChecksum(oldICMPChecksumBytes, 0, newICMPChecksumBytes,
                    0, 2, ipHeader.checksum);
            newIPChecksum = InetUtils.updateInetChecksum(oldTypeAndCodeBytes, 0, newTypeAndCode, 0, 2, newIPChecksum);

            // Update the checksum field in the IPv4 header.
            packetBuf.setShort(InetUtils.IPV4_HEADER_OFFSET_CHECKSUM, newIPChecksum);
        }
    }

    private static boolean readProps() {

        tunDeviceName = System.getProperty("tun-device-name", "tun-echo-server");

        try {
            threadPoolSize = Integer.parseInt(System.getProperty("thread-pool-size", "0"));
            if (threadPoolSize < 0) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            System.err.println("Invalid value specified for thread-pool-size property: " +
                    System.getProperty("thread-pool-size"));
            return false;
        }

        try {
            readBufSize = Integer.parseInt(System.getProperty("read-buf-size", "2048"));
            if (readBufSize < 1) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            System.err.println("Invalid value specified for read-buf-size property: " +
                    System.getProperty("read-buf-size"));
            return false;
        }

        if (Boolean.parseBoolean(System.getProperty("use-unpooled", "false"))) {
            bufAllocator = UnpooledByteBufAllocator.DEFAULT;
        }

        forceGC = Boolean.parseBoolean(System.getProperty("force-gc", "false"));

        debug = Boolean.parseBoolean(System.getProperty("debug", "true"));

        return true;
    }

    public static void main(String[] args) {

        if (!readProps()) {
            return;
        }

        System.out.println("TunEchoServer starting");

        try {
            logger = Logger.getLogger("");
            logger.setLevel(debug ? Level.FINEST : Level.SEVERE);

            if (threadPoolSize > 0) {
                threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadPoolSize);
            }

            final TunEchoServer server = new TunEchoServer();

            EventLoopGroup group = new EpollEventLoopGroup(1);

            ChannelFactory<Channel> channelFactory = new ChannelFactory<Channel>() {
                @Override
                public Channel newChannel() {
                    try {
                        Channel channel = new TunTapChannel();
                        if (debug) {
                            channel.pipeline().addLast(new TunTapPacketLogger());
                        }
                        return channel;
                    } catch (Throwable t) {
                        throw new ChannelException("Unable to create TunTapChannel", t);
                    }
                }
            };

            Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channelFactory(channelFactory)
                .option(ChannelOption.ALLOCATOR, bufAllocator)
                .option(TunTapChannelOption.READ_BUF_SIZE, readBufSize)
                .handler(server);

            final TunTapChannel channel =
                    (TunTapChannel) bootstrap.bind(new TunAddress(tunDeviceName)).sync().channel();

            System.out.println("TunEchoServer ready");

            channel.closeFuture().await();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception in TunEchoServer", ex);
        }

        System.out.println("TunEchoServer exiting");
    }

}
