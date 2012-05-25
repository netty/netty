/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.DetectionUtil;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Provides an NIO based {@link io.netty.channel.socket.DatagramChannel}.
 */
public final class NioDatagramChannel extends AbstractNioChannel implements io.netty.channel.socket.DatagramChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioDatagramChannel.class);

    private final DatagramChannelConfig config;
    private final Map<InetAddress, List<MembershipKey>> memberships =
            new HashMap<InetAddress, List<MembershipKey>>();
    private final ChannelBufferHolder<Object> out = ChannelBufferHolders.messageBuffer();

    private static DatagramChannel newSocket() {
        try {
            return DatagramChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    public NioDatagramChannel() {
        this(newSocket());
    }

    public NioDatagramChannel(DatagramChannel socket) {
        this(null, socket);
    }

    public NioDatagramChannel(Integer id, DatagramChannel socket) {
        super(null, id, socket);
        try {
            socket.configureBlocking(false);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized socket.", e2);
                }

            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }

        config = new DefaultNioDatagramChannelConfig(socket);

    }

    @Override
    public DatagramChannelConfig config() {
        return config;
    }

    @Override
    public boolean isActive() {
        DatagramChannel ch = javaChannel();
        return ch.isOpen() && ch.socket().isBound();
    }

    @Override
    protected DatagramChannel javaChannel() {
        return (DatagramChannel) super.javaChannel();
    }

    @Override
    protected ChannelBufferHolder<Object> firstOut() {
        return out;
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
        javaChannel().socket().bind(localAddress);
        selectionKey().interestOps(SelectionKey.OP_READ);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            javaChannel().socket().bind(localAddress);
        }

        boolean success = false;
        try {
            javaChannel().connect(remoteAddress);
            selectionKey().interestOps(selectionKey().interestOps() | SelectionKey.OP_READ);
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
    protected void doDeregister() throws Exception {
        selectionKey().cancel();
        ((SingleThreadSelectorEventLoop) eventLoop()).cancelledKeys ++;
    }

    @Override
    protected int doRead(Queue<Object> buf) throws Exception {
        DatagramChannel ch = javaChannel();
        ByteBuffer data = ByteBuffer.allocate(config().getReceivePacketSize());
        InetSocketAddress remoteAddress = (InetSocketAddress) ch.receive(data);
        if (remoteAddress == null) {
            return 0;
        }

        data.flip();
        buf.add(new DatagramPacket(ChannelBuffers.wrappedBuffer(data), remoteAddress));
        return 1;
    }

    @Override
    protected int doRead(ChannelBuffer buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected int doFlush(boolean lastSpin) throws Exception {
        final Queue<Object> buf = unsafe().out().messageBuffer();
        if (buf.isEmpty()) {
            return 0;
        }

        DatagramPacket packet = (DatagramPacket) buf.peek();
        final int writtenBytes = javaChannel().send(packet.data().toByteBuffer(), packet.remoteAddress());

        final SelectionKey key = selectionKey();
        final int interestOps = key.interestOps();
        if (writtenBytes <= 0) {
            // Did not write a packet.
            // 1) If 'lastSpin' is false, the caller will call this method again real soon.
            //    - Do not update OP_WRITE.
            // 2) If 'lastSpin' is true, the caller will not retry.
            //    - Set OP_WRITE so that the event loop calls flushForcibly() later.
            if (lastSpin) {
                if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(interestOps | SelectionKey.OP_WRITE);
                }
            }
            return 0;
        }

        // Wrote a packet.
        buf.remove();
        if (buf.isEmpty()) {
            // Wrote the outbound buffer completely - clear OP_WRITE.
            if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
            }
        }
        return 1;
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newFuture());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelFuture future) {
        try {
            return joinGroup(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    null, future);
        } catch (SocketException e) {
            future.setFailure(e);
        }
        return future;
    }

    @Override
    public ChannelFuture joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress, networkInterface, newFuture());
    }

    @Override
    public ChannelFuture joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface,
            ChannelFuture future) {
        return joinGroup(multicastAddress.getAddress(), networkInterface, null, future);
    }

    @Override
    public ChannelFuture joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return joinGroup(multicastAddress, networkInterface, source, newFuture());
    }

    @Override
    public ChannelFuture joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress source, ChannelFuture future) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            if (multicastAddress == null) {
                throw new NullPointerException("multicastAddress");
            }

            if (networkInterface == null) {
                throw new NullPointerException("networkInterface");
            }

            try {
                MembershipKey key;
                if (source == null) {
                    key = javaChannel().join(multicastAddress, networkInterface);
                } else {
                    key = javaChannel().join(multicastAddress, networkInterface, source);
                }

                synchronized (this) {
                    List<MembershipKey> keys = memberships.get(multicastAddress);
                    if (keys == null) {
                        keys = new ArrayList<MembershipKey>();
                        memberships.put(multicastAddress, keys);
                    }
                    keys.add(key);
                }

                future.setSuccess();
            } catch (Throwable e) {
                future.setFailure(e);
            }
        }
        return future;
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress) {
        return leaveGroup(multicastAddress, newFuture());
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelFuture future) {
        try {
            return leaveGroup(multicastAddress, NetworkInterface.getByInetAddress(localAddress().getAddress()), null, future);
        } catch (SocketException e) {
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
            InetSocketAddress multicastAddress,
            NetworkInterface networkInterface, ChannelFuture future) {
        return leaveGroup(multicastAddress.getAddress(), networkInterface, null, future);
    }

    @Override
    public ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return leaveGroup(multicastAddress, networkInterface, source, newFuture());
    }

    @Override
    public ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
            ChannelFuture future) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        }
        if (multicastAddress == null) {
            throw new NullPointerException("multicastAddress");
        }
        if (networkInterface == null) {
            throw new NullPointerException("networkInterface");
        }

        synchronized (this) {
            if (memberships != null) {
                List<MembershipKey> keys = memberships.get(multicastAddress);
                if (keys != null) {
                    Iterator<MembershipKey> keyIt = keys.iterator();

                    while (keyIt.hasNext()) {
                        MembershipKey key = keyIt.next();
                        if (networkInterface.equals(key.networkInterface())) {
                           if (source == null && key.sourceAddress() == null || source != null && source.equals(key.sourceAddress())) {
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

        future.setSuccess();
        return future;
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     */
    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newFuture());
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     */
    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock, ChannelFuture future) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            if (multicastAddress == null) {
                throw new NullPointerException("multicastAddress");
            }
            if (sourceToBlock == null) {
                throw new NullPointerException("sourceToBlock");
            }

            if (networkInterface == null) {
                throw new NullPointerException("networkInterface");
            }
            synchronized (this) {
                if (memberships != null) {
                    List<MembershipKey> keys = memberships.get(multicastAddress);
                    for (MembershipKey key: keys) {
                        if (networkInterface.equals(key.networkInterface())) {
                            try {
                                key.block(sourceToBlock);
                            } catch (IOException e) {
                                future.setFailure(e);
                            }
                        }
                    }
                }
            }
            future.setSuccess();
            return future;
        }
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress
     *
     */
    @Override
    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newFuture());
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress
     *
     */
    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, InetAddress sourceToBlock, ChannelFuture future) {
        try {
            return block(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    sourceToBlock, future);
        } catch (SocketException e) {
            future.setFailure(e);
        }
        return future;
    }
}
