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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.NetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * <p> {@link PcapWriteHandler} captures {@link ByteBuf} from {@link SocketChannel} / {@link ServerChannel}
 * or {@link DatagramPacket} and writes it into Pcap {@link OutputStream}. </p>
 *
 * <p>
 * Things to keep in mind when using {@link PcapWriteHandler} with TCP:
 *
 *    <ul>
 *        <li> Whenever {@link ChannelInboundHandlerAdapter#channelActive(ChannelHandlerContext)} is called,
 *        a fake TCP 3-way handshake (SYN, SYN+ACK, ACK) is simulated as new connection in Pcap. </li>
 *
 *        <li> Whenever {@link ChannelInboundHandlerAdapter#handlerRemoved(ChannelHandlerContext)} is called,
 *        a fake TCP 3-way handshake (FIN+ACK, FIN+ACK, ACK) is simulated as connection shutdown in Pcap.  </li>
 *
 *        <li> Whenever {@link ChannelInboundHandlerAdapter#exceptionCaught(ChannelHandlerContext, Throwable)}
 *        is called, a fake TCP RST is sent to simulate connection Reset in Pcap. </li>
 *
 *        <li> ACK is sent each time data is send / received. </li>
 *
 *        <li> Zero Length Data Packets can cause TCP Double ACK error in Wireshark. To tackle this,
 *        set {@code captureZeroByte} to {@code false}. </li>
 *    </ul>
 * </p>
 */
public final class PcapWriteHandler extends ChannelDuplexHandler implements Closeable {

    /**
     * Logger for logging events
     */
    private final InternalLogger logger = InternalLoggerFactory.getInstance(PcapWriteHandler.class);

    /**
     * {@link PcapWriter} Instance
     */
    private PcapWriter pCapWriter;

    /**
     * {@link OutputStream} where we'll write Pcap data.
     */
    private final OutputStream outputStream;

    /**
     * {@code true} if we want to capture packets with zero bytes else {@code false}.
     */
    private final boolean captureZeroByte;

    /**
     * {@code true} if we want to write Pcap Global Header on initialization of
     * {@link PcapWriter} else {@code false}.
     */
    private final boolean writePcapGlobalHeader;

    /**
     * {@code true} if we want to synchronize on the {@link OutputStream} while writing
     * else {@code false}.
     */
    private final boolean sharedOutputStream;

    /**
     * TCP Sender Segment Number.
     * It'll start with 1 and keep incrementing with number of bytes read/sent and wrap at the uint32 max.
     */
    private long sendSegmentNumber = 1;

    /**
     * TCP Receiver Segment Number.
     * It'll start with 1 and keep incrementing with number of bytes read/sent and wrap at the uint32 max
     */
    private long receiveSegmentNumber = 1;

    /**
     * Type of the channel this handler is registered on
     */
    private ChannelType channelType;

    /**
     * Address of the initiator of the connection
     */
    private InetSocketAddress initiatorAddr;

    /**
     * Address of the receiver of the connection
     */
    private InetSocketAddress handlerAddr;

    /**
     * Set to {@code true} if this handler is registered on a server pipeline
     */
    private boolean isServerPipeline;

    /**
     * Current of this {@link PcapWriteHandler}
     */
    private final AtomicReference<State> state = new AtomicReference<State>(State.INIT);

    /**
     * Create new {@link PcapWriteHandler} Instance.
     * {@code captureZeroByte} is set to {@code false} and
     * {@code writePcapGlobalHeader} is set to {@code true}.
     *
     * @param outputStream OutputStream where Pcap data will be written. Call {@link #close()} to close this
     *                     OutputStream.
     * @throws NullPointerException If {@link OutputStream} is {@code null} then we'll throw an
     *                              {@link NullPointerException}
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated
    public PcapWriteHandler(OutputStream outputStream) {
        this(outputStream, false, true);
    }

    /**
     * Create new {@link PcapWriteHandler} Instance
     *
     * @param outputStream          OutputStream where Pcap data will be written. Call {@link #close()} to close this
     *                              OutputStream.
     * @param captureZeroByte       Set to {@code true} to enable capturing packets with empty (0 bytes) payload.
     *                              Otherwise, if set to {@code false}, empty packets will be filtered out.
     * @param writePcapGlobalHeader Set to {@code true} to write Pcap Global Header on initialization.
     *                              Otherwise, if set to {@code false}, Pcap Global Header will not be written
     *                              on initialization. This could when writing Pcap data on a existing file where
     *                              Pcap Global Header is already present.
     * @throws NullPointerException If {@link OutputStream} is {@code null} then we'll throw an
     *                              {@link NullPointerException}
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated
    public PcapWriteHandler(OutputStream outputStream, boolean captureZeroByte, boolean writePcapGlobalHeader) {
        this.outputStream = checkNotNull(outputStream, "OutputStream");
        this.captureZeroByte = captureZeroByte;
        this.writePcapGlobalHeader = writePcapGlobalHeader;
        sharedOutputStream = false;
    }

    private PcapWriteHandler(Builder builder, OutputStream outputStream) {
        this.outputStream = outputStream;
        captureZeroByte = builder.captureZeroByte;
        sharedOutputStream = builder.sharedOutputStream;
        writePcapGlobalHeader = builder.writePcapGlobalHeader;
        channelType = builder.channelType;
        handlerAddr = builder.handlerAddr;
        initiatorAddr = builder.initiatorAddr;
        isServerPipeline = builder.isServerPipeline;
    }

    /**
     * Writes the Pcap Global Header to the provided {@code OutputStream}
     *
     * @param outputStream OutputStream where Pcap data will be written.
     * @throws IOException if there is an error writing to the {@code OutputStream}
     */
    public static void writeGlobalHeader(OutputStream outputStream) throws IOException {
        PcapHeaders.writeGlobalHeader(outputStream);
    }

    private void initializeIfNecessary(ChannelHandlerContext ctx) throws Exception {
        // If State is not 'INIT' then it means we're already initialized so then no need to initiaize again.
        if (state.get() != State.INIT) {
            return;
        }

        pCapWriter = new PcapWriter(this);

        if (channelType == null) {
            // infer channel type
            if (ctx.channel() instanceof SocketChannel) {
                channelType = ChannelType.TCP;

                // If Channel belongs to `SocketChannel` then we're handling TCP.
                // Capture correct `localAddress` and `remoteAddress`
                if (ctx.channel().parent() instanceof ServerSocketChannel) {
                    isServerPipeline = true;
                    initiatorAddr = (InetSocketAddress) ctx.channel().remoteAddress();
                    handlerAddr = getLocalAddress(ctx.channel(), initiatorAddr);
                } else {
                    isServerPipeline = false;
                    handlerAddr = (InetSocketAddress) ctx.channel().remoteAddress();
                    initiatorAddr = getLocalAddress(ctx.channel(), handlerAddr);
                }
            } else if (ctx.channel() instanceof DatagramChannel) {
                channelType = ChannelType.UDP;

                DatagramChannel datagramChannel = (DatagramChannel) ctx.channel();

                // If `DatagramChannel` is connected then we can get
                // `localAddress` and `remoteAddress` from Channel.
                if (datagramChannel.isConnected()) {
                    handlerAddr = (InetSocketAddress) ctx.channel().remoteAddress();
                    initiatorAddr = getLocalAddress(ctx.channel(), handlerAddr);
                }
            }
        }

        if (channelType == ChannelType.TCP) {
            logger.debug("Initiating Fake TCP 3-Way Handshake");

            ByteBuf tcpBuf = ctx.alloc().buffer();

            try {
                // Write SYN with Normal Source and Destination Address
                TCPPacket.writePacket(tcpBuf, null, 0, 0,
                                      initiatorAddr.getPort(), handlerAddr.getPort(), TCPPacket.TCPFlag.SYN);
                completeTCPWrite(initiatorAddr, handlerAddr, tcpBuf, ctx.alloc(), ctx);

                // Write SYN+ACK with Reversed Source and Destination Address
                TCPPacket.writePacket(tcpBuf, null, 0, 1,
                                      handlerAddr.getPort(), initiatorAddr.getPort(), TCPPacket.TCPFlag.SYN,
                                      TCPPacket.TCPFlag.ACK);
                completeTCPWrite(handlerAddr, initiatorAddr, tcpBuf, ctx.alloc(), ctx);

                // Write ACK with Normal Source and Destination Address
                TCPPacket.writePacket(tcpBuf, null, 1, 1, initiatorAddr.getPort(),
                                      handlerAddr.getPort(), TCPPacket.TCPFlag.ACK);
                completeTCPWrite(initiatorAddr, handlerAddr, tcpBuf, ctx.alloc(), ctx);
            } finally {
                tcpBuf.release();
            }

            logger.debug("Finished Fake TCP 3-Way Handshake");
        }

        state.set(State.WRITING);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        initializeIfNecessary(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Initialize if needed
        if (state.get() == State.INIT) {
            initializeIfNecessary(ctx);
        }

        // Only write if State is STARTED
        if (state.get() == State.WRITING) {
            if (channelType == ChannelType.TCP) {
                handleTCP(ctx, msg, false);
            } else if (channelType == ChannelType.UDP) {
                handleUDP(ctx, msg, false);
            } else {
                logDiscard();
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Initialize if needed
        if (state.get() == State.INIT) {
            initializeIfNecessary(ctx);
        }

        // Only write if State is STARTED
        if (state.get() == State.WRITING) {
            if (channelType == ChannelType.TCP) {
                handleTCP(ctx, msg, true);
            } else if (channelType == ChannelType.UDP) {
                handleUDP(ctx, msg, true);
            } else {
                logDiscard();
            }
        }
        super.write(ctx, msg, promise);
    }

    /**
     * Handle TCP L4
     *
     * @param ctx              {@link ChannelHandlerContext} for {@link ByteBuf} allocation and
     *                         {@code fireExceptionCaught}
     * @param msg              {@link Object} must be {@link ByteBuf} else it'll be discarded
     * @param isWriteOperation Set {@code true} if we have to process packet when packets are being sent out
     *                         else set {@code false}
     */
    private void handleTCP(ChannelHandlerContext ctx, Object msg, boolean isWriteOperation) {
        if (msg instanceof ByteBuf) {

            // If bytes are 0 and `captureZeroByte` is false, we won't capture this.
            int totalBytes = ((ByteBuf) msg).readableBytes();
            if (totalBytes == 0 && !captureZeroByte) {
                logger.debug("Discarding Zero Byte TCP Packet. isWriteOperation {}", isWriteOperation);
                return;
            }

            ByteBufAllocator byteBufAllocator = ctx.alloc();
            if (totalBytes == 0) {
                handleTcpPacket(ctx, (ByteBuf) msg, isWriteOperation, byteBufAllocator);
                return;
            }

            // If the payload exceeds the max size of that can fit in a single TCP IPv4 packet, fragment the payload
            int maxTcpPayload = 65495;

            for (int i = 0; i < totalBytes; i += maxTcpPayload) {
                ByteBuf packet = ((ByteBuf) msg).slice(i, Math.min(maxTcpPayload, totalBytes - i));
                handleTcpPacket(ctx, packet, isWriteOperation, byteBufAllocator);
            }
        } else {
            logger.debug("Discarding Pcap Write for TCP Object: {}", msg);
        }
    }

    private void handleTcpPacket(ChannelHandlerContext ctx, ByteBuf packet, boolean isWriteOperation,
                                 ByteBufAllocator byteBufAllocator) {
        ByteBuf tcpBuf = byteBufAllocator.buffer();
        int bytes = packet.readableBytes();

        try {
            if (isWriteOperation) {
                final InetSocketAddress srcAddr;
                final InetSocketAddress dstAddr;
                if (isServerPipeline) {
                    srcAddr = handlerAddr;
                    dstAddr = initiatorAddr;
                } else {
                    srcAddr = initiatorAddr;
                    dstAddr = handlerAddr;
                }

                TCPPacket.writePacket(tcpBuf, packet, sendSegmentNumber, receiveSegmentNumber,
                                      srcAddr.getPort(),
                                      dstAddr.getPort(), TCPPacket.TCPFlag.ACK);
                completeTCPWrite(srcAddr, dstAddr, tcpBuf, byteBufAllocator, ctx);
                logTCP(true, bytes, sendSegmentNumber, receiveSegmentNumber, srcAddr, dstAddr, false);

                sendSegmentNumber = incrementUintSegmentNumber(sendSegmentNumber, bytes);

                TCPPacket.writePacket(tcpBuf, null, receiveSegmentNumber, sendSegmentNumber, dstAddr.getPort(),
                                      srcAddr.getPort(), TCPPacket.TCPFlag.ACK);
                completeTCPWrite(dstAddr, srcAddr, tcpBuf, byteBufAllocator, ctx);
                logTCP(true, bytes, sendSegmentNumber, receiveSegmentNumber, dstAddr, srcAddr, true);
            } else {
                final InetSocketAddress srcAddr;
                final InetSocketAddress dstAddr;
                if (isServerPipeline) {
                    srcAddr = initiatorAddr;
                    dstAddr = handlerAddr;
                } else {
                    srcAddr = handlerAddr;
                    dstAddr = initiatorAddr;
                }

                TCPPacket.writePacket(tcpBuf, packet, receiveSegmentNumber, sendSegmentNumber,
                                      srcAddr.getPort(),
                                      dstAddr.getPort(), TCPPacket.TCPFlag.ACK);
                completeTCPWrite(srcAddr, dstAddr, tcpBuf, byteBufAllocator, ctx);
                logTCP(false, bytes, receiveSegmentNumber, sendSegmentNumber, srcAddr, dstAddr, false);

                receiveSegmentNumber = incrementUintSegmentNumber(receiveSegmentNumber, bytes);

                TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber, receiveSegmentNumber, dstAddr.getPort(),
                                      srcAddr.getPort(), TCPPacket.TCPFlag.ACK);
                completeTCPWrite(dstAddr, srcAddr, tcpBuf, byteBufAllocator, ctx);
                logTCP(false, bytes, sendSegmentNumber, receiveSegmentNumber, dstAddr, srcAddr, true);
            }
        } finally {
            tcpBuf.release();
        }
    }

    /**
     * Write TCP/IP L3 and L2 here.
     *
     * @param srcAddr          {@link InetSocketAddress} Source Address of this Packet
     * @param dstAddr          {@link InetSocketAddress} Destination Address of this Packet
     * @param tcpBuf           {@link ByteBuf} containing TCP L4 Data
     * @param byteBufAllocator {@link ByteBufAllocator} for allocating bytes for TCP/IP L3 and L2 data.
     * @param ctx              {@link ChannelHandlerContext} for {@code fireExceptionCaught}
     */
    private void completeTCPWrite(InetSocketAddress srcAddr, InetSocketAddress dstAddr, ByteBuf tcpBuf,
                                  ByteBufAllocator byteBufAllocator, ChannelHandlerContext ctx) {

        ByteBuf ipBuf = byteBufAllocator.buffer();
        ByteBuf ethernetBuf = byteBufAllocator.buffer();
        ByteBuf pcap = byteBufAllocator.buffer();

        try {
            if (srcAddr.getAddress() instanceof Inet4Address && dstAddr.getAddress() instanceof Inet4Address) {
                IPPacket.writeTCPv4(ipBuf, tcpBuf,
                                    NetUtil.ipv4AddressToInt((Inet4Address) srcAddr.getAddress()),
                                    NetUtil.ipv4AddressToInt((Inet4Address) dstAddr.getAddress()));

                EthernetPacket.writeIPv4(ethernetBuf, ipBuf);
            } else if (srcAddr.getAddress() instanceof Inet6Address && dstAddr.getAddress() instanceof Inet6Address) {
                IPPacket.writeTCPv6(ipBuf, tcpBuf,
                                    srcAddr.getAddress().getAddress(),
                                    dstAddr.getAddress().getAddress());

                EthernetPacket.writeIPv6(ethernetBuf, ipBuf);
            } else {
                logger.error("Source and Destination IP Address versions are not same. Source Address: {}, " +
                             "Destination Address: {}", srcAddr.getAddress(), dstAddr.getAddress());
                return;
            }

            // Write Packet into Pcap
            pCapWriter.writePacket(pcap, ethernetBuf);
        } catch (IOException ex) {
            logger.error("Caught Exception While Writing Packet into Pcap", ex);
            ctx.fireExceptionCaught(ex);
        } finally {
            ipBuf.release();
            ethernetBuf.release();
            pcap.release();
        }
    }

    private static long incrementUintSegmentNumber(long sequenceNumber, int value) {
        // If the sequence number would go above the max for uint32, wrap around
        return (sequenceNumber + value) % (0xFFFFFFFFL + 1);
    }

    /**
     * Handle UDP l4
     *
     * @param ctx {@link ChannelHandlerContext} for {@code localAddress} / {@code remoteAddress},
     *            {@link ByteBuf} allocation and {@code fireExceptionCaught}
     * @param msg {@link DatagramPacket} or {@link ByteBuf}
     */
    private void handleUDP(ChannelHandlerContext ctx, Object msg, boolean isWriteOperation) {
        ByteBuf udpBuf = ctx.alloc().buffer();

        try {
            if (msg instanceof DatagramPacket) {

                // If bytes are 0 and `captureZeroByte` is false, we won't capture this.
                if (((DatagramPacket) msg).content().readableBytes() == 0 && !captureZeroByte) {
                    logger.debug("Discarding Zero Byte UDP Packet");
                    return;
                }

                if (((DatagramPacket) msg).content().readableBytes() > 65507) {
                    logger.warn("Unable to write UDP packet to PCAP. Payload of size {} exceeds max size of 65507");
                    return;
                }

                DatagramPacket datagramPacket = ((DatagramPacket) msg).duplicate();
                InetSocketAddress srcAddr = datagramPacket.sender();
                InetSocketAddress dstAddr = datagramPacket.recipient();

                // If `datagramPacket.sender()` is `null` then DatagramPacket is initialized
                // `sender` (local) address. In this case, we'll get source address from Channel.
                if (srcAddr == null) {
                    srcAddr = getLocalAddress(ctx.channel(), dstAddr);
                }

                logger.debug("Writing UDP Data of {} Bytes, isWriteOperation {}, Src Addr {}, Dst Addr {}",
                             datagramPacket.content().readableBytes(), isWriteOperation, srcAddr, dstAddr);

                UDPPacket.writePacket(udpBuf, datagramPacket.content(), srcAddr.getPort(), dstAddr.getPort());
                completeUDPWrite(srcAddr, dstAddr, udpBuf, ctx.alloc(), ctx);
            } else if (msg instanceof ByteBuf &&
                       (!(ctx.channel() instanceof DatagramChannel) ||
                        ((DatagramChannel) ctx.channel()).isConnected())) {

                // If bytes are 0 and `captureZeroByte` is false, we won't capture this.
                if (((ByteBuf) msg).readableBytes() == 0 && !captureZeroByte) {
                    logger.debug("Discarding Zero Byte UDP Packet");
                    return;
                }

                if (((ByteBuf) msg).readableBytes() > 65507) {
                    logger.warn("Unable to write UDP packet to PCAP. Payload of size {} exceeds max size of 65507");
                    return;
                }

                ByteBuf byteBuf = ((ByteBuf) msg).duplicate();

                InetSocketAddress sourceAddr = isWriteOperation? initiatorAddr : handlerAddr;
                InetSocketAddress destinationAddr = isWriteOperation? handlerAddr : initiatorAddr;

                logger.debug("Writing UDP Data of {} Bytes, Src Addr {}, Dst Addr {}",
                             byteBuf.readableBytes(), sourceAddr, destinationAddr);

                UDPPacket.writePacket(udpBuf, byteBuf, sourceAddr.getPort(), destinationAddr.getPort());
                completeUDPWrite(sourceAddr, destinationAddr, udpBuf, ctx.alloc(), ctx);
            } else {
                logger.debug("Discarding Pcap Write for UDP Object: {}", msg);
            }
        } finally {
            udpBuf.release();
        }
    }

    /**
     * Write UDP/IP L3 and L2 here.
     *
     * @param srcAddr          {@link InetSocketAddress} Source Address of this Packet
     * @param dstAddr          {@link InetSocketAddress} Destination Address of this Packet
     * @param udpBuf           {@link ByteBuf} containing UDP L4 Data
     * @param byteBufAllocator {@link ByteBufAllocator} for allocating bytes for UDP/IP L3 and L2 data.
     * @param ctx              {@link ChannelHandlerContext} for {@code fireExceptionCaught}
     */
    private void completeUDPWrite(InetSocketAddress srcAddr, InetSocketAddress dstAddr, ByteBuf udpBuf,
                                  ByteBufAllocator byteBufAllocator, ChannelHandlerContext ctx) {

        ByteBuf ipBuf = byteBufAllocator.buffer();
        ByteBuf ethernetBuf = byteBufAllocator.buffer();
        ByteBuf pcap = byteBufAllocator.buffer();

        try {
            if (srcAddr.getAddress() instanceof Inet4Address && dstAddr.getAddress() instanceof Inet4Address) {
                IPPacket.writeUDPv4(ipBuf, udpBuf,
                                    NetUtil.ipv4AddressToInt((Inet4Address) srcAddr.getAddress()),
                                    NetUtil.ipv4AddressToInt((Inet4Address) dstAddr.getAddress()));

                EthernetPacket.writeIPv4(ethernetBuf, ipBuf);
            } else if (srcAddr.getAddress() instanceof Inet6Address && dstAddr.getAddress() instanceof Inet6Address) {
                IPPacket.writeUDPv6(ipBuf, udpBuf,
                                    srcAddr.getAddress().getAddress(),
                                    dstAddr.getAddress().getAddress());

                EthernetPacket.writeIPv6(ethernetBuf, ipBuf);
            } else {
                logger.error("Source and Destination IP Address versions are not same. Source Address: {}, " +
                             "Destination Address: {}", srcAddr.getAddress(), dstAddr.getAddress());
                return;
            }

            // Write Packet into Pcap
            pCapWriter.writePacket(pcap, ethernetBuf);
        } catch (IOException ex) {
            logger.error("Caught Exception While Writing Packet into Pcap", ex);
            ctx.fireExceptionCaught(ex);
        } finally {
            ipBuf.release();
            ethernetBuf.release();
            pcap.release();
        }
    }

    /**
     * Get the local address of a channel. If the address is a wildcard address ({@code 0.0.0.0} or {@code ::}), and
     * the address family does not match that of the {@code remote}, return the wildcard address of the {@code remote}'s
     * family instead.
     *
     * @param ch     The channel to get the local address from
     * @param remote The remote address
     * @return The fixed local address
     */
    private static InetSocketAddress getLocalAddress(Channel ch, InetSocketAddress remote) {
        InetSocketAddress local = (InetSocketAddress) ch.localAddress();
        if (remote != null && local.getAddress().isAnyLocalAddress()) {
            if (local.getAddress() instanceof Inet4Address && remote.getAddress() instanceof Inet6Address) {
                return new InetSocketAddress(WildcardAddressHolder.wildcard6, local.getPort());
            }
            if (local.getAddress() instanceof Inet6Address && remote.getAddress() instanceof Inet4Address) {
                return new InetSocketAddress(WildcardAddressHolder.wildcard4, local.getPort());
            }
        }
        return local;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

        // If `isTCP` is true and state is WRITING, then we'll simulate a `FIN` flow.
        if (channelType == ChannelType.TCP && state.get() == State.WRITING) {
            logger.debug("Starting Fake TCP FIN+ACK Flow to close connection");

            ByteBufAllocator byteBufAllocator = ctx.alloc();
            ByteBuf tcpBuf = byteBufAllocator.buffer();

            try {
                long initiatorSegmentNumber = isServerPipeline? receiveSegmentNumber : sendSegmentNumber;
                long initiatorAckNumber = isServerPipeline? sendSegmentNumber : receiveSegmentNumber;
                // Write FIN+ACK with Normal Source and Destination Address
                TCPPacket.writePacket(tcpBuf, null, initiatorSegmentNumber, initiatorAckNumber, initiatorAddr.getPort(),
                                      handlerAddr.getPort(), TCPPacket.TCPFlag.FIN, TCPPacket.TCPFlag.ACK);
                completeTCPWrite(initiatorAddr, handlerAddr, tcpBuf, byteBufAllocator, ctx);

                // Write FIN+ACK with Reversed Source and Destination Address
                TCPPacket.writePacket(tcpBuf, null, initiatorAckNumber, initiatorSegmentNumber, handlerAddr.getPort(),
                                      initiatorAddr.getPort(), TCPPacket.TCPFlag.FIN, TCPPacket.TCPFlag.ACK);
                completeTCPWrite(handlerAddr, initiatorAddr, tcpBuf, byteBufAllocator, ctx);

                // Increment by 1 when responding to FIN
                sendSegmentNumber = incrementUintSegmentNumber(sendSegmentNumber, 1);
                receiveSegmentNumber = incrementUintSegmentNumber(receiveSegmentNumber, 1);
                initiatorSegmentNumber = isServerPipeline? receiveSegmentNumber : sendSegmentNumber;
                initiatorAckNumber = isServerPipeline? sendSegmentNumber : receiveSegmentNumber;

                // Write ACK with Normal Source and Destination Address
                TCPPacket.writePacket(tcpBuf, null, initiatorSegmentNumber, initiatorAckNumber,
                                      initiatorAddr.getPort(), handlerAddr.getPort(), TCPPacket.TCPFlag.ACK);
                completeTCPWrite(initiatorAddr, handlerAddr, tcpBuf, byteBufAllocator, ctx);
            } finally {
                tcpBuf.release();
            }

            logger.debug("Finished Fake TCP FIN+ACK Flow to close connection");
        }

        close();
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // Only write RST if this is an initialized TCP stream
        if (channelType == ChannelType.TCP && state.get() == State.WRITING) {
            ByteBuf tcpBuf = ctx.alloc().buffer();

            try {
                // Write RST with Normal Source and Destination Address
                TCPPacket.writePacket(tcpBuf, null, sendSegmentNumber, receiveSegmentNumber, initiatorAddr.getPort(),
                                      handlerAddr.getPort(), TCPPacket.TCPFlag.RST, TCPPacket.TCPFlag.ACK);
                completeTCPWrite(initiatorAddr, handlerAddr, tcpBuf, ctx.alloc(), ctx);
            } finally {
                tcpBuf.release();
            }

            logger.debug("Sent Fake TCP RST to close connection");
        }

        close();
        ctx.fireExceptionCaught(cause);
    }

    /**
     * Logger for TCP
     */
    private void logTCP(boolean isWriteOperation, int bytes, long sendSegmentNumber, long receiveSegmentNumber,
                        InetSocketAddress srcAddr, InetSocketAddress dstAddr, boolean ackOnly) {
        // If `ackOnly` is `true` when we don't need to write any data so we'll not
        // log number of bytes being written and mark the operation as "TCP ACK".
        if (logger.isDebugEnabled()) {
            if (ackOnly) {
                logger.debug("Writing TCP ACK, isWriteOperation {}, Segment Number {}, Ack Number {}, Src Addr {}, "
                             + "Dst Addr {}", isWriteOperation, sendSegmentNumber, receiveSegmentNumber, dstAddr,
                             srcAddr);
            } else {
                logger.debug("Writing TCP Data of {} Bytes, isWriteOperation {}, Segment Number {}, Ack Number {}, " +
                             "Src Addr {}, Dst Addr {}", bytes, isWriteOperation, sendSegmentNumber,
                             receiveSegmentNumber, srcAddr, dstAddr);
            }
        }
    }

    OutputStream outputStream() {
        return outputStream;
    }

    boolean sharedOutputStream() {
        return sharedOutputStream;
    }

    boolean writePcapGlobalHeader() {
        return writePcapGlobalHeader;
    }

    /**
     * Returns {@code true} if the {@link PcapWriteHandler} is currently
     * writing packets to the {@link OutputStream} else returns {@code false}.
     */
    public boolean isWriting() {
        return state.get() == State.WRITING;
    }

    State state() {
        return state.get();
    }

    /**
     * Pause the {@link PcapWriteHandler} from writing packets to the {@link OutputStream}.
     */
    public void pause() {
        if (!state.compareAndSet(State.WRITING, State.PAUSED)) {
            throw new IllegalStateException("State must be 'STARTED' to pause but current state is: " + state);
        }
    }

    /**
     * Resume the {@link PcapWriteHandler} to writing packets to the {@link OutputStream}.
     */
    public void resume() {
        if (!state.compareAndSet(State.PAUSED, State.WRITING)) {
            throw new IllegalStateException("State must be 'PAUSED' to resume but current state is: " + state);
        }
    }

    void markClosed() {
        if (state.get() != State.CLOSED) {
            state.set(State.CLOSED);
        }
    }

    // Visible for testing only.
    PcapWriter pCapWriter() {
        return pCapWriter;
    }

    private void logDiscard() {
        logger.warn("Discarding pcap write because channel type is unknown. The channel this handler is registered " +
                    "on is not a SocketChannel or DatagramChannel, so the inference does not work. Please call " +
                    "forceTcpChannel or forceUdpChannel before registering the handler.");
    }

    @Override
    public String toString() {
        return "PcapWriteHandler{" +
               "captureZeroByte=" + captureZeroByte +
               ", writePcapGlobalHeader=" + writePcapGlobalHeader +
               ", sharedOutputStream=" + sharedOutputStream +
               ", sendSegmentNumber=" + sendSegmentNumber +
               ", receiveSegmentNumber=" + receiveSegmentNumber +
               ", channelType=" + channelType +
               ", initiatorAddr=" + initiatorAddr +
               ", handlerAddr=" + handlerAddr +
               ", isServerPipeline=" + isServerPipeline +
               ", state=" + state +
               '}';
    }

    /**
     * <p> Close {@code PcapWriter} and {@link OutputStream}. </p>
     * <p> Note: Calling this method does not close {@link PcapWriteHandler}.
     * Only Pcap Writes are closed. </p>
     *
     * @throws IOException If {@link OutputStream#close()} throws an exception
     */
    @Override
    public void close() throws IOException {
        if (state.get() == State.CLOSED) {
            logger.debug("PcapWriterHandler is already closed");
        } else {
            // If close is called prematurely, create writer to close output stream
            if (pCapWriter == null) {
                pCapWriter = new PcapWriter(this);
            }
            pCapWriter.close();
            markClosed();
            logger.debug("PcapWriterHandler is now closed");
        }
    }

    private enum ChannelType {
        TCP, UDP
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PcapWriteHandler}
     */
    public static final class Builder {
        private boolean captureZeroByte;
        private boolean sharedOutputStream;
        private boolean writePcapGlobalHeader = true;

        private ChannelType channelType;
        private InetSocketAddress initiatorAddr;
        private InetSocketAddress handlerAddr;
        private boolean isServerPipeline;

        private Builder() {
        }

        /**
         * Set to {@code true} to enable capturing packets with empty (0 bytes) payload. Otherwise, if set to
         * {@code false}, empty packets will be filtered out.
         *
         * @param captureZeroByte Whether to filter out empty packets.
         * @return this builder
         */
        public Builder captureZeroByte(boolean captureZeroByte) {
            this.captureZeroByte = captureZeroByte;
            return this;
        }

        /**
         * Set to {@code true} if multiple {@link PcapWriteHandler} instances will be
         * writing to the same {@link OutputStream} concurrently, and write locking is
         * required. Otherwise, if set to {@code false}, no locking will be done.
         * Additionally, {@link #close} will not close the underlying {@code OutputStream}.
         * Note: it is probably an error to have both {@code writePcapGlobalHeader} and
         * {@code sharedOutputStream} set to {@code true} at the same time.
         *
         * @param sharedOutputStream Whether {@link OutputStream} is shared or not
         * @return this builder
         */
        public Builder sharedOutputStream(boolean sharedOutputStream) {
            this.sharedOutputStream = sharedOutputStream;
            return this;
        }

        /**
         * Set to {@code true} to write Pcap Global Header on initialization. Otherwise, if set to {@code false}, Pcap
         * Global Header will not be written on initialization. This could when writing Pcap data on a existing file
         * where Pcap Global Header is already present.
         *
         * @param writePcapGlobalHeader Whether to write the pcap global header.
         * @return this builder
         */
        public Builder writePcapGlobalHeader(boolean writePcapGlobalHeader) {
            this.writePcapGlobalHeader = writePcapGlobalHeader;
            return this;
        }

        /**
         * Force this handler to write data as if they were TCP packets, with the given connection metadata. If this
         * method isn't called, we determine the metadata from the channel.
         *
         * @param serverAddress    The address of the TCP server (handler)
         * @param clientAddress    The address of the TCP client (initiator)
         * @param isServerPipeline Whether the handler is part of the server channel
         * @return this builder
         */
        public Builder forceTcpChannel(InetSocketAddress serverAddress, InetSocketAddress clientAddress,
                                       boolean isServerPipeline) {
            channelType = ChannelType.TCP;
            handlerAddr = checkNotNull(serverAddress, "serverAddress");
            initiatorAddr = checkNotNull(clientAddress, "clientAddress");
            this.isServerPipeline = isServerPipeline;
            return this;
        }

        /**
         * Force this handler to write data as if they were UDP packets, with the given connection metadata. If this
         * method isn't called, we determine the metadata from the channel.
         * <br>
         * Note that even if this method is called, the address information on {@link DatagramPacket} takes precedence
         * if it is present.
         *
         * @param localAddress  The address of the UDP local
         * @param remoteAddress The address of the UDP remote
         * @return this builder
         */
        public Builder forceUdpChannel(InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
            channelType = ChannelType.UDP;
            handlerAddr = checkNotNull(remoteAddress, "remoteAddress");
            initiatorAddr = checkNotNull(localAddress, "localAddress");
            return this;
        }

        /**
         * Build the {@link PcapWriteHandler}.
         *
         * @param outputStream The output stream to write the pcap data to.
         * @return The handler.
         */
        public PcapWriteHandler build(OutputStream outputStream) {
            checkNotNull(outputStream, "outputStream");
            return new PcapWriteHandler(this, outputStream);
        }
    }

    private static final class WildcardAddressHolder {
        static final InetAddress wildcard4; // 0.0.0.0
        static final InetAddress wildcard6; // ::

        static {
            try {
                wildcard4 = InetAddress.getByAddress(new byte[4]);
                wildcard6 = InetAddress.getByAddress(new byte[16]);
            } catch (UnknownHostException e) {
                // would only happen if the byte array was of incorrect size
                throw new AssertionError(e);
            }
        }
    }
}
