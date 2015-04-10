/*
 * Copyright 2015 The Netty Project
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
import io.netty.buffer.CompositeByteBuf;
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
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractEpollStreamChannel extends AbstractEpollChannel {

    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(ByteBuf.class) + ", " +
                    StringUtil.simpleClassName(DefaultFileRegion.class) + ')';

    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    protected AbstractEpollStreamChannel(Channel parent, int fd) {
        super(parent, fd, Native.EPOLLIN, true);
        // Add EPOLLRDHUP so we are notified once the remote peer close the connection.
        flags |= Native.EPOLLRDHUP;
    }

    protected AbstractEpollStreamChannel(int fd) {
        super(fd, Native.EPOLLIN);
        // Add EPOLLRDHUP so we are notified once the remote peer close the connection.
        flags |= Native.EPOLLRDHUP;
    }

    protected AbstractEpollStreamChannel(FileDescriptor fd) {
        super(null, fd, Native.EPOLLIN, Native.getSoError(fd.intValue()) == 0);
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollStreamUnsafe();
    }

    /**
     * Write bytes form the given {@link ByteBuf} to the underlying {@link java.nio.channels.Channel}.
     * @param buf           the {@link ByteBuf} from which the bytes should be written
     */
    private boolean writeBytes(ChannelOutboundBuffer in, ByteBuf buf, int writeSpinCount) throws Exception {
        int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            in.remove();
            return true;
        }

        if (buf.hasMemoryAddress() || buf.nioBufferCount() == 1) {
            int writtenBytes = doWriteBytes(buf, writeSpinCount);
            in.removeBytes(writtenBytes);
            return writtenBytes == readableBytes;
        } else {
            ByteBuffer[] nioBuffers = buf.nioBuffers();
            return writeBytesMultiple(in, nioBuffers, nioBuffers.length, readableBytes, writeSpinCount);
        }
    }

    private boolean writeBytesMultiple(
            ChannelOutboundBuffer in, IovArray array, int writeSpinCount) throws IOException {

        long expectedWrittenBytes = array.size();
        final long initialExpectedWrittenBytes = expectedWrittenBytes;

        int cnt = array.count();

        assert expectedWrittenBytes != 0;
        assert cnt != 0;

        boolean done = false;
        int offset = 0;
        int end = offset + cnt;
        for (int i = writeSpinCount - 1; i >= 0; i--) {
            long localWrittenBytes = Native.writevAddresses(fd().intValue(), array.memoryAddress(offset), cnt);
            if (localWrittenBytes == 0) {
                break;
            }
            expectedWrittenBytes -= localWrittenBytes;

            if (expectedWrittenBytes == 0) {
                // Written everything, just break out here (fast-path)
                done = true;
                break;
            }

            do {
                long bytes = array.processWritten(offset, localWrittenBytes);
                if (bytes == -1) {
                    // incomplete write
                    break;
                } else {
                    offset++;
                    cnt--;
                    localWrittenBytes -= bytes;
                }
            } while (offset < end && localWrittenBytes > 0);
        }
        if (!done) {
            setFlag(Native.EPOLLOUT);
        }
        in.removeBytes(initialExpectedWrittenBytes - expectedWrittenBytes);
        return done;
    }

    private boolean writeBytesMultiple(
            ChannelOutboundBuffer in, ByteBuffer[] nioBuffers,
            int nioBufferCnt, long expectedWrittenBytes, int writeSpinCount) throws IOException {

        assert expectedWrittenBytes != 0;
        final long initialExpectedWrittenBytes = expectedWrittenBytes;

        boolean done = false;
        int offset = 0;
        int end = offset + nioBufferCnt;
        for (int i = writeSpinCount - 1; i >= 0; i--) {
            long localWrittenBytes = Native.writev(fd().intValue(), nioBuffers, offset, nioBufferCnt);
            if (localWrittenBytes == 0) {
                break;
            }
            expectedWrittenBytes -= localWrittenBytes;

            if (expectedWrittenBytes == 0) {
                // Written everything, just break out here (fast-path)
                done = true;
                break;
            }
            do {
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
            } while (offset < end && localWrittenBytes > 0);
        }

        in.removeBytes(initialExpectedWrittenBytes - expectedWrittenBytes);
        if (!done) {
            setFlag(Native.EPOLLOUT);
        }
        return done;
    }

    /**
     * Write a {@link DefaultFileRegion}
     *
     * @param region        the {@link DefaultFileRegion} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    private boolean writeFileRegion(
            ChannelOutboundBuffer in, DefaultFileRegion region, int writeSpinCount) throws Exception {
        final long regionCount = region.count();
        if (region.transfered() >= regionCount) {
            in.remove();
            return true;
        }

        final long baseOffset = region.position();
        boolean done = false;
        long flushedAmount = 0;

        for (int i = writeSpinCount - 1; i >= 0; i--) {
            final long offset = region.transfered();
            final long localFlushedAmount =
                    Native.sendfile(fd().intValue(), region, baseOffset, offset, regionCount - offset);
            if (localFlushedAmount == 0) {
                break;
            }

            flushedAmount += localFlushedAmount;
            if (region.transfered() >= regionCount) {
                done = true;
                break;
            }
        }

        if (flushedAmount > 0) {
            in.progress(flushedAmount);
        }

        if (done) {
            in.remove();
        } else {
            // Returned EAGAIN need to set EPOLLOUT
            setFlag(Native.EPOLLOUT);
        }
        return done;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int writeSpinCount = config().getWriteSpinCount();
        for (;;) {
            final int msgCount = in.size();

            if (msgCount == 0) {
                // Wrote all messages.
                clearFlag(Native.EPOLLOUT);
                break;
            }

            // Do gathering write if the outbounf buffer entries start with more than one ByteBuf.
            if (msgCount > 1 && in.current() instanceof ByteBuf) {
                if (!doWriteMultiple(in, writeSpinCount)) {
                    break;
                }

                // We do not break the loop here even if the outbound buffer was flushed completely,
                // because a user might have triggered another write and flush when we notify his or her
                // listeners.
            } else { // msgCount == 1
                if (!doWriteSingle(in, writeSpinCount)) {
                    break;
                }
            }
        }
    }

    protected boolean doWriteSingle(ChannelOutboundBuffer in, int writeSpinCount) throws Exception {
        // The outbound buffer contains only one message or it contains a file region.
        Object msg = in.current();
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (!writeBytes(in, buf, writeSpinCount)) {
                // was not able to write everything so break here we will get notified later again once
                // the network stack can handle more writes.
                return false;
            }
        } else if (msg instanceof DefaultFileRegion) {
            DefaultFileRegion region = (DefaultFileRegion) msg;
            if (!writeFileRegion(in, region, writeSpinCount)) {
                // was not able to write everything so break here we will get notified later again once
                // the network stack can handle more writes.
                return false;
            }
        } else {
            // Should never reach here.
            throw new Error();
        }

        return true;
    }

    private boolean doWriteMultiple(ChannelOutboundBuffer in, int writeSpinCount) throws Exception {
        if (PlatformDependent.hasUnsafe()) {
            // this means we can cast to IovArray and write the IovArray directly.
            IovArray array = IovArrayThreadLocal.get(in);
            int cnt = array.count();
            if (cnt >= 1) {
                // TODO: Handle the case where cnt == 1 specially.
                if (!writeBytesMultiple(in, array, writeSpinCount)) {
                    // was not able to write everything so break here we will get notified later again once
                    // the network stack can handle more writes.
                    return false;
                }
            } else { // cnt == 0, which means the outbound buffer contained empty buffers only.
                in.removeBytes(0);
            }
        } else {
            ByteBuffer[] buffers = in.nioBuffers();
            int cnt = in.nioBufferCount();
            if (cnt >= 1) {
                // TODO: Handle the case where cnt == 1 specially.
                if (!writeBytesMultiple(in, buffers, cnt, in.nioBufferSize(), writeSpinCount)) {
                    // was not able to write everything so break here we will get notified later again once
                    // the network stack can handle more writes.
                    return false;
                }
            } else { // cnt == 0, which means the outbound buffer contained empty buffers only.
                in.removeBytes(0);
            }
        }

        return true;
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (!buf.hasMemoryAddress() && (PlatformDependent.hasUnsafe() || !buf.isDirect())) {
                if (buf instanceof CompositeByteBuf) {
                    // Special handling of CompositeByteBuf to reduce memory copies if some of the Components
                    // in the CompositeByteBuf are backed by a memoryAddress.
                    CompositeByteBuf comp = (CompositeByteBuf) buf;
                    if (!comp.isDirect() || comp.nioBufferCount() > Native.IOV_MAX) {
                        // more then 1024 buffers for gathering writes so just do a memory copy.
                        buf = newDirectBuffer(buf);
                        assert buf.hasMemoryAddress();
                    }
                } else {
                    // We can only handle buffers with memory address so we need to copy if a non direct is
                    // passed to write.
                    buf = newDirectBuffer(buf);
                    assert buf.hasMemoryAddress();
                }
            }
            return buf;
        }

        if (msg instanceof DefaultFileRegion) {
            return msg;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    protected boolean isInputShutdown0() {
        return inputShutdown;
    }

    protected boolean isOutputShutdown0() {
        return outputShutdown || !isActive();
    }

    protected void shutdownOutput0(final ChannelPromise promise) {
        try {
            Native.shutdown(fd().intValue(), false, true);
            outputShutdown = true;
            promise.setSuccess();
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
    }

    /**
     * Connect to the remote peer
     */
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            Native.bind(fd().intValue(), localAddress);
        }

        boolean success = false;
        try {
            boolean connected = Native.connect(fd().intValue(), remoteAddress);
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

    class EpollStreamUnsafe extends AbstractEpollUnsafe {
        /**
         * The future of the current connection attempt.  If not null, subsequent
         * connection attempts will fail.
         */
        private ChannelPromise connectPromise;
        private ScheduledFuture<?> connectTimeoutFuture;
        private SocketAddress requestedRemoteAddress;

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
                                ChannelPromise connectPromise = EpollStreamUnsafe.this.connectPromise;
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
         * Finish the connect
         */
        private boolean doFinishConnect() throws Exception {
            if (Native.finishConnect(fd().intValue())) {
                clearFlag(Native.EPOLLOUT);
                return true;
            } else {
                setFlag(Native.EPOLLOUT);
                return false;
            }
        }

        @Override
        void epollRdHupReady() {
            // Just call closeOnRead(). There is no need to trigger a read as this
            // will result in an IOException anyway.
            //
            // See https://github.com/netty/netty/issues/3539
            closeOnRead(pipeline());
        }

        @Override
        void epollInReady() {
            final ChannelConfig config = config();
            boolean edgeTriggered = isFlagSet(Native.EPOLLET);

            if (!readPending && !edgeTriggered && !config.isAutoRead()) {
                // ChannelConfig.setAutoRead(false) was called in the meantime
                clearEpollIn0();
                return;
            }

            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            RecvByteBufAllocator.Handle allocHandle = this.allocHandle;
            if (allocHandle == null) {
                this.allocHandle = allocHandle = config.getRecvByteBufAllocator().newHandle();
            }

            ByteBuf byteBuf = null;
            boolean close = false;
            try {
                // if edgeTriggered is used we need to read all messages as we are not notified again otherwise.
                final int maxMessagesPerRead = edgeTriggered
                        ? Integer.MAX_VALUE : config.getMaxMessagesPerRead();
                int messages = 0;
                int totalReadAmount = 0;
                do {
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
                    if (!edgeTriggered && !config.isAutoRead()) {
                        // This is not using EPOLLET so we can stop reading
                        // ASAP as we will get notified again later with
                        // pending data
                        break;
                    }
                } while (++ messages < maxMessagesPerRead);

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
                if (!readPending && !config.isAutoRead()) {
                    clearEpollIn0();
                }
            }
        }
    }
}
