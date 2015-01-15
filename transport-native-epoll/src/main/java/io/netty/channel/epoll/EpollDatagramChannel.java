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
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link DatagramChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollDatagramChannel extends AbstractEpollChannel implements DatagramChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(true);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
            StringUtil.simpleClassName(ByteBuf.class) + ')';

    private volatile InetSocketAddress local;
    private volatile InetSocketAddress remote;
    private volatile boolean connected;
    private final EpollDatagramChannelConfig config;

    public EpollDatagramChannel() {
        super(Native.socketDgramFd(), Native.EPOLLIN);
        config = new EpollDatagramChannelConfig(this);
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isActive() {
        return fd() != EpollFileDescriptor.INVALID &&
                (config.getOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) && isRegistered()
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
        int fd = fd().intValue();
        Native.bind(fd, addr);
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

            try {
                // Check if sendmmsg(...) is supported which is only the case for GLIBC 2.14+
                if (Native.IS_SUPPORTING_SENDMMSG && in.size() > 1) {
                    NativeDatagramPacketArray array = NativeDatagramPacketArray.getInstance(in);
                    int cnt = array.count();

                    if (cnt >= 1) {
                        // Try to use gathering writes via sendmmsg(...) syscall.
                        int offset = 0;
                        NativeDatagramPacketArray.NativeDatagramPacket[] packets = array.packets();

                        while (cnt > 0) {
                            int send = Native.sendmmsg(fd().intValue(), packets, offset, cnt);
                            if (send == 0) {
                                // Did not write all messages.
                                setEpollOut();
                                return;
                            }
                            for (int i = 0; i < send; i++) {
                                in.remove();
                            }
                            cnt -= send;
                            offset += send;
                        }
                        continue;
                    }
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
            } catch (IOException e) {
                // Continue on write error as a DatagramChannel can write to multiple remote peers
                //
                // See https://github.com/netty/netty/issues/2665
                in.remove(e);
            }
        }
    }

    private boolean doWriteMessage(Object msg) throws Exception {
        final ByteBuf data;
        InetSocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<ByteBuf, InetSocketAddress> envelope =
                    (AddressedEnvelope<ByteBuf, InetSocketAddress>) msg;
            data = envelope.content();
            remoteAddress = envelope.recipient();
        } else {
            data = (ByteBuf) msg;
            remoteAddress = null;
        }

        final int dataLen = data.readableBytes();
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
            writtenBytes = Native.sendToAddress(fd().intValue(), memoryAddress, data.readerIndex(), data.writerIndex(),
                    remoteAddress.getAddress(), remoteAddress.getPort());
        } else if (data instanceof CompositeByteBuf) {
            IovArray array = IovArrayThreadLocal.get((CompositeByteBuf) data);
            int cnt = array.count();
            assert cnt != 0;

            writtenBytes = Native.sendToAddresses(fd().intValue(), array.memoryAddress(0),
                    cnt, remoteAddress.getAddress(), remoteAddress.getPort());
        } else  {
            ByteBuffer nioData = data.internalNioBuffer(data.readerIndex(), data.readableBytes());
            writtenBytes = Native.sendTo(fd().intValue(), nioData, nioData.position(), nioData.limit(),
                    remoteAddress.getAddress(), remoteAddress.getPort());
        }

        return writtenBytes > 0;
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            ByteBuf content = packet.content();
            if (content.hasMemoryAddress()) {
                return msg;
            }

            if (content.isDirect() && content instanceof CompositeByteBuf) {
                // Special handling of CompositeByteBuf to reduce memory copies if some of the Components
                // in the CompositeByteBuf are backed by a memoryAddress.
                CompositeByteBuf comp = (CompositeByteBuf) content;
                if (comp.isDirect() && comp.nioBufferCount() <= Native.IOV_MAX) {
                    return msg;
                }
            }
            // We can only handle direct buffers so we need to copy if a non direct is
            // passed to write.
            return new DatagramPacket(newDirectBuffer(packet, content), packet.recipient());
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (!buf.hasMemoryAddress() && (PlatformDependent.hasUnsafe() || !buf.isDirect())) {
                if (buf instanceof CompositeByteBuf) {
                    // Special handling of CompositeByteBuf to reduce memory copies if some of the Components
                    // in the CompositeByteBuf are backed by a memoryAddress.
                    CompositeByteBuf comp = (CompositeByteBuf) buf;
                    if (!comp.isDirect() || comp.nioBufferCount() > Native.IOV_MAX) {
                        // more then 1024 buffers for gathering writes so just do a memory copy.
                        buf = newDirectBuffer(buf);
                        assert buf.hasMemoryAddress();
                    }
                } else {
                    // We can only handle buffers with memory address so we need to copy if a non direct is
                    // passed to write.
                    buf = newDirectBuffer(buf);
                    assert buf.hasMemoryAddress();
                }
            }
            return buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            if (e.content() instanceof ByteBuf &&
                (e.recipient() == null || e.recipient() instanceof InetSocketAddress)) {

                ByteBuf content = (ByteBuf) e.content();
                if (content.hasMemoryAddress()) {
                    return e;
                }
                if (content instanceof CompositeByteBuf) {
                    // Special handling of CompositeByteBuf to reduce memory copies if some of the Components
                    // in the CompositeByteBuf are backed by a memoryAddress.
                    CompositeByteBuf comp = (CompositeByteBuf) content;
                    if (comp.isDirect() && comp.nioBufferCount() <= Native.IOV_MAX) {
                        return e;
                    }
                }
                // We can only handle direct buffers so we need to copy if a non direct is
                // passed to write.
                return new DefaultAddressedEnvelope<ByteBuf, InetSocketAddress>(
                        newDirectBuffer(e, content), (InetSocketAddress) e.recipient());
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
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
        private final List<Object> readBuf = new ArrayList<Object>();

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
                    EpollDatagramChannel.this.local = Native.localAddress(fd().intValue());
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
            Throwable exception = null;
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
                                    fd().intValue(), data.memoryAddress(), writerIndex, data.capacity());
                        } else {
                            ByteBuffer nioData = data.internalNioBuffer(writerIndex, data.writableBytes());
                            remoteAddress = Native.recvFrom(
                                    fd().intValue(), nioData, nioData.position(), nioData.limit());
                        }

                        if (remoteAddress == null) {
                            break;
                        }

                        int readBytes = remoteAddress.receivedAmount;
                        data.writerIndex(data.writerIndex() + readBytes);
                        allocHandle.record(readBytes);
                        readPending = false;

                        readBuf.add(new DatagramPacket(data, (InetSocketAddress) localAddress(), remoteAddress));
                        data = null;
                    } catch (Throwable t) {
                        // We do not break from the loop here and remember the last exception,
                        // because we need to consume everything from the socket used with epoll ET.
                        exception = t;
                    } finally {
                        if (data != null) {
                            data.release();
                        }
                    }
                }

                int size = readBuf.size();
                for (int i = 0; i < size; i ++) {
                    pipeline.fireChannelRead(readBuf.get(i));
                }

                readBuf.clear();
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
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
    }

    /**
     * Act as special {@link InetSocketAddress} to be able to easily pass all needed data from JNI without the need
     * to create more objects then needed.
     */
    static final class DatagramSocketAddress extends InetSocketAddress {

        private static final long serialVersionUID = 1348596211215015739L;

        // holds the amount of received bytes
        final int receivedAmount;

        DatagramSocketAddress(String addr, int port, int receivedAmount) {
            super(addr, port);
            this.receivedAmount = receivedAmount;
        }
    }
}
