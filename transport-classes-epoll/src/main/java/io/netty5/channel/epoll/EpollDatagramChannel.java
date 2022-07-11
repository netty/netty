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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.util.Resource;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.unix.Errors;
import io.netty5.channel.unix.Errors.NativeIoException;
import io.netty5.channel.unix.SegmentedDatagramPacket;
import io.netty5.channel.unix.UnixChannelUtil;
import io.netty5.util.UncheckedBooleanSupplier;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.RecyclableArrayList;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.SocketException;

import static io.netty5.channel.epoll.LinuxSocket.newSocketDgram;
import static java.util.Objects.requireNonNull;

/**
 * {@link DatagramChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollDatagramChannel extends AbstractEpollChannel<UnixChannel, SocketAddress, SocketAddress>
        implements DatagramChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EpollDatagramChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(true);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(Buffer.class) + ", " +
            StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
            StringUtil.simpleClassName(Buffer.class) + ')';

    private final EpollDatagramChannelConfig config;
    private volatile boolean connected;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

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
     * Create a new instance which selects the {@link ProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public EpollDatagramChannel(EventLoop eventLoop) {
        this(eventLoop, null);
    }

    /**
     * Create a new instance using the given {@link ProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public EpollDatagramChannel(EventLoop eventLoop, ProtocolFamily family) {
        this(eventLoop, newSocketDgram(family),
        false);
    }

    /**
     * Create a new instance which selects the {@link ProtocolFamily} to use depending
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

    private NetworkInterface networkInterface() throws SocketException {
        NetworkInterface iface = config.getNetworkInterface();
        if (iface == null) {
            SocketAddress localAddress = localAddress();
            if (localAddress instanceof InetSocketAddress) {
                return NetworkInterface.getByInetAddress(((InetSocketAddress) localAddress()).getAddress());
            }
            throw new UnsupportedOperationException();
        }
        return iface;
    }

    @Override
    public Future<Void> joinGroup(InetAddress multicastAddress) {
        try {
            return joinGroup(multicastAddress, networkInterface(), null);
        } catch (IOException | UnsupportedOperationException e) {
            return newFailedFuture(e);
        }
    }

    @Override
    public Future<Void> joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        Promise<Void> promise = newPromise();
        if (executor().inEventLoop()) {
            joinGroup0(multicastAddress, networkInterface, source, promise);
        } else {
            executor().execute(() -> joinGroup0(multicastAddress, networkInterface, source, promise));
        }
        return promise.asFuture();
    }

    private void joinGroup0(InetAddress multicastAddress, NetworkInterface networkInterface,
                           InetAddress source, Promise<Void> promise) {
        assertEventLoop();

        try {
            socket.joinGroup(multicastAddress, networkInterface, source);
        } catch (IOException e) {
            promise.setFailure(e);
            return;
        }
        promise.setSuccess(null);
    }

    @Override
    public Future<Void> leaveGroup(InetAddress multicastAddress) {
        try {
            return leaveGroup(multicastAddress, networkInterface(), null);
        } catch (IOException | UnsupportedOperationException e) {
            return newFailedFuture(e);
        }
    }

    @Override
    public Future<Void> leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        Promise<Void> promise = newPromise();
        if (executor().inEventLoop()) {
            leaveGroup0(multicastAddress, networkInterface, source, promise);
        } else {
            executor().execute(() -> leaveGroup0(multicastAddress, networkInterface, source, promise));
        }
        return promise.asFuture();
    }

    private void leaveGroup0(
            final InetAddress multicastAddress, final NetworkInterface networkInterface, final InetAddress source,
            final Promise<Void> promise) {
        assert executor().inEventLoop();

        try {
            socket.leaveGroup(multicastAddress, networkInterface, source);
        } catch (IOException e) {
            promise.setFailure(e);
            return;
        }
        promise.setSuccess(null);
    }

    @Override
    public Future<Void> block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(sourceToBlock, "sourceToBlock");
        requireNonNull(networkInterface, "networkInterface");
        return newFailedFuture(new UnsupportedOperationException("Multicast block not supported"));
    }

    @Override
    public Future<Void> block(
            InetAddress multicastAddress, InetAddress sourceToBlock) {
        try {
            return block(
                    multicastAddress,
                    networkInterface(),
                    sourceToBlock);
        } catch (IOException | UnsupportedOperationException e) {
            return newFailedFuture(e);
        }
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) {
        switch (direction) {
            case Inbound:
                inputShutdown = true;
                break;
            case Outbound:
                outputShutdown = true;
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        if (!isActive()) {
            return true;
        }
        switch (direction) {
            case Inbound:
                return inputShutdown;
            case Outbound:
                return outputShutdown;
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (localAddress instanceof InetSocketAddress) {
            InetSocketAddress socketAddress = (InetSocketAddress) localAddress;
            if (socketAddress.getAddress().isAnyLocalAddress() &&
                    socketAddress.getAddress() instanceof Inet4Address) {
                if (socket.family() == StandardProtocolFamily.INET6) {
                    localAddress = new InetSocketAddress(LinuxSocket.INET6_ANY, socketAddress.getPort());
                }
            }
        }
        super.doBind(localAddress);
        active = true;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int maxMessagesPerWrite = config().getMaxMessagesPerWrite();
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
        final Buffer data;
        final InetSocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<?, InetSocketAddress> envelope = (AddressedEnvelope<?, InetSocketAddress>) msg;
            data = (Buffer) envelope.content();
            remoteAddress = envelope.recipient();
        } else {
            data = (Buffer) msg;
            remoteAddress = null;
        }

        if (data.readableBytes() == 0) {
            return true;
        }
        return doWriteOrSendBytes(data, remoteAddress, false) > 0;
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof SegmentedDatagramPacket) {
            if (!Native.IS_SUPPORTING_UDP_SEGMENT) {
                throw new UnsupportedOperationException(
                        "Unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
            }
            SegmentedDatagramPacket packet = (SegmentedDatagramPacket) msg;
            Buffer content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    packet.replace(newDirectBuffer(packet, content)) : msg;
        }
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            Buffer content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new DatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
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
                            SilentDispose.dispose(e, logger); // Don't fail here, because we allocated a buffer.
                        }
                    }
                    return e;
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
            Throwable exception = doReadBuffer(allocHandle);
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();

            if (exception != null) {
                pipeline.fireChannelExceptionCaught(exception);
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
                component.skipWritableBytes(bytesRead);
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
            DatagramPacket packet = new DatagramPacket(buf, localAddress(), remoteAddress());
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
        if (packet instanceof SegmentedDatagramPacket) {
            try (SegmentedDatagramPacket segmentedDatagramPacket = (SegmentedDatagramPacket) packet) {
                Buffer content = segmentedDatagramPacket.content();
                SocketAddress recipient = segmentedDatagramPacket.recipient();
                SocketAddress sender = segmentedDatagramPacket.sender();
                int segmentSize = segmentedDatagramPacket.segmentSize();
                do {
                    out.add(new DatagramPacket(content.readSplit(segmentSize), recipient, sender));
                } while (content.readableBytes() > 0);
            }
        } else {
            out.add(packet);
        }
    }

    private static void releaseAndRecycle(Object buffer, RecyclableArrayList packetList) {
        Resource.dispose(buffer);
        if (packetList != null) {
            for (int i = 0; i < packetList.size(); i++) {
                Resource.dispose(packetList.get(i));
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
        BufferAllocator allocator = pipeline.channel().bufferAllocator();
        for (int i = 0; i < messagesRead; i++) {
            pipeline.fireChannelRead(packetList.set(i, allocator.allocate(0)));
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
            InetSocketAddress local = (InetSocketAddress) localAddress();
            DatagramPacket packet = msg.newDatagramPacket(buf, local);
            if (!(packet instanceof SegmentedDatagramPacket)) {
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
            InetSocketAddress local = (InetSocketAddress) localAddress();
            if (received == 1) {
                // Single packet fast-path
                DatagramPacket packet = packets[0].newDatagramPacket(buf, local);
                if (!(packet instanceof SegmentedDatagramPacket)) {
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
                DatagramPacket packet = packets[i].newDatagramPacket(buf.readSplit(datagramSize), local);
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
