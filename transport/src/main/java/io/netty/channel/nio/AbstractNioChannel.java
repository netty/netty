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
package io.netty.channel.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.EventLoop;
import io.netty.channel.IoEvent;
import io.netty.channel.IoRegistration;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Abstract base class for {@link Channel} implementations which use a Selector based approach.
 */
public abstract class AbstractNioChannel extends AbstractChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractNioChannel.class);

    private final NioIoHandle ioHandle = new NioIoHandleImpl();
    private final SelectableChannel ch;
    protected final int readInterestOp;
    protected final NioIoOps readOps;
    volatile IoRegistration registration;
    boolean readPending;
    private final Runnable clearReadPendingRunnable = new Runnable() {
        @Override
        public void run() {
            setReadPending0(false);
        }
    };

    private Promise<Void> pendingConnectPromise;

    protected AbstractNioChannel(EventLoop eventLoop, Channel parent, SelectableChannel ch, NioIoOps readOps,
                                 boolean hasDisconnect) {
        super(eventLoop, NioIoHandle.class, parent, DefaultChannelId.newInstance(), hasDisconnect);
        this.ch = ch;
        this.readInterestOp = ObjectUtil.checkNotNull(readOps, "readOps").value;
        this.readOps = readOps;
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
        int interestOps = selectionKey().interestOps();
        if (!addOps.isIncludedIn(interestOps)) {
            try {
                registration().submit(NioIoOps.valueOf(interestOps).with(addOps));
            } catch (Exception e) {
                throw new ChannelException(e);
            }
        }
    }

    protected void removeAndSubmit(NioIoOps removeOps) {
        int interestOps = selectionKey().interestOps();
        if (removeOps.isIncludedIn(interestOps)) {
            try {
                registration().submit(NioIoOps.valueOf(interestOps).without(removeOps));
            } catch (Exception e) {
                throw new ChannelException(e);
            }
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
     * Return the current {@link SelectionKey}
     *
     * @deprecated use {@link #registration}.
     */
    @Deprecated
    protected SelectionKey selectionKey() {
        return registration().attachment();
    }

    @SuppressWarnings("unchecked")
    protected IoRegistration registration() {
        assert registration != null;
        return registration;
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
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        setReadPending0(readPending);
                    }
                });
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
                setReadPending0(false);
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

    @Override
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
        final boolean connected;
        try {
            connected = doConnect(remoteAddress, localAddress);
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        if (connected) {
            promise.setSuccess(null);
        } else {
            pendingConnectPromise = promise;
        }
    }

    @Override
    protected final boolean isWriteFlushedScheduled() {
        IoRegistration registration = registration();
        return registration.isValid() && NioIoOps.WRITE.isIncludedIn((
                (SelectionKey) registration.attachment()).interestOps());
    }

    private final class NioIoHandleImpl implements NioIoHandle {
        @Override
        public void close() {
            ioTransport().close(CompletionHandler.ignore());
        }

        @Override
        public SelectableChannel selectableChannel() {
            return javaChannel();
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
                    finishConnect();
                }

                // Process OP_WRITE first as we may be able to write some queued buffers and so free memory.
                if (nioReadyOps.contains(NioIoOps.WRITE)) {
                    // Call writeFlushedNow which will also take care of clear the OP_WRITE once there is nothing left
                    // to write
                    writeFlushedNow();
                }

                // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead
                // to a spin loop
                if (nioReadyOps.contains(NioIoOps.READ_AND_ACCEPT) || nioReadyOps.equals(NioIoOps.NONE)) {
                    readNow();
                }
            } catch (CancelledKeyException ignored) {
                ioTransport().close(CompletionHandler.ignore());
            }
        }
    }

    protected abstract void readNow();

    protected final void finishConnect() {
        // Note this method is invoked by the event loop only if the connection attempt was
        // neither cancelled nor timed out.

        assert executor().inEventLoop();
        assert pendingConnectPromise != null;
        Promise<Void> promise = pendingConnectPromise;
        pendingConnectPromise = null;
        try {
            doFinishConnect();
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess(null);
    }

    protected final void removeReadOp() {
        IoRegistration registration = registration();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!registration.isValid()) {
            return;
        }
        removeAndSubmit(readOps);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doRegister(Promise<Void> promise) {
        assert registration == null;
        executor().register(ioHandle).addListener(f -> {
            if (f.isSuccess()) {
                registration = (IoRegistration) f.getNow();
                promise.setSuccess(null);
            } else {
                promise.setFailure(f.cause());
            }
        });
    }

    @Override
    protected void doDeregister(Promise<Void> promise) {
        IoRegistration registration = this.registration;
        if (registration != null) {
            this.registration = null;
            registration.cancel();
        }
        promise.setSuccess(null);
    }

    @Override
    protected void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        IoRegistration registration = this.registration;
        if (registration == null || !registration.isValid()) {
            return;
        }

        readPending = true;

        addAndSubmit(readOps);
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
}
