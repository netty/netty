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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoop;
import io.netty.channel.IoEvent;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoRegistration;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.UnixChannel;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static io.netty.channel.unix.Errors.ERRNO_EINPROGRESS_NEGATIVE;
import static io.netty.channel.unix.Errors.ERROR_EALREADY_NEGATIVE;
import static io.netty.channel.unix.UnixChannelUtil.computeRemoteAddr;
import static io.netty.util.internal.ObjectUtil.checkNotNull;


abstract class AbstractIoUringChannel extends AbstractChannel implements UnixChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractIoUringChannel.class);
    final LinuxSocket socket;
    protected volatile boolean active;

    // Different masks for outstanding I/O operations.
    private static final int POLL_IN_SCHEDULED = 1;
    private static final int POLL_OUT_SCHEDULED = 1 << 2;
    private static final int POLL_RDHUP_SCHEDULED = 1 << 3;
    private static final int WRITE_SCHEDULED = 1 << 4;
    private static final int READ_SCHEDULED = 1 << 5;
    private static final int CONNECT_SCHEDULED = 1 << 6;

    private short opsId = Short.MIN_VALUE;

    private long pollInId;
    private long pollOutId;
    private long pollRdhupId;
    private long connectId;

    // A byte is enough for now.
    private byte ioState;

    // It's possible that multiple read / writes are issued. We need to keep track of these.
    // Let's limit the amount of pending writes and reads by Short.MAX_VALUE. Maybe Byte.MAX_VALUE would also be good
    // enough but let's be a bit more flexible for now.
    private short numOutstandingWrites;
    private short numOutstandingReads;

    private boolean readPending;
    private boolean inReadComplete;

    private boolean inputClosedSeenErrorOnRead;

    /**
     * The future of the current connection attempt.  If not null, subsequent connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;
    private ByteBuffer remoteAddressMemory;
    private MsgHdrMemoryArray msgHdrMemoryArray;

    private IoUringIoRegistration registration;

    private volatile SocketAddress local;
    private volatile SocketAddress remote;

    AbstractIoUringChannel(final Channel parent, LinuxSocket socket, boolean active) {
        super(parent);
        this.socket = checkNotNull(socket, "fd");

        if (active) {
            // Directly cache the remote and local addresses
            // See https://github.com/netty/netty/issues/2359
            this.active = true;
            this.local = socket.localAddress();
            this.remote = socket.remoteAddress();
        }

        logger.trace("Create {} Socket: {}", this instanceof ServerChannel ? "Server" : "Channel", socket.intValue());
    }

    AbstractIoUringChannel(Channel parent, LinuxSocket fd, SocketAddress remote) {
        super(parent);
        this.socket = checkNotNull(fd, "fd");
        this.active = true;

        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        this.remote = remote;
        this.local = fd.localAddress();
    }

    /**
     * Returns the next id that should be used when submitting {@link IoUringIoOps}.
     *
     * @return  opsId
     */
    protected final short nextOpsId() {
        short id = opsId++;

        // We use 0 for "none".
        if (id == 0) {
            id = opsId++;
        }
        return id;
    }

    public final boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public final FileDescriptor fd() {
        return socket;
    }

    private AbstractUringUnsafe ioUringUnsafe() {
        return (AbstractUringUnsafe) unsafe();
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return loop instanceof IoEventLoop && ((IoEventLoop) loop).isCompatible(AbstractUringUnsafe.class);
    }

    protected final ByteBuf newDirectBuffer(ByteBuf buf) {
        return newDirectBuffer(buf, buf);
    }

    protected final ByteBuf newDirectBuffer(Object holder, ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.release(holder);
            return Unpooled.EMPTY_BUFFER;
        }

        final ByteBufAllocator alloc = alloc();
        if (alloc.isDirectBufferPooled()) {
            return newDirectBuffer0(holder, buf, alloc, readableBytes);
        }

        final ByteBuf directBuf = ByteBufUtil.threadLocalDirectBuffer();
        if (directBuf == null) {
            return newDirectBuffer0(holder, buf, alloc, readableBytes);
        }

        directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
        ReferenceCountUtil.safeRelease(holder);
        return directBuf;
    }

    private static ByteBuf newDirectBuffer0(Object holder, ByteBuf buf, ByteBufAllocator alloc, int capacity) {
        final ByteBuf directBuf = alloc.directBuffer(capacity);
        directBuf.writeBytes(buf, buf.readerIndex(), capacity);
        ReferenceCountUtil.safeRelease(holder);
        return directBuf;
    }

    /**
     * Cancel all outstanding reads
     *
     * @param registration          the {@link IoUringIoRegistration}.
     * @param numOutstandingReads   the number of outstanding reads.
     */
    protected abstract void cancelOutstandingReads(IoUringIoRegistration registration, int numOutstandingReads);

    /**
     * Cancel all outstanding writes
     *
     * @param registration          the {@link IoUringIoRegistration}.
     * @param numOutstandingWrites  the number of outstanding writes.
     */
    protected abstract void cancelOutstandingWrites(IoUringIoRegistration registration, int numOutstandingWrites);

    @Override
    protected void doDisconnect() throws Exception {
    }

    private void freeRemoteAddressMemory() {
        if (remoteAddressMemory != null) {
            Buffer.free(remoteAddressMemory);
            remoteAddressMemory = null;
        }
    }

    private void freeMsgHdrArray() {
        if (msgHdrMemoryArray != null) {
            msgHdrMemoryArray.release();
            msgHdrMemoryArray = null;
        }
    }

    @Override
    protected void doClose() throws Exception {
        active = false;

        if (registration != null && registration.isValid()) {
            if (socket.markClosed()) {
                boolean cancelConnect = false;
                try {
                    ChannelPromise connectPromise = AbstractIoUringChannel.this.connectPromise;
                    if (connectPromise != null) {
                        // Use tryFailure() instead of setFailure() to avoid the race against cancel().
                        connectPromise.tryFailure(new ClosedChannelException());
                        AbstractIoUringChannel.this.connectPromise = null;
                        cancelConnect = true;
                    }

                    cancelConnectTimeoutFuture();
                } finally {
                    // It's important we cancel all outstanding connect, write and read operations now so
                    // we will be able to process a delayed close if needed.
                    ioUringUnsafe().cancelOps(cancelConnect);
                }

                int fd = fd().intValue();
                IoUringIoOps ops = IoUringIoOps.newClose(fd, (byte) 0, nextOpsId());
                registration.submit(ops);
            }
        } else {
            // This one was never registered just use a syscall to close.
            socket.close();
            ioUringUnsafe().freeResourcesNowIfNeeded(null);
        }
    }

    @Override
    protected final void doBeginRead() {
        if (inputClosedSeenErrorOnRead) {
            // We did see an error while reading and so closed the input. Stop reading.
            return;
        }
        if (readPending) {
            // We already have a read pending.
            return;
        }
        if (inReadComplete || !isActive()) {
            // We are currently in the readComplete(...) callback which might issue more reads by itself. Just
            // mark it as a pending read. If readComplete(...) will not issue more reads itself it will pick up
            // the readPending flag, reset it and call doBeginReadNow().
            readPending = true;
            return;
        }
        doBeginReadNow();
    }

    private void doBeginReadNow() {
        if (socket.isBlocking()) {
            // If the socket is blocking we will directly call scheduleFirstReadIfNeeded() as we can use FASTPOLL.
            ioUringUnsafe().scheduleFirstReadIfNeeded();
        } else if ((ioState & POLL_IN_SCHEDULED) == 0) {
            ioUringUnsafe().schedulePollIn();
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) {
        if ((ioState & WRITE_SCHEDULED) != 0) {
            return;
        }
        if (scheduleWrite(in) > 0) {
            ioState |= WRITE_SCHEDULED;
        }
    }

    private int scheduleWrite(ChannelOutboundBuffer in) {
        if (numOutstandingWrites == Short.MAX_VALUE) {
            return 0;
        }
        if (in == null) {
            return 0;
        }

        int msgCount = in.size();
        if (msgCount == 0) {
            return 0;
        }
        Object msg = in.current();

        if (msgCount > 1 && in.current() instanceof ByteBuf) {
            numOutstandingWrites = (short) ioUringUnsafe().scheduleWriteMultiple(in);
        } else if (msg instanceof ByteBuf && ((ByteBuf) msg).nioBufferCount() > 1 ||
                    (msg instanceof ByteBufHolder && ((ByteBufHolder) msg).content().nioBufferCount() > 1)) {
            // We also need some special handling for CompositeByteBuf
            numOutstandingWrites = (short) ioUringUnsafe().scheduleWriteMultiple(in);
        } else {
            numOutstandingWrites = (short) ioUringUnsafe().scheduleWriteSingle(msg);
        }
        // Ensure we never overflow
        assert numOutstandingWrites > 0;
        return numOutstandingWrites;
    }

    protected final IoUringIoRegistration registration() {
        assert registration != null;
        return registration;
    }

    private void schedulePollOut() {
        // This should only be done if the socket is non-blocking.
        assert !socket.isBlocking();
        pollOutId = schedulePollAdd(POLL_OUT_SCHEDULED, Native.POLLOUT);
    }

    final void schedulePollRdHup() {
        pollRdhupId = schedulePollAdd(POLL_RDHUP_SCHEDULED, Native.POLLRDHUP);
    }

    private long schedulePollAdd(int ioMask, int mask) {
        assert (ioState & ioMask) == 0;
        int fd = fd().intValue();
        IoUringIoRegistration registration = registration();
        IoUringIoOps ops = IoUringIoOps.newPollAdd(
                fd, (byte) 0, mask, (short) mask);
        long id = registration.submit(ops);
        if (id != 0) {
            ioState |= ioMask;
        }
        return id;
    }

    final void resetCachedAddresses() {
        local = socket.localAddress();
        remote = socket.remoteAddress();
    }

    abstract class AbstractUringUnsafe extends AbstractUnsafe implements IoUringIoHandle {
        private IoUringRecvByteAllocatorHandle allocHandle;

        /**
         * Schedule the write of multiple messages in the {@link ChannelOutboundBuffer} and returns the number of
         * {@link #writeComplete(byte, int, int, short)} calls that are expected because of the scheduled write.
         */
        protected abstract int scheduleWriteMultiple(ChannelOutboundBuffer in);

        /**
         * Schedule the write of a single message and returns the number of
         * {@link #writeComplete(byte, int, int, short)} calls that are expected because of the scheduled write.
         */
        protected abstract int scheduleWriteSingle(Object msg);

        private boolean closed;
        private boolean freed;

        @Override
        public final void handle(IoRegistration registration, IoEvent ioEvent) {
            IoUringIoRegistration reg = (IoUringIoRegistration) registration;
            IoUringIoEvent event = (IoUringIoEvent) ioEvent;
            byte op = event.opcode();
            int res = event.res();
            int flags = event.flags();
            short data = event.data();
            switch (op) {
                case Native.IORING_OP_RECV:
                case Native.IORING_OP_ACCEPT:
                case Native.IORING_OP_RECVMSG:
                case Native.IORING_OP_READ:
                    readComplete(op, res, flags, data);
                    break;
                case Native.IORING_OP_WRITEV:
                case Native.IORING_OP_SEND:
                case Native.IORING_OP_SENDMSG:
                case Native.IORING_OP_WRITE:
                case Native.IORING_OP_SPLICE:
                    writeComplete(op, res, flags, data);
                    break;
                case Native.IORING_OP_POLL_ADD:
                    pollAddComplete(res, flags, data);
                    break;
                case Native.IORING_OP_POLL_REMOVE:
                    // fd reuse can replace a closed channel (with pending POLL_REMOVE CQEs)
                    // with a new one: better not making it to mess-up with its state!
                    if (res == 0) {
                        clearPollFlag(data);
                    }
                    break;
                case Native.IORING_OP_ASYNC_CANCEL:
                    cancelComplete0(op, res, flags, data);
                    break;
                case Native.IORING_OP_CONNECT:
                    connectComplete(op, res, flags, data);

                    // once the connect was completed we can also free some resources that are not needed anymore.
                    freeMsgHdrArray();
                    freeRemoteAddressMemory();
                    break;
                case Native.IORING_OP_CLOSE:
                    if (res != Native.ERRNO_ECANCELED_NEGATIVE) {
                        closed = true;
                    }
                    break;
            }

            if (ioState == 0 && closed) {
                freeResourcesNowIfNeeded(reg);
            }
        }

        private void freeResourcesNowIfNeeded(IoUringIoRegistration reg) {
            if (!freed) {
                freed = true;
                freeResourcesNow(reg);
            }
        }

        /**
         * Free all resources now. No new IO will be submitted for this channel via io_uring
         *
         * @param reg   the {@link IoUringIoRegistration} or {@code null} if it was never registered
         */
        protected void freeResourcesNow(IoUringIoRegistration reg) {
            freeMsgHdrArray();
            freeRemoteAddressMemory();
            if (reg != null) {
                reg.cancel();
            }
        }

        private void pollAddComplete(int res, int flags, short data) {
            if ((data & Native.POLLOUT) != 0) {
                pollOut(res);
            }
            if ((data & Native.POLLIN) != 0) {
                pollIn(res);
            }
            if ((data & Native.POLLRDHUP) != 0) {
                pollRdHup(res);
            }
        }

        @Override
        public final void close() throws Exception {
            close(voidPromise());
        }

        private void cancelOps(boolean cancelConnect) {
            if (registration == null || !registration.isValid()) {
                return;
            }
            int fd = fd().intValue();
            if ((ioState & POLL_RDHUP_SCHEDULED) != 0) {
                registration.submit(IoUringIoOps.newPollRemove(
                        fd, (byte) 0, pollRdhupId, (short) Native.POLLRDHUP));
            }
            if ((ioState & POLL_IN_SCHEDULED) != 0) {
                registration.submit(IoUringIoOps.newPollRemove(
                        fd, (byte) 0, pollInId, (short) Native.POLLIN));
            }
            if ((ioState & POLL_OUT_SCHEDULED) != 0) {
                registration.submit(IoUringIoOps.newPollRemove(
                        fd,  (byte) 0, pollOutId, (short) Native.POLLOUT));
            }
            if (cancelConnect && connectId != 0) {
                // Best effort to cancel the already submitted connect request.
                registration.submit(IoUringIoOps.newAsyncCancel(
                        fd, (byte) 0, connectId, Native.IORING_OP_CONNECT));
            }
            cancelOutstandingReads(registration, numOutstandingReads);
            cancelOutstandingWrites(registration, numOutstandingWrites);
        }

        @Override
        protected final void flush0() {
            // Flush immediately only when there's no pending flush.
            // If there's a pending flush operation, event loop will call forceFlush() later,
            // and thus there's no need to call it now.
            if ((ioState & POLL_OUT_SCHEDULED) == 0) {
                super.flush0();
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, Throwable cause) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(cause);
            closeIfClosed();
        }

        private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }
            active = true;

            if (local == null) {
                local = socket.localAddress();
            }
            computeRemote();

            // Register POLLRDHUP
            schedulePollRdHup();

            // Get the state as trySuccess() may trigger an ChannelFutureListener that will close the Channel.
            // We still need to ensure we call fireChannelActive() in this case.
            boolean active = isActive();

            // trySuccess() will return false if a user cancelled the connection attempt.
            boolean promiseSet = promise.trySuccess();

            // Regardless if the connection attempt was cancelled, channelActive() event should be triggered,
            // because what happened is what happened.
            if (!wasActive && active) {
                pipeline().fireChannelActive();
            }

            // If a user cancelled the connection attempt, close the channel, which is followed by channelInactive().
            if (!promiseSet) {
                close(voidPromise());
            }
        }

        @Override
        public final IoUringRecvByteAllocatorHandle recvBufAllocHandle() {
            if (allocHandle == null) {
                allocHandle = new IoUringRecvByteAllocatorHandle(
                        (RecvByteBufAllocator.ExtendedHandle) super.recvBufAllocHandle());
            }
            return allocHandle;
        }

        final void shutdownInput(boolean allDataRead) {
            logger.trace("shutdownInput Fd: {}", fd().intValue());
            if (!socket.isInputShutdown()) {
                if (isAllowHalfClosure(config())) {
                    try {
                        socket.shutdown(true, false);
                    } catch (IOException ignored) {
                        // We attempted to shutdown and failed, which means the input has already effectively been
                        // shutdown.
                        fireEventAndClose(ChannelInputShutdownEvent.INSTANCE);
                        return;
                    } catch (NotYetConnectedException ignore) {
                        // We attempted to shutdown and failed, which means the input has already effectively been
                        // shutdown.
                    }
                    pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                } else {
                    close(voidPromise());
                    return;
                }
            }
            if (allDataRead && !inputClosedSeenErrorOnRead) {
                inputClosedSeenErrorOnRead = true;
                pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            }
        }

        private void fireEventAndClose(Object evt) {
            pipeline().fireUserEventTriggered(evt);
            close(voidPromise());
        }

        final void schedulePollIn() {
            // This should only be done if the socket is non-blocking.
            assert !socket.isBlocking();

            assert (ioState & POLL_IN_SCHEDULED) == 0;
            if (!isActive() || shouldBreakIoUringInReady(config())) {
                return;
            }
            pollInId = schedulePollAdd(POLL_IN_SCHEDULED, Native.POLLIN);
        }

        private void readComplete(byte op, int res, int flags, short data) {
            assert numOutstandingReads > 0;
            if (--numOutstandingReads == 0) {
                readPending = false;
                ioState &= ~READ_SCHEDULED;
            }
            inReadComplete = true;
            try {
                readComplete0(op, res, flags, data, numOutstandingReads);
            } finally {
                inReadComplete = false;
                // There is a pending read and readComplete0(...) also did stop issue read request.
                // Let's trigger the requested read now.
                if (readPending && recvBufAllocHandle().isReadComplete()) {
                    doBeginReadNow();
                }
            }
        }

        /**
         * Called once a read was completed.
         */
        protected abstract void readComplete0(byte op, int res, int flags, short data, int outstandingCompletes);

        /**
         * Called once POLLRDHUP event is ready to be processed
         */
        private void pollRdHup(int res) {
            ioState &= ~POLL_RDHUP_SCHEDULED;
            pollRdhupId = 0;
            if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                return;
            }

            // Mark that we received a POLLRDHUP and so need to continue reading until all the input ist drained.
            recvBufAllocHandle().rdHupReceived();

            if (isActive()) {
                scheduleFirstReadIfNeeded();
            } else {
                // Just to be safe make sure the input marked as closed.
                shutdownInput(false);
            }
        }

        /**
         * Called once POLLIN event is ready to be processed
         */
        private void pollIn(int res) {
            ioState &= ~POLL_IN_SCHEDULED;
            pollInId = 0;
            if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                return;
            }

            scheduleFirstReadIfNeeded();
        }

        private void scheduleFirstReadIfNeeded() {
            if ((ioState & READ_SCHEDULED) == 0) {
                scheduleFirstRead();
            }
        }

        private void scheduleFirstRead() {
            // This is a new "read loop" so we need to reset the allocHandle.
            final ChannelConfig config = config();
            final IoUringRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.reset(config);
            scheduleRead(true);
        }

        protected final void scheduleRead(boolean first) {
            // Only schedule another read if the fd is still open.
            if (fd().isOpen() && (ioState & READ_SCHEDULED) == 0) {
                numOutstandingReads = (short) scheduleRead0(first);
                if (numOutstandingReads > 0) {
                    ioState |= READ_SCHEDULED;
                }
            }
        }

        /**
         * Schedule a read and returns the number of {@link #readComplete(byte, int, int, short)}
         * calls that are expected because of the scheduled read.
         *
         * @param first {@code true} if this is the first read of a read loop.
         */
        protected abstract int scheduleRead0(boolean first);

        /**
         * Called once POLLOUT event is ready to be processed
         *
         * @param res   the result.
         */
        private void pollOut(int res) {
            ioState &= ~POLL_OUT_SCHEDULED;
            pollOutId = 0;
            if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                return;
            }
            // pending connect
            if (connectPromise != null) {
                // Note this method is invoked by the event loop only if the connection attempt was
                // neither cancelled nor timed out.

                assert eventLoop().inEventLoop();

                boolean connectStillInProgress = false;
                try {
                    boolean wasActive = isActive();
                    if (!socket.finishConnect()) {
                        connectStillInProgress = true;
                        return;
                    }
                    fulfillConnectPromise(connectPromise, wasActive);
                } catch (Throwable t) {
                    fulfillConnectPromise(connectPromise, annotateConnectException(t, requestedRemoteAddress));
                } finally {
                    if (!connectStillInProgress) {
                        // Check for null as the connectTimeoutFuture is only created if a connectTimeoutMillis > 0
                        // is used
                        // See https://github.com/netty/netty/issues/1770
                        cancelConnectTimeoutFuture();
                        connectPromise = null;
                    } else {
                        // This should only happen if the socket is non-blocking.
                        assert !socket.isBlocking();

                        // The connect was not done yet, register for POLLOUT again
                        schedulePollOut();
                    }
                }
            } else if (!socket.isOutputShutdown()) {
                // Try writing again
                super.flush0();
            }
        }

        /**
         * Called once a write was completed.
         *
         * @param op    the op code.
         * @param res   the result.
         * @param flags the flags.
         * @param data  the data that was passed when submitting the op.
         */
        private void writeComplete(byte op, int res, int flags, short data) {
            if ((ioState & CONNECT_SCHEDULED) != 0) {
                // The writeComplete(...) callback was called because of a sendmsg(...) result that was used for
                // TCP_FASTOPEN_CONNECT.
                freeMsgHdrArray();
                if (res > 0) {
                    // Connect complete!
                    outboundBuffer().removeBytes(res);

                    // Explicit pass in 0 as this is returned by a connect(...) call when it was successful.
                    connectComplete(op, 0, flags, data);
                } else if (res == ERRNO_EINPROGRESS_NEGATIVE || res == 0) {
                    // This happens when we (as a client) have no pre-existing cookie for doing a fast-open connection.
                    // In this case, our TCP connection will be established normally, but no data was transmitted at
                    // this time. We'll just transmit the data with normal writes later.
                    // Let's submit a normal connect.
                    submitConnect((InetSocketAddress) requestedRemoteAddress);
                } else {
                    // There was an error, handle it as a normal connect error.
                    connectComplete(op, res, flags, data);
                }
                return;
            }
            assert numOutstandingWrites > 0;
            --numOutstandingWrites;

            boolean writtenAll = writeComplete0(op, res, flags, data, numOutstandingWrites);
            if (!writtenAll && (ioState & POLL_OUT_SCHEDULED) == 0) {
                // This should only happen if the socket is non-blocking.
                assert !socket.isBlocking();

                // We were not able to write everything, let's register for POLLOUT
                schedulePollOut();
            }

            // We only reset this once we are done with calling removeBytes(...) as otherwise we may trigger a write
            // while still removing messages internally in removeBytes(...) which then may corrupt state.
            if (numOutstandingWrites == 0) {
                ioState &= ~WRITE_SCHEDULED;

                // If we could write all and we did not schedule a pollout yet let us try to write again
                if (writtenAll && (ioState & POLL_OUT_SCHEDULED) == 0) {
                    doWrite(unsafe().outboundBuffer());
                }
            }
        }

        /**
         * Called once a write was completed.
         * @param op            the op code
         * @param res           the result.
         * @param flags         the flags.
         * @param data          the data that was passed when submitting the op.
         * @param outstanding   the outstanding write completions.
         */
        abstract boolean writeComplete0(byte op, int res, int flags, short data, int outstanding);

        /**
         * Called once a cancel was completed.
         *
         * @param op            the op code
         * @param res           the result.
         * @param flags         the flags.
         * @param data          the data that was passed when submitting the op.
         */
        void cancelComplete0(byte op, int res, int flags, short data) {
            // NOOP
        }

        /**
         * Called once a connect was completed.
         * @param op            the op code.
         * @param res           the result.
         * @param flags         the flags.
         * @param data          the data that was passed when submitting the op.
         */
        void connectComplete(byte op, int res, int flags, short data) {
            ioState &= ~CONNECT_SCHEDULED;
            freeRemoteAddressMemory();

            if (res == ERRNO_EINPROGRESS_NEGATIVE || res == ERROR_EALREADY_NEGATIVE) {
                // This should only happen if the socket is non-blocking.
                assert !socket.isBlocking();

                // connect not complete yet need to wait for poll_out event
                schedulePollOut();
            } else {
                try {
                    if (res == 0) {
                        fulfillConnectPromise(connectPromise, active);
                        if (readPending) {
                            doBeginReadNow();
                        }
                    } else {
                        try {
                            Errors.throwConnectException("io_uring connect", res);
                        } catch (Throwable cause) {
                            fulfillConnectPromise(connectPromise, cause);
                        }
                    }
                } finally {
                    // Check for null as the connectTimeoutFuture is only created if a connectTimeoutMillis > 0 is
                    // used
                    // See https://github.com/netty/netty/issues/1770
                    cancelConnectTimeoutFuture();
                    connectPromise = null;
                }
            }
        }

        @Override
        public void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            // Don't mark the connect promise as uncancellable as in fact we can cancel it as it is using
            // non-blocking io.
            if (promise.isDone() || !ensureOpen(promise)) {
                return;
            }

            if (!isOpen()) {
                promise.tryFailure(annotateConnectException(new ClosedChannelException(), remoteAddress));
                return;
            }
            try {
                if (connectPromise != null) {
                    throw new ConnectionPendingException();
                }
                if (localAddress instanceof InetSocketAddress) {
                    checkResolvable((InetSocketAddress) localAddress);
                }

                if (remoteAddress instanceof InetSocketAddress) {
                    checkResolvable((InetSocketAddress) remoteAddress);
                }

                if (remote != null) {
                    // Check if already connected before trying to connect. This is needed as connect(...) will not#
                    // return -1 and set errno to EISCONN if a previous connect(...) attempt was setting errno to
                    // EINPROGRESS and finished later.
                    throw new AlreadyConnectedException();
                }

                if (localAddress != null) {
                    socket.bind(localAddress);
                }

                InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;

                ByteBuf initialData = null;
                if (IoUring.isTcpFastOpenClientSideAvailable() &&
                        config().getOption(ChannelOption.TCP_FASTOPEN_CONNECT) == Boolean.TRUE) {
                    ChannelOutboundBuffer outbound = unsafe().outboundBuffer();
                    outbound.addFlush();
                    Object curr;
                    if ((curr = outbound.current()) instanceof ByteBuf) {
                        initialData = (ByteBuf) curr;
                    }
                }
                if (initialData != null) {
                    msgHdrMemoryArray = new MsgHdrMemoryArray((short) 1);
                    MsgHdrMemory hdr = msgHdrMemoryArray.hdr(0);
                    hdr.write(socket, inetSocketAddress, initialData.memoryAddress(),
                            initialData.readableBytes(), (short) 0);

                    int fd = fd().intValue();
                    IoUringIoRegistration registration = registration();
                    IoUringIoOps ops = IoUringIoOps.newSendmsg(fd, (byte) 0, Native.MSG_FASTOPEN,
                            hdr.address(), hdr.idx());
                    connectId = registration.submit(ops);
                    if (connectId == 0) {
                        // Directly release the memory if submitting failed.
                        freeMsgHdrArray();
                    }
                } else {
                    submitConnect(inetSocketAddress);
                }
                if (connectId != 0) {
                    ioState |= CONNECT_SCHEDULED;
                }
            } catch (Throwable t) {
                closeIfClosed();
                promise.tryFailure(annotateConnectException(t, remoteAddress));
                return;
            }
            connectPromise = promise;
            requestedRemoteAddress = remoteAddress;
            // Schedule connect timeout.
            int connectTimeoutMillis = config().getConnectTimeoutMillis();
            if (connectTimeoutMillis > 0) {
                connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                    @Override
                    public void run() {
                        ChannelPromise connectPromise = AbstractIoUringChannel.this.connectPromise;
                        if (connectPromise != null && !connectPromise.isDone() &&
                                connectPromise.tryFailure(new ConnectTimeoutException(
                                        "connection timed out: " + remoteAddress))) {
                            close(voidPromise());
                        }
                    }
                }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
            }

            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    // If the connect future is cancelled we also cancel the timeout and close the
                    // underlying socket.
                    if (future.isCancelled()) {
                        cancelConnectTimeoutFuture();
                        connectPromise = null;
                        close(voidPromise());
                    }
                }
            });
        }
    }

    private void submitConnect(InetSocketAddress inetSocketAddress) {
        remoteAddressMemory = Buffer.allocateDirectWithNativeOrder(Native.SIZEOF_SOCKADDR_STORAGE);
        long remoteAddressMemoryAddress = Buffer.memoryAddress(remoteAddressMemory);

        SockaddrIn.write(socket.isIpv6(), remoteAddressMemoryAddress, inetSocketAddress);

        int fd = fd().intValue();
        IoUringIoRegistration registration = registration();
        IoUringIoOps ops = IoUringIoOps.newConnect(
                fd, (byte) 0, remoteAddressMemoryAddress, nextOpsId());
        connectId = registration.submit(ops);
        if (connectId == 0) {
            // Directly release the memory if submitting failed.
            freeRemoteAddressMemory();
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }
        throw new UnsupportedOperationException("unsupported message type");
    }

    @Override
    protected void doRegister(ChannelPromise promise) {
        IoEventLoop eventLoop = (IoEventLoop) eventLoop();
        eventLoop.register(ioUringUnsafe()).addListener(f -> {
            if (f.isSuccess()) {
                registration = (IoUringIoRegistration) f.getNow();
                promise.setSuccess();
            } else {
                promise.setFailure(f.cause());
            }
        });
    }

    @Override
    protected final void doDeregister() {
        // Cancel all previous submitted ops.
        ioUringUnsafe().cancelOps(connectPromise != null);
    }

    @Override
    protected void doBind(final SocketAddress local) throws Exception {
        if (local instanceof InetSocketAddress) {
            checkResolvable((InetSocketAddress) local);
        }
        socket.bind(local);
        this.local = socket.localAddress();
    }

    protected static void checkResolvable(InetSocketAddress addr) {
        if (addr.isUnresolved()) {
            throw new UnresolvedAddressException();
        }
    }

    @Override
    protected final SocketAddress localAddress0() {
        return local;
    }

    @Override
    protected final SocketAddress remoteAddress0() {
        return remote;
    }

    private static boolean isAllowHalfClosure(ChannelConfig config) {
        return config instanceof SocketChannelConfig &&
               ((SocketChannelConfig) config).isAllowHalfClosure();
    }

    private void cancelConnectTimeoutFuture() {
        if (connectTimeoutFuture != null) {
            connectTimeoutFuture.cancel(false);
            connectTimeoutFuture = null;
        }
    }

    private void computeRemote() {
        if (requestedRemoteAddress instanceof InetSocketAddress) {
            remote = computeRemoteAddr((InetSocketAddress) requestedRemoteAddress, socket.remoteAddress());
        }
    }

    private boolean shouldBreakIoUringInReady(ChannelConfig config) {
        return socket.isInputShutdown() && (inputClosedSeenErrorOnRead || !isAllowHalfClosure(config));
    }

    private void clearPollFlag(int pollMask) {
        if ((pollMask & Native.POLLIN) != 0) {
            ioState &= ~POLL_IN_SCHEDULED;
            pollInId = 0;
        }
        if ((pollMask & Native.POLLOUT) != 0) {
            ioState &= ~POLL_OUT_SCHEDULED;
            pollOutId = 0;
        }
        if ((pollMask & Native.POLLRDHUP) != 0) {
            ioState &= ~POLL_RDHUP_SCHEDULED;
            pollRdhupId = 0;
        }
     }
}
