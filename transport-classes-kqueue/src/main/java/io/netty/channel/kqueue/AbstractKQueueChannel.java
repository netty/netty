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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.IoEvent;
import io.netty.channel.IoRegistration;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.UnixChannel;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;

import static io.netty.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;
import static io.netty.channel.unix.UnixChannelUtil.computeRemoteAddr;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

abstract class AbstractKQueueChannel extends AbstractChannel implements UnixChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    private SocketAddress requestedRemoteAddress;

    private final KQueueIoHandle ioHandle = new KQueueIoHandleImpl();
    final BsdSocket socket;
    private IoRegistration registration;
    private boolean readFilterEnabled;
    private boolean writeFilterEnabled;

    boolean readReadyRunnablePending;
    boolean inputClosedSeenErrorOnRead;
    protected volatile boolean active;
    private volatile SocketAddress local;
    private volatile SocketAddress remote;

    AbstractKQueueChannel(EventLoop eventLoop, Channel parent, BsdSocket fd, boolean active) {
        super(eventLoop, KQueueIoHandle.class, parent);
        socket = checkNotNull(fd, "fd");
        this.active = active;
        if (active) {
            // Directly cache the remote and local addresses
            // See https://github.com/netty/netty/issues/2359
            local = fd.localAddress();
            remote = fd.remoteAddress();
        }
    }

    AbstractKQueueChannel(EventLoop eventLoop, Channel parent, BsdSocket fd, SocketAddress remote) {
        super(eventLoop, KQueueIoHandle.class, parent);
        socket = checkNotNull(fd, "fd");
        active = true;
        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        this.remote = remote;
        local = fd.localAddress();
    }

    static boolean isSoErrorZero(BsdSocket fd) {
        try {
            return fd.getSoError() == 0;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    protected final IoRegistration registration() {
        assert registration != null;
        return registration;
    }

    @Override
    public final FileDescriptor fd() {
        return socket;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected void doClose(ChannelPromise promise) {
        active = false;
        // Even if we allow half closed sockets we should give up on reading. Otherwise we may allow a read attempt on a
        // socket which has not even been connected yet. This has been observed to block during unit tests.
        inputClosedSeenErrorOnRead = true;
        try {
            socket.close();
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess();
    }

    @Override
    protected void doDisconnect(ChannelPromise promise)  {
        doClose(promise);
    }

    void resetCachedAddresses() {
        local = socket.localAddress();
        remote = socket.remoteAddress();
    }

    @Override
    public boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    protected void doDeregister(ChannelPromise promise) {
        // As unregisteredFilters() may have not been called because isOpen() returned false we just set both filters
        // to false to ensure a consistent state in all cases.
        // Make sure we unregister our filters from kqueue!
        readFilter(false);
        writeFilter(false);

        clearRdHup0();

        IoRegistration registration = this.registration;
        if (registration != null) {
            registration.cancel();
        }
        promise.setSuccess();
    }

    private void clearRdHup0() {
        submit(KQueueIoOps.newOps(Native.EVFILT_SOCK, Native.EV_DELETE_DISABLE, Native.NOTE_RDHUP));
    }

    private void submit(KQueueIoOps ops) {
        try {
            registration.submit(ops);
        } catch (Exception e) {
            throw new ChannelException(e);
        }
    }

    @Override
    protected final void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        readPending = true;

        // We must set the read flag here as it is possible the user didn't read in the last read loop, the
        // executeReadReadyRunnable could read nothing, and if the user doesn't explicitly call read they will
        // never get data after this.
        readFilter(true);
    }

    @Override
    protected void doRegister(ChannelPromise promise) {
        executor().register(ioHandle).addListener(f -> {
            if (f.isSuccess()) {
                this.registration = (IoRegistration) f.getNow();
                // Just in case the previous EventLoop was shutdown abruptly, or an event is still pending on the old
                // EventLoop make sure the readReadyRunnablePending variable is reset so we will be able to execute
                // the Runnable on the new EventLoop.
                readReadyRunnablePending = false;

                submit(KQueueIoOps.newOps(Native.EVFILT_SOCK, Native.EV_ADD, Native.NOTE_RDHUP));

                // Add the write event first so we get notified of connection refused on the client side!
                if (writeFilterEnabled) {
                    submit(Native.WRITE_ENABLED_OPS);
                }
                if (readFilterEnabled) {
                    submit(Native.READ_ENABLED_OPS);
                }
                promise.setSuccess();
            } else {
                promise.setFailure(f.cause());
            }
        });
    }

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the original one.
     */
    protected final ByteBuf newDirectBuffer(ByteBuf buf) {
        return newDirectBuffer(buf, buf);
    }

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the specified holder.
     * The caller must ensure that the holder releases the original {@link ByteBuf} when the holder is released by
     * this method.
     */
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

    protected static void checkResolvable(InetSocketAddress addr) {
        if (addr.isUnresolved()) {
            throw new UnresolvedAddressException();
        }
    }

    /**
     * Read bytes into the given {@link ByteBuf} and return the amount.
     */
    protected final int doReadBytes(ByteBuf byteBuf) throws Exception {
        int writerIndex = byteBuf.writerIndex();
        int localReadAmount;
        recvBufAllocHandle().attemptedBytesRead(byteBuf.writableBytes());
        if (byteBuf.hasMemoryAddress()) {
            localReadAmount = socket.readAddress(byteBuf.memoryAddress(), writerIndex, byteBuf.capacity());
        } else {
            ByteBuffer buf = byteBuf.internalNioBuffer(writerIndex, byteBuf.writableBytes());
            localReadAmount = socket.read(buf, buf.position(), buf.limit());
        }
        if (localReadAmount > 0) {
            byteBuf.writerIndex(writerIndex + localReadAmount);
        }
        return localReadAmount;
    }

    protected final int doWriteBytes(ChannelOutboundBuffer in, ByteBuf buf) throws Exception {
        if (buf.hasMemoryAddress()) {
            int localFlushedAmount = socket.writeAddress(buf.memoryAddress(), buf.readerIndex(), buf.writerIndex());
            if (localFlushedAmount > 0) {
                in.removeBytes(localFlushedAmount);
                return 1;
            }
        } else {
            final ByteBuffer nioBuf = buf.nioBufferCount() == 1?
                    buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()) : buf.nioBuffer();
            int localFlushedAmount = socket.write(nioBuf, nioBuf.position(), nioBuf.limit());
            if (localFlushedAmount > 0) {
                nioBuf.position(nioBuf.position() + localFlushedAmount);
                in.removeBytes(localFlushedAmount);
                return 1;
            }
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    final boolean shouldBreakReadReady() {
        return socket.isInputShutdown() && (inputClosedSeenErrorOnRead || !isAllowHalfClosure());
    }

    protected boolean isAllowHalfClosure() {
        return false;
    }

    final void clearReadFilter() {
        // Only clear if registered with an EventLoop as otherwise
        if (isRegistered()) {
            final EventLoop loop = executor();
            if (loop.inEventLoop()) {
                clearReadFilter0();
            } else {
                // schedule a task to clear the EPOLLIN as it is not safe to modify it directly
                loop.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (!readPending && !config().isAutoRead()) {
                            // Still no read triggered so clear it now
                            clearReadFilter0();
                        }
                    }
                });
            }
        } else  {
            // The EventLoop is not registered atm so just update the flags so the correct value
            // will be used once the channel is registered
            readFilterEnabled = false;
        }
    }

    void readFilter(boolean readFilterEnabled) {
        if (this.readFilterEnabled != readFilterEnabled) {
            this.readFilterEnabled = readFilterEnabled;
            submit(readFilterEnabled ? Native.READ_ENABLED_OPS : Native.READ_DISABLED_OPS);
        }
    }

    void writeFilter(boolean writeFilterEnabled) {
        if (this.writeFilterEnabled != writeFilterEnabled) {
            this.writeFilterEnabled = writeFilterEnabled;
            submit(writeFilterEnabled ? Native.WRITE_ENABLED_OPS : Native.WRITE_DISABLED_OPS);
        }
    }

    @Override
    protected void doConnect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        final boolean connected;
        requestedRemoteAddress = remoteAddress;
        try {
            connected = doConnect(remoteAddress, localAddress);
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        if (connected) {
            promise.setSuccess();
        } else {
            connectPromise = promise;
        }
    }

    @Override
    protected boolean isWriteFlushedScheduled() {
        return writeFilterEnabled;
    }

    private final class KQueueIoHandleImpl  implements KQueueIoHandle {
        @Override
        public int ident() {
            return fd().intValue();
        }

        @Override
        public void close() {
            ioTransport().close(newPromise());
        }

        @Override
        public void handle(IoRegistration registration, IoEvent event) {
            KQueueIoEvent kqueueEvent = (KQueueIoEvent) event;
            final short filter = kqueueEvent.filter();
            final short flags = kqueueEvent.flags();
            final int fflags = kqueueEvent.fflags();
            final long data = kqueueEvent.data();

            // First check for EPOLLOUT as we may need to fail the connect ChannelPromise before try
            // to read from the file descriptor.
            if (filter == Native.EVFILT_WRITE) {
                writeReady();
            } else if (filter == Native.EVFILT_READ) {
                // Check READ before EOF to ensure all data is read before shutting down the input.
                readReady();
            } else if (filter == Native.EVFILT_SOCK && (fflags & Native.NOTE_RDHUP) != 0) {
                readEOF();
                return;
            }

            // Check if EV_EOF was set, this will notify us for connection-reset in which case
            // we may close the channel directly or try to read more data depending on the state of the
            // Channel and also depending on the AbstractKQueueChannel subtype.
            if ((flags & Native.EV_EOF) != 0) {
                readEOF();
            }
        }
    }

    boolean readPending;
    private KQueueRecvByteAllocatorHandle allocHandle;

    final void readReady() {
        // Check READ before EOF to ensure all data is read before shutting down the input.
        KQueueRecvByteAllocatorHandle allocHandle = (KQueueRecvByteAllocatorHandle) recvBufAllocHandle();
        readReady(allocHandle);
    }

    abstract void readReady(KQueueRecvByteAllocatorHandle allocHandle);

    final boolean shouldStopReading(ChannelConfig config) {
        // Check if there is a readPending which was not processed yet.
        // This could be for two reasons:
        // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
        // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
        //
        // See https://github.com/netty/netty/issues/2254
        return !readPending && !config.isAutoRead();
    }

    final boolean failConnectPromise(Throwable cause) {
        if (connectPromise != null) {
            // SO_ERROR has been shown to return 0 on macOS if detect an error via read() and the write filter was
            // not set before calling connect. This means finishConnect will not detect any error and would
            // successfully complete the connectPromise and update the channel state to active (which is incorrect).
            ChannelPromise connectPromise = AbstractKQueueChannel.this.connectPromise;
            AbstractKQueueChannel.this.connectPromise = null;
            if (connectPromise.tryFailure((cause instanceof ConnectException) ? cause
                            : new ConnectException("failed to connect").initCause(cause))) {
                return true;
            }
        }
        return false;
    }

    private void writeReady() {
        if (connectPromise != null) {
            // pending connect which is now complete so handle it.
            finishConnect();
        } else if (!socket.isOutputShutdown()) {
            // directly call writeFlushedNow() to force a flush now
            writeFlushedNow();
        }
    }

    /**
     * Shutdown the input side of the channel.
     */
    void shutdownInput(boolean readEOF) {
        // We need to take special care of calling finishConnect() if readEOF is true and we not
        // fulfilled the connectPromise yet. If we fail to do so the connectPromise will be failed
        // with a ClosedChannelException as a close() will happen and so the FD is closed before we
        // have a chance to call finishConnect() later on. Calling finishConnect() here will ensure
        // we observe the correct exception in case of a connect failure.
        if (readEOF && connectPromise != null) {
            finishConnect();
        }
        if (!socket.isInputShutdown()) {
            if (isAllowHalfClosure()) {
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
                if (shouldStopReading(config())) {
                    clearReadFilter0();
                }
                pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
            } else {
                close(newPromise());
                return;
            }
        }
        if (!readEOF && !inputClosedSeenErrorOnRead) {
            inputClosedSeenErrorOnRead = true;
            pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
        }
    }

    private void readEOF() {
        // This must happen before we attempt to read. This will ensure reading continues until an error occurs.
        final KQueueRecvByteAllocatorHandle allocHandle = (KQueueRecvByteAllocatorHandle) recvBufAllocHandle();
        allocHandle.readEOF();

        if (isActive()) {
            // If it is still active, we need to call readReady as otherwise we may miss to
            // read pending data from the underlying file descriptor.
            // See https://github.com/netty/netty/issues/3709
            readReady(allocHandle);
        } else {
            // Just to be safe make sure the input marked as closed.
            shutdownInput(true);
        }

        // Clear the RDHUP flag to prevent continuously getting woken up on this event.
        clearRdHup0();
    }

    @Override
    protected RecvByteBufAllocator.Handle newRecvBufAllocHandle() {
        return new KQueueRecvByteAllocatorHandle(
                (RecvByteBufAllocator.ExtendedHandle) super.newRecvBufAllocHandle());
    }

    protected final void clearReadFilter0() {
        assert executor().inEventLoop();
        readPending = false;
        readFilter(false);
    }

    private void fireEventAndClose(Object evt) {
        pipeline().fireUserEventTriggered(evt);
        close(newPromise());
    }

    private void finishConnect() {
        // Note this method is invoked by the event loop only if the connection attempt was
        // neither cancelled nor timed out.

        assert executor().inEventLoop();
        assert connectPromise != null;
        ChannelPromise promise = connectPromise;
        final boolean connected;
        try {
            connected = doFinishConnect();
        } catch (Throwable cause) {
            connectPromise = null;
            promise.setFailure(cause);
            return;
        }
        if (connected) {
            active = true;
            connectPromise = null;
            promise.setSuccess();
        }
    }

    private boolean doFinishConnect() throws Exception {
        if (socket.finishConnect()) {
            writeFilter(false);
            if (requestedRemoteAddress instanceof InetSocketAddress) {
                remote = computeRemoteAddr((InetSocketAddress) requestedRemoteAddress,
                        (InetSocketAddress) socket.remoteAddress());
            }
            requestedRemoteAddress = null;
            return true;
        }
        writeFilter(true);
        return false;
    }

    @Override
    protected void doBind(SocketAddress local, ChannelPromise promise) {
        try {
            if (local instanceof InetSocketAddress) {
                checkResolvable((InetSocketAddress) local);
            }
            socket.bind(local);
            this.local = socket.localAddress();
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess();
    }

    /**
     * Connect to the remote peer
     */
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress instanceof InetSocketAddress) {
            checkResolvable((InetSocketAddress) localAddress);
        }

        InetSocketAddress remoteSocketAddr = remoteAddress instanceof InetSocketAddress
                ? (InetSocketAddress) remoteAddress : null;
        if (remoteSocketAddr != null) {
            checkResolvable(remoteSocketAddr);
        }

        if (remote != null) {
            // Check if already connected before trying to connect. This is needed as connect(...) will not return -1
            // and set errno to EISCONN if a previous connect(...) attempt was setting errno to EINPROGRESS and finished
            // later.
            throw new AlreadyConnectedException();
        }

        if (localAddress != null) {
            socket.bind(localAddress);
        }

        boolean connected = doConnect0(remoteAddress, localAddress);
        if (connected) {
            remote = remoteSocketAddr == null?
                    remoteAddress : computeRemoteAddr(remoteSocketAddr, (InetSocketAddress) socket.remoteAddress());
            active = true;
        }
        // We always need to set the localAddress even if not connected yet as the bind already took place.
        //
        // See https://github.com/netty/netty/issues/3463
        local = socket.localAddress();
        return connected;
    }

    protected boolean doConnect0(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        boolean success = false;
        try {
            boolean connected = socket.connect(remoteAddress);
            if (!connected) {
                writeFilter(true);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose(newPromise());
            }
        }
    }

    @Override
    protected SocketAddress localAddress0() {
        return local;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remote;
    }
}
