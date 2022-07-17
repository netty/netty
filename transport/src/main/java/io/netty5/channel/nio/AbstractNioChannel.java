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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.util.Resource;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.EventLoop;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * Abstract base class for {@link Channel} implementations which use a Selector based approach.
 */
public abstract class AbstractNioChannel<P extends Channel, L extends SocketAddress, R extends SocketAddress>
        extends AbstractChannel<P, L, R> {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractNioChannel.class);

    private final SelectableChannel ch;
    protected final int readInterestOp;
    volatile SelectionKey selectionKey;
    boolean readPending;
    private final Runnable clearReadPendingRunnable = this::clearReadPending0;

    private final NioProcessor nioProcessor = new NioProcessor() {
        @Override
        public void register(Selector selector) throws ClosedChannelException {
            int interestOps;
            SelectionKey key = selectionKey;
            if (key != null) {
                interestOps = key.interestOps();
                key.cancel();
            } else {
                interestOps = 0;
            }
            selectionKey = javaChannel().register(selector, interestOps, this);
        }

        @Override
        public void deregister() {
            SelectionKey key = selectionKey;
            if (key != null) {
                key.cancel();
                selectionKey = null;
            }
        }

        @Override
        public void handle(SelectionKey k) {
            if (!k.isValid()) {

                // close the channel if the key is not valid anymore
                closeTransportNow();
                return;
            }

            try {
                int readyOps = k.readyOps();
                // We first need to call finishConnect() before try to trigger a read(...) or write(...) as otherwise
                // the NIO JDK channel implementation may throw a NotYetConnectedException.
                if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                    // remove OP_CONNECT as otherwise Selector.select(..) will always return without blocking
                    // See https://github.com/netty/netty/issues/924
                    int ops = k.interestOps();
                    ops &= ~SelectionKey.OP_CONNECT;
                    k.interestOps(ops);

                    finishConnectNow();
                }

                // Process OP_WRITE first as we may be able to write some queued buffers and so free memory.
                if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                    // Call forceFlush which will also take care of clear the OP_WRITE once there is nothing left to
                    // write
                    forceFlush();
                }

                // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead
                // to a spin loop
                if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                    readNow();
                }
            } catch (CancelledKeyException ignored) {
                closeTransportNow();
            }
        }

        @Override
        public void close() {
            closeTransportNow();
        }
    };

    /**
     * Create a new instance
     *
     * @param parent                the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param eventLoop             the {@link EventLoop} to use for all I/O.
     * @param metadata              the {@link ChannelMetadata} to use.
     * @param defaultRecvAllocator  the default {@link RecvBufferAllocator} to use.
     * @param ch                    the underlying {@link SelectableChannel} on which it operates
     * @param readInterestOp        the ops to set to receive data from the {@link SelectableChannel}
     */
    protected AbstractNioChannel(P parent, EventLoop eventLoop, ChannelMetadata metadata,
                                 RecvBufferAllocator defaultRecvAllocator,
                                 SelectableChannel ch, int readInterestOp) {
        super(parent, eventLoop, metadata, defaultRecvAllocator);
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

    protected SelectableChannel javaChannel() {
        return ch;
    }

    /**
     * Return the current {@link SelectionKey} or {@code null} if the underlying channel was not registered with the
     * {@link Selector} yet.
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
            removeReadOp();
        }
    }

    private void clearReadPending0() {
        readPending = false;
        removeReadOp();
    }

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
    protected final void writeFlushed() {
        // Flush immediately only when there's no pending flush.
        // If there's a pending flush operation, event loop will call forceFlush() later,
        // and thus there's no need to call it now.
        if (!isFlushPending()) {
            super.writeFlushed();
        }
    }

    final void forceFlush() {
        // directly call super.flush0() to force a flush now
        super.writeFlushed();
    }

    private boolean isFlushPending() {
        SelectionKey selectionKey = selectionKey();
        return selectionKey != null && selectionKey.isValid()
                && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
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

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
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
        if (!bufferAllocator.getAllocationType().isDirect()) {
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
            if (!bufferAllocator.getAllocationType().isDirect()) {
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

    protected abstract void readNow();

    private void closeTransportNow() {
        closeTransport(newPromise());
    }

    private void finishConnectNow() {
        finishConnect();
    }

    final NioProcessor nioProcessor() {
        return nioProcessor;
    }
}
