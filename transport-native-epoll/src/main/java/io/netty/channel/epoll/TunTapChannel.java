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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;

/**
 * A {@link Channel} implementation that can be used to send or receive packets over
 * a Linux TUN/TAP interface.
 * <p>
 * To initialize a {@link TunTapChannel}, create a {@link TunAddress} or {@link TapAddress}
 * object containing the name of the TUN/TAP device to open, and then bind the {@link TunTapChannel}
 * to the address.
 * <p>
 * To send packets into the device (causing them to be received by the local host's network
 * stack) create a {@link TunTapPacket} object containing the packet type and data, and call
 * {@link TunTapChannel} write() or writeAndFlush().
 * <p>
 * When the kernel sends packets out via the device, the packets are delivered to the channel
 * handlers via the channelRead() method, wrapped in {@link TunTapPacket} objects.
 */
public class TunTapChannel extends AbstractEpollChannel {

    private boolean isOpen = true;
    private TunTapChannelConfig config;
    private TunTapAddress boundLocalAddress;
    private boolean isTapChannel;
    private static final ChannelMetadata metadata = new ChannelMetadata(true);

    /**
     * Create a new {@link TunTapChannel} object that uses the default TUN/TAP clone device.
     */
    public TunTapChannel() throws IOException {
        this(defaultCloneDeviceName());
    }

    /**
     * Create a new {@link TunTapChannel} object that uses the specified TUN/TAP clone device.
     */
    public TunTapChannel(String cloneDevName) throws IOException {
        // Open the specified clone device and pass the fd to the base class.
        super(LinuxSocket.openTunTapCloneDevice(cloneDevName), Native.EPOLLIN);

        config = new TunTapChannelConfig(this);
    }

    @Override
    public TunTapChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    public boolean isTapChannel() {
        return isTapChannel;
    }

    @Override
    public ChannelMetadata metadata() {
        return metadata;
    }

    public static String defaultCloneDeviceName() {
        return "/dev/net/tun";
    }

    @Override
    protected SocketAddress localAddress0() {
        return boundLocalAddress;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doRegister() throws Exception {
        // Override doRegester() to prevent the base class from adding the TUN/TAP file descriptor
        // to the epoll set until after the call is made to allocate/open the network interface
        // (see doBind() below).  If the file descriptor is added to the epoll set before the
        // network interface is allocated, epoll will never wake on incoming packets.  This
        // appears to be an idiosyncrasy of the Linux kernel.
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (isActive()) {
            throw new IllegalStateException("TunTapChannel already bound to interface");
        }

        TunTapAddress tunTapAddr = (TunTapAddress) localAddress;

        String ifName = tunTapAddr.interfaceName();
        boolean isTapDevice = tunTapAddr.isTapAddress();

        // Allocate/open the specified network interface name.
        tunTapFd().allocTunTapInterface(ifName, isTapDevice);

        boundLocalAddress = tunTapAddr;
        isTapChannel = isTapDevice;

        // Add the TUN/TAP file descriptor to the epoll set.
        // NOTE: this must happen *after* the call to allocInterface() above (see
        // doRegister() for further details).
        EpollEventLoop loop = (EpollEventLoop) eventLoop();
        loop.add(this);

        // Mark the channel as active.
        active = true;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (;;) {
            // Get the next packet to be written.  Bail if no more packets.
            TunTapPacket packet = (TunTapPacket) in.current();
            if (packet == null) {
                break;
            }

            ByteBuf packetData = packet.packetData();

            try {
                // If the buffer has an accessible memory address, write the packet data
                // directly from buffer memory. Otherwise, get the packet data as an NIO
                // buffer and write the data from that.
                if (packetData.hasMemoryAddress()) {
                    long memoryAddress = packetData.memoryAddress();
                    int readerIndex = packetData.readerIndex();
                    int len = packetData.readableBytes();
                    tunTapFd().writeTunTapPacketAddress(packet.protocol(), memoryAddress, readerIndex, len);
                } else {
                    ByteBuffer nioBuf = packetData.nioBuffer();
                    int pos = nioBuf.position();
                    int limit = nioBuf.limit();
                    tunTapFd().writeTunTapPacket(packet.protocol(), nioBuf, pos, limit - pos);
                }

                // Drop the packet from the outbound queue.
                in.remove();
            } catch (Exception e) {
                in.remove(e);
            }
        }
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new TunTapChannelUnsafe();
    }

    final LinuxSocket tunTapFd() {
        return (LinuxSocket) fd();
    }

    final class TunTapChannelUnsafe extends AbstractEpollUnsafe {

        private final List<TunTapPacket> rcvdPackets = new ArrayList<TunTapPacket>();

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            throw new UnsupportedOperationException("TunTapChannelUnsafe.connect");
        }

        @Override
        void epollInReady() {
            assert eventLoop().inEventLoop();
            if (tunTapFd().isInputShutdown()) {
                clearEpollIn0();
                return;
            }

            EpollRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.edgeTriggered(isFlagSet(Native.EPOLLET));

            ByteBufAllocator allocator = config.getAllocator();

            // Tell the allocator handle we're about to start another read loop.
            allocHandle.reset(config);

            epollInBefore();

            try {
                Throwable exception = null;
                ByteBuf packetData = null;
                LinuxSocket tunTapFd = tunTapFd();

                try {
                    // Repeatedly read packets from the interface for as long as there are
                    // packets to be read and the allocator handle tells us its okay...
                    do {
                        // Allocate an appropriately sized buffer in which to receive data.
                        packetData = allocHandle.allocate(allocator);

                        // Tell the allocator handle how much data we're attempting to read.
                        allocHandle.attemptedBytesRead(packetData.writableBytes());

                        int writerIndex = packetData.writerIndex();
                        long readRes;

                        // If the buffer has an accessible memory address, read packet data directly into the
                        // buffer memory. Otherwise, get the internal NIO buffer associated with the ByteBuf
                        // and read into that.
                        if (packetData.hasMemoryAddress()) {
                            readRes = tunTapFd.readTunTapPacketAddress(packetData.memoryAddress(), writerIndex,
                                packetData.capacity());
                        } else {
                            ByteBuffer nioData = packetData.internalNioBuffer(writerIndex, packetData.writableBytes());
                            readRes = tunTapFd.readTunTapPacket(nioData, nioData.position(), nioData.limit());
                        }

                        // Stop reading if no packet was received.
                        if (readRes == 0) {
                            allocHandle.lastBytesRead(-1);
                            packetData.release();
                            packetData = null;
                            break;
                        }

                        // Extract the length of the packet and the protocol from the read result.
                        int packetLen = (int) (readRes & 0xFFFFFFFF);
                        int protocol = (int) ((readRes >> 32) & 0xFFFF);

                        // Advance the writer index in the packet data buffer by the length of the packet.
                        packetData.writerIndex(writerIndex + packetLen);

                        // Allocate a TunTapPacket object containing the packet data and the protocol information.
                        TunTapPacket packet = new TunTapPacket(protocol, packetData);
                        rcvdPackets.add(packet);

                        // Avoid releasing the packet data buffer now that its wrapped in a TunTapPacket.
                        packetData = null;

                        // Tell the allocator handle about the newly received packet.
                        allocHandle.incMessagesRead(1);
                        allocHandle.lastBytesRead(packetLen);

                    } while (allocHandle.continueReading());
                } catch (Throwable ex) {
                    if (packetData != null) {
                        packetData.release();
                    }
                    exception = ex;
                }

                ChannelPipeline pipeline = pipeline();

                // For each received packet, fire a read event up the pipeline.
                int packetCount = rcvdPackets.size();
                for (int i = 0; i < packetCount; i ++) {
                    readPending = false;
                    pipeline.fireChannelRead(rcvdPackets.get(i));
                }
                rcvdPackets.clear();

                // Tell the allocator handle the read is complete.
                allocHandle.readComplete();

                // Fire a channel read complete event up the pipeline.
                pipeline.fireChannelReadComplete();

                // If an exception occurred, fire a exception caught event up the pipeline.
                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
                }
            } finally {
                epollInFinally(config);
            }
        }
    }
}
