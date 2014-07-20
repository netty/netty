/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.epoll.EpollChannelOutboundBuffer.AddressEntry;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannelOutboundBuffer;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * {@link SocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollSocketChannel extends AbstractEpollChannel implements SocketChannel {

    private final EpollSocketChannelConfig config;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;

    private volatile InetSocketAddress local;
    private volatile InetSocketAddress remote;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    EpollSocketChannel(Channel parent, int fd) {
        super(parent, fd, Native.EPOLLIN, true);
        config = new EpollSocketChannelConfig(this);
        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        remote = Native.remoteAddress(fd);
        local = Native.localAddress(fd);
    }

    public EpollSocketChannel() {
        super(Native.socketStreamFd(), Native.EPOLLIN);
        config = new EpollSocketChannelConfig(this);
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollSocketUnsafe();
    }

    @Override
    protected SocketAddress localAddress0() {
        return local;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remote;
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        InetSocketAddress localAddress = (InetSocketAddress) local;
        Native.bind(fd, localAddress.getAddress(), localAddress.getPort());
        this.local = Native.localAddress(fd);
    }

    /**
     * Write bytes form the given {@link ByteBuf} to the underlying {@link java.nio.channels.Channel}.
     * @param buf           the {@link ByteBuf} from which the bytes should be written
     */
    private boolean writeBytes(ChannelOutboundBuffer in, ByteBuf buf) throws Exception {
        int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            in.remove();
            return true;
        }
        boolean done = false;
        long writtenBytes = 0;
        if (buf.nioBufferCount() == 1) {
            int readerIndex = buf.readerIndex();
            ByteBuffer nioBuf = buf.internalNioBuffer(readerIndex, buf.readableBytes());
            for (;;) {
                int pos = nioBuf.position();
                int limit = nioBuf.limit();
                int localFlushedAmount = Native.write(fd, nioBuf, pos, limit);
                if (localFlushedAmount > 0) {
                    nioBuf.position(pos + localFlushedAmount);
                    writtenBytes += localFlushedAmount;
                    if (writtenBytes == readableBytes) {
                        done = true;
                        break;
                    }
                } else {
                    // Returned EAGAIN need to set EPOLLOUT
                    setEpollOut();
                    break;
                }
            }
            updateOutboundBuffer(in, writtenBytes, 1, done);
            return done;
        } else {
            ByteBuffer[] nioBuffers = buf.nioBuffers();
            return writeBytesMultiple(in, 1, nioBuffers, nioBuffers.length, readableBytes);
        }
    }

    private boolean writeBytesMultiple(
            EpollChannelOutboundBuffer in, int msgCount, AddressEntry[] addresses,
            int addressCnt, int expectedWrittenBytes) throws IOException {
        boolean done = false;
        long writtenBytes = 0;
        int offset = 0;
        int end = offset + addressCnt;
        loop: while (addressCnt > 0) {
            for (;;) {
                int cnt = addressCnt > Native.IOV_MAX? Native.IOV_MAX : addressCnt;

                long localWrittenBytes = Native.writevAddresses(fd, addresses, offset, cnt);
                if (localWrittenBytes == 0) {
                    // Returned EAGAIN need to set EPOLLOUT
                    setEpollOut();
                    break loop;
                }
                expectedWrittenBytes -= localWrittenBytes;
                writtenBytes += localWrittenBytes;

                while (offset < end && localWrittenBytes > 0) {
                    AddressEntry address = addresses[offset];
                    int readerIndex = address.readerIndex;
                    int bytes = address.writerIndex - readerIndex;
                    if (bytes > localWrittenBytes) {
                        address.readerIndex += (int) localWrittenBytes;
                        // incomplete write
                        break;
                    } else {
                        offset++;
                        addressCnt--;
                        localWrittenBytes -= bytes;
                    }
                }

                if (expectedWrittenBytes == 0) {
                    done = true;
                    break;
                }
            }
        }

        updateOutboundBuffer(in, writtenBytes, msgCount, done);
        return done;
    }

    private boolean writeBytesMultiple(
            ChannelOutboundBuffer in, int msgCount, ByteBuffer[] nioBuffers,
            int nioBufferCnt, long expectedWrittenBytes) throws IOException {
        boolean done = false;
        long writtenBytes = 0;
        int offset = 0;
        int end = offset + nioBufferCnt;
        loop: while (nioBufferCnt > 0) {
            for (;;) {
                int cnt = nioBufferCnt > Native.IOV_MAX? Native.IOV_MAX : nioBufferCnt;

                long localWrittenBytes = Native.writev(fd, nioBuffers, offset, cnt);
                if (localWrittenBytes == 0) {
                    // Returned EAGAIN need to set EPOLLOUT
                    setEpollOut();
                    break loop;
                }
                expectedWrittenBytes -= localWrittenBytes;
                writtenBytes += localWrittenBytes;

                while (offset < end && localWrittenBytes > 0) {
                    ByteBuffer buffer = nioBuffers[offset];
                    int pos = buffer.position();
                    int bytes = buffer.limit() - pos;
                    if (bytes > localWrittenBytes) {
                        buffer.position(pos + (int) localWrittenBytes);
                        // incomplete write
                        break;
                    } else {
                        offset++;
                        nioBufferCnt--;
                        localWrittenBytes -= bytes;
                    }
                }

                if (expectedWrittenBytes == 0) {
                    done = true;
                    break;
                }
            }
        }
        updateOutboundBuffer(in, writtenBytes, msgCount, done);
        return done;
    }

    private static void updateOutboundBuffer(ChannelOutboundBuffer in, long writtenBytes, int msgCount,
                                      boolean done) {
        if (done) {
            // Release all buffers
            for (int i = msgCount; i > 0; i --) {
                in.remove();
            }
            in.progress(writtenBytes);
        } else {
            // Did not write all buffers completely.
            // Release the fully written buffers and update the indexes of the partially written buffer.

            // Did not write all buffers completely.
            // Release the fully written buffers and update the indexes of the partially written buffer.
            for (int i = msgCount; i > 0; i --) {
                final ByteBuf buf = (ByteBuf) in.current();
                final int readerIndex = buf.readerIndex();
                final int readableBytes = buf.writerIndex() - readerIndex;

                if (readableBytes < writtenBytes) {
                    in.remove();
                    writtenBytes -= readableBytes;
                } else if (readableBytes > writtenBytes) {
                    buf.readerIndex(readerIndex + (int) writtenBytes);
                    in.progress(writtenBytes);
                    break;
                } else { // readable == writtenBytes
                    in.remove();
                    break;
                }
            }
        }
    }

    /**
     * Write a {@link DefaultFileRegion}
     *
     * @param region        the {@link DefaultFileRegion} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    private boolean writeFileRegion(ChannelOutboundBuffer in, DefaultFileRegion region) throws Exception {
        boolean done = false;
        long flushedAmount = 0;

        for (int i = config().getWriteSpinCount() - 1; i >= 0; i --) {
            long expected = region.count() - region.position();
            long localFlushedAmount = Native.sendfile(fd, region, region.transfered(), expected);
            if (localFlushedAmount == 0) {
                // Returned EAGAIN need to set EPOLLOUT
                setEpollOut();
                break;
            }

            flushedAmount += localFlushedAmount;
            if (region.transfered() >= region.count()) {
                done = true;
                break;
            }
        }

        in.progress(flushedAmount);

        if (done) {
            in.remove();
        }
        return done;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (;;) {
            final int msgCount = in.size();

            if (msgCount == 0) {
                // Wrote all messages.
                clearEpollOut();
                break;
            }

            // Do gathering write if:
            // * the outbound buffer contains more than one messages and
            // * they are all buffers rather than a file region.
            if (msgCount > 1) {
                if (PlatformDependent.hasUnsafe()) {
                    // this means we can cast to EpollChannelOutboundBuffer and write the AdressEntry directly.
                    EpollChannelOutboundBuffer epollIn = (EpollChannelOutboundBuffer) in;
                    // Ensure the pending writes are made of memoryaddresses only.
                    AddressEntry[] addresses = epollIn.memoryAddresses();
                    int addressesCnt = epollIn.addressCount();
                    if (addressesCnt > 1) {
                        if (!writeBytesMultiple(epollIn, msgCount, addresses, addressesCnt, epollIn.addressCount())) {
                            // was not able to write everything so break here we will get notified later again once
                            // the network stack can handle more writes.
                            break;
                        }

                        // We do not break the loop here even if the outbound buffer was flushed completely,
                        // because a user might have triggered another write and flush when we notify his or her
                        // listeners.
                        continue;
                    }
                } else {
                    NioSocketChannelOutboundBuffer nioIn = (NioSocketChannelOutboundBuffer) in;
                    // Ensure the pending writes are made of memoryaddresses only.
                    ByteBuffer[] nioBuffers = nioIn.nioBuffers();
                    int nioBufferCnt = nioIn.nioBufferCount();
                    if (nioBufferCnt > 1) {
                        if (!writeBytesMultiple(nioIn, msgCount, nioBuffers, nioBufferCnt, nioIn.nioBufferSize())) {
                            // was not able to write everything so break here we will get notified later again once
                            // the network stack can handle more writes.
                            break;
                        }

                        // We do not break the loop here even if the outbound buffer was flushed completely,
                        // because a user might have triggered another write and flush when we notify his or her
                        // listeners.
                        continue;
                    }
                }
            }

            // The outbound buffer contains only one message or it contains a file region.
            Object msg = in.current();
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                if (!writeBytes(in, buf)) {
                    // was not able to write everything so break here we will get notified later again once
                    // the network stack can handle more writes.
                    break;
                }
            } else if (msg instanceof DefaultFileRegion) {
                DefaultFileRegion region = (DefaultFileRegion) msg;
                if (!writeFileRegion(in, region)) {
                    // was not able to write everything so break here we will get notified later again once
                    // the network stack can handle more writes.
                    break;
                }
            } else {
                throw new UnsupportedOperationException("unsupported message type: " + StringUtil.simpleClassName(msg));
            }
        }
    }

    @Override
    public EpollSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isInputShutdown() {
        return inputShutdown;
    }

    @Override
    public boolean isOutputShutdown() {
        return outputShutdown || !isActive();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        EventLoop loop = eventLoop();
        if (loop.inEventLoop()) {
            try {
                Native.shutdown(fd, false, true);
                outputShutdown = true;
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    shutdownOutput(promise);
                }
            });
        }
        return promise;
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    final class EpollSocketUnsafe extends AbstractEpollUnsafe {
        private RecvByteBufAllocator.Handle allocHandle;

        private void closeOnRead(ChannelPipeline pipeline) {
            inputShutdown = true;
            if (isOpen()) {
                if (Boolean.TRUE.equals(config().getOption(ChannelOption.ALLOW_HALF_CLOSURE))) {
                    clearEpollIn0();
                    pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                } else {
                    close(voidPromise());
                }
            }
        }

        private boolean handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause, boolean close) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    readPending = false;
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }
            pipeline.fireChannelReadComplete();
            pipeline.fireExceptionCaught(cause);
            if (close || cause instanceof IOException) {
                closeOnRead(pipeline);
                return true;
            }
            return false;
        }

        @Override
        public void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                if (connectPromise != null) {
                    throw new IllegalStateException("connection attempt already made");
                }

                boolean wasActive = isActive();
                if (doConnect((InetSocketAddress) remoteAddress, (InetSocketAddress) localAddress)) {
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
                                ChannelPromise connectPromise = EpollSocketChannel.this.connectPromise;
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
            } catch (Throwable t) {
                if (t instanceof ConnectException) {
                    Throwable newT = new ConnectException(t.getMessage() + ": " + remoteAddress);
                    newT.setStackTrace(t.getStackTrace());
                    t = newT;
                }
                closeIfClosed();
                promise.tryFailure(t);
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }
            active = true;

            // trySuccess() will return false if a user cancelled the connection attempt.
            boolean promiseSet = promise.trySuccess();

            // Regardless if the connection attempt was cancelled, channelActive() event should be triggered,
            // because what happened is what happened.
            if (!wasActive && isActive()) {
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
                if (t instanceof ConnectException) {
                    Throwable newT = new ConnectException(t.getMessage() + ": " + requestedRemoteAddress);
                    newT.setStackTrace(t.getStackTrace());
                    t = newT;
                }

                fulfillConnectPromise(connectPromise, t);
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

        @Override
        void epollOutReady() {
            if (connectPromise != null) {
                // pending connect which is now complete so handle it.
                finishConnect();
            } else {
                super.epollOutReady();
            }
        }

        /**
         * Connect to the remote peer
         */
        private boolean doConnect(InetSocketAddress remoteAddress, InetSocketAddress localAddress) throws Exception {
            if (localAddress != null) {
                checkResolvable(localAddress);
                Native.bind(fd, localAddress.getAddress(), localAddress.getPort());
            }

            boolean success = false;
            try {
                checkResolvable(remoteAddress);
                boolean connected = Native.connect(fd, remoteAddress.getAddress(),
                        remoteAddress.getPort());
                remote = remoteAddress;
                local = Native.localAddress(fd);
                if (!connected) {
                    setEpollOut();
                }
                success = true;
                return connected;
            } finally {
                if (!success) {
                    doClose();
                }
            }
        }

        /**
         * Finish the connect
         */
        private boolean doFinishConnect() throws Exception {
            if (Native.finishConnect(fd)) {
                clearEpollOut();
                return true;
            } else {
                setEpollOut();
                return false;
            }
        }

        /**
         * Read bytes into the given {@link ByteBuf} and return the amount.
         */
        private int doReadBytes(ByteBuf byteBuf) throws Exception {
            int writerIndex = byteBuf.writerIndex();
            int localReadAmount;
            if (byteBuf.hasMemoryAddress()) {
                localReadAmount = Native.readAddress(fd, byteBuf.memoryAddress(), writerIndex, byteBuf.capacity());
            } else {
                ByteBuffer buf = byteBuf.internalNioBuffer(writerIndex, byteBuf.writableBytes());
                localReadAmount = Native.read(fd, buf, buf.position(), buf.limit());
            }
            if (localReadAmount > 0) {
                byteBuf.writerIndex(writerIndex + localReadAmount);
            }
            return localReadAmount;
        }

        @Override
        void epollRdHupReady() {
            if (isActive()) {
                epollInReady();
            } else {
                closeOnRead(pipeline());
            }
        }

        @Override
        void epollInReady() {
            final ChannelConfig config = config();
            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            RecvByteBufAllocator.Handle allocHandle = this.allocHandle;
            if (allocHandle == null) {
                this.allocHandle = allocHandle = config.getRecvByteBufAllocator().newHandle();
            }

            ByteBuf byteBuf = null;
            boolean close = false;
            try {
                int totalReadAmount = 0;
                for (;;) {
                    // we use a direct buffer here as the native implementations only be able
                    // to handle direct buffers.
                    byteBuf = allocHandle.allocate(allocator);
                    int writable = byteBuf.writableBytes();
                    int localReadAmount = doReadBytes(byteBuf);
                    if (localReadAmount <= 0) {
                        // not was read release the buffer
                        byteBuf.release();
                        close = localReadAmount < 0;
                        break;
                    }
                    readPending = false;
                    pipeline.fireChannelRead(byteBuf);
                    byteBuf = null;

                    if (totalReadAmount >= Integer.MAX_VALUE - localReadAmount) {
                        allocHandle.record(totalReadAmount);

                        // Avoid overflow.
                        totalReadAmount = localReadAmount;
                    } else {
                        totalReadAmount += localReadAmount;
                    }

                    if (localReadAmount < writable) {
                        // Read less than what the buffer can hold,
                        // which might mean we drained the recv buffer completely.
                        break;
                    }
                }
                pipeline.fireChannelReadComplete();
                allocHandle.record(totalReadAmount);

                if (close) {
                    closeOnRead(pipeline);
                    close = false;
                }
            } catch (Throwable t) {
                boolean closed = handleReadException(pipeline, byteBuf, t, close);
                if (!closed) {
                    // trigger a read again as there may be something left to read and because of epoll ET we
                    // will not get notified again until we read everything from the socket
                    eventLoop().execute(new Runnable() {
                        @Override
                        public void run() {
                            epollInReady();
                        }
                    });
                }
            } finally {
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                if (!config.isAutoRead() && !readPending) {
                    clearEpollIn0();
                }
            }
        }
    }

    @Override
    protected ChannelOutboundBuffer newOutboundBuffer() {
        if (PlatformDependent.hasUnsafe()) {
            // This means we will be able to access the memory addresses directly and so be able to do
            // gathering writes with the AddressEntry.
            return EpollChannelOutboundBuffer.newInstance(this);
        } else {
            // No access to the memoryAddres, so fallback to use ByteBuffer[] for gathering writes.
            return NioSocketChannelOutboundBuffer.newInstance(this);
        }
    }
}
