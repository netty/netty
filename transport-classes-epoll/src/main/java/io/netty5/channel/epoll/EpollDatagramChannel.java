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
package io.netty5.channel.epoll;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.ByteBufConvertible;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Resource;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.DefaultByteBufAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.socket.BufferDatagramPacket;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.InternetProtocolFamily;
import io.netty5.channel.unix.BufferSegmentedDatagramPacket;
import io.netty5.channel.unix.Errors;
import io.netty5.channel.unix.Errors.NativeIoException;
import io.netty5.channel.unix.SegmentedDatagramPacket;
import io.netty5.channel.unix.Socket;
import io.netty5.channel.unix.UnixChannelUtil;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.ReferenceCounted;
import io.netty5.util.UncheckedBooleanSupplier;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.RecyclableArrayList;
import io.netty5.util.internal.StringUtil;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty5.channel.epoll.LinuxSocket.newSocketDgram;
import static java.util.Objects.requireNonNull;

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
     * Returns {@code true} if {@link SegmentedDatagramPacket} is supported natively.
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    public static boolean isSegmentedDatagramPacketSupported() {
        return Epoll.isAvailable() &&
                // We only support it together with sendmmsg(...)
                Native.IS_SUPPORTING_SENDMMSG && Native.IS_SUPPORTING_UDP_SEGMENT;
    }

    /**
     * Create a new instance which selects the {@link InternetProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public EpollDatagramChannel(EventLoop eventLoop) {
        this(eventLoop, null);
    }

    /**
     * Create a new instance using the given {@link InternetProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public EpollDatagramChannel(EventLoop eventLoop, InternetProtocolFamily family) {
        this(eventLoop, family == null ?
            newSocketDgram(Socket.isIPv6Preferred()) :
            newSocketDgram(family == InternetProtocolFamily.IPv6),
        false);
    }

    /**
     * Create a new instance which selects the {@link InternetProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public EpollDatagramChannel(EventLoop eventLoop, int fd) {
        this(eventLoop, new LinuxSocket(fd), true);
    }

    private EpollDatagramChannel(EventLoop eventLoop, LinuxSocket fd, boolean active) {
        super(null, eventLoop, fd, active);
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
    public boolean isActive() {
        return socket.isOpen() && (config.getActiveOnOpen() && isRegistered() || active);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public Future<Void> joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public Future<Void> joinGroup(InetAddress multicastAddress, Promise<Void> promise) {
        try {
            NetworkInterface iface = config().getNetworkInterface();
            if (iface == null) {
                iface = NetworkInterface.getByInetAddress(localAddress().getAddress());
            }
            return joinGroup(multicastAddress, iface, null, promise);
        } catch (IOException e) {
            return promise.setFailure(e).asFuture();
        }
    }

    @Override
    public Future<Void> joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public Future<Void> joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface,
            Promise<Void> promise) {
        return joinGroup(multicastAddress.getAddress(), networkInterface, null, promise);
    }

    @Override
    public Future<Void> joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return joinGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public Future<Void> joinGroup(
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress source, final Promise<Void> promise) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        try {
            socket.joinGroup(multicastAddress, networkInterface, source);
            promise.setSuccess(null);
        } catch (IOException e) {
            promise.setFailure(e);
        }
        return promise.asFuture();
    }

    @Override
    public Future<Void> leaveGroup(InetAddress multicastAddress) {
        return leaveGroup(multicastAddress, newPromise());
    }

    @Override
    public Future<Void> leaveGroup(InetAddress multicastAddress, Promise<Void> promise) {
        try {
            return leaveGroup(
                    multicastAddress, NetworkInterface.getByInetAddress(localAddress().getAddress()), null, promise);
        } catch (IOException e) {
            return promise.setFailure(e).asFuture();
        }
    }

    @Override
    public Future<Void> leaveGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return leaveGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public Future<Void> leaveGroup(
            InetSocketAddress multicastAddress,
            NetworkInterface networkInterface, Promise<Void> promise) {
        return leaveGroup(multicastAddress.getAddress(), networkInterface, null, promise);
    }

    @Override
    public Future<Void> leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return leaveGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public Future<Void> leaveGroup(
            final InetAddress multicastAddress, final NetworkInterface networkInterface, final InetAddress source,
            final Promise<Void> promise) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        try {
            socket.leaveGroup(multicastAddress, networkInterface, source);
            promise.setSuccess(null);
        } catch (IOException e) {
            promise.setFailure(e);
        }
        return promise.asFuture();
    }

    @Override
    public Future<Void> block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newPromise());
    }

    @Override
    public Future<Void> block(
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress sourceToBlock, final Promise<Void> promise) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(sourceToBlock, "sourceToBlock");
        requireNonNull(networkInterface, "networkInterface");
        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));
        return promise.asFuture();
    }

    @Override
    public Future<Void> block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    @Override
    public Future<Void> block(
            InetAddress multicastAddress, InetAddress sourceToBlock, Promise<Void> promise) {
        try {
            return block(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    sourceToBlock, promise);
        } catch (Throwable e) {
            promise.setFailure(e);
        }
        return promise.asFuture();
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
        final Object data;
        final InetSocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<?, InetSocketAddress> envelope = (AddressedEnvelope<?, InetSocketAddress>) msg;
            data = envelope.content();
            remoteAddress = envelope.recipient();
        } else {
            data = msg;
            remoteAddress = null;
        }

        if (data instanceof Buffer) {
            Buffer buf = (Buffer) data;
            if (buf.readableBytes() == 0) {
                return true;
            }
            return doWriteOrSendBytes(buf, remoteAddress, false) > 0;
        } else {
            ByteBuf buf = (ByteBuf) data;
            if (buf.readableBytes() == 0) {
                return true;
            }
            return doWriteOrSendBytes(buf, remoteAddress, false) > 0;
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof BufferSegmentedDatagramPacket) {
            if (!Native.IS_SUPPORTING_UDP_SEGMENT) {
                throw new UnsupportedOperationException(
                        "Unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
            }
            BufferSegmentedDatagramPacket packet = (BufferSegmentedDatagramPacket) msg;
            Buffer content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    packet.replace(newDirectBuffer(packet, content)) : msg;
        }
        if (msg instanceof SegmentedDatagramPacket) {
            if (!Native.IS_SUPPORTING_UDP_SEGMENT) {
                throw new UnsupportedOperationException(
                        "Unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
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
        if (msg instanceof BufferDatagramPacket) {
            BufferDatagramPacket packet = (BufferDatagramPacket) msg;
            Buffer content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new BufferDatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }
        if (msg instanceof ByteBufConvertible) {
            ByteBuf buf = ((ByteBufConvertible) msg).asByteBuf();
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            if (e.recipient() == null || e.recipient() instanceof InetSocketAddress) {
                InetSocketAddress recipient = (InetSocketAddress) e.recipient();
                Object content = e.content();
                if (content instanceof Buffer) {
                    Buffer buf = (Buffer) content;
                    if (UnixChannelUtil.isBufferCopyNeededForWrite(buf)) {
                        try {
                            return new DefaultBufferAddressedEnvelope<>(newDirectBuffer(buf), recipient);
                        } finally {
                            ReferenceCountUtil.release(e);
                        }
                    }
                    return e;
                } else if (content instanceof ByteBufConvertible) {
                    ByteBuf buf = ((ByteBufConvertible) content).asByteBuf();
                    return UnixChannelUtil.isBufferCopyNeededForWrite(buf)?
                            new DefaultByteBufAddressedEnvelope<>(
                                    newDirectBuffer(e, buf), recipient) : e;
                }
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
            assert executor().inEventLoop();
            EpollDatagramChannelConfig config = config();
            if (shouldBreakEpollInReady(config)) {
                clearEpollIn0();
                return;
            }
            final EpollRecvBufferAllocatorHandle allocHandle = recvBufAllocHandle();

            final ChannelPipeline pipeline = pipeline();
            allocHandle.reset(config);
            epollInBefore();

            try {
                Throwable exception = config.getRecvBufferAllocatorUseBuffer()?
                        doReadBuffer(allocHandle) :
                        doReadByteBuf(allocHandle);

                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
                }
                readIfIsAutoRead();
            } finally {
                epollInFinally(config);
            }
        }

        private Throwable doReadBuffer(EpollRecvBufferAllocatorHandle allocHandle) {
            final BufferAllocator allocator = config().getBufferAllocator();
            try {
                boolean connected = isConnected();
                do {
                    final boolean read;
                    int datagramSize = config().getMaxDatagramPayloadSize();

                    Buffer buf = allocHandle.allocate(allocator);
                    // Only try to use recvmmsg if its really supported by the running system.
                    int numDatagram = Native.IS_SUPPORTING_RECVMMSG ?
                            datagramSize == 0 ? 1 : buf.writableBytes() / datagramSize :
                            0;
                    try {
                        if (numDatagram <= 1) {
                            if (!connected || config().isUdpGro()) {
                                read = recvmsg(allocHandle, cleanDatagramPacketArray(), buf);
                            } else {
                                read = connectedRead(allocHandle, buf, datagramSize);
                            }
                        } else {
                            // Try to use scattering reads via recvmmsg(...) syscall.
                            read = scatteringRead(allocHandle, cleanDatagramPacketArray(),
                                                  buf, datagramSize, numDatagram);
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
                return t;
            }
            return null;
        }

        private Throwable doReadByteBuf(EpollRecvBufferAllocatorHandle allocHandle) {
            final ByteBufAllocator allocator = config().getAllocator();
            try {
                boolean connected = isConnected();
                do {
                    final boolean read;
                    int datagramSize = config().getMaxDatagramPayloadSize();

                    ByteBuf buf = allocHandle.allocate(allocator);
                    // Only try to use recvmmsg if its really supported by the running system.
                    int numDatagram = Native.IS_SUPPORTING_RECVMMSG ?
                            datagramSize == 0 ? 1 : buf.writableBytes() / datagramSize :
                            0;
                    try {
                        if (numDatagram <= 1) {
                            if (!connected || config().isUdpGro()) {
                                read = recvmsg(allocHandle, cleanDatagramPacketArray(), buf);
                            } else {
                                read = connectedRead(allocHandle, buf, datagramSize);
                            }
                        } else {
                            // Try to use scattering reads via recvmmsg(...) syscall.
                            read = scatteringRead(allocHandle, cleanDatagramPacketArray(),
                                                  buf, datagramSize, numDatagram);
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
                return t;
            }
            return null;
        }
    }

    private boolean connectedRead(EpollRecvBufferAllocatorHandle allocHandle, ByteBuf byteBuf,
                                  int maxDatagramPacketSize) throws Exception {
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

    private boolean connectedRead(EpollRecvBufferAllocatorHandle allocHandle, Buffer buf,
                                  int maxDatagramPacketSize) throws Exception {
        try {
            int writable = maxDatagramPacketSize != 0 ? Math.min(buf.writableBytes(), maxDatagramPacketSize)
                    : buf.writableBytes();
            allocHandle.attemptedBytesRead(writable);

            int initialWritableBytes = buf.writableBytes();
            buf.forEachWritable(0, (index, component) -> {
                long address = component.writableNativeAddress();
                assert address != 0;
                int bytesRead = socket.readAddress(address, 0, component.writableBytes());
                allocHandle.lastBytesRead(bytesRead);
                if (bytesRead <= 0) {
                    return false;
                }
                component.skipWritable(bytesRead);
                return true;
            });
            final int totalBytesRead = initialWritableBytes - buf.writableBytes();
            if (totalBytesRead == 0) {
                // nothing was read, release the buffer.
                return false;
            }
            if (maxDatagramPacketSize > 0) {
                allocHandle.lastBytesRead(totalBytesRead);
            }
            BufferDatagramPacket packet = new BufferDatagramPacket(buf, localAddress(), remoteAddress());
            allocHandle.incMessagesRead(1);

            pipeline().fireChannelRead(packet);
            buf = null;
            return true;
        } finally {
            if (buf != null) {
                buf.close();
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

    private static void addDatagramPacketToOut(AddressedEnvelope<?, ?> packet, RecyclableArrayList out) {
        if (packet instanceof BufferSegmentedDatagramPacket) {
            try (BufferSegmentedDatagramPacket segmentedDatagramPacket = (BufferSegmentedDatagramPacket) packet) {
                Buffer content = segmentedDatagramPacket.content();
                InetSocketAddress recipient = segmentedDatagramPacket.recipient();
                InetSocketAddress sender = segmentedDatagramPacket.sender();
                int segmentSize = segmentedDatagramPacket.segmentSize();
                do {
                    out.add(new BufferDatagramPacket(content.readSplit(segmentSize), recipient, sender));
                } while (content.readableBytes() > 0);
            }
        } else if (packet instanceof SegmentedDatagramPacket) {
            SegmentedDatagramPacket segmentedDatagramPacket =
                    (SegmentedDatagramPacket) packet;
            ByteBuf content = segmentedDatagramPacket.content();
            InetSocketAddress recipient = segmentedDatagramPacket.recipient();
            InetSocketAddress sender = segmentedDatagramPacket.sender();
            int segmentSize = segmentedDatagramPacket.segmentSize();
            do {
                out.add(new DatagramPacket(content.readRetainedSlice(Math.min(content.readableBytes(),
                        segmentSize)), recipient, sender));
            } while (content.isReadable());

            segmentedDatagramPacket.release();
        } else {
            out.add(packet);
        }
    }

    private static void releaseAndRecycle(Object buffer, RecyclableArrayList packetList) {
        if (buffer instanceof ReferenceCounted) {
            ((ReferenceCounted) buffer).release();
        } else if (buffer instanceof Resource<?>) {
            ((Resource<?>) buffer).close();
        }
        if (packetList != null) {
            for (int i = 0; i < packetList.size(); i++) {
                ReferenceCountUtil.release(packetList.get(i));
            }
            packetList.recycle();
        }
    }

    private static void processPacket(ChannelPipeline pipeline, EpollRecvBufferAllocatorHandle handle,
                                      int bytesRead, AddressedEnvelope<?, ?> packet) {
        handle.lastBytesRead(bytesRead);
        handle.incMessagesRead(1);
        pipeline.fireChannelRead(packet);
    }

    private static void processPacketList(ChannelPipeline pipeline, EpollRecvBufferAllocatorHandle handle,
                                          int bytesRead, RecyclableArrayList packetList) {
        int messagesRead = packetList.size();
        handle.lastBytesRead(bytesRead);
        handle.incMessagesRead(messagesRead);
        for (int i = 0; i < messagesRead; i++) {
            pipeline.fireChannelRead(packetList.set(i, Unpooled.EMPTY_BUFFER));
        }
    }

    private boolean recvmsg(EpollRecvBufferAllocatorHandle allocHandle,
                            NativeDatagramPacketArray array, ByteBuf byteBuf) throws IOException {
        RecyclableArrayList datagramPackets = null;
        try {
            int writable = byteBuf.writableBytes();

            boolean added = array.addWritable(byteBuf, byteBuf.writerIndex(), writable);
            assert added;

            allocHandle.attemptedBytesRead(writable);

            NativeDatagramPacketArray.NativeDatagramPacket msg = array.packets()[0];

            int bytesReceived = socket.recvmsg(msg);
            if (bytesReceived == 0) {
                allocHandle.lastBytesRead(-1);
                return false;
            }
            byteBuf.writerIndex(bytesReceived);
            InetSocketAddress local = localAddress();
            DatagramPacket packet = msg.newDatagramPacket(byteBuf, local);
            if (!(packet instanceof SegmentedDatagramPacket)) {
                processPacket(pipeline(), allocHandle, bytesReceived, packet);
                byteBuf = null;
            } else {
                // Its important we process all received data out of the NativeDatagramPacketArray
                // before we call fireChannelRead(...). This is because the user may call flush()
                // in a channelRead(...) method and so may re-use the NativeDatagramPacketArray again.
                datagramPackets = RecyclableArrayList.newInstance();
                addDatagramPacketToOut(packet, datagramPackets);
                // null out byteBuf as addDatagramPacketToOut did take ownership of the ByteBuf / packet and transferred
                // it into the RecyclableArrayList.
                byteBuf = null;

                processPacketList(pipeline(), allocHandle, bytesReceived, datagramPackets);
                datagramPackets.recycle();
                datagramPackets = null;
            }

            return true;
        } finally {
            releaseAndRecycle(byteBuf, datagramPackets);
        }
    }

    private boolean recvmsg(EpollRecvBufferAllocatorHandle allocHandle,
                            NativeDatagramPacketArray array, Buffer buf) throws IOException {
        RecyclableArrayList datagramPackets = null;
        try {
            int initialWriterOffset = buf.writerOffset();

            boolean added = array.addWritable(buf, 0, null);
            assert added;

            allocHandle.attemptedBytesRead(buf.writerOffset() - initialWriterOffset);

            NativeDatagramPacketArray.NativeDatagramPacket msg = array.packets()[0];

            int bytesReceived = socket.recvmsg(msg);
            if (bytesReceived == 0) {
                allocHandle.lastBytesRead(-1);
                return false;
            }
            buf.writerOffset(initialWriterOffset + bytesReceived);
            InetSocketAddress local = localAddress();
            BufferDatagramPacket packet = msg.newDatagramPacket(buf, local);
            if (!(packet instanceof BufferSegmentedDatagramPacket)) {
                processPacket(pipeline(), allocHandle, bytesReceived, packet);
                buf = null;
            } else {
                // Its important we process all received data out of the NativeDatagramPacketArray
                // before we call fireChannelRead(...). This is because the user may call flush()
                // in a channelRead(...) method and so may re-use the NativeDatagramPacketArray again.
                datagramPackets = RecyclableArrayList.newInstance();
                addDatagramPacketToOut(packet, datagramPackets);
                // null out buf as addDatagramPacketToOut did take ownership of the Buffer / packet and transferred
                // it into the RecyclableArrayList.
                buf = null;

                processPacketList(pipeline(), allocHandle, bytesReceived, datagramPackets);
                datagramPackets.recycle();
                datagramPackets = null;
            }

            return true;
        } finally {
            releaseAndRecycle(buf, datagramPackets);
        }
    }

    private boolean scatteringRead(EpollRecvBufferAllocatorHandle allocHandle, NativeDatagramPacketArray array,
                                   ByteBuf byteBuf, int datagramSize, int numDatagram) throws IOException {
        RecyclableArrayList datagramPackets = null;
        try {
            int offset = byteBuf.writerIndex();
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
                if (!(packet instanceof SegmentedDatagramPacket)) {
                    processPacket(pipeline(), allocHandle, datagramSize, packet);
                    byteBuf = null;
                    return true;
                }
            }
            // It's important we process all received data out of the NativeDatagramPacketArray
            // before we call fireChannelRead(...). This is because the user may call flush()
            // in a channelRead(...) method and so may re-use the NativeDatagramPacketArray again.
            datagramPackets = RecyclableArrayList.newInstance();
            for (int i = 0; i < received; i++) {
                DatagramPacket packet = packets[i].newDatagramPacket(byteBuf.readRetainedSlice(datagramSize), local);
                addDatagramPacketToOut(packet, datagramPackets);
            }
            // Since we used readRetainedSlice(...) before, we should now release the byteBuf and null it out.
            byteBuf.release();
            byteBuf = null;

            processPacketList(pipeline(), allocHandle, bytesReceived, datagramPackets);
            datagramPackets.recycle();
            datagramPackets = null;
            return true;
        } finally {
            releaseAndRecycle(byteBuf, datagramPackets);
        }
    }

    private boolean scatteringRead(EpollRecvBufferAllocatorHandle allocHandle, NativeDatagramPacketArray array,
                                   Buffer buf, int datagramSize, int numDatagram) throws IOException {
        RecyclableArrayList datagramPackets = null;
        try {
            int initialWriterOffset = buf.writerOffset();
            for (int i = 0; i < numDatagram; i++) {
                if (!array.addWritable(buf, datagramSize, null)) {
                    break;
                }
            }

            allocHandle.attemptedBytesRead(buf.writerOffset() - initialWriterOffset);

            NativeDatagramPacketArray.NativeDatagramPacket[] packets = array.packets();

            int received = socket.recvmmsg(packets, 0, array.count());
            if (received == 0) {
                allocHandle.lastBytesRead(-1);
                return false;
            }
            int bytesReceived = received * datagramSize;
            buf.writerOffset(initialWriterOffset + bytesReceived);
            InetSocketAddress local = localAddress();
            if (received == 1) {
                // Single packet fast-path
                BufferDatagramPacket packet = packets[0].newDatagramPacket(buf, local);
                if (!(packet instanceof BufferSegmentedDatagramPacket)) {
                    processPacket(pipeline(), allocHandle, datagramSize, packet);
                    buf = null;
                    return true;
                }
            }
            // It's important we process all received data out of the NativeDatagramPacketArray
            // before we call fireChannelRead(...). This is because the user may call flush()
            // in a channelRead(...) method and so may re-use the NativeDatagramPacketArray again.
            datagramPackets = RecyclableArrayList.newInstance();
            for (int i = 0; i < received; i++) {
                BufferDatagramPacket packet = packets[i].newDatagramPacket(buf.readSplit(datagramSize), local);
                addDatagramPacketToOut(packet, datagramPackets);
            }
            // Since we used readSplit(...) before, we should now release the buffer and null it out.
            buf.close();
            buf = null;

            processPacketList(pipeline(), allocHandle, bytesReceived, datagramPackets);
            datagramPackets.recycle();
            datagramPackets = null;
            return true;
        } finally {
            releaseAndRecycle(buf, datagramPackets);
        }
    }

    private NativeDatagramPacketArray cleanDatagramPacketArray() {
        return registration().cleanDatagramPacketArray();
    }
}
