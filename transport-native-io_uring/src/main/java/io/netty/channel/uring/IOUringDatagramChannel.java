/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.Errors.NativeIoException;
import io.netty.channel.unix.Socket;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.unix.Errors.ioResult;

public final class IOUringDatagramChannel extends AbstractIOUringChannel implements DatagramChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(true);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
            StringUtil.simpleClassName(ByteBuf.class) + ')';

    private final IOUringDatagramChannelConfig config;
    private volatile boolean connected;

    /**
     * Create a new instance which selects the {@link InternetProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public IOUringDatagramChannel() {
        this(null);
    }

    /**
     * Create a new instance using the given {@link InternetProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public IOUringDatagramChannel(InternetProtocolFamily family) {
        this(family == null ?
                LinuxSocket.newSocketDgram(Socket.isIPv6Preferred()) :
                        LinuxSocket.newSocketDgram(family == InternetProtocolFamily.IPv6), false);
    }

    /**
     * Create a new instance which selects the {@link InternetProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public IOUringDatagramChannel(int fd) {
        this(new LinuxSocket(fd), true);
    }

    private IOUringDatagramChannel(LinuxSocket fd, boolean active) {
        super(null, fd, active);
        config = new IOUringDatagramChannelConfig(this);
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
    public boolean isActive() {
        return socket.isOpen() && (config.getActiveOnOpen() && isRegistered() || active);
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
                    NetworkInterface.getByInetAddress(localAddress().getAddress()), null, promise);
        } catch (IOException e) {
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

        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

        try {
            socket.joinGroup(multicastAddress, networkInterface, source);
            promise.setSuccess();
        } catch (IOException e) {
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

        try {
            socket.leaveGroup(multicastAddress, networkInterface, source);
            promise.setSuccess();
        } catch (IOException e) {
            promise.setFailure(e);
        }
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
    protected AbstractUringUnsafe newUnsafe() {
        return new IOUringDatagramChannelUnsafe();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (localAddress instanceof InetSocketAddress) {
            InetSocketAddress socketAddress = (InetSocketAddress) localAddress;
            if (socketAddress.getAddress().isAnyLocalAddress() &&
                    socketAddress.getAddress() instanceof Inet4Address && Socket.isIPv6Preferred()) {
                localAddress = new InetSocketAddress(LinuxSocket.INET6_ANY, socketAddress.getPort());
            }
        }
        super.doBind(localAddress);
        active = true;
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            ByteBuf content = packet.content();
            return !content.hasMemoryAddress() ?
                    new DatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return !buf.hasMemoryAddress()? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            if (e.content() instanceof ByteBuf &&
                (e.recipient() == null || e.recipient() instanceof InetSocketAddress)) {

                ByteBuf content = (ByteBuf) e.content();
                return !content.hasMemoryAddress()?
                        new DefaultAddressedEnvelope<ByteBuf, InetSocketAddress>(
                            newDirectBuffer(e, content), (InetSocketAddress) e.recipient()) : e;
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public IOUringDatagramChannelConfig config() {
        return config;
    }

    @Override
    protected void doDisconnect() throws Exception {
        // TODO: use io_uring for this too...
        socket.disconnect();
        connected = active = false;

        resetCachedAddresses();
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        ((IOUringDatagramChannelUnsafe) unsafe()).releaseBuffers();
        connected = false;
    }

    final class IOUringDatagramChannelUnsafe extends AbstractUringUnsafe {
        private ByteBuf readBuffer;
        private boolean recvMsg;

        // These buffers are used for msghdr, iov, sockaddr_in / sockaddr_in6 when doing recvmsg / sendmsg
        //
        // TODO: Alternative we could also allocate these everytime from the ByteBufAllocator or we could use
        //       some sort of other pool. Let's keep it simple for now.
        private ByteBuffer recvmsgBuffer;
        private long recvmsgBufferAddr = -1;
        private ByteBuffer sendmsgBuffer;
        private long sendmsgBufferAddr = -1;

        private long sendmsgBufferAddr() {
            long address = this.sendmsgBufferAddr;
            if (address == -1) {
                assert sendmsgBuffer == null;
                int length = Native.SIZEOF_MSGHDR + Native.SIZEOF_SOCKADDR_STORAGE + Native.SIZEOF_IOVEC;
                sendmsgBuffer = Buffer.allocateDirectWithNativeOrder(length);
                sendmsgBufferAddr = address = Buffer.memoryAddress(sendmsgBuffer);

                // memset once
                PlatformDependent.setMemory(address, length, (byte) 0);
            }
            return address;
        }

        private long recvmsgBufferAddr() {
            long address = this.recvmsgBufferAddr;
            if (address == -1) {
                assert recvmsgBuffer == null;
                int length = Native.SIZEOF_MSGHDR + Native.SIZEOF_SOCKADDR_STORAGE + Native.SIZEOF_IOVEC;
                recvmsgBuffer = Buffer.allocateDirectWithNativeOrder(length);
                recvmsgBufferAddr = address = Buffer.memoryAddress(recvmsgBuffer);

                // memset once
                PlatformDependent.setMemory(address, length, (byte) 0);
            }
            return address;
        }

        void releaseBuffers() {
            if (sendmsgBuffer != null) {
                Buffer.free(sendmsgBuffer);
                sendmsgBuffer = null;
                sendmsgBufferAddr = -1;
            }

            if (recvmsgBuffer != null) {
                Buffer.free(recvmsgBuffer);
                recvmsgBuffer = null;
                recvmsgBufferAddr = -1;
            }
        }

        @Override
        protected void readComplete0(int res) {
            final IOUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
            ByteBuf byteBuf = this.readBuffer;
            this.readBuffer = null;
            assert byteBuf != null;
            boolean recvmsg = this.recvMsg;
            this.recvMsg = false;

            try {
                if (res < 0) {
                    // If res is negative we should pass it to ioResult(...) which will either throw
                    // or convert it to 0 if we could not read because the socket was not readable.
                    allocHandle.lastBytesRead(ioResult("io_uring read / recvmsg", res));
                } else if (res > 0) {
                    byteBuf.writerIndex(byteBuf.writerIndex() + res);
                    allocHandle.lastBytesRead(res);
                } else {
                    allocHandle.lastBytesRead(-1);
                }
                if (allocHandle.lastBytesRead() <= 0) {
                    // nothing was read, release the buffer.
                    byteBuf.release();
                    byteBuf = null;

                    allocHandle.readComplete();
                    pipeline.fireChannelReadComplete();
                    return;
                }
                DatagramPacket packet;
                if (!recvmsg) {
                    packet = new DatagramPacket(byteBuf, IOUringDatagramChannel.this.localAddress(),
                            IOUringDatagramChannel.this.remoteAddress());
                } else {
                    long sockaddrAddress = recvmsgBufferAddr() + Native.SIZEOF_MSGHDR;
                    final InetSocketAddress remote;
                    if (socket.isIpv6()) {
                        byte[] bytes = ((IOUringEventLoop) eventLoop()).inet6AddressArray();
                        remote = SockaddrIn.readIPv6(sockaddrAddress, bytes);
                    } else {
                        byte[] bytes = ((IOUringEventLoop) eventLoop()).inet4AddressArray();
                        remote = SockaddrIn.readIPv4(sockaddrAddress, bytes);
                    }
                    packet = new DatagramPacket(byteBuf,
                            IOUringDatagramChannel.this.localAddress(), remote);
                }
                allocHandle.incMessagesRead(1);
                pipeline.fireChannelRead(packet);
                byteBuf = null;
                if (allocHandle.continueReading()) {
                    // Let's schedule another read.
                    scheduleRead();
                } else {
                    // We did not fill the whole ByteBuf so we should break the "read loop" and try again later.
                    allocHandle.readComplete();
                    pipeline.fireChannelReadComplete();
                }
            } catch (Throwable t) {
                if (connected && t instanceof NativeIoException) {
                    t = translateForConnected((NativeIoException) t);
                }
                pipeline.fireExceptionCaught(t);
            } finally {
                if (byteBuf != null) {
                    byteBuf.release();
                }
            }
        }

        @Override
        protected void scheduleRead0() {
            final IOUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            ByteBuf byteBuf = allocHandle.allocate(alloc());
            IOUringSubmissionQueue submissionQueue = submissionQueue();

            assert readBuffer == null;
            readBuffer = byteBuf;

            recvMsg = !isConnected();
            long bufferAddress = byteBuf.memoryAddress();
            allocHandle.attemptedBytesRead(byteBuf.writableBytes());

            if (!recvMsg) {
                submissionQueue.addRead(socket.intValue(), bufferAddress,
                        byteBuf.writerIndex(), byteBuf.capacity(), (short) 0);
            } else {
                int addrLen = addrLen();
                long recvmsgBufferAddr = recvmsgBufferAddr();
                long sockaddrAddress = recvmsgBufferAddr + Native.SIZEOF_MSGHDR;
                long iovecAddress = sockaddrAddress + addrLen;

                Iov.write(iovecAddress, bufferAddress + byteBuf.writerIndex(), byteBuf.writableBytes());
                MsgHdr.write(recvmsgBufferAddr, sockaddrAddress, addrLen, iovecAddress, 1);
                submissionQueue.addRecvmsg(socket.intValue(), recvmsgBufferAddr, (short) 0);
            }
        }

        private int addrLen() {
            return socket.isIpv6() ? Native.SIZEOF_SOCKADDR_IN6 :
                    Native.SIZEOF_SOCKADDR_IN;
        }

        @Override
        protected void removeFromOutboundBuffer(ChannelOutboundBuffer outboundBuffer, int bytes) {
            // When using Datagram we should consider the message written as long as there were any bytes written.
            boolean removed = outboundBuffer.remove();
            assert removed;
        }

        @Override
        void connectComplete(int res) {
            if (res >= 0) {
                connected = true;
            }
            super.connectComplete(res);
        }

        @Override
        protected void scheduleWriteMultiple(ChannelOutboundBuffer in) {
            // We always just use scheduleWriteSingle for now.
            scheduleWriteSingle(in.current());
        }

        @Override
        protected void scheduleWriteSingle(Object msg) {
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

            long bufferAddress = data.memoryAddress();
            IOUringSubmissionQueue submissionQueue = submissionQueue();
            if (remoteAddress == null) {
                submissionQueue.addWrite(socket.intValue(), bufferAddress, data.readerIndex(),
                        data.writerIndex(), (short) 0);
            } else {
                int addrLen = addrLen();
                long sendmsgBufferAddr = sendmsgBufferAddr();
                long sockaddrAddress = sendmsgBufferAddr + Native.SIZEOF_MSGHDR;
                long iovecAddress =  sockaddrAddress + Native.SIZEOF_SOCKADDR_STORAGE;

                SockaddrIn.write(socket.isIpv6(), sockaddrAddress, remoteAddress);
                Iov.write(iovecAddress, bufferAddress + data.readerIndex(), data.readableBytes());
                MsgHdr.write(sendmsgBufferAddr, sockaddrAddress, addrLen, iovecAddress, 1);
                submissionQueue.addSendmsg(socket.intValue(), sendmsgBufferAddr, (short) 0);
            }
        }
    }

    private IOException translateForConnected(NativeIoException e) {
        // We need to correctly translate connect errors to match NIO behaviour.
        if (e.expectedErr() == Errors.ERROR_ECONNREFUSED_NEGATIVE) {
            PortUnreachableException error = new PortUnreachableException(e.getMessage());
            error.initCause(e);
            return error;
        }
        return e;
    }
}
