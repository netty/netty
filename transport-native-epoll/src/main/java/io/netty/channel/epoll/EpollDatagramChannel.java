/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.DatagramSocketAddress;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.Errors.NativeIoException;
import io.netty.channel.unix.Socket;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.RecyclableArrayList;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.epoll.LinuxSocket.newSocketDgram;

/**
 * {@link DatagramChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollDatagramChannel extends AbstractEpollChannel implements DatagramChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(true);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
            StringUtil.simpleClassName(ByteBuf.class) + ')';

    private final EpollDatagramChannelConfig config;
    private volatile boolean connected;

    /**
     * Create a new instance which selects the {@link InternetProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public EpollDatagramChannel() {
        this(null);
    }

    /**
     * Create a new instance using the given {@link InternetProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public EpollDatagramChannel(InternetProtocolFamily family) {
        this(family == null ?
                newSocketDgram(Socket.isIPv6Preferred()) : newSocketDgram(family == InternetProtocolFamily.IPv6),
                false);
    }

    /**
     * Create a new instance which selects the {@link InternetProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public EpollDatagramChannel(int fd) {
        this(new LinuxSocket(fd), true);
    }

    private EpollDatagramChannel(LinuxSocket fd, boolean active) {
        super(null, fd, active);
        config = new EpollDatagramChannelConfig(this);
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isActive() {
        return socket.isOpen() && (config.getActiveOnOpen() && isRegistered() || active);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise promise) {
        try {
            return joinGroup(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()), null, promise);
        } catch (IOException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface,
            ChannelPromise promise) {
        return joinGroup(multicastAddress.getAddress(), networkInterface, null, promise);
    }

    @Override
    public ChannelFuture joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return joinGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress source, final ChannelPromise promise) {

        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

        try {
            socket.joinGroup(multicastAddress, networkInterface, source);
            promise.setSuccess();
        } catch (IOException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress) {
        return leaveGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelPromise promise) {
        try {
            return leaveGroup(
                    multicastAddress, NetworkInterface.getByInetAddress(localAddress().getAddress()), null, promise);
        } catch (IOException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture leaveGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return leaveGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(
            InetSocketAddress multicastAddress,
            NetworkInterface networkInterface, ChannelPromise promise) {
        return leaveGroup(multicastAddress.getAddress(), networkInterface, null, promise);
    }

    @Override
    public ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return leaveGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(
            final InetAddress multicastAddress, final NetworkInterface networkInterface, final InetAddress source,
            final ChannelPromise promise) {
        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

        try {
            socket.leaveGroup(multicastAddress, networkInterface, source);
            promise.setSuccess();
        } catch (IOException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newPromise());
    }

    @Override
    public ChannelFuture block(
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress sourceToBlock, final ChannelPromise promise) {
        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(sourceToBlock, "sourceToBlock");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));
        return promise;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, InetAddress sourceToBlock, ChannelPromise promise) {
        try {
            return block(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    sourceToBlock, promise);
        } catch (Throwable e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollDatagramChannelUnsafe();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (localAddress instanceof InetSocketAddress) {
            InetSocketAddress socketAddress = (InetSocketAddress) localAddress;
            if (socketAddress.getAddress().isAnyLocalAddress() &&
                    socketAddress.getAddress() instanceof Inet4Address) {
                if (socket.family() == InternetProtocolFamily.IPv6) {
                    localAddress = new InetSocketAddress(LinuxSocket.INET6_ANY, socketAddress.getPort());
                }
            }
        }
        super.doBind(localAddress);
        active = true;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int maxMessagesPerWrite = maxMessagesPerWrite();
        while (maxMessagesPerWrite > 0) {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                break;
            }

            try {
                // Check if sendmmsg(...) is supported which is only the case for GLIBC 2.14+
                if (Native.IS_SUPPORTING_SENDMMSG && in.size() > 1 ||
                        // We only handle UDP_SEGMENT in sendmmsg.
                        in.current() instanceof SegmentedDatagramPacket) {
                    NativeDatagramPacketArray array = cleanDatagramPacketArray();
                    array.add(in, isConnected(), maxMessagesPerWrite);
                    int cnt = array.count();

                    if (cnt >= 1) {
                        // Try to use gathering writes via sendmmsg(...) syscall.
                        int offset = 0;
                        NativeDatagramPacketArray.NativeDatagramPacket[] packets = array.packets();

                        int send = socket.sendmmsg(packets, offset, cnt);
                        if (send == 0) {
                            // Did not write all messages.
                            break;
                        }
                        for (int i = 0; i < send; i++) {
                            in.remove();
                        }
                        maxMessagesPerWrite -= send;
                        continue;
                    }
                }
                boolean done = false;
                for (int i = config().getWriteSpinCount(); i > 0; --i) {
                    if (doWriteMessage(msg)) {
                        done = true;
                        break;
                    }
                }

                if (done) {
                    in.remove();
                    maxMessagesPerWrite --;
                } else {
                    break;
                }
            } catch (IOException e) {
                maxMessagesPerWrite --;
                // Continue on write error as a DatagramChannel can write to multiple remote peers
                //
                // See https://github.com/netty/netty/issues/2665
                in.remove(e);
            }
        }

        if (in.isEmpty()) {
            // Did write all messages.
            clearFlag(Native.EPOLLOUT);
        } else {
            // Did not write all messages.
            setFlag(Native.EPOLLOUT);
        }
    }

    private boolean doWriteMessage(Object msg) throws Exception {
        final ByteBuf data;
        final InetSocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<ByteBuf, InetSocketAddress> envelope =
                    (AddressedEnvelope<ByteBuf, InetSocketAddress>) msg;
            data = envelope.content();
            remoteAddress = envelope.recipient();
        } else {
            data = (ByteBuf) msg;
            remoteAddress = null;
        }

        final int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        return doWriteOrSendBytes(data, remoteAddress, false) > 0;
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof SegmentedDatagramPacket) {
            if (!Native.IS_SUPPORTING_UDP_SEGMENT) {
                throw new UnsupportedOperationException(
                        "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
            }
            SegmentedDatagramPacket packet = (SegmentedDatagramPacket) msg;
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    packet.replace(newDirectBuffer(packet, content)) : msg;
        }
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new DatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            if (e.content() instanceof ByteBuf &&
                (e.recipient() == null || e.recipient() instanceof InetSocketAddress)) {

                ByteBuf content = (ByteBuf) e.content();
                return UnixChannelUtil.isBufferCopyNeededForWrite(content)?
                        new DefaultAddressedEnvelope<ByteBuf, InetSocketAddress>(
                            newDirectBuffer(e, content), (InetSocketAddress) e.recipient()) : e;
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public EpollDatagramChannelConfig config() {
        return config;
    }

    @Override
    protected void doDisconnect() throws Exception {
        socket.disconnect();
        connected = active = false;
        resetCachedAddresses();
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (super.doConnect(remoteAddress, localAddress)) {
            connected = true;
            return true;
        }
        return false;
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        connected = false;
    }

    final class EpollDatagramChannelUnsafe extends AbstractEpollUnsafe {

        @Override
        void epollInReady() {
            assert eventLoop().inEventLoop();
            DatagramChannelConfig config = config();
            if (shouldBreakEpollInReady(config)) {
                clearEpollIn0();
                return;
            }
            final EpollRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.edgeTriggered(isFlagSet(Native.EPOLLET));

            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            allocHandle.reset(config);
            epollInBefore();

            Throwable exception = null;
            try {
                try {
                    boolean connected = isConnected();
                    do {
                        ByteBuf byteBuf = allocHandle.allocate(allocator);
                        final boolean read;
                        int datagramSize = config().getMaxDatagramPayloadSize();

                        // Only try to use recvmmsg if its really supported by the running system.
                        int numDatagram = Native.IS_SUPPORTING_RECVMMSG ?
                                datagramSize == 0 ? 1 : byteBuf.writableBytes() / datagramSize :
                                0;

                        try {
                            if (numDatagram <= 1) {
                                if (connected) {
                                    read = connectedRead(allocHandle, byteBuf, datagramSize);
                                } else {
                                    read = read(allocHandle, byteBuf, datagramSize);
                                }
                            } else {
                                // Try to use scattering reads via recvmmsg(...) syscall.
                                read = scatteringRead(allocHandle, byteBuf, datagramSize, numDatagram);
                            }
                        } catch (NativeIoException e) {
                            if (connected) {
                                throw translateForConnected(e);
                            }
                            throw e;
                        }

                        if (read) {
                            readPending = false;
                        } else {
                            break;
                        }
                    // We use the TRUE_SUPPLIER as it is also ok to read less then what we did try to read (as long
                    // as we read anything).
                    } while (allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER));
                } catch (Throwable t) {
                    exception = t;
                }

                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
                }
            } finally {
                epollInFinally(config);
            }
        }
    }

    private boolean connectedRead(EpollRecvByteAllocatorHandle allocHandle, ByteBuf byteBuf, int maxDatagramPacketSize)
            throws Exception {
        try {
            int writable = maxDatagramPacketSize != 0 ? Math.min(byteBuf.writableBytes(), maxDatagramPacketSize)
                    : byteBuf.writableBytes();
            allocHandle.attemptedBytesRead(writable);

            int writerIndex = byteBuf.writerIndex();
            int localReadAmount;
            if (byteBuf.hasMemoryAddress()) {
                localReadAmount = socket.readAddress(byteBuf.memoryAddress(), writerIndex, writerIndex + writable);
            } else {
                ByteBuffer buf = byteBuf.internalNioBuffer(writerIndex, writable);
                localReadAmount = socket.read(buf, buf.position(), buf.limit());
            }

            if (localReadAmount <= 0) {
                allocHandle.lastBytesRead(localReadAmount);

                // nothing was read, release the buffer.
                return false;
            }
            byteBuf.writerIndex(writerIndex + localReadAmount);

            allocHandle.lastBytesRead(maxDatagramPacketSize <= 0 ?
                    localReadAmount : writable);

            DatagramPacket packet = new DatagramPacket(byteBuf, localAddress(), remoteAddress());
            allocHandle.incMessagesRead(1);

            pipeline().fireChannelRead(packet);
            byteBuf = null;
            return true;
        } finally {
            if (byteBuf != null) {
                byteBuf.release();
            }
        }
    }

    private IOException translateForConnected(NativeIoException e) {
        // We need to correctly translate connect errors to match NIO behaviour.
        if (e.expectedErr() == Errors.ERROR_ECONNREFUSED_NEGATIVE) {
            PortUnreachableException error = new PortUnreachableException(e.getMessage());
            error.initCause(e);
            return error;
        }
        return e;
    }

    private boolean scatteringRead(EpollRecvByteAllocatorHandle allocHandle,
            ByteBuf byteBuf, int datagramSize, int numDatagram) throws IOException {
        RecyclableArrayList bufferPackets = null;
        try {
            int offset = byteBuf.writerIndex();
            NativeDatagramPacketArray array = cleanDatagramPacketArray();

            for (int i = 0; i < numDatagram;  i++, offset += datagramSize) {
                if (!array.addWritable(byteBuf, offset, datagramSize)) {
                    break;
                }
            }

            allocHandle.attemptedBytesRead(offset - byteBuf.writerIndex());

            NativeDatagramPacketArray.NativeDatagramPacket[] packets = array.packets();

            int received = socket.recvmmsg(packets, 0, array.count());
            if (received == 0) {
                allocHandle.lastBytesRead(-1);
                return false;
            }
            int bytesReceived = received * datagramSize;
            byteBuf.writerIndex(bytesReceived);
            InetSocketAddress local = localAddress();
            if (received == 1) {
                // Single packet fast-path
                DatagramPacket packet = packets[0].newDatagramPacket(byteBuf, local);
                allocHandle.lastBytesRead(datagramSize);
                allocHandle.incMessagesRead(1);
                pipeline().fireChannelRead(packet);
                byteBuf = null;
                return true;
            }

            // Its important that we process all received data out of the NativeDatagramPacketArray
            // before we call fireChannelRead(...). This is because the user may call flush()
            // in a channelRead(...) method and so may re-use the NativeDatagramPacketArray again.
            bufferPackets = RecyclableArrayList.newInstance();
            for (int i = 0; i < received; i++) {
                DatagramPacket packet = packets[i].newDatagramPacket(byteBuf.readRetainedSlice(datagramSize), local);
                bufferPackets.add(packet);
            }

            allocHandle.lastBytesRead(bytesReceived);
            allocHandle.incMessagesRead(received);

            for (int i = 0; i < received; i++) {
                pipeline().fireChannelRead(bufferPackets.set(i, Unpooled.EMPTY_BUFFER));
            }
            bufferPackets.recycle();
            bufferPackets = null;
            return true;
        } finally {
            if (byteBuf != null) {
                byteBuf.release();
            }
            if (bufferPackets != null) {
                for (int i = 0; i < bufferPackets.size(); i++) {
                    ReferenceCountUtil.release(bufferPackets.get(i));
                }
                bufferPackets.recycle();
            }
        }
    }

    private boolean read(EpollRecvByteAllocatorHandle allocHandle, ByteBuf byteBuf, int maxDatagramPacketSize)
            throws IOException {
        try {
            int writable = maxDatagramPacketSize != 0 ? Math.min(byteBuf.writableBytes(), maxDatagramPacketSize)
                    : byteBuf.writableBytes();
            allocHandle.attemptedBytesRead(writable);
            int writerIndex = byteBuf.writerIndex();
            final DatagramSocketAddress remoteAddress;
            if (byteBuf.hasMemoryAddress()) {
                // has a memory address so use optimized call
                remoteAddress = socket.recvFromAddress(
                        byteBuf.memoryAddress(), writerIndex, writerIndex + writable);
            } else {
                ByteBuffer nioData = byteBuf.internalNioBuffer(writerIndex, writable);
                remoteAddress = socket.recvFrom(nioData, nioData.position(), nioData.limit());
            }

            if (remoteAddress == null) {
                allocHandle.lastBytesRead(-1);
                return false;
            }
            InetSocketAddress localAddress = remoteAddress.localAddress();
            if (localAddress == null) {
                localAddress = localAddress();
            }
            int received = remoteAddress.receivedAmount();
            allocHandle.lastBytesRead(maxDatagramPacketSize <= 0 ?
                    received : writable);
            byteBuf.writerIndex(writerIndex + received);
            allocHandle.incMessagesRead(1);

            pipeline().fireChannelRead(new DatagramPacket(byteBuf, localAddress, remoteAddress));
            byteBuf = null;
            return true;
        } finally {
            if (byteBuf != null) {
                byteBuf.release();
            }
        }
    }

    private NativeDatagramPacketArray cleanDatagramPacketArray() {
        return ((EpollEventLoop) eventLoop()).cleanDatagramPacketArray();
    }
}
