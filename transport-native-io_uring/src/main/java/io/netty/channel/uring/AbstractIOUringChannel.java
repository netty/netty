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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.NativeInetAddress;
import io.netty.channel.unix.Socket;
import io.netty.channel.unix.UnixChannel;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static io.netty.channel.unix.Errors.*;
import static io.netty.channel.unix.UnixChannelUtil.*;
import static io.netty.util.internal.ObjectUtil.*;

abstract class AbstractIOUringChannel extends AbstractChannel implements UnixChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractIOUringChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    final LinuxSocket socket;
    protected volatile boolean active;
    boolean uringInReadyPending;
    boolean inputClosedSeenErrorOnRead;

    static final int SOCK_ADDR_LEN = 128;

    //can only submit one write operation at a time
    private boolean writeable = true;
    /**
     * The future of the current connection attempt.  If not null, subsequent connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;

    private final ByteBuffer remoteAddressMemory;
    private final long remoteAddressMemoryAddress;

    private volatile SocketAddress local;
    private volatile SocketAddress remote;

    AbstractIOUringChannel(final Channel parent, LinuxSocket socket) {
        super(parent);
        this.socket = checkNotNull(socket, "fd");
        this.active = true;
        this.uringInReadyPending = false;

        if (active) {
            // Directly cache the remote and local addresses
            // See https://github.com/netty/netty/issues/2359
            this.local = socket.localAddress();
            this.remote = socket.remoteAddress();
        }

        if (parent != null) {
            logger.trace("Create Channel Socket: {}", socket.intValue());
        } else {
            logger.trace("Create Server Socket: {}", socket.intValue());
        }

        remoteAddressMemory = Buffer.allocateDirectWithNativeOrder(SOCK_ADDR_LEN);
        remoteAddressMemoryAddress = Buffer.memoryAddress(remoteAddressMemory);
    }

    AbstractIOUringChannel(final Channel parent, LinuxSocket socket, boolean active) {
        super(parent);
        this.socket = checkNotNull(socket, "fd");
        this.active = active;

        if (active) {
            this.local = socket.localAddress();
            this.remote = socket.remoteAddress();
        }

        if (parent != null) {
            logger.trace("Create Channel Socket: {}", socket.intValue());
        } else {
            logger.trace("Create Server Socket: {}", socket.intValue());
        }
        remoteAddressMemory = Buffer.allocateDirectWithNativeOrder(SOCK_ADDR_LEN);
        remoteAddressMemoryAddress = Buffer.memoryAddress(remoteAddressMemory);
    }

    AbstractIOUringChannel(Channel parent, LinuxSocket fd, SocketAddress remote) {
        super(parent);
        this.socket = checkNotNull(fd, "fd");
        this.active = true;

        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        this.remote = remote;
        this.local = fd.localAddress();

        remoteAddressMemory = Buffer.allocateDirectWithNativeOrder(SOCK_ADDR_LEN);
        remoteAddressMemoryAddress = Buffer.memoryAddress(remoteAddressMemory);
    }

    public boolean isOpen() {
        return socket.isOpen();
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
    public FileDescriptor fd() {
        return socket;
    }

    @Override
    protected abstract AbstractUringUnsafe newUnsafe();

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return loop instanceof IOUringEventLoop;
    }

    private ByteBuf readBuffer;

    public void doReadBytes(ByteBuf byteBuf) {
        assert readBuffer == null;
        IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();
        IOUringSubmissionQueue submissionQueue = ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue();

        unsafe().recvBufAllocHandle().attemptedBytesRead(byteBuf.writableBytes());

        if (byteBuf.hasMemoryAddress()) {
            readBuffer = byteBuf;
            submissionQueue.addRead(socket.intValue(), byteBuf.memoryAddress(),
                                    byteBuf.writerIndex(), byteBuf.capacity());
            submissionQueue.submit();
        }
    }

    void writeComplete(int res) {
        ChannelOutboundBuffer channelOutboundBuffer = unsafe().outboundBuffer();

        if (res > 0) {
            channelOutboundBuffer.removeBytes(res);
            setWriteable(true);
            try {
                doWrite(channelOutboundBuffer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void readComplete(int localReadAmount) {
        boolean close = false;
        ByteBuf byteBuf = null;
        final IOUringRecvByteAllocatorHandle allocHandle =
                (IOUringRecvByteAllocatorHandle) unsafe()
                        .recvBufAllocHandle();
        final ChannelPipeline pipeline = pipeline();
        try {
            logger.trace("EventLoop Read Res: {}", localReadAmount);
            logger.trace("EventLoop Fd: {}", fd().intValue());
            setUringInReadyPending(false);
            byteBuf = this.readBuffer;
            this.readBuffer = null;

            if (localReadAmount > 0) {
                byteBuf.writerIndex(byteBuf.writerIndex() + localReadAmount);
            }

            allocHandle.lastBytesRead(localReadAmount);
            if (allocHandle.lastBytesRead() <= 0) {
                // nothing was read, release the buffer.
                byteBuf.release();
                byteBuf = null;
                close = allocHandle.lastBytesRead() < 0;
                if (close) {
                    // There is nothing left to read as we received an EOF.
                    shutdownInput(false);
                }
                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();
                return;
            }

            allocHandle.incMessagesRead(1);
            pipeline.fireChannelRead(byteBuf);
            byteBuf = null;
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();

            logger.trace("READ autoRead {}", config().isAutoRead());
            if (config().isAutoRead()) {
                executeReadEvent();
            }
        } catch (Throwable t) {
            handleReadException(pipeline, byteBuf, t, close, allocHandle);
        }
    }

    private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf,
                                     Throwable cause, boolean close,
                                     IOUringRecvByteAllocatorHandle allocHandle) {
        if (byteBuf != null) {
            if (byteBuf.isReadable()) {
                pipeline.fireChannelRead(byteBuf);
            } else {
                byteBuf.release();
            }
        }
        allocHandle.readComplete();
        pipeline.fireChannelReadComplete();
        pipeline.fireExceptionCaught(cause);
        if (close || cause instanceof IOException) {
            shutdownInput(false);
        } else {
            if (config().isAutoRead()) {
                executeReadEvent();
            }
        }
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

    @Override
    protected void doDisconnect() throws Exception {
    }

    @Override
    protected void doClose() throws Exception {
        if (parent() == null) {
            logger.trace("ServerSocket Close: {}", this.socket.intValue());
            IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();
            IOUringSubmissionQueue submissionQueue = ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue();
            submissionQueue.addPollRemove(socket.intValue());
            submissionQueue.submit();
        }
        active = false;
        // Even if we allow half closed sockets we should give up on reading. Otherwise we may allow a read attempt on a
        // socket which has not even been connected yet. This has been observed to block during unit tests.
        //inputClosedSeenErrorOnRead = true;
        try {
            ChannelPromise promise = connectPromise;
            if (promise != null) {
                // Use tryFailure() instead of setFailure() to avoid the race against cancel().
                promise.tryFailure(new ClosedChannelException());
                connectPromise = null;
            }

            ScheduledFuture<?> future = connectTimeoutFuture;
            if (future != null) {
                future.cancel(false);
                connectTimeoutFuture = null;
            }

            if (isRegistered()) {
                // Need to check if we are on the EventLoop as doClose() may be triggered by the GlobalEventExecutor
                // if SO_LINGER is used.
                //
                // See https://github.com/netty/netty/issues/7159
                EventLoop loop = eventLoop();
                if (loop.inEventLoop()) {
                    doDeregister();
                } else {
                    loop.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                doDeregister();
                            } catch (Throwable cause) {
                                pipeline().fireExceptionCaught(cause);
                            }
                        }
                    });
                }
            }
        } finally {
            socket.close();
            if (readBuffer != null) {
                readBuffer.release();
                readBuffer = null;
            }
        }
    }

    //deregister
    // Channel/ChannelHandlerContext.read() was called
    @Override
    protected void doBeginRead() {
        System.out.println("Begin Read");
        final AbstractUringUnsafe unsafe = (AbstractUringUnsafe) unsafe();
        if (!uringInReadyPending) {
            unsafe.executeUringReadOperator();
        }
    }

    public void executeReadEvent() {
        final AbstractUringUnsafe unsafe = (AbstractUringUnsafe) unsafe();
        unsafe.executeUringReadOperator();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        logger.trace("IOUring doWrite message size: {}", in.size());
        if (writeable && in.size() >= 1) {
            Object msg = in.current();
            if (msg instanceof ByteBuf) {
                doWriteBytes((ByteBuf) msg);
            }
        }
    }

    protected final void doWriteBytes(ByteBuf buf) {
        if (buf.hasMemoryAddress()) {
            //link poll<link>write operation
            addPollOut();

            IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();
            IOUringSubmissionQueue submissionQueue = ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue();
            submissionQueue.addWrite(socket.intValue(), buf.memoryAddress(), buf.readerIndex(),
                                     buf.writerIndex());
            submissionQueue.submit();
            writeable = false;
        }
    }

    //POLLOUT
    private void addPollOut() {
        IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();
        IOUringSubmissionQueue submissionQueue = ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue();
        submissionQueue.addPollOutLink(socket.intValue());
        submissionQueue.submit();
    }

    abstract class AbstractUringUnsafe extends AbstractUnsafe {
        private IOUringRecvByteAllocatorHandle allocHandle;
        private final Runnable readRunnable = new Runnable() {

            @Override
            public void run() {
                uringEventExecution(); //flush and submit SQE
            }
        };

        public void fulfillConnectPromise(ChannelPromise promise, Throwable t, SocketAddress remoteAddress) {
            Throwable cause = annotateConnectException(t, remoteAddress);
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(cause);
            closeIfClosed();
        }


        IOUringRecvByteAllocatorHandle newIOUringHandle(RecvByteBufAllocator.ExtendedHandle handle) {
            return new IOUringRecvByteAllocatorHandle(handle);
        }

        @Override
        public IOUringRecvByteAllocatorHandle recvBufAllocHandle() {
            if (allocHandle == null) {
                allocHandle = newIOUringHandle((RecvByteBufAllocator.ExtendedHandle) super.recvBufAllocHandle());
            }
            return allocHandle;
        }

        final void executeUringReadOperator() {
            if (uringInReadyPending || !isActive() || shouldBreakIoUringInReady(config())) {
                return;
            }
            uringInReadyPending = true;
            eventLoop().execute(readRunnable);
        }

        public abstract void uringEventExecution();

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

                doConnect(remoteAddress, localAddress);
                InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
                NativeInetAddress address = NativeInetAddress.newInstance(inetSocketAddress.getAddress());
                socket.initAddress(address.address(), address.scopeId(), inetSocketAddress.getPort(),remoteAddressMemoryAddress);
                IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();
                final IOUringSubmissionQueue ioUringSubmissionQueue =
                        ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue();
                ioUringSubmissionQueue.addConnect(socket.intValue(), remoteAddressMemoryAddress, SOCK_ADDR_LEN);
                ioUringSubmissionQueue.submit();

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
                        ChannelPromise connectPromise = AbstractIOUringChannel.this.connectPromise;
                        ConnectTimeoutException cause =
                                new ConnectTimeoutException("connection timed out: " + remoteAddress);
                        if (connectPromise != null && connectPromise.tryFailure(cause)) {
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
    }

    public void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
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

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }

        throw new UnsupportedOperationException("unsupported message type");
    }

    @Override
    protected void doRegister() throws Exception {
        ((IOUringEventLoop) eventLoop()).add(this);
    }

    @Override
    protected void doDeregister() throws Exception {
        ((IOUringEventLoop) eventLoop()).remove(this);
    }

    @Override
    public void doBind(final SocketAddress local) throws Exception {
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

    public void setUringInReadyPending(boolean uringInReadyPending) {
        this.uringInReadyPending = uringInReadyPending;
    }

    @Override
    public abstract DefaultChannelConfig config();

    @Override
    protected SocketAddress localAddress0() {
        return local;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remote;
    }

    public Socket getSocket() {
        return socket;
    }

    /**
     * Connect to the remote peer
     */
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
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
    }

//    public void setRemote() {
//        remote = remoteSocketAddr == null ?
//                    remoteAddress : computeRemoteAddr(remoteSocketAddr, socket.remoteAddress());
//    }

    private boolean doConnect0(SocketAddress remote) throws Exception {
        boolean success = false;
        try {
            boolean connected = socket.connect(remote);
            if (!connected) {
                //setFlag(Native.EPOLLOUT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    void shutdownInput(boolean rdHup) {
        logger.trace("shutdownInput Fd: {}", this.socket.intValue());
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
        } else if (!rdHup) {
            inputClosedSeenErrorOnRead = true;
            pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
        }
    }


    //Todo we should move it to a error class
    // copy unix Errors
    static void throwConnectException(String method, int err)
            throws IOException {
        if (err == ERROR_EALREADY_NEGATIVE) {
            throw new ConnectionPendingException();
        }
        if (err == ERROR_ENETUNREACH_NEGATIVE) {
            throw new NoRouteToHostException();
        }
        if (err == ERROR_EISCONN_NEGATIVE) {
            throw new AlreadyConnectedException();
        }
        if (err == ERRNO_ENOENT_NEGATIVE) {
            throw new FileNotFoundException();
        }
        throw new ConnectException(method + "(..) failed: ");
    }

    private static boolean isAllowHalfClosure(ChannelConfig config) {
        return config instanceof SocketChannelConfig &&
               ((SocketChannelConfig) config).isAllowHalfClosure();
    }

    private void fireEventAndClose(Object evt) {
        pipeline().fireUserEventTriggered(evt);
        close(voidPromise());
    }

    void cancelTimeoutFuture() {
        if (connectTimeoutFuture != null) {
            connectTimeoutFuture.cancel(false);
        }
        connectPromise = null;
    }

    boolean doFinishConnect() throws Exception {
        if (socket.finishConnect()) {
            if (requestedRemoteAddress instanceof InetSocketAddress) {
                remote = computeRemoteAddr((InetSocketAddress) requestedRemoteAddress, socket.remoteAddress());
            }
            requestedRemoteAddress = null;

            return true;
        }
        return false;
    }

    void computeRemote() {
         if (requestedRemoteAddress instanceof InetSocketAddress) {
                remote = computeRemoteAddr((InetSocketAddress) requestedRemoteAddress, socket.remoteAddress());
            }
    }

    final boolean shouldBreakIoUringInReady(ChannelConfig config) {
        return socket.isInputShutdown() && (inputClosedSeenErrorOnRead || !isAllowHalfClosure(config));
    }

    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    public long getRemoteAddressMemoryAddress() {
        return remoteAddressMemoryAddress;
    }

    public ChannelPromise getConnectPromise() {
        return connectPromise;
    }

    public ScheduledFuture<?> getConnectTimeoutFuture() {
        return connectTimeoutFuture;
    }

    public SocketAddress getRequestedRemoteAddress() {
        return requestedRemoteAddress;
    }
}
