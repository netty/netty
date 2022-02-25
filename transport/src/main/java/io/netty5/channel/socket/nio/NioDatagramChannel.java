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

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufConvertible;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.WritableComponent;
import io.netty5.buffer.api.WritableComponentProcessor;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.DefaultByteBufAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.RecvBufferAllocator.Handle;
import io.netty5.channel.nio.AbstractNioMessageChannel;
import io.netty5.channel.socket.BufferDatagramPacket;
import io.netty5.channel.socket.DatagramChannelConfig;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.InternetProtocolFamily;
import io.netty5.util.ReferenceCounted;
import io.netty5.util.UncheckedBooleanSupplier;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SocketUtils;
import io.netty5.util.internal.StringUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
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
        extends AbstractNioMessageChannel implements io.netty5.channel.socket.DatagramChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(true);
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(SocketAddress.class) + ">, " +
            StringUtil.simpleClassName(ByteBuf.class) + ')';

    private final DatagramChannelConfig config;

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

    private static DatagramChannel newSocket(SelectorProvider provider, InternetProtocolFamily ipFamily) {
        if (ipFamily == null) {
            return newSocket(provider);
        }

        checkJavaVersion();

        try {
            return provider.openDatagramChannel(ProtocolFamilyConverter.convert(ipFamily));
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    private static void checkJavaVersion() {
    }

    /**
     * Create a new instance which will use the Operation Systems default {@link InternetProtocolFamily}.
     */
    public NioDatagramChannel(EventLoop eventLoop) {
        this(eventLoop, newSocket(DEFAULT_SELECTOR_PROVIDER));
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}
     * which will use the Operation Systems default {@link InternetProtocolFamily}.
     */
    public NioDatagramChannel(EventLoop eventLoop, SelectorProvider provider) {
        this(eventLoop, newSocket(provider));
    }

    /**
     * Create a new instance using the given {@link InternetProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public NioDatagramChannel(EventLoop eventLoop, InternetProtocolFamily ipFamily) {
        this(eventLoop, newSocket(DEFAULT_SELECTOR_PROVIDER, ipFamily));
    }

    /**
     * Create a new instance using the given {@link SelectorProvider} and {@link InternetProtocolFamily}.
     * If {@link InternetProtocolFamily} is {@code null} it will depend on the Operation Systems default
     * which will be chosen.
     */
    public NioDatagramChannel(EventLoop eventLoop, SelectorProvider provider, InternetProtocolFamily ipFamily) {
        this(eventLoop, newSocket(provider, ipFamily));
    }

    /**
     * Create a new instance from the given {@link DatagramChannel}.
     */
    public NioDatagramChannel(EventLoop eventLoop, DatagramChannel socket) {
        super(null, eventLoop, socket, SelectionKey.OP_READ);
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
    protected void doFinishConnect() throws Exception {
        throw new Error();
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
        DatagramChannelConfig config = config();
        RecvBufferAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();

        if (config.getRecvBufferAllocatorUseBuffer()) {
            return doReadBufferMessages(allocHandle, buf);
        }
        return doReadByteBufMessages(allocHandle, buf);
    }

    private int doReadBufferMessages(Handle allocHandle, List<Object> buf) throws IOException {
        DatagramChannel ch = javaChannel();
        Buffer data = allocHandle.allocate(config.getBufferAllocator());
        allocHandle.attemptedBytesRead(data.writableBytes());
        boolean free = true;
        try {
            ReceiveDatagram receiveDatagram = new ReceiveDatagram(javaChannel());
            data.forEachWritable(0, receiveDatagram);
            InetSocketAddress remoteAddress = receiveDatagram.remoteAddress;
            if (remoteAddress == null) {
                return 0;
            }

            allocHandle.lastBytesRead(receiveDatagram.bytesReceived);
            data.skipWritable(allocHandle.lastBytesRead());
            buf.add(new BufferDatagramPacket(data, localAddress(), remoteAddress));
            free = false;
            return 1;
        } finally {
            if (free) {
                data.close();
            }
        }
    }

    private int doReadByteBufMessages(Handle allocHandle, List<Object> buf) throws IOException {
        ByteBuf data = allocHandle.allocate(config.getAllocator());
        allocHandle.attemptedBytesRead(data.writableBytes());
        boolean free = true;
        try {
            ByteBuffer nioData = data.internalNioBuffer(data.writerIndex(), data.writableBytes());
            int pos = nioData.position();
            InetSocketAddress remoteAddress = (InetSocketAddress) javaChannel().receive(nioData);
            if (remoteAddress == null) {
                return 0;
            }

            allocHandle.lastBytesRead(nioData.position() - pos);
            buf.add(new DatagramPacket(data.writerIndex(data.writerIndex() + allocHandle.lastBytesRead()),
                                       localAddress(), remoteAddress));
            free = false;
            return 1;
        } finally {
            if (free) {
                data.release();
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

        if (data instanceof Buffer) {
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
                component.skipReadable(writtenBytes);
                return true;
            });
            return buf.readableBytes() < initialReadable;
        } else {
            ByteBuf buf = ((ByteBufConvertible) data).asByteBuf();
            final int length = buf.readableBytes();
            if (length == 0) {
                return true;
            }

            final ByteBuffer nioData = buf.nioBufferCount() == 1 ? buf.internalNioBuffer(buf.readerIndex(), length)
                    : buf.nioBuffer(buf.readerIndex(), length);
            final int writtenBytes;
            if (remoteAddress != null) {
                writtenBytes = javaChannel().send(nioData, remoteAddress);
            } else {
                writtenBytes = javaChannel().write(nioData);
            }
            return writtenBytes > 0;
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket p = (DatagramPacket) msg;
            ByteBuf content = p.content();
            if (isSingleDirectBuffer(content)) {
                return p;
            }
            return new DatagramPacket(newDirectBuffer(p, content), p.recipient());
        }
        if (msg instanceof BufferDatagramPacket) {
            BufferDatagramPacket p = (BufferDatagramPacket) msg;
            Buffer content = p.content();
            if (isSingleDirectBuffer(content)) {
                return p;
            }
            return new BufferDatagramPacket(newDirectBuffer(p, content), p.recipient());
        }

        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            if (isSingleDirectBuffer(buf)) {
                return buf;
            }
            return newDirectBuffer(buf);
        }
        if (msg instanceof ByteBufConvertible) {
            ByteBuf buf = ((ByteBufConvertible) msg).asByteBuf();
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
            if (content instanceof ByteBufConvertible) {
                ByteBuf buf = ((ByteBufConvertible) content).asByteBuf();
                if (isSingleDirectBuffer(buf)) {
                    return e;
                }
                return new DefaultByteBufAddressedEnvelope<>(newDirectBuffer((ReferenceCounted) e, buf), e.recipient());
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

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public Future<Void> joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public Future<Void> joinGroup(InetAddress multicastAddress, Promise<Void> promise) {
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
        return promise.asFuture();
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
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress source, Promise<Void> promise) {

        checkJavaVersion();

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

            promise.setSuccess(null);
        } catch (Throwable e) {
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
        } catch (SocketException e) {
            promise.setFailure(e);
        }
        return promise.asFuture();
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
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
            Promise<Void> promise) {
        checkJavaVersion();

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

        promise.setSuccess(null);
        return promise.asFuture();
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     */
    @Override
    public Future<Void> block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newPromise());
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     */
    @Override
    public Future<Void> block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock, Promise<Void> promise) {
        checkJavaVersion();

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
                            promise.setFailure(e);
                        }
                    }
                }
            }
        }
        promise.setSuccess(null);
        return promise.asFuture();
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress
     */
    @Override
    public Future<Void> block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress
     */
    @Override
    public Future<Void> block(
            InetAddress multicastAddress, InetAddress sourceToBlock, Promise<Void> promise) {
        try {
            return block(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    sourceToBlock, promise);
        } catch (SocketException e) {
            promise.setFailure(e);
        }
        return promise.asFuture();
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
        private InetSocketAddress remoteAddress;
        private int bytesReceived;

        ReceiveDatagram(DatagramChannel channel) {
            this.channel = channel;
        }

        @Override
        public boolean process(int index, WritableComponent component) throws IOException {
            ByteBuffer dst = component.writableBuffer();
            int position = dst.position();
            remoteAddress = (InetSocketAddress) channel.receive(dst);
            bytesReceived = dst.position() - position;
            return false;
        }
    }
}
