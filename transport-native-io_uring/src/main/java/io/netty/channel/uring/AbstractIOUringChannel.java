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
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.Socket;
import io.netty.channel.unix.UnixChannel;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.ScheduledFuture;

import static io.netty.util.internal.ObjectUtil.*;

abstract class AbstractIOUringChannel extends AbstractChannel implements UnixChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractIOUringChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    final LinuxSocket socket;
    protected volatile boolean active;
    boolean uringInReadyPending;
    boolean inputClosedSeenErrorOnRead;

    //can only submit one write operation at a time
    private boolean writeable = true;
    /**
     * The future of the current connection attempt.  If not null, subsequent connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;

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
    }

    protected AbstractIOUringChannel(final Channel parent, LinuxSocket socket, boolean active) {
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
        logger.trace("Begin Read");
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
        submissionQueue.addPollOut(socket.intValue());
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

        IOUringRecvByteAllocatorHandle newIOUringHandle(RecvByteBufAllocator.ExtendedHandle handle) {
            return new IOUringRecvByteAllocatorHandle(handle);
        }

        @Override
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress,
                            final ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
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

    private static boolean isAllowHalfClosure(ChannelConfig config) {
        return config instanceof SocketChannelConfig &&
               ((SocketChannelConfig) config).isAllowHalfClosure();
    }

    private void fireEventAndClose(Object evt) {
        pipeline().fireUserEventTriggered(evt);
        close(voidPromise());
    }

    final boolean shouldBreakIoUringInReady(ChannelConfig config) {
        return socket.isInputShutdown() && (inputClosedSeenErrorOnRead || !isAllowHalfClosure(config));
    }

    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }
}
