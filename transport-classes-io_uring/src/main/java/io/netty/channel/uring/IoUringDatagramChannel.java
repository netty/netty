/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.IoRegistration;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.Errors.NativeIoException;
import io.netty.channel.unix.SegmentedDatagramPacket;
import io.netty.channel.unix.Socket;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.channels.UnresolvedAddressException;

import static io.netty.channel.unix.Errors.ioResult;

public final class IoUringDatagramChannel extends AbstractIoUringChannel implements DatagramChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(true, 16);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
            StringUtil.simpleClassName(ByteBuf.class) + ')';

    private final IoUringDatagramChannelConfig config;
    private volatile boolean connected;

    // These buffers are used for msghdr, iov, sockaddr_in / sockaddr_in6 when doing recvmsg / sendmsg
    //
    // TODO: Alternative we could also allocate these everytime from the ByteBufAllocator or we could use
    //       some sort of other pool. Let's keep it simple for now.
    //
    // Consider exposing some configuration for that.
    private final MsgHdrMemoryArray recvmsgHdrs = new MsgHdrMemoryArray((short) 256);
    private final MsgHdrMemoryArray sendmsgHdrs = new MsgHdrMemoryArray((short) 256);
    private final int[] sendmsgResArray = new int[sendmsgHdrs.capacity()];

    /**
     * Create a new instance which selects the {@link SocketProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public IoUringDatagramChannel() {
        this(null);
    }

    /**
     * Create a new instance using the given {@link SocketProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public IoUringDatagramChannel(SocketProtocolFamily family) {
        this(LinuxSocket.newSocketDgram(useIpv6(family)), false);
    }

    private static boolean useIpv6(SocketProtocolFamily family) {
        if (family == null) {
            return Socket.isIPv6Preferred();
        }
        return family == SocketProtocolFamily.INET6;
    }

    /**
     * Create a new instance which selects the {@link SocketProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public IoUringDatagramChannel(int fd) {
        this(new LinuxSocket(fd), true);
    }

    private IoUringDatagramChannel(LinuxSocket fd, boolean active) {
        // Always use a blocking fd and so make use of fast-poll.
        super(null, fd, active);
        config = new IoUringDatagramChannelConfig(this);
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
        return socket.isOpen() && (config.getActiveOnOpen() && isRegistered() || super.isActive());
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
    protected AbstractUnsafe newUnsafe() {
        return new IoUringDatagramChannelUnsafe();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (localAddress instanceof InetSocketAddress) {
            InetSocketAddress socketAddress = (InetSocketAddress) localAddress;
            if (socketAddress.getAddress().isAnyLocalAddress() &&
                    socketAddress.getAddress() instanceof Inet4Address) {
                if (socket.family() == SocketProtocolFamily.INET6) {
                    localAddress = new InetSocketAddress(LinuxSocket.INET6_ANY, socketAddress.getPort());
                }
            }
        }
        super.doBind(localAddress);
        active = true;
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
            DatagramPacket packet = (DatagramPacket) msg;
            checkUnresolved(packet);
            ByteBuf content = packet.content();
            return !content.hasMemoryAddress() ?
                    packet.replace(newDirectBuffer(packet, content)) : msg;
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return !buf.hasMemoryAddress()? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            checkUnresolved(e);
            if (e.content() instanceof ByteBuf &&
                (e.recipient() == null || e.recipient() instanceof InetSocketAddress)) {

                ByteBuf content = (ByteBuf) e.content();
                return !content.hasMemoryAddress()?
                        new DefaultAddressedEnvelope<>(
                            newDirectBuffer(e, content), (InetSocketAddress) e.recipient()) : e;
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public DatagramChannelConfig config() {
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
        connected = false;
    }

    private final class IoUringDatagramChannelUnsafe extends AbstractUringUnsafe {
        private final WriteProcessor writeProcessor = new WriteProcessor();

        private ByteBuf readBuffer;

        private final class WriteProcessor implements ChannelOutboundBuffer.MessageProcessor {
            private int written;
            @Override
            public boolean processMessage(Object msg) {
                if (scheduleWrite(msg, written == 0)) {
                    written++;
                    return true;
                }
                return false;
            }

            int write(ChannelOutboundBuffer in) {
                written = 0;
                try {
                    in.forEachFlushedMessage(this);
                } catch (Exception e) {
                    // This should never happen as our processMessage(...) never throws.
                    throw new IllegalStateException(e);
                }
                return written;
            }
        }

        @Override
        protected void readComplete0(byte op, int res, int flags, short data, int outstanding) {
            assert outstanding != -1 : "multi-shot not implemented yet";

            final IoUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
            ByteBuf byteBuf = this.readBuffer;
            assert byteBuf != null;
            try {
                recvmsgComplete(pipeline, allocHandle, byteBuf, res, flags, data, outstanding);
            } catch (Throwable t) {
                if (connected && t instanceof NativeIoException) {
                    t = translateForConnected((NativeIoException) t);
                }
                pipeline.fireExceptionCaught(t);
            }
        }

        private void recvmsgComplete(ChannelPipeline pipeline, IoUringRecvByteAllocatorHandle allocHandle,
                                      ByteBuf byteBuf, int res, int flags, int idx, int outstanding)
                throws IOException {
            MsgHdrMemory hdr = recvmsgHdrs.hdr(idx);
            if (res < 0) {
                if (res != Native.ERRNO_ECANCELED_NEGATIVE) {
                    // If res is negative we should pass it to ioResult(...) which will either throw
                    // or convert it to 0 if we could not read because the socket was not readable.
                    allocHandle.lastBytesRead(ioResult("io_uring recvmsg", res));
                }
            } else {
                allocHandle.lastBytesRead(res);
                if (hdr.hasPort(IoUringDatagramChannel.this)) {
                    allocHandle.incMessagesRead(1);
                    DatagramPacket packet = hdr.get(
                            IoUringDatagramChannel.this, registration().attachment(), byteBuf, res);
                    pipeline.fireChannelRead(packet);
                }
            }

            // Reset the id as this read was completed and so don't need to be cancelled later.
            recvmsgHdrs.setId(idx, MsgHdrMemoryArray.NO_ID);
            if (outstanding == 0) {
                // There are no outstanding completion events, release the readBuffer and see if we need to schedule
                // another one or if the user will do it.
                this.readBuffer.release();
                this.readBuffer = null;
                recvmsgHdrs.clear();

                if (res != Native.ERRNO_ECANCELED_NEGATIVE) {
                    if (allocHandle.lastBytesRead() > 0 &&
                            allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER) &&
                            // If IORING_CQE_F_SOCK_NONEMPTY is supported we should check for it first before
                            // trying to schedule a read. If it's supported and not part of the flags we know for sure
                            // that the next read (which would be using Native.MSG_DONTWAIT) will complete without
                            // be able to read any data. This is useless work and we can skip it.
                            (!IoUring.isCqeFSockNonEmptySupported() ||
                                    (flags & Native.IORING_CQE_F_SOCK_NONEMPTY) != 0)) {
                        // Let's schedule another read.
                        scheduleRead(false);
                    } else {
                        // the read was completed with EAGAIN.
                        allocHandle.readComplete();
                        pipeline.fireChannelReadComplete();
                    }
                }
            }
        }

        @Override
        protected int scheduleRead0(boolean first, boolean socketIsEmpty) {
            final IoUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            ByteBuf byteBuf = allocHandle.allocate(alloc());
            assert readBuffer == null;
            readBuffer = byteBuf;

            int writable = byteBuf.writableBytes();
            allocHandle.attemptedBytesRead(writable);
            int datagramSize = ((IoUringDatagramChannelConfig) config()).getMaxDatagramPayloadSize();

            int numDatagram = datagramSize == 0 ? 1 : Math.max(1, byteBuf.writableBytes() / datagramSize);

            int scheduled = scheduleRecvmsg(byteBuf, numDatagram, datagramSize);
            if (scheduled == 0) {
                // We could not schedule any recvmmsg so we need to release the buffer as there will be no
                // completion event.
                readBuffer = null;
                byteBuf.release();
            }
            return scheduled;
        }

        private int scheduleRecvmsg(ByteBuf byteBuf, int numDatagram, int datagramSize) {
            int writable = byteBuf.writableBytes();
            long bufferAddress = IoUring.memoryAddress(byteBuf) + byteBuf.writerIndex();
            if (numDatagram <= 1) {
                return scheduleRecvmsg0(bufferAddress, writable, true) ? 1 : 0;
            }
            int i = 0;
            // Add multiple IORING_OP_RECVMSG to the submission queue. This basically emulates recvmmsg(...)
            for (; i < numDatagram && writable >= datagramSize; i++) {
                if (!scheduleRecvmsg0(bufferAddress, datagramSize, i == 0)) {
                    break;
                }
                bufferAddress += datagramSize;
                writable -= datagramSize;
            }
            return i;
        }

        private boolean scheduleRecvmsg0(long bufferAddress, int bufferLength, boolean first) {
            MsgHdrMemory msgHdrMemory = recvmsgHdrs.nextHdr();
            if (msgHdrMemory == null) {
                // We can not continue reading before we did not submit the recvmsg(s) and received the results.
                return false;
            }
            msgHdrMemory.set(socket, null, bufferAddress, bufferLength, (short) 0);

            int fd = fd().intValue();
            int msgFlags = first ? 0 : Native.MSG_DONTWAIT;
            IoRegistration registration = registration();
            // We always use idx here so we can detect if no idx was used by checking if data < 0 in
            // readComplete0(...)
            IoUringIoOps ops = IoUringIoOps.newRecvmsg(
                    fd, (byte) 0, msgFlags, msgHdrMemory.address(), msgHdrMemory.idx());
            long id = registration.submit(ops);
            if (id == 0) {
                // Submission failed we don't used the MsgHdrMemory and so should give it back.
                recvmsgHdrs.restoreNextHdr(msgHdrMemory);
                return false;
            }
            recvmsgHdrs.setId(msgHdrMemory.idx(), id);
            return true;
        }

        @Override
        boolean writeComplete0(byte op, int res, int flags, short data, int outstanding) {
            ChannelOutboundBuffer outboundBuffer = outboundBuffer();

            // Reset the id as this write was completed and so don't need to be cancelled later.
            sendmsgHdrs.setId(data, MsgHdrMemoryArray.NO_ID);
            sendmsgResArray[data] = res;
            // Store the result so we can handle it as soon as we have no outstanding writes anymore.
            if (outstanding == 0) {
                // All writes are done as part of a batch. Let's remove these from the ChannelOutboundBuffer
                boolean writtenSomething = false;
                int numWritten = sendmsgHdrs.length();
                sendmsgHdrs.clear();
                for (int i = 0; i < numWritten; i++) {
                    writtenSomething |= removeFromOutboundBuffer(
                            outboundBuffer, sendmsgResArray[i], "io_uring sendmsg");
                }
                return writtenSomething;
            }
            return true;
        }

        private boolean removeFromOutboundBuffer(ChannelOutboundBuffer outboundBuffer, int res, String errormsg) {
            if (res >= 0) {
                // When using Datagram we should consider the message written as long as res is not negative.
                return outboundBuffer.remove();
            }
            if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                return false;
            }
            try {
                return ioResult(errormsg, res) != 0;
            } catch (Throwable cause) {
                return outboundBuffer.remove(cause);
            }
        }

        @Override
        void connectComplete(byte op, int res, int flags, short data) {
            if (res >= 0) {
                connected = true;
            }
            super.connectComplete(op, res, flags, data);
        }

        @Override
        protected int scheduleWriteMultiple(ChannelOutboundBuffer in) {
            return writeProcessor.write(in);
        }

        @Override
        protected int scheduleWriteSingle(Object msg) {
            return scheduleWrite(msg, true) ? 1 : 0;
        }

        private boolean scheduleWrite(Object msg, boolean first) {
            final ByteBuf data;
            final InetSocketAddress remoteAddress;
            final int segmentSize;
            if (msg instanceof AddressedEnvelope) {
                @SuppressWarnings("unchecked")
                AddressedEnvelope<ByteBuf, InetSocketAddress> envelope =
                        (AddressedEnvelope<ByteBuf, InetSocketAddress>) msg;
                data = envelope.content();
                remoteAddress = envelope.recipient();
                if (msg instanceof SegmentedDatagramPacket) {
                    segmentSize = ((SegmentedDatagramPacket) msg).segmentSize();
                } else {
                    segmentSize = 0;
                }
            } else {
                data = (ByteBuf) msg;
                remoteAddress = (InetSocketAddress) remoteAddress();
                segmentSize = 0;
            }

            long bufferAddress = IoUring.memoryAddress(data);
            return scheduleSendmsg(remoteAddress, bufferAddress, data.readableBytes(), segmentSize, first);
        }

        private boolean scheduleSendmsg(InetSocketAddress remoteAddress, long bufferAddress,
                                        int bufferLength, int segmentSize, boolean first) {
            MsgHdrMemory hdr = sendmsgHdrs.nextHdr();
            if (hdr == null) {
                // There is no MsgHdrMemory left to use. We need to submit and wait for the writes to complete
                // before we can write again.
                return false;
            }
            hdr.set(socket, remoteAddress, bufferAddress, bufferLength, (short) segmentSize);

            int fd = fd().intValue();
            int msgFlags = first ? 0 : Native.MSG_DONTWAIT;
            IoRegistration registration = registration();
            IoUringIoOps ops = IoUringIoOps.newSendmsg(fd, (byte) 0, msgFlags, hdr.address(), hdr.idx());
            long id = registration.submit(ops);
            if (id == 0) {
                // Submission failed we don't used the MsgHdrMemory and so should give it back.
                sendmsgHdrs.restoreNextHdr(hdr);
                return false;
            }
            sendmsgHdrs.setId(hdr.idx(), id);
            return true;
        }

        @Override
        protected void freeResourcesNow(IoRegistration reg) {
            sendmsgHdrs.release();
            recvmsgHdrs.release();
            super.freeResourcesNow(reg);
        }
    }

    private static IOException translateForConnected(NativeIoException e) {
        // We need to correctly translate connect errors to match NIO behaviour.
        if (e.expectedErr() == Errors.ERROR_ECONNREFUSED_NEGATIVE) {
            PortUnreachableException error = new PortUnreachableException(e.getMessage());
            error.initCause(e);
            return error;
        }
        return e;
    }

    /**
     * Returns {@code true} if the usage of {@link io.netty.channel.unix.SegmentedDatagramPacket} is supported.
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    public static boolean isSegmentedDatagramPacketSupported() {
        return IoUring.isAvailable();
    }

    @Override
    protected void cancelOutstandingReads(IoRegistration registration, int numOutstandingReads) {
        if (numOutstandingReads > 0) {
            int canceled = cancel(registration, Native.IORING_OP_RECVMSG, recvmsgHdrs);
            assert canceled == numOutstandingReads;
        }
    }

    @Override
    protected void cancelOutstandingWrites(IoRegistration registration, int numOutstandingWrites) {
        if (numOutstandingWrites > 0) {
            int canceled = cancel(registration, Native.IORING_OP_SENDMSG, sendmsgHdrs);
            assert canceled == numOutstandingWrites;
        }
    }

    private int cancel(IoRegistration registration, byte op, MsgHdrMemoryArray array) {
        int cancelled = 0;
        for (int idx = 0; idx < array.length(); idx++) {
            long id = array.id(idx);
            if (id == MsgHdrMemoryArray.NO_ID) {
                continue;
            }
            // Let's try to cancel outstanding op as these might be submitted and waiting for data
            // (via fastpoll).
            IoUringIoOps ops = IoUringIoOps.newAsyncCancel((byte) 0, id, op);
            registration.submit(ops);
            cancelled++;
        }
        return cancelled;
    }

    @Override
    protected boolean socketIsEmpty(int flags) {
        return IoUring.isCqeFSockNonEmptySupported() && (flags & Native.IORING_CQE_F_SOCK_NONEMPTY) == 0;
    }

    @Override
    boolean isPollInFirst() {
        return false;
    }
}
