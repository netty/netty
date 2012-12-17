/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.MessageBuf;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.internal.DetectionUtil;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A skeletal {@link Channel} implementation.
 */
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannel.class);

    static final ConcurrentMap<Integer, Channel> allChannels = new ConcurrentHashMap<Integer, Channel>();

    private static final Random random = new Random();

    /**
     * Generates a negative unique integer ID.  This method generates only
     * negative integers to avoid conflicts with user-specified IDs where only
     * non-negative integers are allowed.
     */
    private static Integer allocateId(Channel channel) {
        int idVal = random.nextInt();
        if (idVal > 0) {
            idVal = -idVal;
        } else if (idVal == 0) {
            idVal = -1;
        }

        Integer id;
        for (;;) {
            id = Integer.valueOf(idVal);
            // Loop until a unique ID is acquired.
            // It should be found in one loop practically.
            if (allChannels.putIfAbsent(id, channel) == null) {
                // Successfully acquired.
                return id;
            } else {
                // Taken by other channel at almost the same moment.
                idVal --;
                if (idVal >= 0) {
                    idVal = -1;
                }
            }
        }
    }

    private final Channel parent;
    private final Integer id;
    private final Unsafe unsafe;
    private final DefaultChannelPipeline pipeline;
    private final ChannelFuture succeededFuture = new SucceededChannelFuture(this);
    private final ChannelFuture voidFuture = new VoidChannelFuture(this);
    private final CloseFuture closeFuture = new CloseFuture(this);

    protected final ChannelFlushFutureNotifier flushFutureNotifier = new ChannelFlushFutureNotifier();

    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;
    private volatile EventLoop eventLoop;
    private volatile boolean registered;

    private ClosedChannelException closedChannelException;
    private boolean inFlushNow;
    private boolean flushNowPending;

    /** Cache for the string representation of this channel */
    private boolean strValActive;
    private String strVal;

    private AbstractUnsafe.FlushTask flushTaskInProgress;

    /**
     * Creates a new instance.
     *
     * @param id
     *        the unique non-negative integer ID of this channel.
     *        Specify {@code null} to auto-generate a unique negative integer
     *        ID.
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(Channel parent, Integer id) {
        if (id == null) {
            id = allocateId(this);
        } else {
            if (id.intValue() < 0) {
                throw new IllegalArgumentException("id: " + id + " (expected: >= 0)");
            }
            if (allChannels.putIfAbsent(id, this) != null) {
                throw new IllegalArgumentException("duplicate ID: " + id);
            }
        }

        this.parent = parent;
        this.id = id;
        unsafe = newUnsafe();
        pipeline = new DefaultChannelPipeline(this);

        closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                allChannels.remove(id());
            }
        });
    }

    @Override
    public final Integer id() {
        return id;
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        return config().getAllocator();
    }

    @Override
    public EventLoop eventLoop() {
        EventLoop eventLoop = this.eventLoop;
        if (eventLoop == null) {
            throw new IllegalStateException("channel not registered to an event loop");
        }
        return eventLoop;
    }

    @Override
    public SocketAddress localAddress() {
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress = unsafe().localAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    protected void invalidateLocalAddress() {
        localAddress = null;
    }

    @Override
    public SocketAddress remoteAddress() {
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress = unsafe().remoteAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    protected void invalidateRemoteAddress() {
        remoteAddress = null;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return pipeline.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return pipeline.close();
    }

    @Override
    public ChannelFuture deregister() {
        return pipeline.deregister();
    }

    @Override
    public ChannelFuture flush() {
        return pipeline.flush();
    }

    @Override
    public ChannelFuture write(Object message) {
        return pipeline.write(message);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelFuture future) {
        return pipeline.bind(localAddress, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelFuture future) {
        return pipeline.connect(remoteAddress, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
        return pipeline.connect(remoteAddress, localAddress, future);
    }

    @Override
    public ChannelFuture disconnect(ChannelFuture future) {
        return pipeline.disconnect(future);
    }

    @Override
    public ChannelFuture close(ChannelFuture future) {
        return pipeline.close(future);
    }

    @Override
    public ChannelFuture deregister(ChannelFuture future) {
        return pipeline.deregister(future);
    }

    @Override
    public ByteBuf outboundByteBuffer() {
        return pipeline.outboundByteBuffer();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> outboundMessageBuffer() {
        return (MessageBuf<T>) pipeline.outboundMessageBuffer();
    }

    @Override
    public ChannelFuture flush(ChannelFuture future) {
        return pipeline.flush(future);
    }

    @Override
    public ChannelFuture write(Object message, ChannelFuture future) {
        return pipeline.write(message, future);
    }

    @Override
    public ChannelFuture newFuture() {
        return new DefaultChannelFuture(this, false);
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return succeededFuture;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(this, cause);
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture;
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    @Override
    public ChannelFuture sendFile(FileRegion region) {
        return pipeline.sendFile(region);
    }

    @Override
    public ChannelFuture sendFile(FileRegion region, ChannelFuture future) {
        return pipeline.sendFile(region, future);
    }

    protected abstract Unsafe newUnsafe();

    /**
     * Returns the ID of this channel.
     */
    @Override
    public final int hashCode() {
        return id;
    }

    /**
     * Returns {@code true} if and only if the specified object is identical
     * with this channel (i.e: {@code this == o}).
     */
    @Override
    public final boolean equals(Object o) {
        return this == o;
    }

    /**
     * Compares the {@linkplain #id() ID} of the two channels.
     */
    @Override
    public final int compareTo(Channel o) {
        return id().compareTo(o.id());
    }

    /**
     * Returns the {@link String} representation of this channel.  The returned
     * string contains the {@linkplain #id() ID}, {@linkplain #localAddress() local address},
     * and {@linkplain #remoteAddress() remote address} of this channel for
     * easier identification.
     */
    @Override
    public String toString() {
        boolean active = isActive();
        if (strValActive == active && strVal != null) {
            return strVal;
        }

        SocketAddress remoteAddr = remoteAddress();
        SocketAddress localAddr = localAddress();
        if (remoteAddr != null) {
            SocketAddress srcAddr;
            SocketAddress dstAddr;
            if (parent == null) {
                srcAddr = localAddr;
                dstAddr = remoteAddr;
            } else {
                srcAddr = remoteAddr;
                dstAddr = localAddr;
            }
            strVal = String.format("[id: 0x%08x, %s %s %s]", id, srcAddr, active? "=>" : ":>", dstAddr);
        } else if (localAddr != null) {
            strVal = String.format("[id: 0x%08x, %s]", id, localAddr);
        } else {
            strVal = String.format("[id: 0x%08x]", id);
        }

        strValActive = active;
        return strVal;
    }

    protected abstract class AbstractUnsafe implements Unsafe {

        private final class FlushTask {
            final FileRegion region;
            final ChannelFuture future;
            FlushTask next;

            FlushTask(FileRegion region, ChannelFuture future) {
                this.region = region;
                this.future = future;
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        flushTaskInProgress = next;
                        if (next != null) {
                            try {
                                FileRegion region = next.region;
                                if (region == null) {
                                    // no region present means the next flush task was to directly flush
                                    // the outbound buffer
                                    flushNotifierAndFlush(future);
                                } else {
                                    // flush the region now
                                    doFlushFileRegion(region, future);
                                }
                            } catch (Throwable cause) {
                                future.setFailure(cause);
                            }
                        } else {
                            // notify the flush futures
                            flushFutureNotifier.notifyFlushFutures();
                        }
                    }
                });
            }
        }

        private final Runnable flushLaterTask = new Runnable() {
            @Override
            public void run() {
                flushNowPending = false;
                flush(voidFuture());
            }
        };

        @Override
        public final void sendFile(final FileRegion region, final ChannelFuture future) {

            if (eventLoop().inEventLoop()) {
                if (outboundBufSize() > 0) {
                    flushNotifier(newFuture()).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture cf) throws Exception {
                            sendFile0(region, future);
                        }
                    });
                } else {
                    // nothing pending try to send the fileRegion now!
                    sendFile0(region, future);
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        sendFile(region, future);
                    }
                });
            }
        }

        private void sendFile0(FileRegion region, ChannelFuture future) {
            if (flushTaskInProgress == null) {
                flushTaskInProgress = new FlushTask(region, future);
                try {
                    // the first FileRegion to flush so trigger it now!
                    doFlushFileRegion(region, future);
                } catch (Throwable cause) {
                    region.close();
                    future.setFailure(cause);
                }
                return;
            }

            FlushTask task = flushTaskInProgress;
            for (;;) {
                FlushTask next = task.next;
                if (next == null) {
                    break;
                }
                task = next;
            }
            // there is something that needs to get flushed first so add it as next in the chain
            task.next = new FlushTask(region, future);
        }

        @Override
        public final ChannelHandlerContext directOutboundContext() {
            return pipeline.head;
        }

        @Override
        public final ChannelFuture voidFuture() {
            return voidFuture;
        }

        @Override
        public final SocketAddress localAddress() {
            return localAddress0();
        }

        @Override
        public final SocketAddress remoteAddress() {
            return remoteAddress0();
        }

        @Override
        public final void register(EventLoop eventLoop, final ChannelFuture future) {
            if (eventLoop == null) {
                throw new NullPointerException("eventLoop");
            }
            if (isRegistered()) {
                throw new IllegalStateException("registered to an event loop already");
            }
            if (!isCompatible(eventLoop)) {
                throw new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName());
            }

            AbstractChannel.this.eventLoop = eventLoop;

            assert eventLoop().inEventLoop();

            if (!ensureOpen(future)) {
                return;
            }

            // check if the eventLoop which was given is currently in the eventloop.
            // if that is the case we are safe to call register, if not we need to
            // schedule the execution as otherwise we may say some race-conditions.
            //
            // See https://github.com/netty/netty/issues/654
            if (eventLoop.inEventLoop()) {
                register0(future);
            } else {
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        register0(future);
                    }
                });
            }
        }

        private void register0(ChannelFuture future) {
            try {
                Runnable postRegisterTask = doRegister();
                registered = true;
                future.setSuccess();
                pipeline.fireChannelRegistered();
                if (postRegisterTask != null) {
                    postRegisterTask.run();
                }
                if (isActive()) {
                    pipeline.fireChannelActive();
                }
            } catch (Throwable t) {
                // Close the channel directly to avoid FD leak.
                try {
                    doClose();
                } catch (Throwable t2) {
                    logger.warn("Failed to close a channel", t2);
                }

                future.setFailure(t);
                closeFuture.setClosed();
            }
        }

        @Override
        public final void bind(final SocketAddress localAddress, final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(future)) {
                    return;
                }

                try {
                    boolean wasActive = isActive();

                    // See: https://github.com/netty/netty/issues/576
                    if (!DetectionUtil.isWindows() && !DetectionUtil.isRoot() &&
                        Boolean.TRUE.equals(config().getOption(ChannelOption.SO_BROADCAST)) &&
                        localAddress instanceof InetSocketAddress &&
                        !((InetSocketAddress) localAddress).getAddress().isAnyLocalAddress()) {
                        // Warn a user about the fact that a non-root user can't receive a
                        // broadcast packet on *nix if the socket is bound on non-wildcard address.
                        logger.warn(
                                "A non-root user can't receive a broadcast packet if the socket " +
                                "is not bound to a wildcard address; binding to a non-wildcard " +
                                "address (" + localAddress + ") anyway as requested.");
                    }

                    doBind(localAddress);
                    future.setSuccess();
                    if (!wasActive && isActive()) {
                        pipeline.fireChannelActive();
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        bind(localAddress, future);
                    }
                });
            }
        }

        @Override
        public final void disconnect(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                try {
                    boolean wasActive = isActive();
                    doDisconnect();
                    future.setSuccess();
                    if (wasActive && !isActive()) {
                        pipeline.fireChannelInactive();
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        disconnect(future);
                    }
                });
            }
        }

        @Override
        public final void close(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                boolean wasActive = isActive();
                if (closeFuture.setClosed()) {
                    try {
                        doClose();
                        future.setSuccess();
                    } catch (Throwable t) {
                        future.setFailure(t);
                    }

                    if (closedChannelException != null) {
                        closedChannelException = new ClosedChannelException();
                    }

                    flushFutureNotifier.notifyFlushFutures(closedChannelException);

                    if (wasActive && !isActive()) {
                        pipeline.fireChannelInactive();
                    }

                    deregister(voidFuture());
                } else {
                    // Closed already.
                    future.setSuccess();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        close(future);
                    }
                });
            }
        }

        @Override
        public final void closeForcibly() {
            try {
                doClose();
            } catch (Exception e) {
                logger.warn("Failed to close a channel.", e);
            }
        }

        @Override
        public final void deregister(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!registered) {
                    future.setSuccess();
                    return;
                }

                try {
                    doDeregister();
                } catch (Throwable t) {
                    logger.warn("Unexpected exception occurred while deregistering a channel.", t);
                } finally {
                    if (registered) {
                        registered = false;
                        future.setSuccess();
                        pipeline.fireChannelUnregistered();
                    } else {
                        // Some transports like local and AIO does not allow the deregistration of
                        // an open channel.  Their doDeregister() calls close().  Consequently,
                        // close() calls deregister() again - no need to fire channelUnregistered.
                        future.setSuccess();
                    }
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        deregister(future);
                    }
                });
            }
        }

        @Override
        public void flush(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {

                if (flushTaskInProgress != null) {
                    FlushTask task = flushTaskInProgress;

                    // loop over the tasks to find the last one
                    for (;;) {
                        FlushTask t = task.next;
                        if (t == null) {
                            break;
                        }
                        task = t.next;
                    }
                    task.next = new FlushTask(null, future);

                    return;
                }
                flushNotifierAndFlush(future);
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        flush(future);
                    }
                });
            }
        }

        private void flushNotifierAndFlush(ChannelFuture future) {
            flushNotifier(future);
            flush0();
        }

        private int outboundBufSize() {
            final int bufSize;
            final ChannelHandlerContext ctx = directOutboundContext();
            if (ctx.hasOutboundByteBuffer()) {
                bufSize = ctx.outboundByteBuffer().readableBytes();
            } else {
                bufSize = ctx.outboundMessageBuffer().size();
            }
            return bufSize;
        }

        private ChannelFuture flushNotifier(ChannelFuture future) {
            // Append flush future to the notification list.
            if (future != voidFuture) {
                flushFutureNotifier.addFlushFuture(future, outboundBufSize());
            }
            return future;
        }

        private void flush0() {
            if (!inFlushNow) { // Avoid re-entrance
                try {
                    if (!isFlushPending()) {
                        flushNow();
                    } else {
                        // Event loop will call flushNow() later by itself.
                    }
                } catch (Throwable t) {
                    flushFutureNotifier.notifyFlushFutures(t);
                    if (t instanceof IOException) {
                        close(voidFuture());
                    }
                } finally {
                    if (!isActive()) {
                        close(unsafe().voidFuture());
                    }
                }
            } else {
                if (!flushNowPending) {
                    flushNowPending = true;
                    eventLoop().execute(flushLaterTask);
                }
            }
        }
        @Override
        public final void flushNow() {
            if (inFlushNow || flushTaskInProgress != null) {
                return;
            }

            inFlushNow = true;
            ChannelHandlerContext ctx = directOutboundContext();
            Throwable cause = null;
            try {
                if (ctx.hasOutboundByteBuffer()) {
                    ByteBuf out = ctx.outboundByteBuffer();
                    int oldSize = out.readableBytes();
                    try {
                        doFlushByteBuffer(out);
                    } catch (Throwable t) {
                        cause = t;
                    } finally {
                        final int newSize = out.readableBytes();
                        final int writtenBytes = oldSize - newSize;
                        if (writtenBytes > 0) {
                            flushFutureNotifier.increaseWriteCounter(writtenBytes);
                            if (newSize == 0) {
                                out.discardReadBytes();
                            }
                        }
                    }
                } else {
                    MessageBuf<Object> out = ctx.outboundMessageBuffer();
                    int oldSize = out.size();
                    try {
                        doFlushMessageBuffer(out);
                    } catch (Throwable t) {
                        cause = t;
                    } finally {
                        flushFutureNotifier.increaseWriteCounter(oldSize - out.size());
                    }
                }

                if (cause == null) {
                    flushFutureNotifier.notifyFlushFutures();
                } else {
                    flushFutureNotifier.notifyFlushFutures(cause);
                    if (cause instanceof IOException) {
                        close(voidFuture());
                    }
                }
            } finally {
                inFlushNow = false;
            }
        }

        protected final boolean ensureOpen(ChannelFuture future) {
            if (isOpen()) {
                return true;
            }

            Exception e = new ClosedChannelException();
            future.setFailure(e);
            return false;
        }

        protected final void closeIfClosed() {
            if (isOpen()) {
                return;
            }
            close(voidFuture());
        }
    }

    protected abstract boolean isCompatible(EventLoop loop);

    protected abstract SocketAddress localAddress0();
    protected abstract SocketAddress remoteAddress0();

    protected abstract Runnable doRegister() throws Exception;
    protected abstract void doBind(SocketAddress localAddress) throws Exception;
    protected abstract void doDisconnect() throws Exception;
    protected void doPreClose() throws Exception {
        // NOOP by default
    }

    protected abstract void doClose() throws Exception;
    protected abstract void doDeregister() throws Exception;
    protected void doFlushByteBuffer(ByteBuf buf) throws Exception {
        throw new UnsupportedOperationException();
    }
    protected void doFlushMessageBuffer(MessageBuf<Object> buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    protected void doFlushFileRegion(FileRegion region, ChannelFuture future) throws Exception {
        throw new UnsupportedOperationException();
    }

    protected static void checkEOF(FileRegion region, long writtenBytes) throws IOException {
        if (writtenBytes < region.count()) {
            throw new EOFException("Expected to be able to write "
                    + region.count() + " bytes, but only wrote "
                    + writtenBytes);
        }
    }

    protected abstract boolean isFlushPending();

    private final class CloseFuture extends DefaultChannelFuture implements ChannelFuture.Unsafe {

        CloseFuture(AbstractChannel ch) {
            super(ch, false);
        }

        @Override
        public boolean setSuccess() {
            throw new IllegalStateException();
        }

        @Override
        public boolean setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        boolean setClosed() {
            try {
                doPreClose();
            } catch (Exception e) {
                logger.warn("doPreClose() raised an exception.", e);
            }
            return super.setSuccess();
        }
    }
}
