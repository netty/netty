/*
 * Copyright 2021 The Netty Project
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
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FixedRecvBufferAllocator;
import io.netty5.channel.unix.DomainDatagramChannel;
import io.netty5.channel.unix.DomainDatagramPacket;
import io.netty5.channel.unix.DomainDatagramSocketAddress;
import io.netty5.channel.unix.DomainSocketAddress;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.PeerCredentials;
import io.netty5.channel.unix.RecvFromAddressDomainSocket;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.channel.unix.UnixChannelUtil;
import io.netty5.util.UncheckedBooleanSupplier;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;

import static io.netty5.channel.ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION;
import static io.netty5.channel.ChannelOption.SO_SNDBUF;
import static io.netty5.channel.epoll.LinuxSocket.newSocketDomainDgram;
import static io.netty5.util.CharsetUtil.UTF_8;

/**
 * {@link DomainDatagramChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link DomainDatagramChannel} and {@link UnixChannel},
 * {@link EpollDomainDatagramChannel} allows the following options in the option map:
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th>
 * </tr><tr>
 * <td>{@link io.netty5.channel.unix.RawUnixChannelOption}</td>
 * </tr><tr>
 * <td>{@link io.netty5.channel.unix.IntegerUnixChannelOption}</td>
 * </tr>
 * </table>
 */
@UnstableApi
public final class EpollDomainDatagramChannel
        extends AbstractEpollChannel<UnixChannel, DomainSocketAddress, DomainSocketAddress>
        implements DomainDatagramChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EpollDomainDatagramChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(true);

    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();
    private static final String EXPECTED_TYPES =
            " (expected: " +
                    StringUtil.simpleClassName(DomainDatagramPacket.class) + ", " +
                    StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
                    StringUtil.simpleClassName(Buffer.class) + ", " +
                    StringUtil.simpleClassName(DomainSocketAddress.class) + ">, " +
                    StringUtil.simpleClassName(Buffer.class) + ')';

    private volatile boolean connected;
    private volatile DomainSocketAddress local;
    private volatile DomainSocketAddress remote;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;
    private volatile boolean activeOnOpen;

    public EpollDomainDatagramChannel(EventLoop eventLoop) {
        this(eventLoop, newSocketDomainDgram(), false);
    }

    public EpollDomainDatagramChannel(EventLoop eventLoop, int fd) {
        this(eventLoop, new LinuxSocket(fd), true);
    }

    private EpollDomainDatagramChannel(EventLoop eventLoop, LinuxSocket socket, boolean active) {
        super(null, eventLoop, METADATA, new FixedRecvBufferAllocator(2048), socket, active);
    }

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            return (T) Boolean.valueOf(activeOnOpen);
        }
        if (option == SO_SNDBUF) {
            return (T) Integer.valueOf(getSendBufferSize());
        }
        return super.getExtendedOption(option);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            setActiveOnOpen((Boolean) value);
        } else if (option == SO_SNDBUF) {
            setSendBufferSize((Integer) value);
        } else {
            super.setExtendedOption(option, value);
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (SUPPORTED_OPTIONS.contains(option)) {
            return true;
        }
        return super.isExtendedOptionSupported(option);
    }

    @SuppressWarnings("deprecation")
    private static Set<ChannelOption<?>> supportedOptions() {
        return newSupportedIdentityOptionsSet(
                DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, SO_SNDBUF);
    }

    private void setActiveOnOpen(boolean activeOnOpen) {
        if (isRegistered()) {
            throw new IllegalStateException("Can only changed before channel was registered");
        }
        this.activeOnOpen = activeOnOpen;
    }

    private boolean getActiveOnOpen() {
        return activeOnOpen;
    }

    private void setSendBufferSize(int sendBufferSize) {
        try {
            socket.setSendBufferSize(sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSendBufferSize() {
        try {
            return socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        local = (DomainSocketAddress) localAddress;
        active = true;
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        connected = active = false;
        local = null;
        remote = null;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (super.doConnect(remoteAddress, localAddress)) {
            if (localAddress != null) {
                local = (DomainSocketAddress) localAddress;
            }
            remote = (DomainSocketAddress) remoteAddress;
            connected = true;
            return true;
        }
        return false;
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
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
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int maxMessagesPerWrite = getMaxMessagesPerWrite();
        while (maxMessagesPerWrite > 0) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }

            try {
                boolean done = false;
                for (int i = getWriteSpinCount(); i > 0; --i) {
                    if (doWriteMessage(msg)) {
                        done = true;
                        break;
                    }
                }

                if (done) {
                    in.remove();
                    maxMessagesPerWrite--;
                } else {
                    break;
                }
            } catch (IOException e) {
                maxMessagesPerWrite--;

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
        DomainSocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<?, DomainSocketAddress> envelope =
                    (AddressedEnvelope<?, DomainSocketAddress>) msg;
            data = envelope.content();
            remoteAddress = envelope.recipient();
        } else {
            data = msg;
            remoteAddress = null;
        }

        return doWriteBufferMessage((Buffer) data, remoteAddress);
    }

    private boolean doWriteBufferMessage(Buffer data, DomainSocketAddress remoteAddress) throws IOException {
        final int initialReadableBytes = data.readableBytes();
        if (initialReadableBytes == 0) {
            return true;
        }

        if (data.countReadableComponents() > 1) {
            IovArray array = registration().cleanIovArray();
            data.forEachReadable(0, array);
            int count = array.count();
            assert count != 0;

            final long writtenBytes;
            if (remoteAddress == null) {
                writtenBytes = socket.writevAddresses(array.memoryAddress(0), count);
            } else {
                writtenBytes = socket.sendToAddressesDomainSocket(
                        array.memoryAddress(0), count, remoteAddress.path().getBytes(UTF_8));
            }
            return writtenBytes > 0;
        }
        if (remoteAddress == null) {
            data.forEachReadable(0, (index, component) -> {
                int written = socket.writeAddress(component.readableNativeAddress(), 0, component.readableBytes());
                component.skipReadableBytes(written);
                return false;
            });
        } else {
            data.forEachReadable(0, (index, component) -> {
                int written = socket.sendToAddressDomainSocket(
                        component.readableNativeAddress(), 0, component.readableBytes(),
                        remoteAddress.path().getBytes(UTF_8));
                component.skipReadableBytes(written);
                return false;
            });
        }
        return data.readableBytes() < initialReadableBytes;
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DomainDatagramPacket) {
            DomainDatagramPacket packet = (DomainDatagramPacket) msg;
            Buffer content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new DomainDatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf) ? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            SocketAddress recipient = e.recipient();
            if (recipient == null || recipient instanceof DomainSocketAddress) {
                DomainSocketAddress domainRecipient = (DomainSocketAddress) recipient;
                if (e.content() instanceof Buffer) {
                    Buffer buf = (Buffer) e.content();
                    if (UnixChannelUtil.isBufferCopyNeededForWrite(buf)) {
                        try {
                            return new DefaultBufferAddressedEnvelope<>(newDirectBuffer(null, buf), domainRecipient);
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
    public boolean isActive() {
        return socket.isOpen() && (getActiveOnOpen() && isRegistered() || active);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    protected DomainSocketAddress localAddress0() {
        return local;
    }

    /**
     * Returns the unix credentials (uid, gid, pid) of the peer
     * <a href=https://man7.org/linux/man-pages/man7/socket.7.html>SO_PEERCRED</a>
     */
    public PeerCredentials peerCredentials() throws IOException {
        return socket.getPeerCredentials();
    }

    @Override
    protected DomainSocketAddress remoteAddress0() {
        return remote;
    }

    @Override
    void epollInReady() {
        assert executor().inEventLoop();
        if (shouldBreakEpollInReady()) {
            clearEpollIn0();
            return;
        }
        final EpollRecvBufferAllocatorHandle allocHandle = recvBufAllocHandle();

        final ChannelPipeline pipeline = pipeline();
        allocHandle.reset();
        epollInBefore();

        try {
            Throwable exception = doReadBuffer(allocHandle, pipeline);

            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();

            if (exception != null) {
                pipeline.fireChannelExceptionCaught(exception);
            }
            readIfIsAutoRead();
        } finally {
            epollInFinally();
        }
    }

    private Throwable doReadBuffer(EpollRecvBufferAllocatorHandle allocHandle, ChannelPipeline pipeline) {
        BufferAllocator allocator = bufferAllocator();
        if (!allocator.getAllocationType().isDirect()) {
            allocator = DefaultBufferAllocators.offHeapAllocator();
        }
        Buffer buf = null;
        try {
            boolean connected = isConnected();
            do {
                buf = allocHandle.allocate(allocator);
                allocHandle.attemptedBytesRead(buf.writableBytes());

                final DomainDatagramPacket packet;
                if (connected) {
                    doReadBytes(buf);
                    if (allocHandle.lastBytesRead() <= 0) {
                        // nothing was read, release the buffer.
                        buf.close();
                        break;
                    }
                    packet = new DomainDatagramPacket(buf, (DomainSocketAddress) localAddress(),
                                                      (DomainSocketAddress) remoteAddress());
                } else {
                    final RecvFromAddressDomainSocket recvFrom = new RecvFromAddressDomainSocket(socket);
                    buf.forEachWritable(0, recvFrom);
                    final DomainDatagramSocketAddress remoteAddress = recvFrom.remoteAddress();

                    if (remoteAddress == null) {
                        allocHandle.lastBytesRead(-1);
                        buf.close();
                        break;
                    }
                    DomainSocketAddress localAddress = remoteAddress.localAddress();
                    if (localAddress == null) {
                        localAddress = (DomainSocketAddress) localAddress();
                    }
                    allocHandle.lastBytesRead(remoteAddress.receivedAmount());
                    buf.skipWritableBytes(allocHandle.lastBytesRead());

                    packet = new DomainDatagramPacket(buf, localAddress, remoteAddress);
                }

                allocHandle.incMessagesRead(1);

                readPending = false;
                pipeline.fireChannelRead(packet);

                buf = null;

                // We use the TRUE_SUPPLIER as it is also ok to read less than what we did try to read (as long
                // as we read anything).
            } while (allocHandle.continueReading(isAutoRead(), UncheckedBooleanSupplier.TRUE_SUPPLIER));
        } catch (Throwable t) {
            if (buf != null) {
                buf.close();
            }
            return t;
        }
        return null;
    }
}
