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

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.IoEvent;
import io.netty5.channel.IoHandle;
import io.netty5.channel.IoRegistration;
import io.netty5.channel.ReadHandleFactory;
import io.netty5.channel.WriteHandleFactory;
import io.netty5.channel.kqueue.KQueueReadHandleFactory.KQueueReadHandle;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.IntegerUnixChannelOption;
import io.netty5.channel.unix.RawUnixChannelOption;
import io.netty5.util.Resource;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.EventLoop;
import io.netty5.channel.unix.FileDescriptor;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.util.concurrent.Future;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;

import static io.netty5.channel.unix.UnixChannelUtil.computeRemoteAddr;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

abstract class AbstractKQueueChannel<P extends UnixChannel>
        extends AbstractChannel<P, SocketAddress, SocketAddress> implements UnixChannel {

    final BsdSocket socket;

    protected volatile boolean active;

    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;

    private final Runnable readNowRunnable = new Runnable() {
        @Override
        public void run() {
            readNowRunnablePending = false;
            readNow();
        }
    };

    private final KQueueIoHandle handle = new KQueueIoHandle() {
        @Override
        public int ident() {
            return AbstractKQueueChannel.this.fd().intValue();
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
                readReady(data);
            } else if (filter == Native.EVFILT_SOCK && (fflags & Native.NOTE_RDHUP) != 0) {
                readEOF();
            }

            // Check if EV_EOF was set, this will notify us for connection-reset in which case
            // we may close the channel directly or try to read more data depending on the state of the
            // Channel and also depending on the AbstractKQueueChannel subtype.
            if ((flags & Native.EV_EOF) != 0) {
                readEOF();
            }
        }

        @Override
        public void close() throws Exception {
            closeTransportNow();
        }
    };

    private final KQueueIoOps readEnabledOps;
    private final KQueueIoOps writeEnabledOps;
    private final KQueueIoOps readDisabledOps;
    private final KQueueIoOps writeDisabledOps;

    private long numberBytesPending;

    private boolean readFilterEnabled;
    private boolean writeFilterEnabled;
    private boolean readNowRunnablePending;
    private boolean maybeMoreDataToRead;

    private boolean eof;

    AbstractKQueueChannel(P parent, EventLoop eventLoop, boolean supportsDisconnect,
                          ReadHandleFactory defaultReadHandleFactory, WriteHandleFactory defaultWriteHandleFactory,
                          BsdSocket fd, boolean active) {
        super(parent, eventLoop, supportsDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory,
                KQueueIoHandle.class);
        socket = requireNonNull(fd, "fd");
        this.active = active;
        if (active) {
            // Directly cache the remote and local addresses
            // See https://github.com/netty/netty/issues/2359
            this.localAddress = fd.localAddress();
            this.remoteAddress = fd.remoteAddress();
        }

        readEnabledOps = KQueueIoOps.newOps(fd().intValue(), Native.EVFILT_READ, Native.EV_ADD_CLEAR_ENABLE, 0);
        writeEnabledOps = KQueueIoOps.newOps(fd().intValue(), Native.EVFILT_WRITE, Native.EV_ADD_CLEAR_ENABLE, 0);
        readDisabledOps = KQueueIoOps.newOps(fd().intValue(), Native.EVFILT_READ, Native.EV_DELETE_DISABLE, 0);
        writeDisabledOps = KQueueIoOps.newOps(fd().intValue(), Native.EVFILT_WRITE, Native.EV_DELETE_DISABLE, 0);
    }

    AbstractKQueueChannel(P parent, EventLoop eventLoop, boolean supportsDisconnect,
                          ReadHandleFactory defaultReadHandleFactory, WriteHandleFactory defaultWriteHandleFactory,
                          BsdSocket fd, SocketAddress remote) {
        super(parent, eventLoop, supportsDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory,
                KQueueIoHandle.class);
        socket = requireNonNull(fd, "fd");
        active = true;
        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        this.localAddress = fd.localAddress();
        this.remoteAddress = remote == null ? fd.remoteAddress() : remote;

        readEnabledOps = KQueueIoOps.newOps(fd().intValue(), Native.EVFILT_READ, Native.EV_ADD_CLEAR_ENABLE, 0);
        writeEnabledOps = KQueueIoOps.newOps(fd().intValue(), Native.EVFILT_WRITE, Native.EV_ADD_CLEAR_ENABLE, 0);
        readDisabledOps = KQueueIoOps.newOps(fd().intValue(), Native.EVFILT_READ, Native.EV_DELETE_DISABLE, 0);
        writeDisabledOps = KQueueIoOps.newOps(fd().intValue(), Native.EVFILT_WRITE, Native.EV_DELETE_DISABLE, 0);
    }

    @Override
    protected IoHandle ioHandle() {
        return handle;
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

    static boolean isSoErrorZero(BsdSocket fd) {
        try {
            return fd.getSoError() == 0;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    protected final KQueueIoRegistration registration() {
        return (KQueueIoRegistration) super.registration();
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
        socket.close();
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    final void resetCachedAddresses() {
        cacheAddresses(localAddress, null);
        remoteAddress = null;
    }

    @Override
    public final boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    public Future<Void> register() {
        return super.register().addListener(f -> {
            if (f.isSuccess()) {
                // Just in case the previous EventLoop was shutdown abruptly, or an event is still pending on the old
                // EventLoop make sure the readReadyRunnablePending variable is reset so we will be able to execute
                // the Runnable on the new EventLoop.
                readNowRunnablePending = false;

                int ident = fd().intValue();
                submit(KQueueIoOps.newOps(
                        ident, Native.EVFILT_SOCK, Native.EV_ADD, Native.NOTE_RDHUP));

                // Add the write event first so we get notified of connection refused on the client side!
                if (writeFilterEnabled) {
                    submit(writeEnabledOps);
                }
                if (readFilterEnabled) {
                    submit(readEnabledOps);
                }
            }
        });
    }

    @Override
    protected void doDeregister() throws Exception {
        // As unregisteredFilters() may have not been called because isOpen() returned false we just set both filters
        // to false to ensure a consistent state in all cases.
        // Make sure we unregister our filters from kqueue!
        readFilter(false);
        writeFilter(false);
        clearRdHup0();

        super.doDeregister();
    }

    @Override
    protected final void doRead(boolean wasReadPendingAlready) {
        if (!wasReadPendingAlready) {
            // We must set the read flag here as it is possible the user didn't read in the last read loop, the
            // executeReadReadyRunnable could read nothing, and if the user doesn't explicitly call read they will
            // never get data after this.
            readFilter(true);
        }

        // If auto read was toggled off on the last read loop then we may not be notified
        // again if we didn't consume all the data. So we force a read operation here if there may be more data or
        // eof was received.
        if (maybeMoreDataToRead || eof) {
            executeReadNowRunnable();
        }
    }

    private void clearRdHup0() {
        submit(KQueueIoOps.newOps(fd().intValue(),
                Native.EVFILT_SOCK, Native.EV_DELETE_DISABLE, Native.NOTE_RDHUP));
    }

    private void submit(KQueueIoOps ops) {
        if (isRegistered()) {
            try {
                registration().submit(ops);
            } catch (Exception e) {
                throw new ChannelException(e);
            }
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
        try (var iterator = buffer.forEachComponent()) {
            var component = iterator.firstWritable();
            if (component == null) {
                return 0;
            }
            long address = component.writableNativeAddress();
            assert address != 0;
            return socket.readAddress(address, 0, component.writableBytes());
        }
    }

    protected final int doWriteBytes(Buffer buf) throws Exception {
        int written = 0;
        try (var iteration = buf.forEachComponent()) {
            var component = iteration.firstReadable();
            if (component != null) {
                long address = component.readableNativeAddress();
                assert address != 0;
                written = socket.writeAddress(address, 0, component.readableBytes());
            }
        }
        return written;
    }

    final void readFilter(boolean readFilterEnabled) {
        if (this.readFilterEnabled != readFilterEnabled) {
            this.readFilterEnabled = readFilterEnabled;
            submit(readFilterEnabled ? readEnabledOps : readDisabledOps);
        }
    }

    final void writeFilter(boolean writeFilterEnabled) {
        if (this.writeFilterEnabled != writeFilterEnabled) {
            this.writeFilterEnabled = writeFilterEnabled;
            submit(writeFilterEnabled ? writeEnabledOps : writeDisabledOps);
        }
    }

    final void readReady(long numberBytesPending) {
        ReadHandleFactory.ReadHandle readHandle = readHandle();
        if (readHandle instanceof KQueueReadHandle) {
            ((KQueueReadHandle) readHandle)
                    .bufferCapacity(min(128, (int) min(numberBytesPending, 8 * 1024 * 1024)));
        }
        this.numberBytesPending = numberBytesPending;
        readNow();
    }

    @Override
    protected boolean doReadNow(ReadSink readSink)
            throws Exception {
        maybeMoreDataToRead = false;

        int readBytes = readReady(readSink);
        if (readBytes > 0) {
            this.numberBytesPending -= readBytes;
        } else if (readBytes == -1) {
            this.numberBytesPending = 0;
            // Inbound should be shutdown.
            return true;
        }
        return false;
    }

    @Override
    protected void readLoopComplete() {
        super.readLoopComplete();
        maybeMoreDataToRead = this.numberBytesPending != 0;

        if (eof || isReadPending() && maybeMoreDataToRead) {
            // trigger a read again as there may be something left to read and because of ET we
            // will not get notified again until we read everything from the socket
            //
            // It is possible the last fireChannelRead call could cause the user to call read() again, or if
            // autoRead is true the call to channelReadComplete would also call read, but maybeMoreDataToRead is set
            // to false before every read operation to prevent re-entry into readReady() we will not read from
            // the underlying OS again unless the user happens to call read again.
            executeReadNowRunnable();
        }
    }

    abstract int readReady(ReadSink readSink) throws Exception;

    final void writeReady() {
        if (isConnectPending()) {
            // pending connect which is now complete so handle it.
            finishConnect();
        } else if (!socket.isOutputShutdown()) {
            // directly call writeFlushedNow() to force a flush now
            writeFlushedNow();
        }
    }

    final void readEOF() {
        // This must happen before we attempt to read. This will ensure reading continues until an error occurs.
        eof = true;

        // Clear the RDHUP flag to prevent continuously getting woken up on this event.
        clearRdHup0();

        if (isActive()) {
            // If it is still active, we need to call read() as otherwise we may miss to
            // read pending data from the underlying file descriptor.
            // See https://github.com/netty/netty/issues/3709
            read();
        } else {
            // We need to take special care of calling finishConnect() if readEOF is true and we not
            // fullfilled the connectPromise yet. If we fail to do so the connectPromise will be failed
            // with a ClosedChannelException as a close() will happen and so the FD is closed before we
            // have a chance to call finishConnect() later on. Calling finishConnect() here will ensure
            // we observe the correct exception in case of a connect failure.
            if (isConnectPending()) {
                finishConnect();
            }
            // Just to be safe make sure the input marked as closed.
            shutdownReadSide();
        }
    }

    @Override
    protected boolean isWriteFlushedScheduled() {
        // Flush immediately only when there's no pending flush.
        return writeFilterEnabled;
    }

    private void executeReadNowRunnable() {
        if (readNowRunnablePending || !isActive()) {
            return;
        }
        readNowRunnablePending = true;
        executor().execute(readNowRunnable);
    }

    @Override
    protected final boolean doFinishConnect(SocketAddress requestedRemoteAddress) throws Exception {
        if (socket.finishConnect()) {
            active = true;
            writeFilter(false);
            if (requestedRemoteAddress instanceof InetSocketAddress) {
                remoteAddress = computeRemoteAddr((InetSocketAddress) requestedRemoteAddress,
                        (InetSocketAddress) socket.remoteAddress());
            } else {
                remoteAddress = requestedRemoteAddress;
            }
            return true;
        }
        writeFilter(true);
        return false;
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        if (local instanceof InetSocketAddress) {
            checkResolvable((InetSocketAddress) local);
        }
        socket.bind(local);
        if (socket.protocolFamily() != SocketProtocolFamily.UNIX) {
            this.localAddress = socket.localAddress();
        } else {
            // getsockname(...) is not widely supported for UDS.
            // See https://man.freebsd.org/cgi/man.cgi?query=getsockname&sektion=2&n=1
            this.localAddress = local;
        }
    }

    /**
     * Connect to the remote peer
     */
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData)
            throws Exception {
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

        boolean connected = doConnect0(remoteAddress, localAddress, initialData);
        if (connected) {
            active = true;
            this.remoteAddress = remoteSocketAddr == null?
                    remoteAddress : computeRemoteAddr(remoteSocketAddr, (InetSocketAddress) socket.remoteAddress());
        }

        // We always need to set the localAddress even if not connected yet as the bind already took place.
        //
        // See https://github.com/netty/netty/issues/3463
        this.localAddress = socket.localAddress();
        return connected;
    }

    protected boolean doConnect0(SocketAddress remoteAddress, SocketAddress localAddress, Buffer data)
            throws Exception {
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

    @Override
    protected final void doClearScheduledRead() {
        readFilter(false);
    }

    @Override
    protected final void writeLoopComplete(boolean allWritten) {
        writeFilter(!allWritten);
        super.writeLoopComplete(allWritten);
    }

    final void closeTransportNow() {
        closeTransport(newPromise());
    }

    @Override
    protected BufferAllocator readBufferAllocator() {
        return ioBufferAllocator(super.readBufferAllocator());
    }

    private BufferAllocator ioBufferAllocator() {
        return ioBufferAllocator(bufferAllocator());
    }

    private static BufferAllocator ioBufferAllocator(BufferAllocator alloc) {
        // We need to ensure we always allocate a direct Buffer as we can only use a direct buffer to read via JNI.
        if (!alloc.getAllocationType().isDirect()) {
            return DefaultBufferAllocators.offHeapAllocator();
        }
        return alloc;
    }
}
