/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DatagramSocketAddress;
import io.netty.channel.unix.DomainDatagramSocketAddress;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.CharsetUtil;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;

import static io.netty.channel.kqueue.BsdSocket.newSocketDgram;
import static io.netty.channel.unix.Socket.isIPv6Preferred;

public final class KQueueDatagramChannel extends AbstractKQueueChannel implements DatagramChannel {
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
                    StringUtil.simpleClassName(ByteBuf.class) + ')';

    private final KQueueDatagramChannelConfig config;

    private volatile boolean connected;

    public KQueueDatagramChannel(EventLoop eventLoop) {
        super(eventLoop, null, newSocketDgram(), false, true);
        config = new KQueueDatagramChannelConfig(this);
    }

    public KQueueDatagramChannel(EventLoop eventLoop, SocketProtocolFamily protocol) {
        super(eventLoop, null, newSocketDgram(protocol), false, true);
        config = new KQueueDatagramChannelConfig(this);
    }

    public KQueueDatagramChannel(EventLoop eventLoop, int fd) {
        this(eventLoop, new BsdSocket(fd, isIPv6Preferred() ?
                SocketProtocolFamily.INET6 : SocketProtocolFamily.INET), true);
    }

    KQueueDatagramChannel(EventLoop eventLoop, BsdSocket socket, boolean active) {
        super(eventLoop, null, socket, active, true);
        config = new KQueueDatagramChannelConfig(this);
    }

    @Override
    protected void doShutdown(ChannelShutdownType type, ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    protected void doDisconnect(ChannelPromise promise) {
        try {
            socket.disconnect();
        } catch (Throwable t) {
            promise.setFailure(t);
            return;
        }
        connected = active = false;
        resetCachedAddresses();
        promise.setSuccess();
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
    protected void doClose(ChannelPromise promise) {
        super.doClose(promise);
        connected = false;
    }

    @Override
    protected void doBind(SocketAddress localAddress, ChannelPromise promise) {
        super.doBind(localAddress, newPromise().addListener(f -> {
            if (f.isSuccess()) {
                active = true;
                promise.setSuccess();
            } else {
                promise.setFailure(f.cause());
            }
        }));
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int maxMessagesPerWrite = maxMessagesPerWrite();
        while (maxMessagesPerWrite > 0) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }

            try {
                boolean done = false;
                for (int i = config().getWriteSpinCount(); i > 0; --i) {
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

        // Whether all messages were written or not.
        writeFilter(!in.isEmpty());
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isActive() {
        return socket.isOpen() && (config.getActiveOnOpen() && isRegistered() || active);
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise promise) {
        SocketProtocolFamily family = socket.protocolFamily();
        switch (family) {
            case INET6:
            case INET:
                try {
                    NetworkInterface iface = config().getOption(KQueueChannelOption.IP_MULTICAST_IF);
                    if (iface == null) {
                        iface = NetworkInterface.getByInetAddress(((InetSocketAddress) localAddress()).getAddress());
                    }
                    return joinGroup(multicastAddress, iface, null, promise);
                } catch (SocketException e) {
                    promise.setFailure(e);
                }
                return promise;
            default:
                return promise.setFailure(
                        new UnsupportedOperationException("Not supported for SocketProtocolFamily: " + family));
        }
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
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress source, final ChannelPromise promise) {

        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

        promise.setFailure(
                new UnsupportedOperationException("Not supported for SocketProtocolFamily: " +
                        socket.protocolFamily()));
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
                    multicastAddress, NetworkInterface.getByInetAddress(
                            ((InetSocketAddress) localAddress()).getAddress()), null, promise);
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
            final InetAddress multicastAddress, final NetworkInterface networkInterface, final InetAddress source,
            final ChannelPromise promise) {
        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

        promise.setFailure(new UnsupportedOperationException("Not supported for SocketProtocolFamily: " +
                socket.protocolFamily()));
        return promise;
    }

    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newPromise());
    }

    @Override
    public ChannelFuture block(
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress sourceToBlock, final ChannelPromise promise) {
        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(sourceToBlock, "sourceToBlock");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");
        promise.setFailure(new UnsupportedOperationException("Not supported for SocketProtocolFamily: " +
                socket.protocolFamily()));
        return promise;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, InetAddress sourceToBlock, ChannelPromise promise) {
        SocketProtocolFamily family = socket.protocolFamily();
        switch (family) {
            case INET6:
            case INET:
                try {
                    return block(
                            multicastAddress,
                            NetworkInterface.getByInetAddress(((InetSocketAddress) localAddress()).getAddress()),
                            sourceToBlock, promise);
                } catch (SocketException e) {
                    promise.setFailure(e);
                }
                return promise;
            default:
                return promise.setFailure(
                    new UnsupportedOperationException("Not supported for SocketProtocolFamily: " + family));
        }
    }

    private boolean doWriteMessage(Object msg) throws Exception {
        final ByteBuf data;
        SocketAddress remoteAddress;
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            data = packet.content();
            remoteAddress = packet.recipient();
        } else {
            data = (ByteBuf) msg;
            remoteAddress = null;
        }

        final int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        final long writtenBytes;
        if (data.hasMemoryAddress()) {
            long memoryAddress = data.memoryAddress();
            if (remoteAddress == null) {
                writtenBytes = socket.writeAddress(memoryAddress, data.readerIndex(), data.writerIndex());
            } else {
                if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                    DomainSocketAddress address = (DomainSocketAddress) remoteAddress;
                    writtenBytes = socket.sendToAddressDomainSocket(memoryAddress, data.readerIndex(),
                            data.writerIndex(), address.path().getBytes(CharsetUtil.UTF_8));
                } else {
                    InetSocketAddress address = (InetSocketAddress) remoteAddress;
                    writtenBytes = socket.sendToAddress(memoryAddress, data.readerIndex(), data.writerIndex(),
                            address.getAddress(), address.getPort());
                }
            }
        } else if (data.nioBufferCount() > 1) {
            IovArray array = ((NativeArrays) registration().attachment()).cleanIovArray();
            array.add(data, data.readerIndex(), data.readableBytes());
            int cnt = array.count();
            assert cnt != 0;

            if (remoteAddress == null) {
                writtenBytes = socket.writevAddresses(array.memoryAddress(0), cnt);
            } else {
                if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                    DomainSocketAddress address = (DomainSocketAddress) remoteAddress;
                    writtenBytes = socket.sendToAddressesDomainSocket(array.memoryAddress(0), cnt,
                            address.path().getBytes(CharsetUtil.UTF_8));
                } else {
                    InetSocketAddress address = (InetSocketAddress) remoteAddress;
                    writtenBytes = socket.sendToAddresses(array.memoryAddress(0), cnt,
                            address.getAddress(), address.getPort());
                }
            }
        } else {
            ByteBuffer nioData = data.internalNioBuffer(data.readerIndex(), data.readableBytes());
            if (remoteAddress == null) {
                writtenBytes = socket.write(nioData, nioData.position(), nioData.limit());
            } else {
                if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                    DomainSocketAddress address = (DomainSocketAddress) remoteAddress;
                    writtenBytes = socket.sendToDomainSocket(nioData, nioData.position(), nioData.limit(),
                            address.path().getBytes(CharsetUtil.UTF_8));
                } else {
                    InetSocketAddress address = (InetSocketAddress) remoteAddress;
                    writtenBytes = socket.sendTo(nioData, nioData.position(), nioData.limit(),
                            address.getAddress(), address.getPort());
                }
            }
        }

        return writtenBytes > 0;
    }

    private static void checkUnresolved(SocketAddress address) {
        if (address instanceof InetSocketAddress
                && (((InetSocketAddress) address).isUnresolved())) {
            throw new UnresolvedAddressException();
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            checkUnresolved(packet.recipient());
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new DatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf) ? newDirectBuffer(buf) : buf;
        }
        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public KQueueDatagramChannelConfig config() {
        return config;
    }

    @Override
    void readReady(KQueueRecvByteAllocatorHandle allocHandle) {
        assert executor().inEventLoop();
        final ChannelConfig config = config();
        if (shouldBreakReadReady()) {
            clearReadFilter0();
            return;
        }
        final ChannelPipeline pipeline = pipeline();
        final ByteBufAllocator allocator = config.getAllocator();
        allocHandle.reset(config);

        Throwable exception = null;
        try {
            ByteBuf byteBuf = null;
            try {
                boolean connected = isConnected();
                do {
                    byteBuf = allocHandle.allocate(allocator);
                    allocHandle.attemptedBytesRead(byteBuf.writableBytes());

                    final DatagramPacket packet;
                    if (connected) {
                        try {
                            allocHandle.lastBytesRead(doReadBytes(byteBuf));
                        } catch (Errors.NativeIoException e) {
                            // We need to correctly translate connect errors to match NIO behaviour.
                            if (e.expectedErr() == Errors.ERROR_ECONNREFUSED_NEGATIVE) {
                                PortUnreachableException error = new PortUnreachableException(e.getMessage());
                                error.initCause(e);
                                throw error;
                            }
                            throw e;
                        }
                        if (allocHandle.lastBytesRead() <= 0) {
                            // nothing was read, release the buffer.
                            byteBuf.release();
                            byteBuf = null;
                            break;
                        }
                        packet = new DatagramPacket(byteBuf, localAddress(), remoteAddress());
                    } else {
                        SocketAddress localAddress;
                        SocketAddress remoteAddress;
                        int receivedAmount;
                        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                            final DomainDatagramSocketAddress received;
                            if (byteBuf.hasMemoryAddress()) {
                                // has a memory address so use optimized call
                                received = socket.recvFromAddressDomainSocket(byteBuf.memoryAddress(),
                                        byteBuf.writerIndex(), byteBuf.capacity());
                            } else {
                                ByteBuffer nioData = byteBuf.internalNioBuffer(
                                        byteBuf.writerIndex(), byteBuf.writableBytes());
                                received =
                                        socket.recvFromDomainSocket(nioData, nioData.position(), nioData.limit());
                            }
                            if (received == null) {
                                allocHandle.lastBytesRead(-1);
                                byteBuf.release();
                                byteBuf = null;
                                break;
                            }
                            localAddress = received.localAddress();
                            receivedAmount = received.receivedAmount();
                            remoteAddress = received;
                        } else {
                            final DatagramSocketAddress received;
                            if (byteBuf.hasMemoryAddress()) {
                                // has a memory address so use optimized call
                                received = socket.recvFromAddress(byteBuf.memoryAddress(), byteBuf.writerIndex(),
                                        byteBuf.capacity());
                            } else {
                                ByteBuffer nioData = byteBuf.internalNioBuffer(
                                        byteBuf.writerIndex(), byteBuf.writableBytes());
                                received = socket.recvFrom(nioData, nioData.position(), nioData.limit());
                            }
                            if (received == null) {
                                allocHandle.lastBytesRead(-1);
                                byteBuf.release();
                                byteBuf = null;
                                break;
                            }
                            localAddress = received.localAddress();
                            receivedAmount = received.receivedAmount();
                            remoteAddress = received;
                        }

                        if (localAddress == null) {
                            localAddress = localAddress();
                        }
                        allocHandle.lastBytesRead(receivedAmount);
                        byteBuf.writerIndex(byteBuf.writerIndex() + allocHandle.lastBytesRead());

                        packet = new DatagramPacket(byteBuf, localAddress, remoteAddress);
                    }

                    allocHandle.incMessagesRead(1);

                    readPending = false;
                    pipeline.fireChannelRead(packet);

                    byteBuf = null;

                // We use the TRUE_SUPPLIER as it is also ok to read less than what we did try to read (as long
                // as we read anything).
                } while (allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER));
            } catch (Throwable t) {
                if (byteBuf != null) {
                    byteBuf.release();
                }
                exception = t;
            }

            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();

            if (exception != null) {
                pipeline.fireExceptionCaught(exception);
            }
        } finally {
            if (shouldStopReading(config)) {
                clearReadFilter0();
            }
        }
    }
}
