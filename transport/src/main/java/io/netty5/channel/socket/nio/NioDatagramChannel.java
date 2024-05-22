/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel.socket.nio;

import io.netty5.buffer.Buffer;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FixedReadHandleFactory;
import io.netty5.channel.MaxMessagesWriteHandleFactory;
import io.netty5.channel.nio.AbstractNioMessageChannel;
import io.netty5.channel.nio.NioIoOps;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SocketUtils;
import io.netty5.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.netty5.channel.ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION;
import static io.netty5.channel.socket.nio.NioChannelUtil.isDomainSocket;
import static io.netty5.channel.socket.nio.NioChannelUtil.toDomainSocketAddress;
import static io.netty5.channel.socket.nio.NioChannelUtil.toJdkFamily;
import static io.netty5.channel.socket.nio.NioChannelUtil.toUnixDomainSocketAddress;
import static java.util.Objects.requireNonNull;

/**
 * An NIO {@link io.netty5.channel.socket.DatagramChannel} that sends and receives an
 * {@link AddressedEnvelope AddressedEnvelope<Buffer, SocketAddress>}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link io.netty5.channel.socket.DatagramChannel},
 * {@link NioDatagramChannel} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>{@link ChannelOption}</th>
 * <th>{@code INET}</th>
 * <th>{@code INET6}</th>
 * <th>{@code UNIX}</th>
 * </tr><tr>
 * <td>{@link NioChannelOption}</td><td>X</td><td>X</td><td>X</td>
 * </tr>
 * </table>
 *
 * @see AddressedEnvelope
 * @see DatagramPacket
 */
public final class NioDatagramChannel
        extends AbstractNioMessageChannel<Channel, SocketAddress, SocketAddress>
        implements io.netty5.channel.socket.DatagramChannel {
    private static final Logger logger = LoggerFactory.getLogger(NioDatagramChannel.class);

    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(Buffer.class) + ", " +
            StringUtil.simpleClassName(SocketAddress.class) + ">, " +
            StringUtil.simpleClassName(Buffer.class) + ')';

    private final ProtocolFamily family;

    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    private Map<InetAddress, List<MembershipKey>> memberships;

    private volatile boolean activeOnOpen;
    private volatile boolean bound;

    private static DatagramChannel newSocket(SelectorProvider provider) {
        try {
             // Use the SelectorProvider to open SocketChannel and so remove condition in
             // SelectorProvider#provider() which is called by each DatagramChannel.open() otherwise.
             // See <a href="https://github.com/netty/netty/issues/2308">#2308</a>.
            return provider.openDatagramChannel();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    private static DatagramChannel newSocket(SelectorProvider provider, ProtocolFamily family) {
        if (family == null) {
            return newSocket(provider);
        }
        try {
            return provider.openDatagramChannel(family);
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    /**
     * Create a new instance which will use the Operation Systems default {@link ProtocolFamily}.
     */
    public NioDatagramChannel(EventLoop eventLoop) {
        this(eventLoop, newSocket(DEFAULT_SELECTOR_PROVIDER), null);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}
     * which will use the Operation Systems default {@link ProtocolFamily}.
     */
    public NioDatagramChannel(EventLoop eventLoop, SelectorProvider provider) {
        this(eventLoop, newSocket(provider), null);
    }

    /**
     * Create a new instance using the given {@link ProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public NioDatagramChannel(EventLoop eventLoop, ProtocolFamily family) {
        this(eventLoop, DEFAULT_SELECTOR_PROVIDER, family);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider} and {@link ProtocolFamily}.
     * If {@link ProtocolFamily} is {@code null} it will depend on the Operation Systems default
     * which will be chosen.
     */
    public NioDatagramChannel(EventLoop eventLoop, SelectorProvider provider, ProtocolFamily family) {
        this(eventLoop, newSocket(provider, toJdkFamily(family)), family);
    }

    /**
     * Create a new instance from the given {@link DatagramChannel}.
     */
    public NioDatagramChannel(EventLoop eventLoop, DatagramChannel socket, ProtocolFamily family) {
        super(null, eventLoop, true, new FixedReadHandleFactory(2048),
                new MaxMessagesWriteHandleFactory(Integer.MAX_VALUE), socket, NioIoOps.READ);
        this.family = toJdkFamily(family);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            return (T) Boolean.valueOf(isActiveOnOpen());
        }
        SocketOption<T> socketOption = NioChannelOption.toSocketOption(option);
        if (socketOption != null) {
            return NioChannelOption.getOption(javaChannel(), socketOption);
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            setActiveOnOpen((Boolean) value);
        } else {
            SocketOption<T> socketOption = NioChannelOption.toSocketOption(option);
            if (socketOption != null) {
                try {
                    // See: https://github.com/netty/netty/issues/576
                    if (socketOption == StandardSocketOptions.SO_BROADCAST &&
                            !isAnyLocalAddress() &&
                            !PlatformDependent.isWindows() && !PlatformDependent.maybeSuperUser()) {
                        // Warn a user about the fact that a non-root user can't receive a
                        // broadcast packet on *nix if the socket is bound on non-wildcard address.
                        logger.warn(
                                "A non-root user can't receive a broadcast packet if the socket " +
                                        "is not bound to a wildcard address; setting the SO_BROADCAST flag " +
                                        "anyway as requested on the socket which is bound to " +
                                        javaChannel().getLocalAddress() + '.');
                    }
                    NioChannelOption.setOption(javaChannel(), socketOption, value);
                } catch (IOException e) {
                    throw new ChannelException(e);
                }
            } else {
                super.setExtendedOption(option, value);
            }
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            return true;
        }
        SocketOption<?> socketOption = NioChannelOption.toSocketOption(option);
        if (socketOption != null) {
            return NioChannelOption.isOptionSupported(javaChannel(), socketOption);
        }
        return super.isExtendedOptionSupported(option);
    }

    private boolean isActiveOnOpen() {
        return activeOnOpen;
    }

    private void setActiveOnOpen(boolean activeOnOpen) {
        if (isRegistered()) {
            throw new IllegalStateException("Can only changed before channel was registered");
        }
        this.activeOnOpen = activeOnOpen;
    }

    private boolean isAnyLocalAddress() throws IOException {
        SocketAddress address = javaChannel().getLocalAddress();
        return address instanceof InetSocketAddress && ((InetSocketAddress) address).getAddress().isAnyLocalAddress();
    }

    private NetworkInterface getNetworkInterface() {
        try {
            return javaChannel().getOption(StandardSocketOptions.IP_MULTICAST_IF);
        } catch (IOException e) {
            throw new ChannelException(e);
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
    public boolean isActive() {
        DatagramChannel ch = javaChannel();
        return ch.isOpen() && (getOption(DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) && isRegistered() || bound);
    }

    @Override
    public boolean isConnected() {
        return javaChannel().isConnected();
    }

    @Override
    protected DatagramChannel javaChannel() {
        return (DatagramChannel) super.javaChannel();
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            SocketAddress address = javaChannel().getLocalAddress();
            if (isDomainSocket(family)) {
                return toDomainSocketAddress(address);
            }
            return address;
        } catch (IOException e) {
            // Just return null
            return null;
        }
    }

    @Override
    protected SocketAddress remoteAddress0() {
        try {
            SocketAddress address = javaChannel().getRemoteAddress();
            if (isDomainSocket(family)) {
                return toDomainSocketAddress(address);
            }
            return address;
        } catch (IOException e) {
            // Just return null
            return null;
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        doBind0(localAddress);
    }

    private void doBind0(SocketAddress localAddress) throws Exception {
        if (isDomainSocket(family)) {
            localAddress = toUnixDomainSocketAddress(localAddress);
        }
        SocketUtils.bind(javaChannel(), localAddress);
        bound = true;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress, Buffer initialData) throws Exception {
        if (localAddress != null) {
            doBind0(localAddress);
        }

        boolean success = false;
        try {
            javaChannel().connect(remoteAddress);
            // When connected we are also bound
            bound = true;
            success = true;
            return true;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) {
        return true;
    }

    @Override
    protected void doDisconnect() throws Exception {
        javaChannel().disconnect();
    }

    @Override
    protected int doReadMessages(ReadSink readSink) throws Exception {
        Buffer data = readSink.allocateBuffer();
        if (data == null) {
            readSink.processRead(0, 0, null);
            return 0;
        }
        int attemptedBytesRead = data.writableBytes();
        boolean free = true;
        try {
            SocketAddress remoteAddress = null;
            int actualBytesRead = 0;
            try (var iterator = data.forEachComponent()) {
                var component = iterator.firstWritable();
                if (component != null) {
                    ByteBuffer dst = component.writableBuffer();
                    int position = dst.position();
                    remoteAddress =  javaChannel().receive(dst);
                    actualBytesRead = dst.position() - position;
                }
            }
            if (remoteAddress == null) {
                readSink.processRead(attemptedBytesRead, 0, null);
                return 0;
            }
            data.skipWritableBytes(actualBytesRead);
            readSink.processRead(attemptedBytesRead, actualBytesRead,
                    new DatagramPacket(data, localAddress(), remoteAddress));
            free = false;
            return 1;
        } finally {
            if (free) {
                data.close();
            }
        }
    }

    @Override
    protected void doWriteNow(WriteSink writeSink) throws Exception {
        Object msg = writeSink.currentFlushedMessage();
        final SocketAddress remoteAddress;
        final Buffer buf;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Buffer, SocketAddress> envelope = (AddressedEnvelope<Buffer, SocketAddress>) msg;
            remoteAddress = envelope.recipient();
            buf = envelope.content();
        } else {
            buf = (Buffer) msg;
            remoteAddress = null;
        }

        final int length = buf.readableBytes();
        if (length == 0) {
            writeSink.complete(0, 0, 1, true);
            return;
        }

        int readable = buf.readableBytes();
        final int writtenBytes;
        try {
            if (buf.countReadableComponents() > 1) {
                // Let's make a copy so we only have one readableComponent. This is needed as the JDK does not support
                // using multiple buffers for one send operation and calling send / write multiple times would result
                // in multiple datagrams.
                try (Buffer copy = bufferAllocator().allocate(buf.readableBytes())) {
                    buf.copyInto(buf.readerOffset(), copy, copy.writerOffset(), buf.readableBytes());
                    copy.writerOffset(buf.readableBytes());
                    writtenBytes = write(copy, remoteAddress);
                }
            } else {
                writtenBytes = write(buf, remoteAddress);
            }
            writeSink.complete(readable, writtenBytes,  writtenBytes > 0 ? 1 : 0, writtenBytes > 0);
        } catch (Throwable e) {
            // Continue on write error as a DatagramChannel can write to multiple remote peers
            //
            // See https://github.com/netty/netty/issues/2665
            writeSink.complete(readable, e, true);
        }
    }

    private int write(Buffer buf, SocketAddress remoteAddress) throws IOException {
        assert buf.countReadableComponents() <= 1;
        try (var iterator = buf.forEachComponent()) {
            var c = iterator.firstReadable();
            final int writtenBytes;
            if (remoteAddress != null) {
                writtenBytes = javaChannel().send(c.readableBuffer(), remoteAddress);
            } else {
                writtenBytes = javaChannel().write(c.readableBuffer());
            }
            return writtenBytes;
        }
    }

    private static void checkUnresolved(AddressedEnvelope<?, ?> envelope) {
        if (envelope.recipient() instanceof InetSocketAddress
                && ((InetSocketAddress) envelope.recipient()).isUnresolved()) {
            throw new UnresolvedAddressException();
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket p = (DatagramPacket) msg;
            checkUnresolved(p);

            Buffer content = p.content();
            if (isSingleDirectBuffer(content)) {
                return p;
            }
            return new DatagramPacket(newDirectBuffer(p, content), p.recipient());
        }

        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            if (isSingleDirectBuffer(buf)) {
                return buf;
            }
            return newDirectBuffer(buf);
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            checkUnresolved(e);

            Object content = e.content();
            if (content instanceof Buffer) {
                Buffer buf = (Buffer) content;
                if (isSingleDirectBuffer(buf)) {
                    return e;
                }
                return new DefaultBufferAddressedEnvelope<>(newDirectBuffer((Resource<?>) e, buf), e.recipient());
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    /**
     * Checks if the specified buffer is a direct buffer and not composite.
     * (We check this because otherwise we need to make it a non-composite buffer.)
     */
    private static boolean isSingleDirectBuffer(Buffer buf) {
        return buf.isDirect() && buf.countComponents() == 1;
    }

    private NetworkInterface networkInterface() throws SocketException {
        NetworkInterface iface = getNetworkInterface();
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
            return joinGroup(
                    multicastAddress, networkInterface(), null);
        } catch (SocketException | UnsupportedOperationException e) {
            return newFailedFuture(e);
        }
    }

    @Override
    public Future<Void> joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        try {
            MembershipKey key;
            if (source == null) {
                key = javaChannel().join(multicastAddress, networkInterface);
            } else {
                key = javaChannel().join(multicastAddress, networkInterface, source);
            }

            synchronized (this) {
                List<MembershipKey> keys = null;
                if (memberships == null) {
                    memberships = new HashMap<>();
                } else {
                    keys = memberships.get(multicastAddress);
                }
                if (keys == null) {
                    keys = new ArrayList<>();
                    memberships.put(multicastAddress, keys);
                }
                keys.add(key);
            }

            return newSucceededFuture();
        } catch (Throwable e) {
            return newFailedFuture(e);
        }
    }

    @Override
    public Future<Void> leaveGroup(InetAddress multicastAddress) {
        try {
            return leaveGroup(
                    multicastAddress, networkInterface(), null);
        } catch (SocketException | UnsupportedOperationException e) {
            return newFailedFuture(e);
        }
    }

    @Override
    public Future<Void> leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        synchronized (this) {
            if (memberships != null) {
                List<MembershipKey> keys = memberships.get(multicastAddress);
                if (keys != null) {
                    Iterator<MembershipKey> keyIt = keys.iterator();

                    while (keyIt.hasNext()) {
                        MembershipKey key = keyIt.next();
                        if (networkInterface.equals(key.networkInterface())) {
                           if (source == null && key.sourceAddress() == null ||
                               source != null && source.equals(key.sourceAddress())) {
                               key.drop();
                               keyIt.remove();
                           }
                        }
                    }
                    if (keys.isEmpty()) {
                        memberships.remove(multicastAddress);
                    }
                }
            }
        }
        return newSucceededFuture();
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     */
    @Override
    public Future<Void> block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(sourceToBlock, "sourceToBlock");
        requireNonNull(networkInterface, "networkInterface");

        synchronized (this) {
            if (memberships != null) {
                List<MembershipKey> keys = memberships.get(multicastAddress);
                for (MembershipKey key: keys) {
                    if (networkInterface.equals(key.networkInterface())) {
                        try {
                            key.block(sourceToBlock);
                        } catch (IOException e) {
                            return newFailedFuture(e);
                        }
                    }
                }
            }
        }
        return newSucceededFuture();
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress
     */
    @Override
    public Future<Void> block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        try {
            return block(
                    multicastAddress, networkInterface(),
                    sourceToBlock);
        } catch (SocketException | UnsupportedOperationException e) {
            return newFailedFuture(e);
        }
    }
}
