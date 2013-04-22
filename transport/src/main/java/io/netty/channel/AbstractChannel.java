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

import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.MessageBuf;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;

/**
 * A skeletal {@link Channel} implementation.
 */
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannel.class);

    static final ConcurrentMap<Integer, Channel> allChannels = PlatformDependent.newConcurrentHashMap();

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
    private final ChannelFuture succeededFuture = new SucceededChannelFuture(this, null);
    private final VoidChannelPromise voidPromise = new VoidChannelPromise(this);
    private final CloseFuture closeFuture = new CloseFuture(this);

    protected final ChannelFlushPromiseNotifier flushFutureNotifier = new ChannelFlushPromiseNotifier();

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

    /**
     * Reset the stored remoteAddress
     */
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
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline.disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return pipeline.close(promise);
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return pipeline.deregister(promise);
    }

    @Override
    public ByteBuf outboundByteBuffer() {
        return pipeline.outboundByteBuffer();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> outboundMessageBuffer() {
        return pipeline.outboundMessageBuffer();
    }

    @Override
    public void read() {
        pipeline.read();
    }

    @Override
    public ChannelFuture flush(ChannelPromise promise) {
        return pipeline.flush(promise);
    }

    @Override
    public ChannelFuture write(Object message, ChannelPromise promise) {
        return pipeline.write(message, promise);
    }

    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(this);
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return new DefaultChannelProgressivePromise(this);
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return succeededFuture;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(this, null, cause);
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
    public ChannelFuture sendFile(FileRegion region, ChannelPromise promise) {
        return pipeline.sendFile(region, promise);
    }

    // 0 - not expanded because the buffer is writable
    // 1 - expanded because the buffer was not writable
    // 2 - could not expand because the buffer was at its maximum although the buffer is not writable.
    protected static int expandReadBuffer(ByteBuf byteBuf) {
        final int writerIndex = byteBuf.writerIndex();
        final int capacity = byteBuf.capacity();
        if (capacity != writerIndex) {
            return 0;
        }

        final int maxCapacity = byteBuf.maxCapacity();
        if (capacity == maxCapacity) {
            return 2;
        }

        // FIXME: Magic number
        final int increment = 4096;

        if (writerIndex + increment > maxCapacity) {
            // Expand to maximum capacity.
            byteBuf.capacity(maxCapacity);
        } else {
            // Expand by the increment.
            byteBuf.ensureWritable(increment);
        }

        return 1;
    }

    /**
     * Create a new {@link AbstractUnsafe} instance which will be used for the life-time of the {@link Channel}
     */
    protected abstract AbstractUnsafe newUnsafe();

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

    /**
     * {@link Unsafe} implementation which sub-classes must extend and use.
     */
    protected abstract class AbstractUnsafe implements Unsafe {

        private final class FlushTask {
            final FileRegion region;
            final ChannelPromise promise;
            FlushTask next;

            FlushTask(FileRegion region, ChannelPromise promise) {
                this.region = region;
                this.promise = promise;
                promise.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        flushTaskInProgress = next;
                        if (next != null) {
                            try {
                                FileRegion region = next.region;
                                if (region == null) {
                                    // no region present means the next flush task was to directly flush
                                    // the outbound buffer
                                    flushNotifierAndFlush(next.promise);
                                } else {
                                    // flush the region now
                                    doFlushFileRegion(region, next.promise);
                                }
                            } catch (Throwable cause) {
                                next.promise.setFailure(cause);
                            }
                        } else {
                            // notify the flush futures
                            flushFutureNotifier.notifyFlushFutures();
                        }
                    }
                });
            }
        }

        private final Runnable beginReadTask = new Runnable() {
            @Override
            public void run() {
                beginRead();
            }
        };

        private final Runnable flushLaterTask = new Runnable() {
            @Override
            public void run() {
                flushNowPending = false;
                flush(voidFuture());
            }
        };

        private FlushTask flushTaskInProgress;

        @Override
        public final void sendFile(final FileRegion region, final ChannelPromise promise) {

            if (eventLoop().inEventLoop()) {
                if (outboundBufSize() > 0) {
                    flushNotifier(newPromise()).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture cf) throws Exception {
                            sendFile0(region, promise);
                        }
                    });
                } else {
                    // nothing pending try to send the fileRegion now!
                    sendFile0(region, promise);
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        sendFile(region, promise);
                    }
                });
            }
        }

        private void sendFile0(FileRegion region, ChannelPromise promise) {
            FlushTask task = flushTaskInProgress;
            if (task == null) {
                flushTaskInProgress = new FlushTask(region, promise);
                try {
                    // the first FileRegion to flush so trigger it now!
                    doFlushFileRegion(region, promise);
                } catch (Throwable cause) {
                    region.release();
                    promise.setFailure(cause);
                }
                return;
            }

            for (;;) {
                FlushTask next = task.next;
                if (next == null) {
                    break;
                }
                task = next;
            }
            // there is something that needs to get flushed first so add it as next in the chain
            task.next = new FlushTask(region, promise);
        }

        @Override
        public final ChannelHandlerContext directOutboundContext() {
            return pipeline.head;
        }

        @Override
        public final ChannelPromise voidFuture() {
            return voidPromise;
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
        public final void register(EventLoop eventLoop, final ChannelPromise promise) {
            if (eventLoop == null) {
                throw new NullPointerException("eventLoop");
            }
            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("registered to an event loop already"));
                return;
            }
            if (!isCompatible(eventLoop)) {
                promise.setFailure(
                        new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName()));
                return;
            }

            AbstractChannel.this.eventLoop = eventLoop;

            if (eventLoop.inEventLoop()) {
                register0(promise);
            } else {
                try {
                    eventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            register0(promise);
                        }
                    });
                } catch (Throwable t) {
                    logger.warn(
                            "Force-closing a channel whose registration task was unaccepted by an event loop: {}",
                            AbstractChannel.this, t);
                    closeForcibly();
                    promise.setFailure(t);
                }
            }
        }

        private void register0(ChannelPromise promise) {
            try {
                // check if the channel is still open as it could be closed in the mean time when the register
                // call was outside of the eventLoop
                if (!ensureOpen(promise)) {
                    return;
                }
                Runnable postRegisterTask = doRegister();
                registered = true;
                promise.setSuccess();
                pipeline.fireChannelRegistered();
                if (postRegisterTask != null) {
                    postRegisterTask.run();
                }
                if (isActive()) {
                    pipeline.fireChannelActive();
                }
            } catch (Throwable t) {
                // Close the channel directly to avoid FD leak.
                closeForcibly();
                promise.setFailure(t);
                closeFuture.setClosed();
            }
        }

        @Override
        public final void bind(final SocketAddress localAddress, final ChannelPromise promise) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(promise)) {
                    return;
                }

                try {
                    boolean wasActive = isActive();

                    // See: https://github.com/netty/netty/issues/576
                    if (!PlatformDependent.isWindows() && !PlatformDependent.isRoot() &&
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
                    promise.setSuccess();
                    if (!wasActive && isActive()) {
                        pipeline.fireChannelActive();
                    }
                } catch (Throwable t) {
                    promise.setFailure(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        bind(localAddress, promise);
                    }
                });
            }
        }

        @Override
        public final void disconnect(final ChannelPromise promise) {
            if (eventLoop().inEventLoop()) {
                try {
                    boolean wasActive = isActive();
                    doDisconnect();
                    promise.setSuccess();
                    if (wasActive && !isActive()) {
                        invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                pipeline.fireChannelInactive();
                            }
                        });
                    }
                } catch (Throwable t) {
                    promise.setFailure(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        disconnect(promise);
                    }
                });
            }
        }

        @Override
        public final void close(final ChannelPromise promise) {
            if (eventLoop().inEventLoop()) {
                boolean wasActive = isActive();
                if (closeFuture.setClosed()) {
                    try {
                        doClose();
                        promise.setSuccess();
                    } catch (Throwable t) {
                        promise.setFailure(t);
                    }

                    if (closedChannelException == null) {
                        closedChannelException = new ClosedChannelException();
                    }

                    flushFutureNotifier.notifyFlushFutures(closedChannelException);

                    if (wasActive && !isActive()) {
                        invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                pipeline.fireChannelInactive();
                            }
                        });
                    }

                    deregister(voidFuture());
                } else {
                    // Closed already.
                    promise.setSuccess();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        close(promise);
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
        public final void deregister(final ChannelPromise promise) {
            if (eventLoop().inEventLoop()) {
                if (!registered) {
                    promise.setSuccess();
                    return;
                }

                Runnable postTask = null;
                try {
                    postTask = doDeregister();
                } catch (Throwable t) {
                    logger.warn("Unexpected exception occurred while deregistering a channel.", t);
                } finally {
                    if (registered) {
                        registered = false;
                        promise.setSuccess();
                        invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                pipeline.fireChannelUnregistered();
                            }
                        });
                    } else {
                        // Some transports like local and AIO does not allow the deregistration of
                        // an open channel.  Their doDeregister() calls close().  Consequently,
                        // close() calls deregister() again - no need to fire channelUnregistered.
                        promise.setSuccess();
                    }

                    if (postTask != null) {
                        postTask.run();
                    }
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        deregister(promise);
                    }
                });
            }
        }

        @Override
        public void beginRead() {
            if (!isActive()) {
                return;
            }

            if (eventLoop().inEventLoop()) {
                try {
                    doBeginRead();
                } catch (final Exception e) {
                    invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            pipeline.fireExceptionCaught(e);
                        }
                    });
                    close(unsafe().voidFuture());
                }
            } else {
                eventLoop().execute(beginReadTask);
            }
        }

        @Override
        public void flush(final ChannelPromise promise) {
            if (eventLoop().inEventLoop()) {
                FlushTask task = flushTaskInProgress;
                if (task != null) {
                    // loop over the tasks to find the last one
                    for (;;) {
                        FlushTask t = task.next;
                        if (t == null) {
                            break;
                        }
                        task = t.next;
                    }
                    task.next = new FlushTask(null, promise);

                    return;
                }
                flushNotifierAndFlush(promise);
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        flush(promise);
                    }
                });
            }
        }

        private void flushNotifierAndFlush(ChannelPromise promise) {
            flushNotifier(promise);
            flush0();
        }

        private int outboundBufSize() {
            final int bufSize;
            final ChannelHandlerContext ctx = directOutboundContext();
            if (metadata().bufferType() == BufType.BYTE) {
                bufSize = ctx.outboundByteBuffer().readableBytes();
            } else {
                bufSize = ctx.outboundMessageBuffer().size();
            }
            return bufSize;
        }

        private ChannelFuture flushNotifier(ChannelPromise promise) {
            // Append flush future to the notification list.
            if (promise != voidPromise) {
                flushFutureNotifier.add(promise, outboundBufSize());
            }
            return promise;
        }

        private void flush0() {
            if (!inFlushNow) { // Avoid re-entrance
                try {
                    // Flush immediately only when there's no pending flush.
                    // If there's a pending flush operation, event loop will call flushNow() later,
                    // and thus there's no need to call it now.
                    if (!isFlushPending()) {
                        flushNow();
                    }
                } catch (Throwable t) {
                    flushFutureNotifier.notifyFlushFutures(t);
                    if (t instanceof IOException) {
                        close(voidFuture());
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
                if (metadata().bufferType() == BufType.BYTE) {
                    ByteBuf out = ctx.outboundByteBuffer();
                    int oldSize = out.readableBytes();
                    try {
                        doFlushByteBuffer(out);
                    } catch (Throwable t) {
                        cause = t;
                    } finally {
                        int delta = oldSize - out.readableBytes();
                        out.discardSomeReadBytes();
                        flushFutureNotifier.increaseWriteCounter(delta);
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

        protected final boolean ensureOpen(ChannelPromise promise) {
            if (isOpen()) {
                return true;
            }

            Exception e = new ClosedChannelException();
            promise.setFailure(e);
            return false;
        }

        protected final void closeIfClosed() {
            if (isOpen()) {
                return;
            }
            close(voidFuture());
        }

        private void invokeLater(Runnable task) {
            // This method is used by outbound operation implementations to trigger an inbound event later.
            // They do not trigger an inbound event immediately because an outbound operation might have been
            // triggered by another inbound event handler method.  If fired immediately, the call stack
            // will look like this for example:
            //
            //   handlerA.inboundBufferUpdated() - (1) an inbound handler method closes a connection.
            //   -> handlerA.ctx.close()
            //      -> channel.unsafe.close()
            //         -> handlerA.channelInactive() - (2) another inbound handler method called while in (1) yet
            //
            // which means the execution of two inbound handler methods of the same handler overlap undesirably.
            eventLoop().execute(task);
        }
    }

    /**
     * Return {@code true} if the given {@link EventLoop} is compatible with this instance.
     */
    protected abstract boolean isCompatible(EventLoop loop);

    /**
     * Returns the {@link SocketAddress} which is bound locally.
     */
    protected abstract SocketAddress localAddress0();

    /**
     * Return the {@link SocketAddress} which the {@link Channel} is connected to.
     */
    protected abstract SocketAddress remoteAddress0();

    /**
     * Is called after the {@link Channel} is registered with its {@link EventLoop} as part of the register process.
     * You can return a {@link Runnable} which will be run as post-task of the registration process.
     *
     * Sub-classes may override this method as it will just return {@code null}
     */
    protected Runnable doRegister() throws Exception {
        return null;
    }

    /**
     * Bind the {@link Channel} to the {@link SocketAddress}
     */
    protected abstract void doBind(SocketAddress localAddress) throws Exception;

    /**
     * Disconnect this {@link Channel} from its remote peer
     */
    protected abstract void doDisconnect() throws Exception;

    /**
     * Will be called before the actual close operation will be performed. Sub-classes may override this as the default
     * is to do nothing.
     */
    protected void doPreClose() throws Exception {
        // NOOP by default
    }

    /**
     * Close the {@link Channel}
     */
    protected abstract void doClose() throws Exception;

    /**
     * Deregister the {@link Channel} from its {@link EventLoop}.
     * You can return a {@link Runnable} which will be run as post-task of the registration process.
     *
     * Sub-classes may override this method
     */
    protected Runnable doDeregister() throws Exception {
        return null;
    }

    /**
     * Schedule a read operation.
     */
    protected abstract void doBeginRead() throws Exception;

    /**
     * Flush the content of the given {@link ByteBuf} to the remote peer.
     *
     * Sub-classes may override this as this implementation will just thrown an {@link UnsupportedOperationException}
     */
    protected void doFlushByteBuffer(ByteBuf buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    /**
     * Flush the content of the given {@link MessageBuf} to the remote peer.
     *
     * Sub-classes may override this as this implementation will just thrown an {@link UnsupportedOperationException}
     */
    protected void doFlushMessageBuffer(MessageBuf<Object> buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    /**
     * Flush the content of the given {@link FileRegion} to the remote peer.
     *
     * Sub-classes may override this as this implementation will just thrown an {@link UnsupportedOperationException}
     */
    protected void doFlushFileRegion(FileRegion region, ChannelPromise promise) throws Exception {
        throw new UnsupportedOperationException();
    }

    protected static void checkEOF(FileRegion region, long writtenBytes) throws IOException {
        if (writtenBytes < region.count()) {
            throw new EOFException("Expected to be able to write "
                    + region.count() + " bytes, but only wrote "
                    + writtenBytes);
        }
    }

    /**
     * Return {@code true} if a flush to the {@link Channel} is currently pending.
     */
    protected abstract boolean isFlushPending();

    private final class CloseFuture extends DefaultChannelPromise implements ChannelFuture.Unsafe {

        CloseFuture(AbstractChannel ch) {
            super(ch);
        }

        @Override
        public ChannelPromise setSuccess() {
            throw new IllegalStateException();
        }

        @Override
        public ChannelPromise setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        @Override
        public boolean trySuccess() {
            throw new IllegalStateException();
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        boolean setClosed() {
            try {
                doPreClose();
            } catch (Exception e) {
                logger.warn("doPreClose() raised an exception.", e);
            }
            return super.trySuccess();
        }
    }
}
