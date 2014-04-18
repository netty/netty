/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;

/**
 * {@link DatagramChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollDatagramChannel extends AbstractEpollChannel implements DatagramChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(true);

    private volatile InetSocketAddress local;
    private volatile InetSocketAddress remote;
    private volatile boolean connected;
    private final EpollDatagramChannelConfig config;

    public EpollDatagramChannel() {
        super(Native.socketDgramFd(), Native.EPOLLIN);
        config = new EpollDatagramChannelConfig(this);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public boolean isActive() {
        return fd != -1 &&
                ((config.getOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) && isRegistered())
                        || active);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise promise) {
        try {
            return joinGroup(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    null, promise);
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
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress source, final ChannelPromise promise) {

        if (multicastAddress == null) {
            throw new NullPointerException("multicastAddress");
        }

        if (networkInterface == null) {
            throw new NullPointerException("networkInterface");
        }

        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));
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
            final InetAddress multicastAddress, final NetworkInterface networkInterface, final InetAddress source,
            final ChannelPromise promise) {
        if (multicastAddress == null) {
            throw new NullPointerException("multicastAddress");
        }
        if (networkInterface == null) {
            throw new NullPointerException("networkInterface");
        }

        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));

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
        if (multicastAddress == null) {
            throw new NullPointerException("multicastAddress");
        }
        if (sourceToBlock == null) {
            throw new NullPointerException("sourceToBlock");
        }

        if (networkInterface == null) {
            throw new NullPointerException("networkInterface");
        }
        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));
        return promise;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, InetAddress sourceToBlock, ChannelPromise promise) {
        try {
            return block(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    sourceToBlock, promise);
        } catch (Throwable e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollDatagramChannelUnsafe();
    }

    @Override
    protected InetSocketAddress localAddress0() {
        return local;
    }

    @Override
    protected InetSocketAddress remoteAddress0() {
        return remote;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        InetSocketAddress addr = (InetSocketAddress) localAddress;
        checkResolvable(addr);
        Native.bind(fd, addr.getAddress(), addr.getPort());
        local = Native.localAddress(fd);
        active = true;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (;;) {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                clearEpollOut();
                break;
            }

            boolean done = false;
            for (int i = config().getWriteSpinCount() - 1; i >= 0; i--) {
                if (doWriteMessage(msg)) {
                    done = true;
                    break;
                }
            }

            if (done) {
                in.remove();
            } else {
                // Did not write all messages.
                setEpollOut();
                break;
            }
        }
    }

    private boolean doWriteMessage(Object msg) throws IOException {
        final Object m;
        InetSocketAddress remoteAddress;
        ByteBuf data;
        if (msg instanceof DatagramPacket) {
            @SuppressWarnings("unchecked")
            DatagramPacket packet = (DatagramPacket) msg;
            remoteAddress = packet.recipient();
            m = packet.content();
        } else {
            m = msg;
            remoteAddress = null;
        }

        if (m instanceof ByteBufHolder) {
            data = ((ByteBufHolder) m).content();
        } else if (m instanceof ByteBuf) {
            data = (ByteBuf) m;
        } else {
            throw new UnsupportedOperationException("unsupported message type: " + StringUtil.simpleClassName(msg));
        }

        int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        if (remoteAddress == null) {
            remoteAddress = remote;
            if (remoteAddress == null) {
                throw new NotYetConnectedException();
            }
        }

        final int writtenBytes;
        if (data.hasMemoryAddress()) {
            long memoryAddress = data.memoryAddress();
            writtenBytes = Native.sendToAddress(fd, memoryAddress, data.readerIndex(), data.writerIndex(),
                    remoteAddress.getAddress(), remoteAddress.getPort());
        } else  {
            ByteBuffer nioData = data.internalNioBuffer(data.readerIndex(), data.readableBytes());
            writtenBytes = Native.sendTo(fd, nioData, nioData.position(), nioData.limit(),
                    remoteAddress.getAddress(), remoteAddress.getPort());
        }
        return writtenBytes > 0;
    }

    @Override
    public EpollDatagramChannelConfig config() {
        return config;
    }

    @Override
    protected void doDisconnect() throws Exception {
        connected = false;
    }

    final class EpollDatagramChannelUnsafe extends AbstractEpollUnsafe {
        private RecvByteBufAllocator.Handle allocHandle;

        @Override
        public void connect(SocketAddress remote, SocketAddress local, ChannelPromise channelPromise) {
            boolean success = false;
            try {
                try {
                    InetSocketAddress remoteAddress = (InetSocketAddress) remote;
                    if (local != null) {
                        InetSocketAddress localAddress = (InetSocketAddress) local;
                        doBind(localAddress);
                    }

                    checkResolvable(remoteAddress);
                    EpollDatagramChannel.this.remote = remoteAddress;
                    EpollDatagramChannel.this.local = Native.localAddress(fd);
                    success = true;
                } finally {
                    if (!success) {
                        doClose();
                    } else {
                        channelPromise.setSuccess();
                        connected = true;
                    }
                }
            } catch (Throwable cause) {
                channelPromise.setFailure(cause);
            }
        }

        @Override
        void epollInReady() {
            DatagramChannelConfig config = config();
            RecvByteBufAllocator.Handle allocHandle = this.allocHandle;
            if (allocHandle == null) {
                this.allocHandle = allocHandle = config.getRecvByteBufAllocator().newHandle();
            }

            assert eventLoop().inEventLoop();
            final ChannelPipeline pipeline = pipeline();
            try {
                for (;;) {
                    ByteBuf data = null;
                    try {
                        data = allocHandle.allocate(config.getAllocator());
                        int writerIndex = data.writerIndex();
                        DatagramSocketAddress remoteAddress;
                        if (data.hasMemoryAddress()) {
                            // has a memory address so use optimized call
                            remoteAddress = Native.recvFromAddress(
                                    fd, data.memoryAddress(), writerIndex, data.capacity());
                        } else {
                            ByteBuffer nioData = data.internalNioBuffer(writerIndex, data.writableBytes());
                            remoteAddress = Native.recvFrom(
                                    fd, nioData, nioData.position(), nioData.limit());
                        }

                        if (remoteAddress == null) {
                            break;
                        }

                        int readBytes = remoteAddress.receivedAmount;
                        data.writerIndex(data.writerIndex() + readBytes);
                        allocHandle.record(readBytes);
                        readPending = false;
                        pipeline.fireChannelRead(
                                new DatagramPacket(data, (InetSocketAddress) localAddress(), remoteAddress));
                        data = null;
                    } catch (Throwable t) {
                        // keep on reading as we use epoll ET and need to consume everything from the socket
                        pipeline.fireChannelReadComplete();
                        pipeline.fireExceptionCaught(t);
                    } finally {
                        if (data != null) {
                            data.release();
                        }
                    }
                }
            } finally {
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                if (!config().isAutoRead() && !readPending) {
                    clearEpollIn();
                }
            }
        }

        @Override
        public void write(Object msg, ChannelPromise promise) {
            if (msg instanceof DatagramPacket) {
                DatagramPacket packet = (DatagramPacket) msg;
                ByteBuf content = packet.content();
                if (isCopyNeeded(content)) {
                    // We can only handle direct buffers so we need to copy if a non direct is
                    // passed to write.
                    int readable = content.readableBytes();
                    ByteBuf dst = alloc().directBuffer(readable);
                    dst.writeBytes(content, content.readerIndex(), readable);

                    content.release();
                    msg = new DatagramPacket(dst, packet.recipient(), packet.sender());
                }
            } else if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                if (isCopyNeeded(buf)) {
                    // We can only handle direct buffers so we need to copy if a non direct is
                    // passed to write.
                    int readable = buf.readableBytes();
                    ByteBuf dst = alloc().directBuffer(readable);
                    dst.writeBytes(buf, buf.readerIndex(), readable);

                    buf.release();
                    msg = dst;
                }
            }

            super.write(msg, promise);
        }

        private boolean isCopyNeeded(ByteBuf content) {
            return !content.hasMemoryAddress() || content.nioBufferCount() != 1;
        }
    }

    /**
     * Act as special {@link InetSocketAddress} to be able to easily pass all needed data from JNI without the need
     * to create more objects then needed.
     */
    static final class DatagramSocketAddress extends InetSocketAddress {
        // holds the amount of received bytes
        final int receivedAmount;

        DatagramSocketAddress(String addr, int port, int receivedAmount) {
            super(addr, port);
            this.receivedAmount = receivedAmount;
        }
    }
}
