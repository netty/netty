/*
 * Copyright 2020 The Netty Project
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
import io.netty.channel.socket.DatagramChannel;
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

public final class IOUringDatagramChannel extends AbstractIOUringChannel implements DatagramChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(true, 16);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
            StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
            StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
            StringUtil.simpleClassName(ByteBuf.class) + ')';

    private final IOUringDatagramChannelConfig config;
    private volatile boolean connected;

    /**
     * Create a new instance which selects the {@link SocketProtocolFamily} to use depending
     * on the Operation Systems default which will be chosen.
     */
    public IOUringDatagramChannel() {
        this(null);
    }

    /**
     * Create a new instance using the given {@link SocketProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     */
    public IOUringDatagramChannel(SocketProtocolFamily family) {
        this(family == null ?
                LinuxSocket.newSocketDgram(Socket.isIPv6Preferred()) :
                        LinuxSocket.newSocketDgram(family == SocketProtocolFamily.INET6), false);
    }

    /**
     * Create a new instance which selects the {@link SocketProtocolFamily} to use depending
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
        // These buffers are used for msghdr, iov, sockaddr_in / sockaddr_in6 when doing recvmsg / sendmsg
        //
        // TODO: Alternative we could also allocate these everytime from the ByteBufAllocator or we could use
        //       some sort of other pool. Let's keep it simple for now.
        //
        // Consider exposing some configuration for that.
        private final MsgHdrMemoryArray recvmsgHdrs = new MsgHdrMemoryArray(256);
        private final MsgHdrMemoryArray sendmsgHdrs = new MsgHdrMemoryArray(256);
        private final int[] sendmsgResArray = new int[sendmsgHdrs.capacity()];
        private final WriteProcessor writeProcessor = new WriteProcessor();

        private ByteBuf readBuffer;

        private final class WriteProcessor implements ChannelOutboundBuffer.MessageProcessor {
            private int written;

            @Override
            public boolean processMessage(Object msg) {
                if (scheduleWrite(msg, true)) {
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

        void releaseBuffers() {
            sendmsgHdrs.release();
            recvmsgHdrs.release();
        }

        @Override
        protected void readComplete0(int res, int flags, int data, int outstanding) {
            final IOUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
            ByteBuf byteBuf = this.readBuffer;
            assert byteBuf != null;
            try {
                if (data == -1) {
                    assert outstanding == 0;
                    // data == -1 means that we did a read(...) and not a recvmmsg(...)
                    readComplete(pipeline, allocHandle, byteBuf, res);
                } else {
                    recvmsgComplete(pipeline, allocHandle, byteBuf, res, data, outstanding);
                }
            } catch (Throwable t) {
                if (connected && t instanceof NativeIoException) {
                    t = translateForConnected((NativeIoException) t);
                }
                pipeline.fireExceptionCaught(t);
            }
        }

        private void readComplete(ChannelPipeline pipeline, IOUringRecvByteAllocatorHandle allocHandle,
                                  ByteBuf byteBuf, int res) throws IOException {
            try {
                this.readBuffer = null;
                if (res < 0) {
                    if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                        return;
                    }
                    // If res is negative we should pass it to ioResult(...) which will either throw
                    // or convert it to 0 if we could not read because the socket was not readable.
                    allocHandle.lastBytesRead(ioResult("io_uring read", res));
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

                allocHandle.incMessagesRead(1);
                pipeline.fireChannelRead(new DatagramPacket(byteBuf, IOUringDatagramChannel.this.localAddress(),
                        IOUringDatagramChannel.this.remoteAddress()));
                byteBuf = null;

                if (allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER)) {
                    // Let's schedule another read.
                    scheduleRead(false);
                } else {
                    // We did not fill the whole ByteBuf so we should break the "read loop" and try again later.
                    allocHandle.readComplete();
                    pipeline.fireChannelReadComplete();
                }
            } finally {
                if (byteBuf != null) {
                    byteBuf.release();
                }
            }
        }

        private void recvmsgComplete(ChannelPipeline pipeline, IOUringRecvByteAllocatorHandle allocHandle,
                                      ByteBuf byteBuf, int res, int idx, int outstanding) throws IOException {
            MsgHdrMemory hdr = recvmsgHdrs.hdr(idx);
            if (res < 0) {
                if (res != Native.ERRNO_ECANCELED_NEGATIVE) {
                    // If res is negative we should pass it to ioResult(...) which will either throw
                    // or convert it to 0 if we could not read because the socket was not readable.
                    allocHandle.lastBytesRead(ioResult("io_uring recvmsg", res));
                }
            } else {
                allocHandle.lastBytesRead(res);
                if (hdr.hasPort(IOUringDatagramChannel.this)) {
                    allocHandle.incMessagesRead(1);
                    DatagramPacket packet = hdr.read(
                            IOUringDatagramChannel.this, registration().ioHandler(), byteBuf, res);
                    pipeline.fireChannelRead(packet);
                }
            }

            if (outstanding == 0) {
                // There are no outstanding completion events, release the readBuffer and see if we need to schedule
                // another one or if the user will do it.
                this.readBuffer.release();
                this.readBuffer = null;
                recvmsgHdrs.clear();

                if (res != Native.ERRNO_ECANCELED_NEGATIVE) {
                    if (allocHandle.lastBytesRead() > 0 &&
                            allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER)) {
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
        protected int scheduleRead0(boolean first) {
            final IOUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            ByteBuf byteBuf = allocHandle.allocate(alloc());
            assert readBuffer == null;
            readBuffer = byteBuf;

            int writable = byteBuf.writableBytes();
            allocHandle.attemptedBytesRead(writable);
            int datagramSize = config().getMaxDatagramPayloadSize();

            int numDatagram = datagramSize == 0 ? 1 : Math.max(1, byteBuf.writableBytes() / datagramSize);

            if (isConnected() && numDatagram <= 1) {
                IOUringIoOps ops = IOUringIoOps.newRecv(fd().intValue(), 0, 0,
                        byteBuf.memoryAddress() + byteBuf.writerIndex(), byteBuf.writableBytes(), (short) -1);
                registration().submit(ops);
                return 1;
            } else {
                int scheduled = scheduleRecvmsg(byteBuf, numDatagram, datagramSize);
                if (scheduled == 0) {
                    // We could not schedule any recvmmsg so we need to release the buffer as there will be no
                    // completion event.
                    readBuffer = null;
                    byteBuf.release();
                }
                return scheduled;
            }
        }

        private int scheduleRecvmsg(ByteBuf byteBuf, int numDatagram, int datagramSize) {
            int writable = byteBuf.writableBytes();
            long bufferAddress = byteBuf.memoryAddress() + byteBuf.writerIndex();
            if (numDatagram <= 1) {
                return scheduleRecvmsg0(bufferAddress, writable) ? 1 : 0;
            }
            int i = 0;
            // Add multiple IORING_OP_RECVMSG to the submission queue. This basically emulates recvmmsg(...)
            for (; i < numDatagram && writable >= datagramSize; i++) {
                if (!scheduleRecvmsg0(bufferAddress, datagramSize)) {
                    break;
                }
                bufferAddress += datagramSize;
                writable -= datagramSize;
            }
            return i;
        }

        private boolean scheduleRecvmsg0(long bufferAddress, int bufferLength) {
            MsgHdrMemory msgHdrMemory = recvmsgHdrs.nextHdr();
            if (msgHdrMemory == null) {
                // We can not continue reading before we did not submit the recvmsg(s) and received the results.
                return false;
            }
            msgHdrMemory.write(socket, null, bufferAddress, bufferLength, (short) 0);
            // We always use idx here so we can detect if no idx was used by checking if data < 0 in
            // readComplete0(...)
            IOUringIoOps ops = IOUringIoOps.newRecvmsg(fd().intValue(), 0, 0, msgHdrMemory.address(),
                    (short) msgHdrMemory.idx());
            registration().submit(ops);
            return true;
        }

        @Override
        boolean writeComplete0(int res, int flags, int data, int outstanding) {
            ChannelOutboundBuffer outboundBuffer = outboundBuffer();
            if (data == -1) {
                assert outstanding == 0;
                // idx == -1 means that we did a write(...) and not a sendmsg(...) operation
                return removeFromOutboundBuffer(outboundBuffer, res, "io_uring write");
            }
            // Store the result so we can handle it as soon as we have no outstanding writes anymore.
            sendmsgResArray[data] = res;
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
            } else if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                return false;
            }
            try {
                return ioResult(errormsg, res) != 0;
            } catch (Throwable cause) {
                return outboundBuffer.remove(cause);
            }
        }

        @Override
        void connectComplete(int res, int flags, short data) {
            if (res >= 0) {
                connected = true;
            }
            super.connectComplete(res, flags, data);
        }

        @Override
        protected int scheduleWriteMultiple(ChannelOutboundBuffer in) {
            return writeProcessor.write(in);
        }

        @Override
        protected int scheduleWriteSingle(Object msg) {
            return scheduleWrite(msg, false) ? 1 : 0;
        }

        private boolean scheduleWrite(Object msg, boolean forceSendmsg) {
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
                remoteAddress = null;
                segmentSize = 0;
            }

            long bufferAddress = data.memoryAddress();
            if (remoteAddress == null) {
                if (forceSendmsg || segmentSize > 0) {
                    return scheduleSendmsg(
                            IOUringDatagramChannel.this.remoteAddress(),
                            bufferAddress, data.readableBytes(), segmentSize);
                }
                IOUringIoOps ops = IOUringIoOps.newSend(fd().intValue(), 0, 0,
                        bufferAddress + data.readerIndex(), data.readableBytes(), (short) -1);
                registration().submit(ops);
                return true;
            }
            return scheduleSendmsg(remoteAddress, bufferAddress, data.readableBytes(), segmentSize);
        }

        private boolean scheduleSendmsg(InetSocketAddress remoteAddress, long bufferAddress,
                                        int bufferLength, int segmentSize) {
            MsgHdrMemory hdr = sendmsgHdrs.nextHdr();
            if (hdr == null) {
                // There is no MsgHdrMemory left to use. We need to submit and wait for the writes to complete
                // before we can write again.
                return false;
            }
            hdr.write(socket, remoteAddress, bufferAddress, bufferLength, (short) segmentSize);

            IOUringIoOps ops = IOUringIoOps.newSendmsg(fd().intValue(), 0, 0, hdr.address(), (short) hdr.idx());
            registration().submit(ops);
            return true;
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
        return IOUring.isAvailable();
    }

    @Override
    protected void cancelOutstandingReads(IOUringIoRegistration registration, int numOutstandingReads) {
        // TODO: implement me
    }

    @Override
    protected void cancelOutstandingWrites(IOUringIoRegistration registration, int numOutstandingWrites) {
        // TODO: implement me
    }
}
