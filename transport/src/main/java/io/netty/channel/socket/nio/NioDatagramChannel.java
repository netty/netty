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
package io.netty.channel.socket.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.netty.channel.ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION;
import static io.netty.channel.ChannelOption.IP_MULTICAST_ADDR;
import static io.netty.channel.ChannelOption.IP_MULTICAST_IF;
import static io.netty.channel.ChannelOption.IP_MULTICAST_LOOP_DISABLED;
import static io.netty.channel.ChannelOption.IP_MULTICAST_TTL;
import static io.netty.channel.ChannelOption.IP_TOS;
import static io.netty.channel.ChannelOption.SO_BROADCAST;
import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_REUSEADDR;
import static io.netty.channel.ChannelOption.SO_SNDBUF;

/**
 * An NIO datagram {@link Channel} that sends and receives an
 * {@link AddressedEnvelope AddressedEnvelope<ByteBuf, SocketAddress>}.
 *
 * @see AddressedEnvelope
 * @see DatagramPacket
 */
public final class NioDatagramChannel
        extends AbstractNioMessageChannel implements io.netty.channel.socket.DatagramChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(true, 16);
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(SocketAddress.class) + ">, " +
            StringUtil.simpleClassName(ByteBuf.class) + ')';

    private final DatagramChannelConfig config;

    private Map<InetAddress, List<MembershipKey>> memberships;

    /**
     *  Use the {@link SelectorProvider} to open {@link DatagramChannel} and so remove condition in
     *  {@link SelectorProvider#provider()} which is called by each DatagramChannel.open() otherwise.
     * <p>
     *  See <a href="https://github.com/netty/netty/issues/2308">#2308</a>.
     */
    private static DatagramChannel newSocket(SelectorProvider provider) {
        try {
            return provider.openDatagramChannel();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    private static DatagramChannel newSocket(SelectorProvider provider, SocketProtocolFamily ipFamily) {
        if (ipFamily == null) {
            return newSocket(provider);
        }

        try {
            return provider.openDatagramChannel(ipFamily.toJdkFamily());
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    /**
     * Create a new instance which will use the Operation Systems default {@link SocketProtocolFamily}.
     */
    public NioDatagramChannel() {
        this(newSocket(DEFAULT_SELECTOR_PROVIDER));
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}
     * which will use the Operation Systems default {@link SocketProtocolFamily}.
     */
    public NioDatagramChannel(SelectorProvider provider) {
        this(newSocket(provider));
    }

    /**
     * Create a new instance using the given {@link SocketProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public NioDatagramChannel(SocketProtocolFamily protocolFamily) {
        this(newSocket(DEFAULT_SELECTOR_PROVIDER, protocolFamily));
    }

    /**
     * Create a new instance using the given {@link SelectorProvider} and {@link SocketProtocolFamily}.
     * If {@link SocketProtocolFamily} is {@code null} it will depend on the Operation Systems default
     * which will be chosen.
     */
    public NioDatagramChannel(SelectorProvider provider, SocketProtocolFamily protocolFamily) {
        this(newSocket(provider, protocolFamily));
    }

    /**
     * Create a new instance from the given {@link DatagramChannel}.
     */
    public NioDatagramChannel(DatagramChannel socket) {
        super(null, socket, SelectionKey.OP_READ);
        config = new NioDatagramChannelConfig(this, socket);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public DatagramChannelConfig config() {
        return config;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isActive() {
        DatagramChannel ch = javaChannel();
        return ch.isOpen() && (
                config.getOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) && isRegistered()
                || localAddress0() != null);
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
            return javaChannel().getLocalAddress();
        } catch (IOException ignore) {
            return null;
        }
    }

    @Override
    protected SocketAddress remoteAddress0() {
        try {
            return javaChannel().getRemoteAddress();
        } catch (IOException ignore) {
            return null;
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        doBind0(localAddress);
    }

    private void doBind0(SocketAddress localAddress) throws Exception {
        SocketUtils.bind(javaChannel(), localAddress);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            doBind0(localAddress);
        }

        boolean success = false;
        try {
            javaChannel().connect(remoteAddress);
            success = true;
            return true;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doFinishConnect() throws Exception {
        throw new UnsupportedOperationException("finishConnect is not supported for " + getClass().getName());
    }

    @Override
    protected void doDisconnect() throws Exception {
        javaChannel().disconnect();
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        DatagramChannel ch = javaChannel();
        DatagramChannelConfig config = config();
        RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();

        ByteBuf data = allocHandle.allocate(config.getAllocator());
        allocHandle.attemptedBytesRead(data.writableBytes());
        boolean free = true;
        try {
            ByteBuffer nioData = data.internalNioBuffer(data.writerIndex(), data.writableBytes());
            int pos = nioData.position();
            InetSocketAddress remoteAddress = (InetSocketAddress) ch.receive(nioData);
            if (remoteAddress == null) {
                return 0;
            }

            allocHandle.lastBytesRead(nioData.position() - pos);
            buf.add(new DatagramPacket(data.writerIndex(data.writerIndex() + allocHandle.lastBytesRead()),
                    localAddress(), remoteAddress));
            free = false;
            return 1;
        } catch (Throwable cause) {
            PlatformDependent.throwException(cause);
            return -1;
        }  finally {
            if (free) {
                data.release();
            }
        }
    }

    @Override
    protected boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception {
        final SocketAddress remoteAddress;
        final ByteBuf data;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<ByteBuf, SocketAddress> envelope = (AddressedEnvelope<ByteBuf, SocketAddress>) msg;
            remoteAddress = envelope.recipient();
            data = envelope.content();
        } else {
            data = (ByteBuf) msg;
            remoteAddress = null;
        }

        final int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        final ByteBuffer nioData = data.nioBufferCount() == 1 ? data.internalNioBuffer(data.readerIndex(), dataLen)
                                                              : data.nioBuffer(data.readerIndex(), dataLen);
        final int writtenBytes;
        if (remoteAddress != null) {
            writtenBytes = javaChannel().send(nioData, remoteAddress);
        } else {
            writtenBytes = javaChannel().write(nioData);
        }
        return writtenBytes > 0;
    }

    private static void checkUnresolved(AddressedEnvelope<?, ?> envelope) {
        if (envelope.recipient() instanceof InetSocketAddress
                && (((InetSocketAddress) envelope.recipient()).isUnresolved())) {
            throw new UnresolvedAddressException();
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket p = (DatagramPacket) msg;
            checkUnresolved(p);
            ByteBuf content = p.content();
            if (isSingleDirectBuffer(content)) {
                return p;
            }
            return new DatagramPacket(newDirectBuffer(p, content), p.recipient());
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (isSingleDirectBuffer(buf)) {
                return buf;
            }
            return newDirectBuffer(buf);
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            checkUnresolved(e);
            if (e.content() instanceof ByteBuf) {
                ByteBuf content = (ByteBuf) e.content();
                if (isSingleDirectBuffer(content)) {
                    return e;
                }
                return new DefaultAddressedEnvelope<ByteBuf, SocketAddress>(newDirectBuffer(e, content), e.recipient());
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    /**
     * Checks if the specified buffer is a direct buffer and is composed of a single NIO buffer.
     * (We check this because otherwise we need to make it a non-composite buffer.)
     */
    private static boolean isSingleDirectBuffer(ByteBuf buf) {
        return buf.isDirect() && buf.nioBufferCount() == 1;
    }

    @Override
    protected boolean continueOnWriteError() {
        // Continue on write error as a DatagramChannel can write to multiple remote peers
        //
        // See https://github.com/netty/netty/issues/2665
        return true;
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise promise) {
        try {
            NetworkInterface iface = config.getNetworkInterface();
            if (iface == null) {
                iface = NetworkInterface.getByInetAddress(localAddress().getAddress());
            }
            return joinGroup(
                    multicastAddress, iface, null, promise);
        } catch (SocketException e) {
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
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress source, ChannelPromise promise) {

        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

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
                    memberships = new HashMap<InetAddress, List<MembershipKey>>();
                } else {
                    keys = memberships.get(multicastAddress);
                }
                if (keys == null) {
                    keys = new ArrayList<MembershipKey>();
                    memberships.put(multicastAddress, keys);
                }
                keys.add(key);
            }

            promise.setSuccess();
        } catch (Throwable e) {
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
        } catch (SocketException e) {
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
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
            ChannelPromise promise) {

        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

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

        promise.setSuccess();
        return promise;
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     */
    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newPromise());
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     */
    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock, ChannelPromise promise) {

        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(sourceToBlock, "sourceToBlock");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

        synchronized (this) {
            if (memberships != null) {
                List<MembershipKey> keys = memberships.get(multicastAddress);
                for (MembershipKey key: keys) {
                    if (networkInterface.equals(key.networkInterface())) {
                        try {
                            key.block(sourceToBlock);
                        } catch (IOException e) {
                            promise.setFailure(e);
                        }
                    }
                }
            }
        }
        promise.setSuccess();
        return promise;
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress
     *
     */
    @Override
    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress
     *
     */
    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, InetAddress sourceToBlock, ChannelPromise promise) {
        try {
            return block(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    sourceToBlock, promise);
        } catch (SocketException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    @Deprecated
    protected void setReadPending(boolean readPending) {
        super.setReadPending(readPending);
    }

    void clearReadPending0() {
        clearReadPending();
    }

    @Override
    protected boolean closeOnReadError(Throwable cause) {
        // We do not want to close on SocketException when using DatagramChannel as we usually can continue receiving.
        // See https://github.com/netty/netty/issues/5893
        if (cause instanceof SocketException) {
            return false;
        }
        return super.closeOnReadError(cause);
    }

    @Override
    protected boolean continueReading(RecvByteBufAllocator.Handle allocHandle) {
        if (allocHandle instanceof RecvByteBufAllocator.ExtendedHandle) {
            // We use the TRUE_SUPPLIER as it is also ok to read less then what we did try to read (as long
            // as we read anything).
            return ((RecvByteBufAllocator.ExtendedHandle) allocHandle)
                    .continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER);
        }
        return allocHandle.continueReading();
    }

    private static final class NioDatagramChannelConfig extends DefaultChannelConfig implements DatagramChannelConfig {
        private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioDatagramChannelConfig.class);

        final DatagramChannel jdkChannel;
        private volatile boolean activeOnOpen;

        /**
         * Creates a new instance.
         */
        NioDatagramChannelConfig(io.netty.channel.socket.DatagramChannel channel,
                                        DatagramChannel jdkChannel) {
            super(channel, new FixedRecvByteBufAllocator(2048));
            this.jdkChannel = ObjectUtil.checkNotNull(jdkChannel, "jdkChannel");
        }

        @Override
        @SuppressWarnings("deprecation")
        public Map<ChannelOption<?>, Object> getOptions() {
            return getOptions(getOptions(
                    super.getOptions(),
                    SO_BROADCAST, SO_RCVBUF, SO_SNDBUF, SO_REUSEADDR, IP_MULTICAST_LOOP_DISABLED,
                    IP_MULTICAST_ADDR, IP_MULTICAST_IF, IP_MULTICAST_TTL, IP_TOS,
                    DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION), NioChannelOption.getOptions(jdkChannel)) ;
        }

        @Override
        @SuppressWarnings({ "unchecked", "deprecation" })
        public <T> T getOption(ChannelOption<T> option) {
            if (option instanceof NioChannelOption) {
                return NioChannelOption.getOption(jdkChannel, (NioChannelOption<T>) option);
            }
            if (option == SO_BROADCAST) {
                return (T) Boolean.valueOf(isBroadcast());
            }
            if (option == SO_RCVBUF) {
                return (T) Integer.valueOf(getReceiveBufferSize());
            }
            if (option == SO_SNDBUF) {
                return (T) Integer.valueOf(getSendBufferSize());
            }
            if (option == SO_REUSEADDR) {
                return (T) Boolean.valueOf(isReuseAddress());
            }
            if (option == IP_MULTICAST_LOOP_DISABLED) {
                return (T) Boolean.valueOf(isLoopbackModeDisabled());
            }
            if (option == IP_MULTICAST_ADDR) {
                return (T) getInterface();
            }
            if (option == IP_MULTICAST_IF) {
                return (T) getNetworkInterface();
            }
            if (option == IP_MULTICAST_TTL) {
                return (T) Integer.valueOf(getTimeToLive());
            }
            if (option == IP_TOS) {
                return (T) Integer.valueOf(getTrafficClass());
            }
            if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
                return (T) Boolean.valueOf(activeOnOpen);
            }
            return super.getOption(option);
        }

        @Override
        @SuppressWarnings("deprecation")
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            validate(option, value);
            if (option instanceof NioChannelOption) {
                return NioChannelOption.setOption(jdkChannel, (NioChannelOption<T>) option, value);
            }
            if (option == SO_BROADCAST) {
                setBroadcast((Boolean) value);
            } else if (option == SO_RCVBUF) {
                setReceiveBufferSize((Integer) value);
            } else if (option == SO_SNDBUF) {
                setSendBufferSize((Integer) value);
            } else if (option == SO_REUSEADDR) {
                setReuseAddress((Boolean) value);
            } else if (option == IP_MULTICAST_LOOP_DISABLED) {
                setLoopbackModeDisabled((Boolean) value);
            } else if (option == IP_MULTICAST_ADDR) {
                setInterface((InetAddress) value);
            } else if (option == IP_MULTICAST_IF) {
                setNetworkInterface((NetworkInterface) value);
            } else if (option == IP_MULTICAST_TTL) {
                setTimeToLive((Integer) value);
            } else if (option == IP_TOS) {
                setTrafficClass((Integer) value);
            } else if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
                setActiveOnOpen((Boolean) value);
            } else {
                return super.setOption(option, value);
            }
            return true;
        }

        private void setActiveOnOpen(boolean activeOnOpen) {
            if (channel.isRegistered()) {
                throw new IllegalStateException("Can only changed before channel was registered");
            }
            this.activeOnOpen = activeOnOpen;
        }

        @Override
        public boolean isBroadcast() {
            try {
                return jdkChannel.getOption(StandardSocketOptions.SO_BROADCAST);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        @Override
        public DatagramChannelConfig setBroadcast(boolean broadcast) {
            try {
                // See: https://github.com/netty/netty/issues/576
                if (broadcast &&
                        !PlatformDependent.isWindows() && !PlatformDependent.maybeSuperUser()) {
                    try {
                        SocketAddress address = jdkChannel.getLocalAddress();
                        if (address instanceof InetSocketAddress &&
                                !((InetSocketAddress) address).getAddress().isAnyLocalAddress()) {
                            // Warn a user about the fact that a non-root user can't receive a
                            // broadcast packet on *nix if the socket is bound on non-wildcard address.
                            logger.warn(
                                    "A non-root user can't receive a broadcast packet if the socket " +
                                            "is not bound to a wildcard address; setting the SO_BROADCAST flag " +
                                            "anyway as requested on the socket which is bound to " +
                                            address + '.');
                        }
                    } catch (IOException ignore) {
                        // ignore
                    }
                }
                jdkChannel.setOption(StandardSocketOptions.SO_BROADCAST, broadcast);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public InetAddress getInterface() {
            NetworkInterface iface = getNetworkInterface();
            if (iface != null) {
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                if  (addresses.hasMoreElements()) {
                    return addresses.nextElement();
                }
            }

            return null;
        }

        @Override
        public DatagramChannelConfig setInterface(InetAddress interfaceAddress) {
            try {
                setNetworkInterface(NetworkInterface.getByInetAddress(interfaceAddress));
            } catch (SocketException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public boolean isLoopbackModeDisabled() {
            try {
                return jdkChannel.getOption(StandardSocketOptions.IP_MULTICAST_LOOP);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        @Override
        public DatagramChannelConfig setLoopbackModeDisabled(boolean loopbackModeDisabled) {
            try {
                jdkChannel.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, loopbackModeDisabled);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public NetworkInterface getNetworkInterface() {
            try {
                return jdkChannel.getOption(StandardSocketOptions.IP_MULTICAST_IF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        @Override
        public DatagramChannelConfig setNetworkInterface(NetworkInterface networkInterface) {
            try {
                jdkChannel.setOption(StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public boolean isReuseAddress() {
            try {
                return jdkChannel.getOption(StandardSocketOptions.SO_REUSEADDR);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        @Override
        public DatagramChannelConfig setReuseAddress(boolean reuseAddress) {
            try {
                jdkChannel.setOption(StandardSocketOptions.SO_REUSEADDR, reuseAddress);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public int getReceiveBufferSize() {
            try {
                return jdkChannel.getOption(StandardSocketOptions.SO_RCVBUF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        @Override
        public DatagramChannelConfig setReceiveBufferSize(int receiveBufferSize) {
            try {
                jdkChannel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public int getSendBufferSize() {
            try {
                return jdkChannel.getOption(StandardSocketOptions.SO_SNDBUF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        @Override
        public DatagramChannelConfig setSendBufferSize(int sendBufferSize) {
            try {
                jdkChannel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public int getTimeToLive() {
            try {
                return jdkChannel.getOption(StandardSocketOptions.IP_MULTICAST_TTL);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        @Override
        public DatagramChannelConfig setTimeToLive(int ttl) {
            try {
                jdkChannel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, ttl);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public int getTrafficClass() {
            try {
                return jdkChannel.getOption(StandardSocketOptions.IP_TOS);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        @Override
        public DatagramChannelConfig setTrafficClass(int trafficClass) {
            try {
                jdkChannel.setOption(StandardSocketOptions.IP_TOS, trafficClass);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public DatagramChannelConfig setWriteSpinCount(int writeSpinCount) {
            super.setWriteSpinCount(writeSpinCount);
            return this;
        }

        @Override
        public DatagramChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
            super.setConnectTimeoutMillis(connectTimeoutMillis);
            return this;
        }

        @Override
        @Deprecated
        public DatagramChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
            super.setMaxMessagesPerRead(maxMessagesPerRead);
            return this;
        }

        @Override
        public DatagramChannelConfig setAllocator(ByteBufAllocator allocator) {
            super.setAllocator(allocator);
            return this;
        }

        @Override
        public DatagramChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
            super.setRecvByteBufAllocator(allocator);
            return this;
        }

        @Override
        public DatagramChannelConfig setAutoRead(boolean autoRead) {
            super.setAutoRead(autoRead);
            return this;
        }

        @Override
        public DatagramChannelConfig setAutoClose(boolean autoClose) {
            super.setAutoClose(autoClose);
            return this;
        }

        @Override
        public DatagramChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
            super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
            return this;
        }

        @Override
        public DatagramChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
            super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
            return this;
        }

        @Override
        public DatagramChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            super.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }

        @Override
        public DatagramChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
            super.setMessageSizeEstimator(estimator);
            return this;
        }

        @Override
        public DatagramChannelConfig setMaxMessagesPerWrite(int maxMessagesPerWrite) {
            super.setMaxMessagesPerWrite(maxMessagesPerWrite);
            return this;
        }

        @Override
        protected void autoReadCleared() {
            ((NioDatagramChannel) channel).clearReadPending0();
        }
    }
}
