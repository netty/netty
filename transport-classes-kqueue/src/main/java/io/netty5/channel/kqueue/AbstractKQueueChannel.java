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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.IntegerUnixChannelOption;
import io.netty5.channel.unix.RawUnixChannelOption;
import io.netty5.util.Resource;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.EventLoop;
import io.netty5.channel.unix.FileDescriptor;
import io.netty5.channel.unix.UnixChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.util.function.Predicate;

import static io.netty5.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;
import static io.netty5.channel.unix.Limits.SSIZE_MAX;
import static io.netty5.channel.unix.UnixChannelUtil.computeRemoteAddr;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

abstract class AbstractKQueueChannel<P extends UnixChannel>
        extends AbstractChannel<P, SocketAddress, SocketAddress> implements UnixChannel {

    final BsdSocket socket;

    protected volatile boolean active;

    protected boolean readPending;

    private long numberBytesPending;

    /**
     * kqueue with EV_CLEAR flag set requires that we read until we consume "data" bytes (see kqueue man:
     * https://www.freebsd.org/cgi/man.cgi?kqueue). However, in order to respect auto read we support reading to stop if
     * auto read is off. If auto read is on we force reading to continue to avoid a {@link StackOverflowError} between
     * channelReadComplete and reading from the channel. It is expected that the {@link KQueueSocketChannel}
     * implementations will track if all data was not read, and will force a EVFILT_READ ready event.
     * <p>
     * It is assumed EOF is handled externally by checking {@link #eof}.
     */
    private final Predicate<RecvBufferAllocator.Handle> maybeMoreData = h -> {
        if (h.lastBytesRead() > 0) {
            numberBytesPending -= h.lastBytesRead();
        }
        return numberBytesPending != 0;
    };

    private final Runnable readReadyRunnable = new Runnable() {
        @Override
        public void run() {
            readReadyRunnablePending = false;
            //RecvBufferAllocator.Handle allocHandle = recvBufAllocHandle();
            //allocHandle.reset();
            //allocHandle.attemptedBytesRead((int) Math.max(numberBytesPending, 128));
            readReady(numberBytesPending);
        }
    };

    private KQueueRegistration registration;

    private boolean readFilterEnabled;
    private boolean writeFilterEnabled;
    private boolean readReadyRunnablePending;
    private boolean inputClosedSeenErrorOnRead;

    private boolean maybeMoreDataToRead;

    private boolean eof;

    private volatile long maxBytesPerGatheringWrite = SSIZE_MAX;

    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;

    AbstractKQueueChannel(P parent, EventLoop eventLoop, boolean supportsDisconnect,
                          RecvBufferAllocator defaultRecvAllocator, BsdSocket fd, boolean active) {
        super(parent, eventLoop, supportsDisconnect, defaultRecvAllocator);
        socket = requireNonNull(fd, "fd");
        this.active = active;
        if (active) {
            // Directly cache the remote and local addresses
            // See https://github.com/netty/netty/issues/2359
            this.localAddress = fd.localAddress();
            this.remoteAddress = fd.remoteAddress();
        }
    }

    AbstractKQueueChannel(P parent, EventLoop eventLoop, boolean supportsDisconnect,
                          RecvBufferAllocator defaultRecvAllocator, BsdSocket fd, SocketAddress remote) {
        super(parent, eventLoop, supportsDisconnect, defaultRecvAllocator);
        socket = requireNonNull(fd, "fd");
        active = true;
        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        this.localAddress = fd.localAddress();
        this.remoteAddress = remote;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected  <T> T getExtendedOption(ChannelOption<T> option) {
        try {
            if (option instanceof IntegerUnixChannelOption) {
                IntegerUnixChannelOption opt = (IntegerUnixChannelOption) option;
                return (T) Integer.valueOf(socket.getIntOpt(opt.level(), opt.optname()));
            }
            if (option instanceof RawUnixChannelOption) {
                RawUnixChannelOption opt = (RawUnixChannelOption) option;
                ByteBuffer out = ByteBuffer.allocate(opt.length());
                socket.getRawOpt(opt.level(), opt.optname(), out);
                return (T) out.flip();
            }
        } catch (IOException e) {
            throw new ChannelException(e);
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        try {
            if (option instanceof IntegerUnixChannelOption) {
                IntegerUnixChannelOption opt = (IntegerUnixChannelOption) option;
                socket.setIntOpt(opt.level(), opt.optname(), (Integer) value);
                return;
            } else if (option instanceof RawUnixChannelOption) {
                RawUnixChannelOption opt = (RawUnixChannelOption) option;
                socket.setRawOpt(opt.level(), opt.optname(), (ByteBuffer) value);
                return;
            }
        } catch (IOException e) {
            throw new ChannelException(e);
        }
        super.setExtendedOption(option, value);
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (option instanceof IntegerUnixChannelOption || option instanceof RawUnixChannelOption) {
            return true;
        }
        return super.isExtendedOptionSupported(option);
    }

    protected void setMaxBytesPerGatheringWrite(long maxBytesPerGatheringWrite) {
        this.maxBytesPerGatheringWrite = min(SSIZE_MAX, maxBytesPerGatheringWrite);
    }

    protected long getMaxBytesPerGatheringWrite() {
        return maxBytesPerGatheringWrite;
    }

    @Override
    protected final void autoReadCleared() {
        clearReadFilter();
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

    @SuppressWarnings("unchecked")
    final void resetCachedAddresses() {
        cacheAddresses(localAddress, null);
        remoteAddress = null;
    }

    @Override
    public final boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    protected final void doRead() {
        // Channel.read() or ChannelHandlerContext.read() was called
        readPending = true;

        // We must set the read flag here as it is possible the user didn't read in the last read loop, the
        // executeReadReadyRunnable could read nothing, and if the user doesn't explicitly call read they will
        // never get data after this.
        readFilter(true);

        // If auto read was toggled off on the last read loop then we may not be notified
        // again if we didn't consume all the data. So we force a read operation here if there maybe more data or
        // eof was received
        if (maybeMoreDataToRead || eof) {
            executeReadReadyRunnable();
        }
    }

    final void register0(KQueueRegistration registration)  {
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

    final void deregister0() {
        // As unregisteredFilters() may have not been called because isOpen() returned false we just set both filters
        // to false to ensure a consistent state in all cases.
        readFilterEnabled = false;
        writeFilterEnabled = false;
    }

    final void unregisterFilters() throws Exception {
        // Make sure we unregister our filters from kqueue!
        readFilter(false);
        writeFilter(false);

        if (registration != null) {
            evSet0(registration, Native.EVFILT_SOCK, Native.EV_DELETE, 0);
            registration = null;
        }
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
        BufferAllocator allocator = ioBufferAllocator();
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
     * Read bytes into the given {@link Buffer} and return the amount.
     */
    protected final int doReadBytes(Buffer buffer) throws Exception {
        recvBufAllocHandle().attemptedBytesRead(buffer.writableBytes());
        try (var iterator = buffer.forEachWritable()) {
            var component = iterator.first();
            if (component == null) {
                recvBufAllocHandle().lastBytesRead(0);
                return 0;
            }
            long address = component.writableNativeAddress();
            assert address != 0;
            int localReadAmount = socket.readAddress(address, 0, component.writableBytes());
            recvBufAllocHandle().lastBytesRead(localReadAmount);
            if (localReadAmount > 0) {
                component.skipWritableBytes(localReadAmount);
            }
            return localReadAmount;
        }
    }

    protected final int doWriteBytes(ChannelOutboundBuffer in, Buffer buf) throws Exception {
        int initialReaderOffset = buf.readerOffset();
        buf.forEachReadable(0, (index, component) -> {
            long address = component.readableNativeAddress();
            assert address != 0;
            int written = socket.writeAddress(address, 0, component.readableBytes());
            if (written > 0) {
                component.skipReadableBytes(written);
            }
            return false;
        });
        int readerOffset = buf.readerOffset();
        if (initialReaderOffset < readerOffset) {
            buf.readerOffset(initialReaderOffset); // Restore read offset for ChannelOutboundBuffer.
            int bytesWritten = readerOffset - initialReaderOffset;
            in.removeBytes(bytesWritten);
            return 1; // Some data was written to the socket.
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    final boolean shouldBreakReadReady() {
        return socket.isInputShutdown() && (inputClosedSeenErrorOnRead || !isAllowHalfClosure());
    }

    private void clearReadFilter() {
        // Only clear if registered with an EventLoop as otherwise
        if (isRegistered()) {
            final EventLoop loop = executor();
            if (loop.inEventLoop()) {
                clearReadFilter0();
            } else {
                // schedule a task to clear the EPOLLIN as it is not safe to modify it directly
                loop.execute(() -> {
                    if (readPending && !isAutoRead()) {
                        // Still no read triggered so clear it now
                        clearReadFilter0();
                    }
                });
            }
        } else  {
            // The EventLoop is not registered atm so just update the flags so the correct value
            // will be used once the channel is registered
            readFilterEnabled = false;
        }
    }

    final void readFilter(boolean readFilterEnabled) {
        if (this.readFilterEnabled != readFilterEnabled) {
            this.readFilterEnabled = readFilterEnabled;
            evSet(Native.EVFILT_READ, readFilterEnabled ? Native.EV_ADD_CLEAR_ENABLE : Native.EV_DELETE_DISABLE);
        }
    }

    final void writeFilter(boolean writeFilterEnabled) {
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

    final void readReady(long numberBytesPending) {
        RecvBufferAllocator.Handle allocHandle = recvBufAllocHandle();
        allocHandle.reset();
        this.numberBytesPending = numberBytesPending;
        allocHandle.attemptedBytesRead((int) Math.min(numberBytesPending, Integer.MAX_VALUE));

        assert executor().inEventLoop();
        if (shouldBreakReadReady()) {
            clearReadFilter0();
            return;
        }
        maybeMoreDataToRead = false;

        try {
            readReady(allocHandle, ioBufferAllocator(), maybeMoreData);
        } finally {
            maybeMoreDataToRead = this.numberBytesPending != 0;

            if (eof || readPending && maybeMoreDataToRead) {
                // trigger a read again as there may be something left to read and because of ET we
                // will not get notified again until we read everything from the socket
                //
                // It is possible the last fireChannelRead call could cause the user to call read() again, or if
                // autoRead is true the call to channelReadComplete would also call read, but maybeMoreDataToRead is set
                // to false before every read operation to prevent re-entry into readReady() we will not read from
                // the underlying OS again unless the user happens to call read again.
                executeReadReadyRunnable();
            } else if (!readPending && !isAutoRead()) {
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                clearReadFilter0();
            }
        }
    }

    abstract void readReady(RecvBufferAllocator.Handle allocHandle, BufferAllocator recvBufferAllocator,
                            Predicate<RecvBufferAllocator.Handle> maybeMoreData);

    final void writeReady() {
        if (isConnectPending()) {
            // pending connect which is now complete so handle it.
            finishConnect();
        } else if (!socket.isOutputShutdown()) {
            // directly call super.flush0() to force a flush now
            super.writeFlushed();
        }
    }

    /**
     * Shutdown the input side of the channel.
     */
    final void shutdownInput(boolean readEOF) {
        // We need to take special care of calling finishConnect() if readEOF is true and we not
        // fullfilled the connectPromise yet. If we fail to do so the connectPromise will be failed
        // with a ClosedChannelException as a close() will happen and so the FD is closed before we
        // have a chance to call finishConnect() later on. Calling finishConnect() here will ensure
        // we observe the correct exception in case of a connect failure.
        if (readEOF && isConnectPending()) {
            finishConnect();
        }
        if (!socket.isInputShutdown()) {
            if (isAllowHalfClosure()) {
                shutdownTransport(ChannelShutdownDirection.Inbound, newPromise());
            } else {
                closeTransport(newPromise());
            }
        } else if (!readEOF) {
            inputClosedSeenErrorOnRead = true;
        }
    }

    final void readEOF() {
        // This must happen before we attempt to read. This will ensure reading continues until an error occurs.
        final RecvBufferAllocator.Handle allocHandle = recvBufAllocHandle();
        eof = true;

        if (isActive()) {
            // If it is still active, we need to call read() as otherwise we may miss to
            // read pending data from the underlying file descriptor.
            // See https://github.com/netty/netty/issues/3709
            read();
        } else {
            // Just to be safe make sure the input marked as closed.
            shutdownInput(true);
        }
    }

    @Override
    protected final void writeFlushed() {
        // Flush immediately only when there's no pending flush.
        // If there's a pending flush operation, event loop will call forceFlush() later,
        // and thus there's no need to call it now.
        if (!writeFilterEnabled) {
            super.writeFlushed();
        }
    }

    private void executeReadReadyRunnable() {
        if (readReadyRunnablePending || !isActive() || shouldBreakReadReady()) {
            return;
        }
        readReadyRunnablePending = true;
        executor().execute(readReadyRunnable);
    }

    private void clearReadFilter0() {
        assert executor().inEventLoop();
        try {
            readPending = false;
            readFilter(false);
        } catch (Throwable e) {
            // When this happens there is something completely wrong with either the filedescriptor or epoll,
            // so fire the exception through the pipeline and close the Channel.
            pipeline().fireChannelExceptionCaught(e);
            closeTransport(newPromise());
        }
    }

    @Override
    protected final boolean doFinishConnect(SocketAddress requestedRemoteAddress) throws Exception {
        if (socket.finishConnect()) {
            active = true;
            writeFilter(false);
            if (requestedRemoteAddress instanceof InetSocketAddress) {
                remoteAddress = computeRemoteAddr((InetSocketAddress) requestedRemoteAddress, socket.remoteAddress());
            } else {
                remoteAddress = requestedRemoteAddress;
            }
            return true;
        }
        writeFilter(true);
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doBind(SocketAddress local) throws Exception {
        if (local instanceof InetSocketAddress) {
            checkResolvable((InetSocketAddress) local);
        }
        socket.bind(local);
        if (fetchLocalAddress()) {
            this.localAddress = socket.localAddress();
        } else {
            this.localAddress = local;
        }
    }

    protected boolean fetchLocalAddress() {
        return socket.protocolFamily() != SocketProtocolFamily.UNIX;
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

        if (localAddress != null) {
            doBind(localAddress);
        }

        boolean connected = doConnect0(remoteAddress, localAddress);
        if (connected) {
            active = true;
            this.remoteAddress = remoteSocketAddr == null?
                    remoteAddress : computeRemoteAddr(remoteSocketAddr, socket.remoteAddress());
        }

        if (fetchLocalAddress()) {
            // We always need to set the localAddress even if not connected yet as the bind already took place.
            //
            // See https://github.com/netty/netty/issues/3463
            this.localAddress = socket.localAddress();
        }
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
        return localAddress;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remoteAddress;
    }

    final void closeTransportNow() {
        closeTransport(newPromise());
    }

    private BufferAllocator ioBufferAllocator() {
        BufferAllocator alloc = bufferAllocator();
        // We need to ensure we always allocate a direct Buffer as we can only use a direct buffer to read via JNI.
        if (!alloc.getAllocationType().isDirect()) {
            return DefaultBufferAllocators.offHeapAllocator();
        }
        return alloc;
    }
}
