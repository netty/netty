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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.UnixChannel;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private ScheduledFuture<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;

    final BsdSocket socket;
    private boolean readFilterEnabled;
    private boolean writeFilterEnabled;
    boolean readReadyRunnablePending;
    boolean inputClosedSeenErrorOnRead;
    protected volatile boolean active;
    private volatile SocketAddress local;
    private volatile SocketAddress remote;

    AbstractKQueueChannel(Channel parent, BsdSocket fd, boolean active) {
        super(parent);
        socket = checkNotNull(fd, "fd");
        this.active = active;
        if (active) {
            // Directly cache the remote and local addresses
            // See https://github.com/netty/netty/issues/2359
            local = fd.localAddress();
            remote = fd.remoteAddress();
        }
    }

    AbstractKQueueChannel(Channel parent, BsdSocket fd, SocketAddress remote) {
        super(parent);
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
    protected void doClose() throws Exception {
        active = false;
        // Even if we allow half closed sockets we should give up on reading. Otherwise we may allow a read attempt on a
        // socket which has not even been connected yet. This has been observed to block during unit tests.
        inputClosedSeenErrorOnRead = true;
        socket.close();
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    void resetCachedAddresses() {
        local = socket.localAddress();
        remote = socket.remoteAddress();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof KQueueEventLoop;
    }

    @Override
    public boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    protected void doDeregister() throws Exception {
        ((KQueueEventLoop) eventLoop()).remove(this);

        // As unregisteredFilters() may have not been called because isOpen() returned false we just set both filters
        // to false to ensure a consistent state in all cases.
        readFilterEnabled = false;
        writeFilterEnabled = false;
    }

    void unregisterFilters() throws Exception {
        // Make sure we unregister our filters from kqueue!
        readFilter(false);
        writeFilter(false);
        evSet0(Native.EVFILT_SOCK, Native.EV_DELETE, 0);
    }

    @Override
    protected final void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        final AbstractKQueueUnsafe unsafe = (AbstractKQueueUnsafe) unsafe();
        unsafe.readPending = true;

        // We must set the read flag here as it is possible the user didn't read in the last read loop, the
        // executeReadReadyRunnable could read nothing, and if the user doesn't explicitly call read they will
        // never get data after this.
        readFilter(true);

        // If auto read was toggled off on the last read loop then we may not be notified
        // again if we didn't consume all the data. So we force a read operation here if there maybe more data.
        if (unsafe.maybeMoreDataToRead) {
            unsafe.executeReadReadyRunnable(config());
        }
    }

    @Override
    protected void doRegister() throws Exception {
        // Just in case the previous EventLoop was shutdown abruptly, or an event is still pending on the old EventLoop
        // make sure the readReadyRunnablePending variable is reset so we will be able to execute the Runnable on the
        // new EventLoop.
        readReadyRunnablePending = false;

        ((KQueueEventLoop) eventLoop()).add(this);

        // Add the write event first so we get notified of connection refused on the client side!
        if (writeFilterEnabled) {
            evSet0(Native.EVFILT_WRITE, Native.EV_ADD_CLEAR_ENABLE);
        }
        if (readFilterEnabled) {
            evSet0(Native.EVFILT_READ, Native.EV_ADD_CLEAR_ENABLE);
        }
        evSet0(Native.EVFILT_SOCK, Native.EV_ADD, Native.NOTE_RDHUP);
    }

    @Override
    protected abstract AbstractKQueueUnsafe newUnsafe();

    @Override
    public abstract KQueueChannelConfig config();

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
        unsafe().recvBufAllocHandle().attemptedBytesRead(byteBuf.writableBytes());
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

    final boolean shouldBreakReadReady(ChannelConfig config) {
        return socket.isInputShutdown() && (inputClosedSeenErrorOnRead || !isAllowHalfClosure(config));
    }

    private static boolean isAllowHalfClosure(ChannelConfig config) {
        if (config instanceof KQueueDomainSocketChannelConfig) {
            return ((KQueueDomainSocketChannelConfig) config).isAllowHalfClosure();
        }

        return config instanceof SocketChannelConfig &&
                ((SocketChannelConfig) config).isAllowHalfClosure();
    }

    final void clearReadFilter() {
        // Only clear if registered with an EventLoop as otherwise
        if (isRegistered()) {
            final EventLoop loop = eventLoop();
            final AbstractKQueueUnsafe unsafe = (AbstractKQueueUnsafe) unsafe();
            if (loop.inEventLoop()) {
                unsafe.clearReadFilter0();
            } else {
                // schedule a task to clear the EPOLLIN as it is not safe to modify it directly
                loop.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (!unsafe.readPending && !config().isAutoRead()) {
                            // Still no read triggered so clear it now
                            unsafe.clearReadFilter0();
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

    void readFilter(boolean readFilterEnabled) throws IOException {
        if (this.readFilterEnabled != readFilterEnabled) {
            this.readFilterEnabled = readFilterEnabled;
            evSet(Native.EVFILT_READ, readFilterEnabled ? Native.EV_ADD_CLEAR_ENABLE : Native.EV_DELETE_DISABLE);
        }
    }

    void writeFilter(boolean writeFilterEnabled) throws IOException {
        if (this.writeFilterEnabled != writeFilterEnabled) {
            this.writeFilterEnabled = writeFilterEnabled;
            evSet(Native.EVFILT_WRITE, writeFilterEnabled ? Native.EV_ADD_CLEAR_ENABLE : Native.EV_DELETE_DISABLE);
        }
    }

    private void evSet(short filter, short flags) {
        if (isRegistered()) {
            evSet0(filter, flags);
        }
    }

    private void evSet0(short filter, short flags) {
        evSet0(filter, flags, 0);
    }

    private void evSet0(short filter, short flags, int fflags) {
        // Only try to add to changeList if the FD is still open, if not we already closed it in the meantime.
        if (isOpen()) {
            ((KQueueEventLoop) eventLoop()).evSet(this, filter, flags, fflags);
        }
    }

    abstract class AbstractKQueueUnsafe extends AbstractUnsafe {
        boolean readPending;
        boolean maybeMoreDataToRead;
        private KQueueRecvByteAllocatorHandle allocHandle;
        private final Runnable readReadyRunnable = new Runnable() {
            @Override
            public void run() {
                readReadyRunnablePending = false;
                readReady(recvBufAllocHandle());
            }
        };

        final void readReady(long numberBytesPending) {
            KQueueRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.numberBytesPending(numberBytesPending);
            readReady(allocHandle);
        }

        abstract void readReady(KQueueRecvByteAllocatorHandle allocHandle);

        final void readReadyBefore() {
            maybeMoreDataToRead = false;
        }

        final void readReadyFinally(ChannelConfig config) {
            maybeMoreDataToRead = allocHandle.maybeMoreDataToRead();

            if (allocHandle.isReadEOF() || readPending && maybeMoreDataToRead) {
                // trigger a read again as there may be something left to read and because of ET we
                // will not get notified again until we read everything from the socket
                //
                // It is possible the last fireChannelRead call could cause the user to call read() again, or if
                // autoRead is true the call to channelReadComplete would also call read, but maybeMoreDataToRead is set
                // to false before every read operation to prevent re-entry into readReady() we will not read from
                // the underlying OS again unless the user happens to call read again.
                executeReadReadyRunnable(config);
            } else if (!readPending && !config.isAutoRead()) {
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                clearReadFilter0();
            }
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
                    closeIfClosed();
                    return true;
                }
            }
            return false;
        }

        final void writeReady() {
            if (connectPromise != null) {
                // pending connect which is now complete so handle it.
                finishConnect();
            } else if (!socket.isOutputShutdown()) {
                // directly call super.flush0() to force a flush now
                super.flush0();
            }
        }

        /**
         * Shutdown the input side of the channel.
         */
        void shutdownInput(boolean readEOF) {
            // We need to take special care of calling finishConnect() if readEOF is true and we not
            // fullfilled the connectPromise yet. If we fail to do so the connectPromise will be failed
            // with a ClosedChannelException as a close() will happen and so the FD is closed before we
            // have a chance to call finishConnect() later on. Calling finishConnect() here will ensure
            // we observe the correct exception in case of a connect failure.
            if (readEOF && connectPromise != null) {
                finishConnect();
            }
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
                }
            } else if (!readEOF) {
                inputClosedSeenErrorOnRead = true;
                pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            }
        }

        final void readEOF() {
            // This must happen before we attempt to read. This will ensure reading continues until an error occurs.
            final KQueueRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
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
        }

        @Override
        public KQueueRecvByteAllocatorHandle recvBufAllocHandle() {
            if (allocHandle == null) {
                allocHandle = new KQueueRecvByteAllocatorHandle(
                        (RecvByteBufAllocator.ExtendedHandle) super.recvBufAllocHandle());
            }
            return allocHandle;
        }

        @Override
        protected final void flush0() {
            // Flush immediately only when there's no pending flush.
            // If there's a pending flush operation, event loop will call forceFlush() later,
            // and thus there's no need to call it now.
            if (!writeFilterEnabled) {
                super.flush0();
            }
        }

        final void executeReadReadyRunnable(ChannelConfig config) {
            if (readReadyRunnablePending || !isActive() || shouldBreakReadReady(config)) {
                return;
            }
            readReadyRunnablePending = true;
            eventLoop().execute(readReadyRunnable);
        }

        protected final void clearReadFilter0() {
            assert eventLoop().inEventLoop();
            try {
                readPending = false;
                readFilter(false);
            } catch (IOException e) {
                // When this happens there is something completely wrong with either the filedescriptor or epoll,
                // so fire the exception through the pipeline and close the Channel.
                pipeline().fireExceptionCaught(e);
                unsafe().close(unsafe().voidPromise());
            }
        }

        private void fireEventAndClose(Object evt) {
            pipeline().fireUserEventTriggered(evt);
            close(voidPromise());
        }

        @Override
        public void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                if (connectPromise != null) {
                    throw new ConnectionPendingException();
                }

                boolean wasActive = isActive();
                if (doConnect(remoteAddress, localAddress)) {
                    fulfillConnectPromise(promise, wasActive);
                } else {
                    connectPromise = promise;
                    requestedRemoteAddress = remoteAddress;

                    // Schedule connect timeout.
                    int connectTimeoutMillis = config().getConnectTimeoutMillis();
                    if (connectTimeoutMillis > 0) {
                        connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                            @Override
                            public void run() {
                                ChannelPromise connectPromise = AbstractKQueueChannel.this.connectPromise;
                                if (connectPromise != null && !connectPromise.isDone()
                                        && connectPromise.tryFailure(new ConnectTimeoutException(
                                        "connection timed out: " + remoteAddress))) {
                                    close(voidPromise());
                                }
                            }
                        }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                    }

                    promise.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isCancelled()) {
                                if (connectTimeoutFuture != null) {
                                    connectTimeoutFuture.cancel(false);
                                }
                                connectPromise = null;
                                close(voidPromise());
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                closeIfClosed();
                promise.tryFailure(annotateConnectException(t, remoteAddress));
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }
            active = true;

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

        private void fulfillConnectPromise(ChannelPromise promise, Throwable cause) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(cause);
            closeIfClosed();
        }

        private void finishConnect() {
            // Note this method is invoked by the event loop only if the connection attempt was
            // neither cancelled nor timed out.

            assert eventLoop().inEventLoop();

            boolean connectStillInProgress = false;
            try {
                boolean wasActive = isActive();
                if (!doFinishConnect()) {
                    connectStillInProgress = true;
                    return;
                }
                fulfillConnectPromise(connectPromise, wasActive);
            } catch (Throwable t) {
                fulfillConnectPromise(connectPromise, annotateConnectException(t, requestedRemoteAddress));
            } finally {
                if (!connectStillInProgress) {
                    // Check for null as the connectTimeoutFuture is only created if a connectTimeoutMillis > 0 is used
                    // See https://github.com/netty/netty/issues/1770
                    if (connectTimeoutFuture != null) {
                        connectTimeoutFuture.cancel(false);
                    }
                    connectPromise = null;
                }
            }
        }

        private boolean doFinishConnect() throws Exception {
            if (socket.finishConnect()) {
                writeFilter(false);
                if (requestedRemoteAddress instanceof InetSocketAddress) {
                    remote = computeRemoteAddr((InetSocketAddress) requestedRemoteAddress, socket.remoteAddress());
                }
                requestedRemoteAddress = null;
                return true;
            }
            writeFilter(true);
            return false;
        }
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        if (local instanceof InetSocketAddress) {
            checkResolvable((InetSocketAddress) local);
        }
        socket.bind(local);
        this.local = socket.localAddress();
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
                    remoteAddress : computeRemoteAddr(remoteSocketAddr, socket.remoteAddress());
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
                doClose();
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
