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

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.EventLoop;
import io.netty5.channel.IoEvent;
import io.netty5.channel.IoHandle;
import io.netty5.channel.IoRegistration;
import io.netty5.channel.ReadHandleFactory;
import io.netty5.channel.WriteHandleFactory;
import io.netty5.util.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for {@link Channel} implementations which use a Selector based approach.
 */
public abstract class AbstractNioChannel<P extends Channel, L extends SocketAddress, R extends SocketAddress>
        extends AbstractChannel<P, L, R> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractNioChannel.class);

    private final SelectableChannel ch;
    protected final NioIoOps readOps;

    private final NioIoHandle handle = new NioIoHandle() {
        @Override
        public SelectableChannel selectableChannel() {
            return ch;
        }

        @Override
        public void handle(IoRegistration registration, IoEvent event) {
            try {
                NioIoEvent nioEvent = (NioIoEvent) event;
                NioIoOps nioReadyOps = nioEvent.ops();
                // We first need to call finishConnect() before try to trigger a read(...) or write(...) as otherwise
                // the NIO JDK channel implementation may throw a NotYetConnectedException.
                if (nioReadyOps.contains(NioIoOps.CONNECT)) {
                    // remove OP_CONNECT as otherwise Selector.select(..) will always return without blocking
                    // See https://github.com/netty/netty/issues/924
                    removeAndSubmit(NioIoOps.CONNECT);

                    finishConnectNow();
                }

                // Process OP_WRITE first as we may be able to write some queued buffers and so free memory.
                if (nioReadyOps.contains(NioIoOps.WRITE)) {
                    // Call forceFlush which will also take care of clear the OP_WRITE once there is nothing left to
                    // write
                    writeFlushedNow();
                }

                // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead
                // to a spin loop
                if (nioReadyOps.contains(NioIoOps.READ_AND_ACCEPT) || nioReadyOps.equals(NioIoOps.NONE)) {
                    readNow();
                }
            } catch (CancelledKeyException ignored) {
                closeTransportNow();
            }
        }

        @Override
        public void close() throws Exception {
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
     * @param readOps                   the ops to set to receive data from the {@link SelectableChannel}
     */
    protected AbstractNioChannel(P parent, EventLoop eventLoop, boolean supportingDisconnect,
                                 ReadHandleFactory defaultReadHandleFactory,
                                 WriteHandleFactory defaultWriteHandleFactory,
                                 SelectableChannel ch, NioIoOps readOps) {
        super(parent, eventLoop, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory,
                NioIoHandle.class);
        this.ch = ch;
        this.readOps = requireNonNull(readOps, "readOps");
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

    protected void addAndSubmit(NioIoOps addOps) {
        int interestOps = registration().selectionKey().interestOps();
        if (!addOps.isIncludedIn(interestOps)) {
            try {
                registration().submit(NioIoOps.valueOf(interestOps).with(addOps));
            } catch (Exception e) {
                throw new ChannelException(e);
            }
        }
    }

    protected void removeAndSubmit(NioIoOps removeOps) {
        int interestOps = registration().selectionKey().interestOps();
        if (removeOps.isIncludedIn(interestOps)) {
            try {
                registration().submit(NioIoOps.valueOf(interestOps).without(removeOps));
            } catch (Exception e) {
                throw new ChannelException(e);
            }
        }
    }

    @Override
    protected final IoHandle ioHandle() {
        return handle;
    }

    @Override
    protected final NioIoRegistration registration() {
        return (NioIoRegistration) super.registration();
    }

    @Override
    public final boolean isOpen() {
        return ch.isOpen();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }

    @Override
    protected final void doClearScheduledRead() {
        if (isRegistered()) {
            NioIoRegistration reg = registration();
            // Check first if the key is still valid as it may be canceled as part of the deregistration
            // from the EventLoop
            // See https://github.com/netty/netty/issues/2104
            if (!reg.isValid()) {
                return;
            }
            removeAndSubmit(readOps);
        }
    }

    @Override
    protected final boolean isWriteFlushedScheduled() {
        if (isRegistered()) {
            // Flush immediately only when there's no pending flush.
            NioIoRegistration registration = registration();
            return registration.isValid() && NioIoOps.WRITE.isIncludedIn(registration.selectionKey().interestOps());
        }
        return false;
    }

    @Override
    protected final void doRead(boolean wasReadPendingAlready) {
        if (wasReadPendingAlready) {
            // We already had doRead(...) called before and so set the interestedOps.
            return;
        }
        // Channel.read() or ChannelHandlerContext.read() was called
        NioIoRegistration registration = registration();
        if (registration == null || !registration.isValid()) {
            return;
        }

        addAndSubmit(readOps);
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
    protected final void writeLoopComplete(boolean allWritten) {
        final NioIoRegistration reg = registration();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (reg.isValid()) {
            // Did not write completely.
            if (!allWritten) {
                setOpWrite();
            } else {
                clearOpWrite();
            }
        }
        super.writeLoopComplete(allWritten);
    }

    protected final void setOpWrite() {
        final NioIoRegistration registration = registration();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!registration.isValid()) {
            return;
        }

        addAndSubmit(NioIoOps.WRITE);
    }

    protected final void clearOpWrite() {
        final NioIoRegistration registration = registration();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!registration.isValid()) {
            return;
        }
        removeAndSubmit(NioIoOps.WRITE);
    }

    private void closeTransportNow() {
        closeTransport(newPromise());
    }

    private void finishConnectNow() {
        finishConnect();
    }
}
