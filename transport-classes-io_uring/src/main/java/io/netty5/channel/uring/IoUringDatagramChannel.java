/*
 * Copyright 2022 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.buffer.Buffer;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FixedReadHandleFactory;
import io.netty5.channel.MaxMessagesWriteHandleFactory;
import io.netty5.channel.ReadHandleFactory;
import io.netty5.channel.WriteHandleFactory;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.Errors;
import io.netty5.channel.unix.SegmentedDatagramPacket;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.channel.unix.UnixChannelUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.ObjLongConsumer;

import static java.util.Objects.requireNonNull;

public final class IoUringDatagramChannel extends AbstractIoUringChannel<UnixChannel> implements DatagramChannel {
    private static final Logger LOGGER = LoggerFactory.getLogger(IoUringDatagramChannel.class);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
                    StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
                    StringUtil.simpleClassName(Buffer.class) + ", " +
                    StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
                    StringUtil.simpleClassName(Buffer.class) + ')';

    private static final String EXPECTED_TYPES_DOMAIN_SOCKET =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
                    StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
                    StringUtil.simpleClassName(Buffer.class) + ", " +
                    StringUtil.simpleClassName(DomainSocketAddress.class) + ">, " +
                    StringUtil.simpleClassName(Buffer.class) + ')';

    private final Deque<CachedMsgHdrMemory> msgHdrCache;
    private final PendingData<Promise<Void>> pendingWrites;

    // The maximum number of bytes for an InetAddress / Inet6Address
    private final byte[] inet4AddressArray = new byte[SockaddrIn.IPV4_ADDRESS_LENGTH];
    private final byte[] inet6AddressArray = new byte[SockaddrIn.IPV6_ADDRESS_LENGTH];

    private volatile boolean activeOnOpen;
    private volatile int maxDatagramSize;
    private volatile boolean gro;
    private volatile boolean connected;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    public IoUringDatagramChannel(EventLoop eventLoop) {
        this(null, eventLoop, true, new FixedReadHandleFactory(2048),
                new MaxMessagesWriteHandleFactory(Integer.MAX_VALUE),
                LinuxSocket.newSocketDgram(), false);
    }

    IoUringDatagramChannel(
            UnixChannel parent, EventLoop eventLoop, boolean supportingDisconnect,
            ReadHandleFactory defaultReadHandleFactory, WriteHandleFactory defaultWriteHandleFactory,
            LinuxSocket socket, boolean active) {
        super(parent, eventLoop, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory,
                socket, null, active);
        msgHdrCache = new ArrayDeque<>();
        pendingWrites = PendingData.newPendingData();
    }

    /**
     * Returns {@code true} if the usage of {@link io.netty5.channel.unix.SegmentedDatagramPacket} is supported.
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    public static boolean isSegmentedDatagramPacketSupported() {
        return IoUring.isAvailable();
    }

    @Override
    public boolean isActive() {
        return isOpen() && (getActiveOnOpen() && isRegistered() || super.isActive());
    }

    @Override
    protected Logger logger() {
        return LOGGER;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        active = true;
    }

    @Override
    protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) throws Exception {
        if (currentCompletionResult >= 0) {
            connected = true;
        }
        return super.doFinishConnect(requestedRemoteAddress);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    private NetworkInterface networkInterface() throws SocketException {
        NetworkInterface iface = getNetworkInterface();
        if (iface == null) {
            SocketAddress localAddress = localAddress();
            if (localAddress instanceof InetSocketAddress) {
                return NetworkInterface.getByInetAddress(((InetSocketAddress) localAddress()).getAddress());
            }
        }
        return null;
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

        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return newFailedFuture(new UnsupportedOperationException("Multicast not supported"));
        }

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
        assert executor().inEventLoop();

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

        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return newFailedFuture(new UnsupportedOperationException("Multicast not supported"));
        }

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
    protected int nextReadBufferSize() {
        int expectedCapacity = readHandle().prepareRead();
        if (expectedCapacity == 0) {
            return 0;
        }
        int datagramSize = maxDatagramSize;
        if (datagramSize == 0) {
            return expectedCapacity;
        }
        return Math.min(datagramSize, expectedCapacity);
    }

    @Override
    protected void submitReadForReadBuffer(Buffer buffer, short readId, boolean nonBlocking, boolean link,
                                           ObjLongConsumer<Object> pendingConsumer) {
        try (var itr = buffer.forEachComponent()) {
            var cmp = itr.firstWritable();
            assert cmp != null;
            long address = cmp.writableNativeAddress();
            int writableBytes = cmp.writableBytes();
            int flags = nonBlocking ? Native.MSG_DONTWAIT : 0;
            if (connected) {
                // Call recv(2) because we have a known peer.
                IoUringIoOps ioOps = IoUringIoOps.newRecv(fd().intValue(), 0, flags, address, writableBytes, readId);
                long udata = registration().submit(ioOps);
                pendingConsumer.accept(buffer, udata);
                return;
            }
            // Call recvmsg(2) because we need the peer address for each packet.
            short segmentSize = 0;
            CachedMsgHdrMemory msgHdr = nextMsgHdr();
            msgHdr.write(socket, null, address, writableBytes, segmentSize);
            msgHdr.attachment = buffer;
            IoUringIoOps ioOps = IoUringIoOps.newRecvmsg(fd().intValue(), 0, flags, msgHdr.address(), readId);
            long udata = registration().submit(ioOps);
            pendingConsumer.accept(msgHdr, udata);
        }
    }

    @Override
    protected Object prepareCompletedRead(Object obj, int result) {
        if (obj instanceof CachedMsgHdrMemory) {
            try (CachedMsgHdrMemory msgHdr = (CachedMsgHdrMemory) obj) {
                Buffer buffer = msgHdr.attachment;
                msgHdr.attachment = null;
                buffer.skipWritableBytes(result);
                return msgHdr.read(this, buffer, result);
            }
        }
        Buffer buffer = (Buffer) super.prepareCompletedRead(obj, result);
        return new DatagramPacket(buffer, localAddress(), remoteAddress());
    }

    /**
     * {@code byte[]} that can be used as temporary storage to encode the ipv4 address
     */
    byte[] inet4AddressArray() {
        return inet4AddressArray;
    }

    /**
     * {@code byte[]} that can be used as temporary storage to encode the ipv6 address
     */
    byte[] inet6AddressArray() {
        return inet6AddressArray;
    }

    @Override
    protected boolean processRead(ReadSink readSink, Object read) {
        DatagramPacket packet = (DatagramPacket) read;
        Buffer buffer = packet.content();
        readSink.processRead(buffer.capacity(), buffer.readableBytes(), packet);
        return false;
    }

    private static void checkUnresolved(AddressedEnvelope<?, ?> envelope) {
        if (envelope.recipient() instanceof InetSocketAddress
                && (((InetSocketAddress) envelope.recipient()).isUnresolved())) {
            throw new UnresolvedAddressException();
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return filterOutboundMessage(msg, DomainSocketAddress.class, EXPECTED_TYPES_DOMAIN_SOCKET);
        }
        return filterOutboundMessage(msg, InetSocketAddress.class, EXPECTED_TYPES);
    }

    private Object filterOutboundMessage(Object msg, Class<? extends SocketAddress> recipientClass,
                                          String expectedTypes) {
        if (msg instanceof SegmentedDatagramPacket) {
            SegmentedDatagramPacket packet = (SegmentedDatagramPacket) msg;
            checkUnresolved(packet);

            if (recipientClass.isInstance(packet.recipient())) {
                Buffer content = packet.content();
                return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                        packet.replace(intoDirectBuffer(content, true)) : msg;
            }
        } else if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            checkUnresolved(packet);

            if (recipientClass.isInstance(packet.recipient())) {
                Buffer content = packet.content();
                return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                        packet.replace(intoDirectBuffer(content, true)) : msg;
            }
        } else if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? intoDirectBuffer(buf, true) : buf;
        } else if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            checkUnresolved(e);

            if (recipientClass.isInstance(e.recipient())) {
                InetSocketAddress recipient = (InetSocketAddress) e.recipient();
                Object content = e.content();
                if (content instanceof Buffer) {
                    Buffer buf = (Buffer) content;
                    if (UnixChannelUtil.isBufferCopyNeededForWrite(buf)) {
                        try {
                            return new DefaultBufferAddressedEnvelope<>(intoDirectBuffer(buf, true), recipient);
                        } finally {
                            SilentDispose.dispose(e, logger()); // Don't fail here, because we allocated a buffer.
                        }
                    }
                    return e;
                }
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + expectedTypes);
    }

    @Override
    protected void submitAllWriteMessages(WriteSink writeSink) {
        writeSink.consumeEachFlushedMessage(this::submiteWriteMessage);
    }

    private boolean submiteWriteMessage(Object msg, Promise<Void> promise) {
        promise.asFuture().addListener(f -> updateWritabilityIfNeeded(true, true));
        final Buffer data;
        final SocketAddress remoteAddress;
        final short segmentSize;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Buffer, SocketAddress> envelope = (AddressedEnvelope<Buffer, SocketAddress>) msg;
            data = envelope.content();
            remoteAddress = envelope.recipient();
            if (msg instanceof SegmentedDatagramPacket) {
                SegmentedDatagramPacket segMsg = (SegmentedDatagramPacket) msg;
                // We only need to tell the kernel that we want to use UDP_SEGMENT if there are multiple
                // segments in the packet.
                segmentSize = (short) (segMsg.segmentSize() < data.readableBytes() ? segMsg.segmentSize() : 0);
            } else {
                segmentSize = 0;
            }
        } else {
            data = (Buffer) msg;
            remoteAddress = remoteAddress();
            segmentSize = 0;
        }

        short pendingId = pendingWrites.addPending(promise);

        try (var itr = data.forEachComponent()) {
            var cmp = itr.firstReadable();
            assert cmp != null;
            if (connected) {
                IoUringIoOps ops = IoUringIoOps.newSend(fd().intValue(), 0, 0, cmp.readableNativeAddress(),
                        cmp.readableBytes(), pendingId);
                registration().submit(ops);
            } else {
                CachedMsgHdrMemory msgHdr = nextMsgHdr();
                msgHdr.write(socket, remoteAddress, cmp.readableNativeAddress(), cmp.readableBytes(), segmentSize);
                IoUringIoOps ops = IoUringIoOps.newSendmsg(fd().intValue(), 0, 0, msgHdr.address(), pendingId);
                registration().submit(ops);

                promise.asFuture().addListener(msgHdr);
            }
        }
        return writeHandle().lastWrite(data.readableBytes(), data.readableBytes(), 1);
    }

    @NotNull
    private CachedMsgHdrMemory nextMsgHdr() {
        CachedMsgHdrMemory msgHdr = msgHdrCache.pollFirst();
        if (msgHdr == null) {
            msgHdr = new CachedMsgHdrMemory(msgHdrCache);
        }
        return msgHdr;
    }

    @Override
    void writeComplete(int result, long udata) {
        Promise<Void> promise = pendingWrites.removePending(UserData.decodeData(udata));
        if (result < 0) {
            promise.setFailure(Errors.newIOException("send/sendmsg", result));
        } else {
            promise.setSuccess(null);
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        super.doDisconnect();
        connected = false;
        cacheAddresses(local, null);
        remote = null;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData)
            throws Exception {
        boolean connected = super.doConnect(remoteAddress, localAddress, initialData);
        if (connected) {
            this.connected = true;
        }
        return connected;
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
                throw new AssertionError();
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
    protected void doClose() {
        super.doClose();
        CachedMsgHdrMemory msgHdr;
        while ((msgHdr = msgHdrCache.pollFirst()) != null) {
            msgHdr.release();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (option == ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            return (T) Boolean.valueOf(activeOnOpen);
        }
        if (option == IoUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE) {
            return (T) Integer.valueOf(getMaxDatagramSize());
        }
        if (option == IoUringChannelOption.UDP_GRO) {
            return (T) Boolean.valueOf(isUdpGro());
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (option == ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            setActiveOnOpen((Boolean) value);
        } else if (option == IoUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE) {
            setMaxDatagramSize((Integer) value);
        } else if (option == IoUringChannelOption.UDP_GRO) {
            setUdpGro((Boolean) value);
        } else {
            super.setExtendedOption(option, value);
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (option == ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION ||
                option == IoUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE ||
                option == IoUringChannelOption.UDP_GRO) {
            return true;
        }
        return super.isExtendedOptionSupported(option);
    }

    private void setActiveOnOpen(boolean activeOnOpen) {
        if (isRegistered()) {
            throw new IllegalStateException("Can only be changed before channel was registered");
        }
        this.activeOnOpen = activeOnOpen;
    }

    boolean getActiveOnOpen() {
        return activeOnOpen;
    }

    private int getMaxDatagramSize() {
        return maxDatagramSize;
    }

    private void setMaxDatagramSize(int maxDatagramSize) {
        this.maxDatagramSize = maxDatagramSize;
    }

    /**
     * Enable / disable <a href="https://lwn.net/Articles/768995/">UDP_GRO</a>.
     * @param gro {@code true} if {@code UDP_GRO} should be enabled, {@code false} otherwise.
     */
    private void setUdpGro(boolean gro) {
        try {
            socket.setUdpGro(gro);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
        this.gro = gro;
    }

    /**
     * Returns if {@code UDP_GRO} is enabled.
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    private boolean isUdpGro() {
        // We don't do a syscall here but just return the cached value due a kernel bug:
        // https://lore.kernel.org/netdev/20210325195614.800687-1-norman_maurer@apple.com/T/#u
        return gro;
    }

    private static final class CachedMsgHdrMemory extends MsgHdrMemory
            implements FutureListener<Void>, AutoCloseable {
        private final Deque<CachedMsgHdrMemory> cache;
        private Buffer attachment;

        CachedMsgHdrMemory(Deque<CachedMsgHdrMemory> cache) {
            this.cache = cache;
        }

        @Override
        public void operationComplete(Future<? extends Void> future) {
            close();
        }

        @Override
        public void close() {
            if (!cache.offerFirst(this)) {
                release();
            }
        }

        @Override
        void release() {
            super.release();
        }
    }
}
