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
package io.netty5.channel.kqueue;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.ByteBufUtil;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.StandardAllocationTypes;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ConnectTimeoutException;
import io.netty5.channel.EventLoop;
import io.netty5.channel.socket.ChannelInputShutdownEvent;
import io.netty5.channel.socket.ChannelInputShutdownReadComplete;
import io.netty5.channel.socket.SocketChannelConfig;
import io.netty5.channel.unix.FileDescriptor;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.UnstableApi;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.TimeUnit;

import static io.netty5.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;
import static io.netty5.channel.unix.UnixChannelUtil.computeRemoteAddr;
import static java.util.Objects.requireNonNull;

abstract class AbstractKQueueChannel extends AbstractChannel implements UnixChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private Promise<Void> connectPromise;
    private Future<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;
    private KQueueRegistration registration;

    final BsdSocket socket;
    private boolean readFilterEnabled;
    private boolean writeFilterEnabled;
    boolean readReadyRunnablePending;
    boolean inputClosedSeenErrorOnRead;

    protected volatile boolean active;
    private volatile SocketAddress local;
    private volatile SocketAddress remote;

    AbstractKQueueChannel(Channel parent, EventLoop eventLoop, BsdSocket fd, boolean active) {
        super(parent, eventLoop);
        socket = requireNonNull(fd, "fd");
        this.active = active;
        if (active) {
            // Directly cache the remote and local addresses
            // See https://github.com/netty/netty/issues/2359
            local = fd.localAddress();
            remote = fd.remoteAddress();
        }
    }

    AbstractKQueueChannel(Channel parent, EventLoop eventLoop, BsdSocket fd, SocketAddress remote) {
        super(parent, eventLoop);
        socket = requireNonNull(fd, "fd");
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

    protected KQueueRegistration registration() {
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
    public boolean isOpen() {
        return socket.isOpen();
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

    void register0(KQueueRegistration registration)  {
        this.registration = registration;
        // Just in case the previous EventLoop was shutdown abruptly, or an event is still pending on the old EventLoop
        // make sure the readReadyRunnablePending variable is reset so we will be able to execute the Runnable on the
        // new EventLoop.
        readReadyRunnablePending = false;

        // Add the write event first so we get notified of connection refused on the client side!
        if (writeFilterEnabled) {
            evSet0(registration, Native.EVFILT_WRITE, Native.EV_ADD_CLEAR_ENABLE);
        }
        if (readFilterEnabled) {
            evSet0(registration, Native.EVFILT_READ, Native.EV_ADD_CLEAR_ENABLE);
        }
        evSet0(registration, Native.EVFILT_SOCK, Native.EV_ADD, Native.NOTE_RDHUP);
    }

    void deregister0() {
        // As unregisteredFilters() may have not been called because isOpen() returned false we just set both filters
        // to false to ensure a consistent state in all cases.
        readFilterEnabled = false;
        writeFilterEnabled = false;
    }

    void unregisterFilters() throws Exception {
        // Make sure we unregister our filters from kqueue!
        readFilter(false);
        writeFilter(false);

        if (registration != null) {
            evSet0(registration, Native.EVFILT_SOCK, Native.EV_DELETE, 0);
            registration = null;
        }
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

    /**
     * Returns an off-heap copy of, and then closes, the given {@link Buffer}.
     */
    protected final Buffer newDirectBuffer(Buffer buf) {
        return newDirectBuffer(buf, buf);
    }

    /**
     * Returns an off-heap copy of the given {@link Buffer}, and then closes the {@code holder} under the assumption
     * that it owned (or was itself) the buffer.
     */
    protected final Buffer newDirectBuffer(Resource<?> holder, Buffer buf) {
        BufferAllocator allocator = bufferAllocator();
        if (allocator.getAllocationType() != StandardAllocationTypes.OFF_HEAP) {
            allocator = DefaultBufferAllocators.offHeapAllocator();
        }
        try (holder) {
            int readableBytes = buf.readableBytes();
            Buffer directCopy = allocator.allocate(readableBytes);
            if (readableBytes > 0) {
                directCopy.writeBytes(buf);
            }
            return directCopy;
        }
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

    /**
     * Read bytes into the given {@link Buffer} and return the amount.
     */
    protected final void doReadBytes(Buffer buffer) throws Exception {
        unsafe().recvBufAllocHandle().attemptedBytesRead(buffer.writableBytes());
        buffer.forEachWritable(0, (index, component) -> {
            long address = component.writableNativeAddress();
            assert address != 0;
            int localReadAmount = socket.readAddress(address, 0, component.writableBytes());
            unsafe().recvBufAllocHandle().lastBytesRead(localReadAmount);
            if (localReadAmount > 0) {
                component.skipWritable(localReadAmount);
            }
            return false;
        });
    }

    protected final int doWriteBytes(ChannelOutboundBuffer in, ByteBuf buf) throws Exception {
        if (buf.hasMemoryAddress()) {
            int localFlushedAmount = socket.writeAddress(buf.memoryAddress(), buf.readerIndex(), buf.writerIndex());
            if (localFlushedAmount > 0) {
                in.removeBytes(localFlushedAmount);
                return 1;
            }
        } else {
            final ByteBuffer nioBuf = buf.nioBufferCount() == 1 ?
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

    protected final int doWriteBytes(ChannelOutboundBuffer in, Buffer buf) throws Exception {
        int initialReadableBytes = buf.readableBytes();
        buf.forEachReadable(0, (index, component) -> {
            long address = component.readableNativeAddress();
            assert address != 0;
            int written = socket.writeAddress(address, 0, component.readableBytes());
            if (written > 0) {
                component.skipReadable(written);
            }
            return false;
        });
        int readableBytesLeft = buf.readableBytes();
        if (readableBytesLeft < initialReadableBytes) {
            int bytesWritten = initialReadableBytes - readableBytesLeft;
            buf.skipReadable(-bytesWritten); // Restore read offset for ChannelOutboundBuffer.
            in.removeBytes(bytesWritten);
            return 1; // Some data was written to the socket.
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
            final EventLoop loop = executor();
            final AbstractKQueueUnsafe unsafe = (AbstractKQueueUnsafe) unsafe();
            if (loop.inEventLoop()) {
                unsafe.clearReadFilter0();
            } else {
                // schedule a task to clear the EPOLLIN as it is not safe to modify it directly
                loop.execute(() -> {
                    if (!unsafe.readPending && !config().isAutoRead()) {
                        // Still no read triggered so clear it now
                        unsafe.clearReadFilter0();
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
            evSet0(registration, filter, flags);
        }
    }

    private void evSet0(KQueueRegistration registration, short filter, short flags) {
        evSet0(registration, filter, flags, 0);
    }

    private void evSet0(KQueueRegistration registration, short filter, short flags, int fflags) {
        // Only try to add to changeList if the FD is still open, if not we already closed it in the meantime.
        if (isOpen()) {
            registration.evSet(filter, flags, fflags);
        }
    }

    @UnstableApi
    public abstract class AbstractKQueueUnsafe extends AbstractUnsafe {
        boolean readPending;
        boolean maybeMoreDataToRead;
        private KQueueRecvBufferAllocatorHandle allocHandle;
        private final Runnable readReadyRunnable = new Runnable() {
            @Override
            public void run() {
                readReadyRunnablePending = false;
                readReady(recvBufAllocHandle());
            }
        };

        final void readReady(long numberBytesPending) {
            KQueueRecvBufferAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.numberBytesPending(numberBytesPending);
            readReady(allocHandle);
        }

        abstract void readReady(KQueueRecvBufferAllocatorHandle allocHandle);

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
                Promise<Void> connectPromise = AbstractKQueueChannel.this.connectPromise;
                AbstractKQueueChannel.this.connectPromise = null;
                if (connectPromise.tryFailure(cause instanceof ConnectException? cause
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
                    close(newPromise());
                }
            } else if (!readEOF) {
                inputClosedSeenErrorOnRead = true;
                pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            }
        }

        final void readEOF() {
            // This must happen before we attempt to read. This will ensure reading continues until an error occurs.
            final KQueueRecvBufferAllocatorHandle allocHandle = recvBufAllocHandle();
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
        public KQueueRecvBufferAllocatorHandle recvBufAllocHandle() {
            if (allocHandle == null) {
                allocHandle = new KQueueRecvBufferAllocatorHandle(super.recvBufAllocHandle());
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
            executor().execute(readReadyRunnable);
        }

        protected final void clearReadFilter0() {
            assert executor().inEventLoop();
            try {
                readPending = false;
                readFilter(false);
            } catch (IOException e) {
                // When this happens there is something completely wrong with either the filedescriptor or epoll,
                // so fire the exception through the pipeline and close the Channel.
                pipeline().fireExceptionCaught(e);
                unsafe().close(newPromise());
            }
        }

        private void fireEventAndClose(Object evt) {
            pipeline().fireUserEventTriggered(evt);
            close(newPromise());
        }

        @Override
        public void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, Promise<Void> promise) {
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
                        connectTimeoutFuture = executor().schedule(() -> {
                            Promise<Void> connectPromise = AbstractKQueueChannel.this.connectPromise;
                            if (connectPromise != null && !connectPromise.isDone()
                                    && connectPromise.tryFailure(new ConnectTimeoutException(
                                    "connection timed out: " + remoteAddress))) {
                                close(newPromise());
                            }
                        }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                    }

                    promise.asFuture().addListener(future -> {
                        if (future.isCancelled()) {
                            if (connectTimeoutFuture != null) {
                                connectTimeoutFuture.cancel();
                            }
                            connectPromise = null;
                            close(newPromise());
                        }
                    });
                }
            } catch (Throwable t) {
                closeIfClosed();
                promise.tryFailure(annotateConnectException(t, remoteAddress));
            }
        }

        private void fulfillConnectPromise(Promise<Void> promise, boolean wasActive) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }
            active = true;

            // Get the state as trySuccess() may trigger an ChannelFutureListeners that will close the Channel.
            // We still need to ensure we call fireChannelActive() in this case.
            boolean active = isActive();

            // trySuccess() will return false if a user cancelled the connection attempt.
            boolean promiseSet = promise.trySuccess(null);

            // Regardless if the connection attempt was cancelled, channelActive() event should be triggered,
            // because what happened is what happened.
            if (!wasActive && active) {
                pipeline().fireChannelActive();
                readIfIsAutoRead();
            }

            // If a user cancelled the connection attempt, close the channel, which is followed by channelInactive().
            if (!promiseSet) {
                close(newPromise());
            }
        }

        private void fulfillConnectPromise(Promise<Void> promise, Throwable cause) {
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

            assert executor().inEventLoop();

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
                        connectTimeoutFuture.cancel();
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
