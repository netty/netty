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
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.DefaultDatagramChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;

public class OioDatagramChannel extends AbstractOioMessageChannel
                                implements DatagramChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OioDatagramChannel.class);

    private static final byte[] EMPTY_DATA = new byte[0];

    private final MulticastSocket socket;
    private final DatagramChannelConfig config;
    private final java.net.DatagramPacket tmpPacket = new java.net.DatagramPacket(EMPTY_DATA, 0);

    private static MulticastSocket newSocket() {
        try {
            return new MulticastSocket(null);
        } catch (Exception e) {
            throw new ChannelException("failed to create a new socket", e);
        }
    }

    public OioDatagramChannel() {
        this(newSocket());
    }

    public OioDatagramChannel(MulticastSocket socket) {
        this(null, socket);
    }

    public OioDatagramChannel(Integer id, MulticastSocket socket) {
        super(null, id);

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
        config = new DefaultDatagramChannelConfig(socket);
    }

    @Override
    public DatagramChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public boolean isActive() {
        return isOpen() && socket.isBound();
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
    protected int doReadMessages(MessageBuf<Object> buf) throws Exception {
        int packetSize = config().getReceivePacketSize();
        byte[] data = new byte[packetSize];
        tmpPacket.setData(data);
        try {
            socket.receive(tmpPacket);
            InetSocketAddress remoteAddr = (InetSocketAddress) tmpPacket.getSocketAddress();
            if (remoteAddr == null) {
                remoteAddr = remoteAddress();
            }
            buf.add(new DatagramPacket(Unpooled.wrappedBuffer(
                    data, tmpPacket.getOffset(), tmpPacket.getLength()), remoteAddr));
            return 1;
        } catch (SocketTimeoutException e) {
            // Expected
            return 0;
        } catch (SocketException e) {
            if (!e.getMessage().toLowerCase(Locale.US).contains("socket closed")) {
                throw e;
            }
            return -1;
        }
    }

    @Override
    protected void doWriteMessages(MessageBuf<Object> buf) throws Exception {
        DatagramPacket p = (DatagramPacket) buf.poll();
        ByteBuf data = p.data();
        int length = data.readableBytes();
        tmpPacket.setSocketAddress(p.remoteAddress());
        if (data.hasArray()) {
            tmpPacket.setData(data.array(), data.arrayOffset() + data.readerIndex(), length);
        } else {
            byte[] tmp = new byte[length];
            data.getBytes(data.readerIndex(), tmp);
            tmpPacket.setData(tmp);
        }

        socket.send(tmpPacket);
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newFuture());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelFuture future) {
        ensureBound();
        try {
            socket.joinGroup(multicastAddress);
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
        return future;
    }

    @Override
    public ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress, networkInterface, newFuture());
    }

    @Override
    public ChannelFuture joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface,
            ChannelFuture future) {
        ensureBound();
        try {
            socket.joinGroup(multicastAddress, networkInterface);
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
        return future;
    }

    @Override
    public ChannelFuture joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return newFailedFuture(new UnsupportedOperationException());
    }

    @Override
    public ChannelFuture joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
            ChannelFuture future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
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
        return leaveGroup(multicastAddress, newFuture());
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelFuture future) {
        try {
            socket.leaveGroup(multicastAddress);
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
        return future;
    }

    @Override
    public ChannelFuture leaveGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return leaveGroup(multicastAddress, networkInterface, newFuture());
    }

    @Override
    public ChannelFuture leaveGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface,
            ChannelFuture future) {
        try {
            socket.leaveGroup(multicastAddress, networkInterface);
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
        return future;
    }

    @Override
    public ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return newFailedFuture(new UnsupportedOperationException());
    }

    @Override
    public ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
            ChannelFuture future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress,
            NetworkInterface networkInterface, InetAddress sourceToBlock) {
        return newFailedFuture(new UnsupportedOperationException());
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress,
            NetworkInterface networkInterface, InetAddress sourceToBlock,
            ChannelFuture future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress,
            InetAddress sourceToBlock) {
        return newFailedFuture(new UnsupportedOperationException());
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress,
            InetAddress sourceToBlock, ChannelFuture future) {
        future.setFailure(new UnsupportedOperationException());
        return future;
    }
}
