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

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.util.Resource;
import io.netty5.buffer.api.WritableComponent;
import io.netty5.buffer.api.WritableComponentProcessor;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.RecvBufferAllocator.Handle;
import io.netty5.channel.nio.AbstractNioMessageChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.DatagramChannelConfig;
import io.netty5.util.UncheckedBooleanSupplier;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.SocketUtils;
import io.netty5.util.internal.StringUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * An NIO datagram {@link Channel} that sends and receives an
 * {@link AddressedEnvelope AddressedEnvelope<ByteBuf, SocketAddress>}.
 *
 * @see AddressedEnvelope
 * @see DatagramPacket
 */
public final class NioDatagramChannel
        extends AbstractNioMessageChannel<Channel, SocketAddress, SocketAddress>
        implements io.netty5.channel.socket.DatagramChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(true);
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(Buffer.class) + ", " +
            StringUtil.simpleClassName(SocketAddress.class) + ">, " +
            StringUtil.simpleClassName(Buffer.class) + ')';

    private final DatagramChannelConfig config;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    private Map<InetAddress, List<MembershipKey>> memberships;

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
        this(eventLoop, newSocket(DEFAULT_SELECTOR_PROVIDER));
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}
     * which will use the Operation Systems default {@link ProtocolFamily}.
     */
    public NioDatagramChannel(EventLoop eventLoop, SelectorProvider provider) {
        this(eventLoop, newSocket(provider));
    }

    /**
     * Create a new instance using the given {@link ProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public NioDatagramChannel(EventLoop eventLoop, ProtocolFamily family) {
        this(eventLoop, newSocket(DEFAULT_SELECTOR_PROVIDER, family));
    }

    /**
     * Create a new instance using the given {@link SelectorProvider} and {@link ProtocolFamily}.
     * If {@link ProtocolFamily} is {@code null} it will depend on the Operation Systems default
     * which will be chosen.
     */
    public NioDatagramChannel(EventLoop eventLoop, SelectorProvider provider, ProtocolFamily family) {
        this(eventLoop, newSocket(provider, family));
    }

    /**
     * Create a new instance from the given {@link DatagramChannel}.
     */
    public NioDatagramChannel(EventLoop eventLoop, DatagramChannel socket) {
        super(null, eventLoop, socket, SelectionKey.OP_READ);
        config = new NioDatagramChannelConfig(this, socket);
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
                || ch.socket().isBound());
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
        return javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return javaChannel().socket().getRemoteSocketAddress();
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
    protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) {
        return true;
    }

    @Override
    protected void doDisconnect() throws Exception {
        javaChannel().disconnect();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        RecvBufferAllocator.Handle allocHandle = recvBufAllocHandle();

        return doReadBufferMessages(allocHandle, buf);
    }

    private int doReadBufferMessages(Handle allocHandle, List<Object> buf) throws IOException {
        Buffer data = allocHandle.allocate(config.getBufferAllocator());
        allocHandle.attemptedBytesRead(data.writableBytes());
        boolean free = true;
        try {
            ReceiveDatagram receiveDatagram = new ReceiveDatagram(javaChannel());
            data.forEachWritable(0, receiveDatagram);
            SocketAddress remoteAddress = receiveDatagram.remoteAddress;
            if (remoteAddress == null) {
                return 0;
            }

            allocHandle.lastBytesRead(receiveDatagram.bytesReceived);
            data.skipWritableBytes(allocHandle.lastBytesRead());
            buf.add(new DatagramPacket(data, localAddress(), remoteAddress));
            free = false;
            return 1;
        } finally {
            if (free) {
                data.close();
            }
        }
    }

    @Override
    protected boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception {
        final SocketAddress remoteAddress;
        final Object data;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<?, SocketAddress> envelope = (AddressedEnvelope<?, SocketAddress>) msg;
            remoteAddress = envelope.recipient();
            data = envelope.content();
        } else {
            data = msg;
            remoteAddress = null;
        }

        Buffer buf = (Buffer) data;
        final int length = buf.readableBytes();
        if (length == 0) {
            return true;
        }

        int initialReadable = buf.readableBytes();
        buf.forEachReadable(0, (index, component) -> {
            final int writtenBytes;
            if (remoteAddress != null) {
                writtenBytes = javaChannel().send(component.readableBuffer(), remoteAddress);
            } else {
                writtenBytes = javaChannel().write(component.readableBuffer());
            }
            component.skipReadableBytes(writtenBytes);
            return true;
        });
        return buf.readableBytes() < initialReadable;
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket p = (DatagramPacket) msg;
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

    @Override
    protected boolean continueOnWriteError() {
        // Continue on write error as a DatagramChannel can write to multiple remote peers
        //
        // See https://github.com/netty/netty/issues/2665
        return true;
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
    protected boolean continueReading(RecvBufferAllocator.Handle allocHandle) {
        // We use the TRUE_SUPPLIER as it is also ok to read less then what we did try to read (as long
        // as we read anything).
        return allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER);
    }

    private static final class ReceiveDatagram implements WritableComponentProcessor<IOException> {
        private final DatagramChannel channel;
        private SocketAddress remoteAddress;
        private int bytesReceived;

        ReceiveDatagram(DatagramChannel channel) {
            this.channel = channel;
        }

        @Override
        public boolean process(int index, WritableComponent component) throws IOException {
            ByteBuffer dst = component.writableBuffer();
            int position = dst.position();
            remoteAddress =  channel.receive(dst);
            bytesReceived = dst.position() - position;
            return false;
        }
    }
}
