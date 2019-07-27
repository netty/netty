/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.socket.oio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.oio.AbstractOioMessageChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.NotYetConnectedException;
import java.util.List;
import java.util.Locale;

/**
 * An OIO datagram {@link Channel} that sends and receives an
 * {@link AddressedEnvelope AddressedEnvelope<ByteBuf, SocketAddress>}.
 *
 * @see AddressedEnvelope
 * @see DatagramPacket
 * @deprecated use NIO / EPOLL / KQUEUE transport.
 */
@Deprecated
public class OioDatagramChannel extends AbstractOioMessageChannel
                                implements DatagramChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OioDatagramChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(true);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(SocketAddress.class) + ">, " +
            StringUtil.simpleClassName(ByteBuf.class) + ')';

    private final MulticastSocket socket;
    private final OioDatagramChannelConfig config;
    private final java.net.DatagramPacket tmpPacket = new java.net.DatagramPacket(EmptyArrays.EMPTY_BYTES, 0);

    private static MulticastSocket newSocket() {
        try {
            return new MulticastSocket(null);
        } catch (Exception e) {
            throw new ChannelException("failed to create a new socket", e);
        }
    }

    /**
     * Create a new instance with an new {@link MulticastSocket}.
     */
    public OioDatagramChannel() {
        this(newSocket());
    }

    /**
     * Create a new instance from the given {@link MulticastSocket}.
     *
     * @param socket    the {@link MulticastSocket} which is used by this instance
     */
    public OioDatagramChannel(MulticastSocket socket) {
        super(null);

        boolean success = false;
        try {
            socket.setSoTimeout(SO_TIMEOUT);
            socket.setBroadcast(false);
            success = true;
        } catch (SocketException e) {
            throw new ChannelException(
                    "Failed to configure the datagram socket timeout.", e);
        } finally {
            if (!success) {
                socket.close();
            }
        }

        this.socket = socket;
        config = new DefaultOioDatagramChannelConfig(this, socket);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    /**
     * {@inheritDoc}
     *
     * This can be safetly cast to {@link OioDatagramChannelConfig}.
     */
    @Override
    // TODO: Change return type to OioDatagramChannelConfig in next major release
    public DatagramChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isActive() {
        return isOpen()
            && (config.getOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) && isRegistered()
                 || socket.isBound());
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected();
    }

    @Override
    protected SocketAddress localAddress0() {
        return socket.getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return socket.getRemoteSocketAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        socket.bind(localAddress);
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
    protected void doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            socket.bind(localAddress);
        }

        boolean success = false;
        try {
            socket.connect(remoteAddress);
            success = true;
        } finally {
            if (!success) {
                try {
                    socket.close();
                } catch (Throwable t) {
                    logger.warn("Failed to close a socket.", t);
                }
            }
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        socket.disconnect();
    }

    @Override
    protected void doClose() throws Exception {
        socket.close();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        DatagramChannelConfig config = config();
        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();

        ByteBuf data = config.getAllocator().heapBuffer(allocHandle.guess());
        boolean free = true;
        try {
            // Ensure we null out the address which may have been set before.
            tmpPacket.setAddress(null);
            tmpPacket.setData(data.array(), data.arrayOffset(), data.capacity());
            socket.receive(tmpPacket);

            InetSocketAddress remoteAddr = (InetSocketAddress) tmpPacket.getSocketAddress();

            allocHandle.lastBytesRead(tmpPacket.getLength());
            buf.add(new DatagramPacket(data.writerIndex(allocHandle.lastBytesRead()), localAddress(), remoteAddr));
            free = false;
            return 1;
        } catch (SocketTimeoutException e) {
            // Expected
            return 0;
        } catch (SocketException e) {
            if (!e.getMessage().toLowerCase(Locale.US).contains("socket closed")) {
                throw e;
            }
            return -1;
        } catch (Throwable cause) {
            PlatformDependent.throwException(cause);
            return -1;
        } finally {
            if (free) {
                data.release();
            }
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (;;) {
            final Object o = in.current();
            if (o == null) {
                break;
            }

            final ByteBuf data;
            final SocketAddress remoteAddress;
            if (o instanceof AddressedEnvelope) {
                @SuppressWarnings("unchecked")
                AddressedEnvelope<ByteBuf, SocketAddress> envelope = (AddressedEnvelope<ByteBuf, SocketAddress>) o;
                remoteAddress = envelope.recipient();
                data = envelope.content();
            } else {
                data = (ByteBuf) o;
                remoteAddress = null;
            }

            final int length = data.readableBytes();
            try {
                if (remoteAddress != null) {
                    tmpPacket.setSocketAddress(remoteAddress);
                } else {
                    if (!isConnected()) {
                        // If not connected we should throw a NotYetConnectedException() to be consistent with
                        // NioDatagramChannel
                        throw new NotYetConnectedException();
                    }
                    // Ensure we null out the address which may have been set before.
                    tmpPacket.setAddress(null);
                }
                if (data.hasArray()) {
                    tmpPacket.setData(data.array(), data.arrayOffset() + data.readerIndex(), length);
                } else {
                    tmpPacket.setData(ByteBufUtil.getBytes(data, data.readerIndex(), length));
                }
                socket.send(tmpPacket);
                in.remove();
            } catch (Exception e) {
                // Continue on write error as a DatagramChannel can write to multiple remote peers
                //
                // See https://github.com/netty/netty/issues/2665
                in.remove(e);
            }
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DatagramPacket || msg instanceof ByteBuf) {
            return msg;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            if (e.content() instanceof ByteBuf) {
                return msg;
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise promise) {
        ensureBound();
        try {
            socket.joinGroup(multicastAddress);
            promise.setSuccess();
        } catch (IOException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface,
            ChannelPromise promise) {
        ensureBound();
        try {
            socket.joinGroup(multicastAddress, networkInterface);
            promise.setSuccess();
        } catch (IOException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return newFailedFuture(new UnsupportedOperationException());
    }

    @Override
    public ChannelFuture joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
            ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
        return promise;
    }

    private void ensureBound() {
        if (!isActive()) {
            throw new IllegalStateException(
                    DatagramChannel.class.getName() +
                    " must be bound to join a group.");
        }
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress) {
        return leaveGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelPromise promise) {
        try {
            socket.leaveGroup(multicastAddress);
            promise.setSuccess();
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
            InetSocketAddress multicastAddress, NetworkInterface networkInterface,
            ChannelPromise promise) {
        try {
            socket.leaveGroup(multicastAddress, networkInterface);
            promise.setSuccess();
        } catch (IOException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return newFailedFuture(new UnsupportedOperationException());
    }

    @Override
    public ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
            ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
        return promise;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress,
            NetworkInterface networkInterface, InetAddress sourceToBlock) {
        return newFailedFuture(new UnsupportedOperationException());
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress,
            NetworkInterface networkInterface, InetAddress sourceToBlock,
            ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
        return promise;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress,
            InetAddress sourceToBlock) {
        return newFailedFuture(new UnsupportedOperationException());
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress,
            InetAddress sourceToBlock, ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
        return promise;
    }
}
