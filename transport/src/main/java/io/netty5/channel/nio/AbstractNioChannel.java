/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel.nio;

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
import io.netty5.channel.ChannelException;
import io.netty5.channel.ConnectTimeoutException;
import io.netty5.channel.EventLoop;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.ReferenceCounted;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link Channel} implementations which use a Selector based approach.
 */
public abstract class AbstractNioChannel extends AbstractChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractNioChannel.class);

    private final SelectableChannel ch;
    protected final int readInterestOp;
    volatile SelectionKey selectionKey;
    boolean readPending;
    private final Runnable clearReadPendingRunnable = this::clearReadPending0;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private Promise<Void> connectPromise;
    private Future<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;

    /**
     * Create a new instance
     *
     * @param parent            the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param eventLoop         the {@link EventLoop} to use for all I/O.
     * @param ch                the underlying {@link SelectableChannel} on which it operates
     * @param readInterestOp    the ops to set to receive data from the {@link SelectableChannel}
     */
    protected AbstractNioChannel(Channel parent, EventLoop eventLoop, SelectableChannel ch, int readInterestOp) {
        super(parent, eventLoop);
        this.ch = ch;
        this.readInterestOp = readInterestOp;
        try {
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                ch.close();
            } catch (IOException e2) {
                logger.warn(
                            "Failed to close a partially initialized socket.", e2);
            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }

    /**
     * Return the current {@link SelectionKey} or {@code null} if the underlying channel was not registered with the
     * {@link java.nio.channels.Selector} yet.
     */
    protected SelectionKey selectionKey() {
        return selectionKey;
    }

    /**
     * @deprecated No longer supported.
     * No longer supported.
     */
    @Deprecated
    protected boolean isReadPending() {
        return readPending;
    }

    /**
     * @deprecated Use {@link #clearReadPending()} if appropriate instead.
     * No longer supported.
     */
    @Deprecated
    protected void setReadPending(final boolean readPending) {
        if (isRegistered()) {
            EventLoop eventLoop = executor();
            if (eventLoop.inEventLoop()) {
                setReadPending0(readPending);
            } else {
                eventLoop.execute(() -> setReadPending0(readPending));
            }
        } else {
            // Best effort if we are not registered yet clear readPending.
            // NB: We only set the boolean field instead of calling clearReadPending0(), because the SelectionKey is
            // not set yet so it would produce an assertion failure.
            this.readPending = readPending;
        }
    }

    /**
     * Set read pending to {@code false}.
     */
    protected final void clearReadPending() {
        if (isRegistered()) {
            EventLoop eventLoop = executor();
            if (eventLoop.inEventLoop()) {
                clearReadPending0();
            } else {
                eventLoop.execute(clearReadPendingRunnable);
            }
        } else {
            // Best effort if we are not registered yet clear readPending. This happens during channel initialization.
            // NB: We only set the boolean field instead of calling clearReadPending0(), because the SelectionKey is
            // not set yet so it would produce an assertion failure.
            readPending = false;
        }
    }

    private void setReadPending0(boolean readPending) {
        this.readPending = readPending;
        if (!readPending) {
            ((AbstractNioUnsafe) unsafe()).removeReadOp();
        }
    }

    private void clearReadPending0() {
        readPending = false;
        ((AbstractNioUnsafe) unsafe()).removeReadOp();
    }

    /**
     * Special {@link Unsafe} sub-type which allows to access the underlying {@link SelectableChannel}
     */
    public interface NioUnsafe extends Unsafe {
        /**
         * Return underlying {@link SelectableChannel}
         */
        SelectableChannel ch();

        /**
         * Finish connect
         */
        void finishConnect();

        /**
         * Read from underlying {@link SelectableChannel}
         */
        void read();

        void forceFlush();
    }

    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {

        protected final void removeReadOp() {
            SelectionKey key = selectionKey();
            // Check first if the key is still valid as it may be canceled as part of the deregistration
            // from the EventLoop
            // See https://github.com/netty/netty/issues/2104
            if (key == null || !key.isValid()) {
                return;
            }
            int interestOps = key.interestOps();
            if ((interestOps & readInterestOp) != 0) {
                // only remove readInterestOp if needed
                key.interestOps(interestOps & ~readInterestOp);
            }
        }

        @Override
        public final SelectableChannel ch() {
            return javaChannel();
        }

        @Override
        public final void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, Promise<Void> promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                if (connectPromise != null) {
                    // Already a connect in process.
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
                            Promise<Void> connectPromise = AbstractNioChannel.this.connectPromise;
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
                promise.tryFailure(annotateConnectException(t, remoteAddress));
                closeIfClosed();
            }
        }

        private void fulfillConnectPromise(Promise<Void> promise, boolean wasActive) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

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

        @Override
        public final void finishConnect() {
            // Note this method is invoked by the event loop only if the connection attempt was
            // neither cancelled nor timed out.

            assert executor().inEventLoop();

            try {
                boolean wasActive = isActive();
                doFinishConnect();
                fulfillConnectPromise(connectPromise, wasActive);
            } catch (Throwable t) {
                fulfillConnectPromise(connectPromise, annotateConnectException(t, requestedRemoteAddress));
            } finally {
                // Check for null as the connectTimeoutFuture is only created if a connectTimeoutMillis > 0 is used
                // See https://github.com/netty/netty/issues/1770
                if (connectTimeoutFuture != null) {
                    connectTimeoutFuture.cancel();
                }
                connectPromise = null;
            }
        }

        @Override
        protected final void flush0() {
            // Flush immediately only when there's no pending flush.
            // If there's a pending flush operation, event loop will call forceFlush() later,
            // and thus there's no need to call it now.
            if (!isFlushPending()) {
                super.flush0();
            }
        }

        @Override
        public final void forceFlush() {
            // directly call super.flush0() to force a flush now
            super.flush0();
        }

        private boolean isFlushPending() {
            SelectionKey selectionKey = selectionKey();
            return selectionKey != null && selectionKey.isValid()
                    && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
        }
    }

    @Override
    protected void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            return;
        }

        readPending = true;

        final int interestOps = selectionKey.interestOps();
        if ((interestOps & readInterestOp) == 0) {
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

    /**
     * Connect to the remote peer
     */
    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    /**
     * Finish the connect
     */
    protected abstract void doFinishConnect() throws Exception;

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the original one.
     * Note that this method does not create an off-heap copy if the allocation / deallocation cost is too high,
     * but just returns the original {@link ByteBuf}..
     */
    protected final ByteBuf newDirectBuffer(ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.safeRelease(buf);
            return Unpooled.EMPTY_BUFFER;
        }

        final ByteBufAllocator alloc = alloc();
        if (alloc.isDirectBufferPooled()) {
            ByteBuf directBuf = alloc.directBuffer(readableBytes);
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(buf);
            return directBuf;
        }

        final ByteBuf directBuf = ByteBufUtil.threadLocalDirectBuffer();
        if (directBuf != null) {
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(buf);
            return directBuf;
        }

        // Allocating and deallocating an unpooled direct buffer is very expensive; give up.
        return buf;
    }

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the specified holder.
     * The caller must ensure that the holder releases the original {@link ByteBuf} when the holder is released by
     * this method.  Note that this method does not create an off-heap copy if the allocation / deallocation cost is
     * too high, but just returns the original {@link ByteBuf}..
     */
    protected final ByteBuf newDirectBuffer(ReferenceCounted holder, ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.safeRelease(holder);
            return Unpooled.EMPTY_BUFFER;
        }

        final ByteBufAllocator alloc = alloc();
        if (alloc.isDirectBufferPooled()) {
            ByteBuf directBuf = alloc.directBuffer(readableBytes);
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(holder);
            return directBuf;
        }

        final ByteBuf directBuf = ByteBufUtil.threadLocalDirectBuffer();
        if (directBuf != null) {
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(holder);
            return directBuf;
        }

        // Allocating and deallocating an unpooled direct buffer is very expensive; give up.
        if (holder != buf) {
            // Ensure to call holder.release() to give the holder a chance to release other resources than its content.
            buf.retain();
            ReferenceCountUtil.safeRelease(holder);
        }

        return buf;
    }

    /**
     * Allocates a new off-heap copy of the given buffer, unless the cost of doing so is too high.
     * The given buffer is closed if a copy is created, or returned directly.
     *
     * @param buf The buffer to copy.
     * @return Probably an off-heap copy of the given buffer.
     */
    protected final Buffer newDirectBuffer(Buffer buf) {
        if (buf.readableBytes() == 0) {
            // Don't bother allocating a zero-sized buffer. They will not cause IO anyway.
            return buf;
        }

        BufferAllocator bufferAllocator = bufferAllocator();
        if (bufferAllocator.getAllocationType() != StandardAllocationTypes.OFF_HEAP) {
            bufferAllocator = DefaultBufferAllocators.offHeapAllocator();
        }
        if (bufferAllocator.isPooling()) {
            try (buf) {
                return bufferAllocator.allocate(buf.readableBytes()).writeBytes(buf);
            }
        }
        return buf; // Un-pooled off-heap allocation is too expensive. Give up.
    }

    /**
     * Allocates a new off-heap copy of the given buffer, unless the cost of doing so is too high.
     * The given holder is closed regardless.
     *
     * @param buf The buffer to copy.
     * @return Probably an off-heap copy of the given buffer.
     */
    protected final Buffer newDirectBuffer(Resource<?> holder, Buffer buf) {
        try (holder) {
            BufferAllocator bufferAllocator = bufferAllocator();
            if (bufferAllocator.getAllocationType() != StandardAllocationTypes.OFF_HEAP) {
                bufferAllocator = DefaultBufferAllocators.offHeapAllocator();
            }
            if (bufferAllocator.isPooling()) {
                return bufferAllocator.allocate(buf.readableBytes()).writeBytes(buf);
            }
            // Un-pooled off-heap allocation is too expensive. Give up.
            // Use split() to grab the readable part of the buffer; the remainder will be closed along with its holder.
            return buf.split();
        }
    }

    @Override
    protected void doClose() throws Exception {
        Promise<Void> promise = connectPromise;
        if (promise != null) {
            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(new ClosedChannelException());
            connectPromise = null;
        }

        Future<?> future = connectTimeoutFuture;
        if (future != null) {
            future.cancel();
            connectTimeoutFuture = null;
        }
    }
}
