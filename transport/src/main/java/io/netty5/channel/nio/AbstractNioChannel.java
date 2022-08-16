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
import io.netty5.channel.ReadHandleFactory;
import io.netty5.channel.WriteHandleFactory;
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
    private final int readInterestOp;
    private volatile SelectionKey selectionKey;

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
                    writeFlushedNow();
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
     * @param parent                    the parent {@link Channel} by which this instance was created.
     *                                  May be {@code null}
     * @param eventLoop                 the {@link EventLoop} to use for all I/O.
     * @param supportingDisconnect      {@code true} if and only if the channel has the {@code disconnect()}
     *                                  operation that allows a user to disconnect and then call {
     *                                  @link Channel#connect(SocketAddress)} again, such as UDP/IP.
     * @param defaultReadHandleFactory  the default {@link ReadHandleFactory} to use.
     * @param defaultWriteHandleFactory the {@link WriteHandleFactory} that is used by default.
     * @param ch                        the underlying {@link SelectableChannel} on which it operates
     * @param readInterestOp            the ops to set to receive data from the {@link SelectableChannel}
     */
    protected AbstractNioChannel(P parent, EventLoop eventLoop, boolean supportingDisconnect,
                                 ReadHandleFactory defaultReadHandleFactory,
                                 WriteHandleFactory defaultWriteHandleFactory,
                                 SelectableChannel ch, int readInterestOp) {
        super(parent, eventLoop, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory);
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
    public final boolean isOpen() {
        return ch.isOpen();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }

    /**
     * Return the current {@link SelectionKey} or {@code null} if the underlying channel was not registered with the
     * {@link Selector} yet.
     */
    protected final SelectionKey selectionKey() {
        return selectionKey;
    }

    @Override
    protected final void doClearScheduledRead() {
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
    protected final boolean isWriteFlushedScheduled() {
        // Flush immediately only when there's no pending flush.
        SelectionKey selectionKey = selectionKey();
        return selectionKey != null && selectionKey.isValid()
                && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
    }

    @Override
    protected final void doRead(boolean wasReadPendingAlready) {
        if (wasReadPendingAlready) {
            // We already had doRead(...) called before and so set the interestedOps.
            return;
        }
        // Channel.read() or ChannelHandlerContext.read() was called
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            // No valid anymore
            return;
        }

        final int interestOps = selectionKey.interestOps();
        if ((interestOps & readInterestOp) == 0) {
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

    @Override
    protected final void doClose() throws Exception {
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

        BufferAllocator bufferAllocator = ioBufferAllocator(bufferAllocator());
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
            BufferAllocator bufferAllocator = ioBufferAllocator(bufferAllocator());
            if (bufferAllocator.isPooling()) {
                return bufferAllocator.allocate(buf.readableBytes()).writeBytes(buf);
            }
            // Un-pooled off-heap allocation is too expensive. Give up.
            // Use split() to grab the readable part of the buffer; the remainder will be closed along with its holder.
            return buf.split();
        }
    }

    private static BufferAllocator ioBufferAllocator(BufferAllocator bufferAllocator) {
        if (!bufferAllocator.getAllocationType().isDirect()) {
            return DefaultBufferAllocators.offHeapAllocator();
        }
        return bufferAllocator;
    }

    @Override
    protected final void writeLoopComplete(boolean allWritten) throws Exception {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (key != null && key.isValid()) {
            final int interestOps = key.interestOps();
            // Did not write completely.
            if (!allWritten) {
                if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(interestOps | SelectionKey.OP_WRITE);
                }
            } else {
                if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                    key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
                }
            }
        }
        super.writeLoopComplete(allWritten);
    }

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
