/*
 * Copyright 2014 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.IoEvent;
import io.netty5.channel.IoHandle;
import io.netty5.channel.IoRegistration;
import io.netty5.channel.ReadHandleFactory;
import io.netty5.channel.WriteHandleFactory;
import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.IntegerUnixChannelOption;
import io.netty5.channel.unix.RawUnixChannelOption;
import io.netty5.util.Resource;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.EventLoop;
import io.netty5.channel.unix.FileDescriptor;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.Socket;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.util.concurrent.Future;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;

import static io.netty5.channel.unix.UnixChannelUtil.computeRemoteAddr;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

abstract class AbstractEpollChannel<P extends UnixChannel>
        extends AbstractChannel<P, SocketAddress, SocketAddress> implements UnixChannel {
    protected final LinuxSocket socket;

    private final Runnable readNowRunnable = new Runnable() {
        @Override
        public void run() {
            readNowRunnablePending = false;
            readNow();
        }
    };

    private final EpollIoHandle handle = new EpollIoHandle() {
        @Override
        public FileDescriptor fd() {
            return AbstractEpollChannel.this.fd();
        }

        @Override
        public void handle(IoRegistration registration, IoEvent event) {
            EpollIoEvent epollEvent = (EpollIoEvent) event;
            EpollIoOps epollOps = epollEvent.ops();

            // Don't change the ordering of processing EPOLLOUT | EPOLLRDHUP / EPOLLIN if you're not 100%
            // sure about it!
            // Re-ordering can easily introduce bugs and bad side-effects, as we found out painfully in the
            // past.

            // First check for EPOLLOUT as we may need to fail the connect ChannelPromise before try
            // to read from the file descriptor.
            // See https://github.com/netty/netty/issues/3785
            //
            // It is possible for an EPOLLOUT or EPOLLERR to be generated when a connection is refused.
            // In either case epollOutReady() will do the correct thing (finish connecting, or fail
            // the connection).
            // See https://github.com/netty/netty/issues/3848
            if (epollOps.contains(EpollIoOps.EPOLLERR) || epollOps.contains(EpollIoOps.EPOLLOUT)) {
                // Force flush of data as the epoll is writable again
                epollOutReady();
            }

            // Check EPOLLIN before EPOLLRDHUP to ensure all data is read before shutting down the input.
            // See https://github.com/netty/netty/issues/4317.
            //
            // If EPOLLIN or EPOLLERR was received and the channel is still open call epollInReady(). This will
            // try to read from the underlying file descriptor and so notify the user about the error.
            if (epollOps.contains(EpollIoOps.EPOLLERR) || epollOps.contains(EpollIoOps.EPOLLIN)) {
                // The Channel is still open and there is something to read. Do it now.
                epollInReady();
            }

            // Check if EPOLLRDHUP was set, this will notify us for connection-reset in which case
            // we may close the channel directly or try to read more data depending on the state of the
            // Channel and als depending on the AbstractEpollChannel subtype.
            if (epollOps.contains(EpollIoOps.EPOLLRDHUP)) {
                epollRdHupReady();
            }
        }

        @Override
        public void close() {
            closeTransportNow();
        }
    };

    protected volatile boolean active;
    private boolean readNowRunnablePending;
    private boolean maybeMoreDataToRead;
    private EpollIoOps ops;

    private boolean receivedRdHup;
    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;

    AbstractEpollChannel(EventLoop eventLoop, boolean supportingDisconnect, EpollIoOps initialOps,
                         ReadHandleFactory defaultReadHandleFactory, WriteHandleFactory defaultWriteHandleFactory,
                         LinuxSocket fd) {
        this(null, eventLoop, supportingDisconnect, initialOps, defaultReadHandleFactory, defaultWriteHandleFactory,
                fd, false);
    }

    AbstractEpollChannel(P parent, EventLoop eventLoop, boolean supportingDisconnect, EpollIoOps initialOps,
                         ReadHandleFactory defaultReadHandleFactory, WriteHandleFactory defaultWriteHandleFactory,
                         LinuxSocket fd, boolean active) {
        super(parent, eventLoop, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory,
                EpollIoHandle.class);
        this.ops = initialOps;
        socket = requireNonNull(fd, "fd");
        this.active = active;
        if (active) {
            // Directly cache the remote and local addresses
            // See https://github.com/netty/netty/issues/2359
            localAddress = fd.localAddress();
            remoteAddress = fd.remoteAddress();
        }
    }

    AbstractEpollChannel(P parent, EventLoop eventLoop, boolean supportingDisconnect, EpollIoOps initialOps,
                         ReadHandleFactory defaultReadHandleFactory, WriteHandleFactory defaultWriteHandleFactory,
                         LinuxSocket fd, SocketAddress remote) {
        super(parent, eventLoop, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory,
                EpollIoHandle.class);
        this.ops = initialOps;
        socket = requireNonNull(fd, "fd");
        active = true;
        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        remoteAddress =  remote == null ? fd.remoteAddress() : remote;
        localAddress = fd.localAddress();
    }

    @Override
    protected IoHandle ioHandle() {
        return handle;
    }

    protected static boolean isSoErrorZero(Socket fd) {
        try {
            return fd.getSoError() == 0;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    protected void setFlag(int flag) throws IOException {
        ops = ops.with(EpollIoOps.valueOf(flag));
        if (isRegistered() && isOpen()) {
            EpollIoRegistration registration = registration();
            try {
                registration.submit(ops);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {
            ops = ops.with(EpollIoOps.valueOf(flag));
        }
    }

    void clearFlag(int flag) throws IOException {
        ops = ops.without(EpollIoOps.valueOf(flag));
        if (isRegistered() && isOpen()) {
            EpollIoRegistration registration = registration();
            try {
                registration.submit(ops);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    protected final EpollIoRegistration registration() {
        return (EpollIoRegistration) super.registration();
    }

    @Override
    public Future<Void> register() {
        return super.register().addListener(f -> {
            if (f.isSuccess() && isOpen()) {
                registration().submit(ops);
            }
        });
    }

    boolean isFlagSet(int flag) {
        return (ops.value & flag) != 0;
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

    final void resetCachedAddresses() {
        cacheAddresses(localAddress, null);
        remoteAddress = null;
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    public final boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    protected final void doRead(boolean wasReadPendingAlready) throws Exception {
        if (!wasReadPendingAlready) {
            // We must set the read flag here as it is possible the user didn't read in the last read loop, the
            // executeEpollInReadyRunnable could read nothing, and if the user doesn't explicitly call read they will
            // never get data after this.
            setFlag(Native.EPOLLIN);
        }

        // If EPOLL ET mode is enabled and auto read was toggled off on the last read loop then we may not be notified
        // again if we didn't consume all the data. So we force a read operation here if there maybe more data or
        // RDHUP was received.
        if (maybeMoreDataToRead || receivedRdHup) {
            executeReadNowRunnable();
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
     * Read bytes into the given {@link Buffer} and return the amount. This method does not modify the offset of the
     * buffer.
     */
    protected final int doReadBytes(Buffer buffer) throws Exception {
        try (var iterator = buffer.forEachComponent()) {
            var component = iterator.firstWritable();
            if (component == null) {
                return 0;
            }
            long address = component.writableNativeAddress();
            assert address != 0;
            return socket.recvAddress(address, 0, component.writableBytes());
        }
    }

    protected final int doWriteBytes(Buffer buf) throws Exception {
        int written = 0;
        try (var iterator = buf.forEachComponent()) {
            var component = iterator.firstReadable();
            if (component != null) {
                long address = component.readableNativeAddress();
                assert address != 0;
                written = socket.sendAddress(address, 0, component.readableBytes());
            }
        }
        return written;
    }

    /**
     * Write bytes to the socket, with or without a remote address.
     * Used for datagram and TCP client fast open writes.
     */
    protected final long doWriteOrSendBytes(Buffer data, SocketAddress remoteAddress, boolean fastOpen)
            throws IOException {
        assert !(fastOpen && remoteAddress == null) : "fastOpen requires a remote address";

        IovArray array = registration().ioHandler().cleanIovArray();
        array.addReadable(data);
        int count = array.count();
        assert count != 0;
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
             return socket.sendToAddressesDomainSocket(
                    array.memoryAddress(0), count, ((DomainSocketAddress) remoteAddress)
                            .path().getBytes(UTF_8));
        } else {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
            return socket.sendToAddresses(array.memoryAddress(0), count,
                    inetSocketAddress.getAddress(), inetSocketAddress.getPort(), fastOpen);
        }
    }

    final void epollInReady() {
        readNow();
    }

    @Override
    protected boolean doReadNow(ReadSink readSink)
            throws Exception {
        maybeMoreDataToRead = false;
        ReadState readState = null;
        try {
            readState = epollInReady(readSink);
            return readState == ReadState.Closed;
        } finally {
            this.maybeMoreDataToRead = readState == ReadState.Partial || receivedRdHup;
        }
    }

    @Override
    protected void readLoopComplete() {
        super.readLoopComplete();

        if (receivedRdHup || isReadPending() && maybeMoreDataToRead) {
            // trigger a read again as there may be something left to read and because of epoll ET we
            // will not get notified again until we read everything from the socket
            //
            // It is possible the last fireChannelRead call could cause the user to call read() again, or if
            // autoRead is true the call to channelReadComplete would also call read, but maybeMoreDataToRead is set
            // to false before every read operation to prevent re-entry into epollInReady() we will not read from
            // the underlying OS again unless the user happens to call read again.
            executeReadNowRunnable();
        }
    }

    enum ReadState {
        All,
        Partial,
        Closed
    }

    /**
     * Called once EPOLLIN event is ready to be processed
     */
    protected abstract ReadState epollInReady(ReadSink readSink) throws Exception;

    private void executeReadNowRunnable() {
        if (readNowRunnablePending || !isActive()) {
            return;
        }
        readNowRunnablePending = true;
        executor().execute(readNowRunnable);
    }

    /**
     * Called once EPOLLRDHUP event is ready to be processed
     */
    final void epollRdHupReady() {
        // This must happen before we attempt to read. This will ensure reading continues until an error occurs.
        receivedRdHup = true;

        // Clear the EPOLLRDHUP flag to prevent continuously getting woken up on this event.
        clearEpollRdHup();

        if (isActive()) {
            // If it is still active, we need to call read() as otherwise we may miss to
            // read pending data from the underlying file descriptor.
            // See https://github.com/netty/netty/issues/3709
            read();
        } else {
            // Just to be safe make sure the input marked as closed.
            shutdownReadSide();
        }
    }

    /**
     * Clear the {@link Native#EPOLLRDHUP} flag from EPOLL, and close on failure.
     */
    private void clearEpollRdHup() {
        try {
            clearFlag(Native.EPOLLRDHUP);
        } catch (IOException e) {
            pipeline().fireChannelExceptionCaught(e);
            closeTransport(newPromise());
        }
    }

    @Override
    protected boolean isWriteFlushedScheduled() {
        // Flush immediately only when there's no pending flush.
        return isFlagSet(Native.EPOLLOUT);
    }

    /**
     * Called once a EPOLLOUT event is ready to be processed
     */
    final void epollOutReady() {
        if (isConnectPending()) {
            // pending connect which is now complete so handle it.
            finishConnect();
        } else if (!socket.isOutputShutdown()) {
            // directly call writeFlushedNow() to force a flush now
            writeFlushedNow();
        }
    }

    @Override
    protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) throws Exception {
        if (socket.finishConnect()) {
            active = true;
            clearFlag(Native.EPOLLOUT);
            if (requestedRemoteAddress instanceof InetSocketAddress) {
                remoteAddress = computeRemoteAddr((InetSocketAddress) requestedRemoteAddress,
                        (InetSocketAddress) socket.remoteAddress());
            } else {
                remoteAddress = requestedRemoteAddress;
            }
            return true;
        }
        setFlag(Native.EPOLLOUT);
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
            socket.bind(localAddress);
        }

        boolean connected = doConnect0(remoteAddress, initialData);
        if (connected) {
            this.remoteAddress = remoteSocketAddr == null ?
                    remoteAddress : computeRemoteAddr(remoteSocketAddr, (InetSocketAddress) socket.remoteAddress());
            active = true;
        }
        // We always need to set the localAddress even if not connected yet as the bind already took place.
        //
        // See https://github.com/netty/netty/issues/3463
        this.localAddress = socket.localAddress();
        return connected;
    }

    protected boolean doConnect0(SocketAddress remote, Buffer initialData) throws Exception {
        boolean success = false;
        try {
            boolean connected = socket.connect(remote);
            if (!connected) {
                setFlag(Native.EPOLLOUT);
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
    protected final SocketAddress localAddress0() {
        return localAddress;
    }

    @Override
    protected final SocketAddress remoteAddress0() {
        return remoteAddress;
    }

    final void closeTransportNow() {
        closeTransport(newPromise());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
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

    @Override
    protected final void doClearScheduledRead() {
        assert executor().inEventLoop();
        try {
            clearFlag(Native.EPOLLIN);
        } catch (IOException e) {
            // When this happens there is something completely wrong with either the filedescriptor or epoll,
            // so fire the exception through the pipeline and close the Channel.
            pipeline().fireChannelExceptionCaught(e);
            closeTransport(newPromise());
        }
    }

    @Override
    protected void writeLoopComplete(boolean allWritten) {
        try {
            if (allWritten) {
                clearFlag(Native.EPOLLOUT);
            } else {
                setFlag(Native.EPOLLOUT);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error while trying to update flags", e);
        }
        super.writeLoopComplete(allWritten);
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
