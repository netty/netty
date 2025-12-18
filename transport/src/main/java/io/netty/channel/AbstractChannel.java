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
package io.netty.channel;

import io.netty.util.DefaultAttributeMap;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.Objects.requireNonNull;

/**
 * A skeletal {@link Channel} implementation.
 */
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannel.class);

    private enum WritabilityStateNotification {
        NONE,
        NOW,
        LATER
    }
    private static final int WRITABLE = 0;
    private static final int UNWRITABLE = 1;
    private static final AtomicIntegerFieldUpdater<AbstractChannel> WRITABLE_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractChannel.class, "writableState");
    private volatile int writableState;

    private final Runnable fireChannelWritabilityChangedTask;
    private final Channel parent;
    private final ChannelId id;
    private final IoTransportImpl ioTransport = new IoTransportImpl();
    private final DefaultChannelPipeline pipeline;
    private final CloseFuture closeFuture = new CloseFuture(this);
    private final EventLoop eventLoop;
    private volatile ChannelOutboundBuffer outboundBuffer;
    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;
    private volatile boolean registered;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    private boolean closeInitiated;
    private Throwable initialCloseCause;
    private boolean inWriteFlushed;
    protected final boolean hasDisconnect;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    private Future<?> connectTimeoutFuture;

    private RecvByteBufAllocator.Handle recvHandle;
    private MessageSizeEstimator.Handle estimatorHandle;

    /** Cache for the string representation of this channel */
    private boolean strValActive;
    private String strVal;

    /**
     * Creates a new instance.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(EventLoop eventLoop, Class<? extends IoHandle> handleType, Channel parent) {
        this(eventLoop, handleType, parent, null, false);
    }

    /**
     * Creates a new instance.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(EventLoop eventLoop, Class<? extends IoHandle> handleType, Channel parent, ChannelId id,
                              boolean hasDisconnect) {
        this.parent = parent;
        this.hasDisconnect = hasDisconnect;
        this.eventLoop = validateEventLoopGroup(eventLoop, "eventLoop", handleType);
        outboundBuffer = new ChannelOutboundBuffer(eventLoop);
        this.id = id == null ? DefaultChannelId.newInstance() : id;
        fireChannelWritabilityChangedTask = this::fireChannelWritabilityChanged;
        pipeline = newChannelPipeline();
        closeFuture.addListener(f -> {
            ChannelPromise connectPromise = this.connectPromise;
            if (connectPromise != null) {
                // Use tryFailure() instead of setFailure() to avoid the race against cancel().
                connectPromise.tryFailure(new ClosedChannelException());
            }

            Future<?> future = connectTimeoutFuture;
            if (future != null) {
                future.cancel(false);
                connectTimeoutFuture = null;
            }
        });
    }

    /**
     * Validate that the {@link EventLoopGroup} supports the given {@link Class channel type}.
     * If validation fails this will throw a runtime exception.
     *
     * @param group         the group to check against
     * @param name          the name of the param that is used when throwing an exception.
     * @param handleType   the {@link Channel} type.
     * @return              the group itself
     * @param <T>           the concreate type of the {@link EventLoopGroup}.
     */
    protected static <T extends EventLoopGroup> T validateEventLoopGroup(
            T group, String name, Class<? extends IoHandle> handleType) {
        requireNonNull(group, name);
        if (handleType != null && !group.isCompatible(handleType)) {
            throw new IllegalArgumentException(group + " does not support IoHandle of type " +
                    StringUtil.simpleClassName(handleType));
        }
        return group;
    }

    protected final int maxMessagesPerWrite() {
        ChannelConfig config = config();
        if (config instanceof DefaultChannelConfig) {
            return ((DefaultChannelConfig) config).getMaxMessagesPerWrite();
        }
        Integer value = config.getOption(ChannelOption.MAX_MESSAGES_PER_WRITE);
        if (value == null) {
            return Integer.MAX_VALUE;
        }
        return value;
    }

    private long totalPendingBytes() {
        ChannelOutboundBuffer buf = outboundBuffer();
        if (buf == null) {
            return -1;
        }
        return buf.totalPendingWriteBytes() + pipeline.pendingOutboundBytes();
    }

    @Override
    public boolean hasPendingBytes() {
        return totalPendingBytes() > 0;
    }

    @Override
    public boolean isWritable() {
        return WRITABLE_STATE_UPDATER.get(this) == WRITABLE;
    }

    @Override
    public long bytesBeforeUnwritable() {
        long totalPending = totalPendingBytes();
        if (totalPending == -1) {
            return 0;
        }
        // +1 because writability doesn't change until the threshold is crossed (not equal to).
        long bytes = config().getWriteBufferWaterMark().high() - totalPending + 1;
        // If bytes is negative we know we are not writable, but if bytes is non-negative we have to check writability.
        // Note that totalPendingSize and isWritable() use different volatile variables that are not synchronized
        // together. totalPendingSize will be updated before isWritable().
        return bytes > 0 && isWritable() ? bytes : 0;
    }

    @Override
    public long bytesBeforeWritable() {
        long totalPending = totalPendingBytes();
        if (totalPending == -1) {
            // Already closed.
            return 0;
        }

        long bytes = totalPending - config().getWriteBufferWaterMark().high();
        // If bytes is negative we know we are not writable, but if bytes is non-negative we have to check writability.
        // Note that totalPendingSize and isWritable() use different volatile variables that are not synchronized
        // together. totalPendingSize will be updated before isWritable().
        if (bytes > 0) {
            return isWritable() ? bytes : 0;
        }
        return 0;
    }

    @Override
    public final ChannelId id() {
        return id;
    }

    /**
     * Returns a new {@link ChannelPipeline} instance.
     */
    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultAbstractChannelPipeline(this);
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
    public EventLoop executor() {
        return eventLoop;
    }

    @Override
    public SocketAddress localAddress() {
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress = localAddress0();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    /**
     * @deprecated no use-case for this.
     */
    @Deprecated
    protected void invalidateLocalAddress() {
        localAddress = null;
    }

    @Override
    public SocketAddress remoteAddress() {
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress = remoteAddress0();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    /**
     * @deprecated no use-case for this.
     */
    @Deprecated
    protected void invalidateRemoteAddress() {
        remoteAddress = null;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture;
    }

    protected IoTransport ioTransport() {
        return ioTransport;
    }

    /**
     * Returns the ID of this channel.
     */
    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    /**
     * Returns {@code true} if and only if the specified object is identical
     * with this channel (i.e: {@code this == o}).
     */
    @Override
    public final boolean equals(Object o) {
        return this == o;
    }

    @Override
    public final int compareTo(Channel o) {
        if (this == o) {
            return 0;
        }

        return id().compareTo(o.id());
    }

    /**
     * Returns the {@link String} representation of this channel.  The returned
     * string contains the {@linkplain #hashCode() ID}, {@linkplain #localAddress() local address},
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
            StringBuilder buf = new StringBuilder(96)
                .append("[id: 0x")
                .append(id.asShortText())
                .append(", L:")
                .append(localAddr)
                .append(active? " - " : " ! ")
                .append("R:")
                .append(remoteAddr)
                .append(']');
            strVal = buf.toString();
        } else if (localAddr != null) {
            StringBuilder buf = new StringBuilder(64)
                .append("[id: 0x")
                .append(id.asShortText())
                .append(", L:")
                .append(localAddr)
                .append(']');
            strVal = buf.toString();
        } else {
            StringBuilder buf = new StringBuilder(16)
                .append("[id: 0x")
                .append(id.asShortText())
                .append(']');
            strVal = buf.toString();
        }

        strValActive = active;
        return strVal;
    }

    @Override
    public final boolean isShutdown(ChannelShutdownDirection direction) {
        if (!isActive()) {
            return true;
        }
        switch (direction) {
            case Outbound:
                return outputShutdown;
            case Inbound:
                return inputShutdown;
            default:
                return false;
        }
    }

    /**
     * Method that should be called to propagate writability state changes if required.
     */
    protected final void updateWritabilityIfNeeded() {
        updateWritabilityIfNeeded(WritabilityStateNotification.NOW);
    }

    private void updateWritabilityIfNeeded(WritabilityStateNotification notification) {
        long totalPending = totalPendingBytes();
        WriteBufferWaterMark mark = config().getWriteBufferWaterMark();
        if (totalPending > mark.high()) {
            if (WRITABLE_STATE_UPDATER.compareAndSet(this, WRITABLE, UNWRITABLE)) {
                fireChannelWritabilityChangedIfNeeded(notification);
            }
        } else if (totalPending < mark.low()) {
            if (WRITABLE_STATE_UPDATER.compareAndSet(this, UNWRITABLE, WRITABLE)) {
                fireChannelWritabilityChangedIfNeeded(notification);
            }
        }
    }

    private void fireChannelWritabilityChangedIfNeeded(WritabilityStateNotification notification) {
        switch (notification) {
            case NONE:
                return;
            case NOW:
                fireChannelWritabilityChanged();
            case LATER:
                executor().execute(fireChannelWritabilityChangedTask);
        }
    }

    private void fireChannelWritabilityChanged() {
        if (isOpen()) {
            pipeline().fireChannelWritabilityChanged();
        }
    }

    protected final ChannelOutboundBuffer outboundBuffer() {
        return outboundBuffer;
    }

    /**
     * {@link IoTransport} implementation which sub-classes must extend and use.
     */
    private final class IoTransportImpl implements IoTransport {

        /** true if the channel has never been registered, false otherwise */
        private boolean neverRegistered = true;

        @Override
        public void shutdown(ChannelShutdownType type, ChannelPromise promise) {
            assertEventLoop();
            if (!promise.setUncancellable()) {
                return;
            }
            if (!isActive()) {
                if (isOpen()) {
                    promise.setFailure(new NotYetConnectedException());
                } else {
                    promise.setFailure(new ClosedChannelException());
                }
                return;
            }
            if (isShutdown(type.direction())) {
                // Already shutdown so let's just make this a noop.
                promise.setSuccess(null);
                return;
            }

            doShutdown(type, newPromise().addListener(f -> {
                if (f.isSuccess()) {
                    switch (type.direction()) {
                        case Outbound:
                            outputShutdown = true;
                            break;
                        case Inbound:
                            inputShutdown = true;
                            break;
                        default:
                            throw new AssertionError();
                    }
                }
                safeCascade(f, promise);
                if (f.isSuccess() && type.direction() == ChannelShutdownDirection.Outbound) {
                    pipeline().fireChannelShutdown(ChannelShutdownType.newOutbound());
                }
            }));
        }

        @Override
        public void register(final ChannelPromise promise) {
            assertEventLoop();

            // check if the channel is still open as it could be closed in the mean time when the register
            // call was outside of the eventLoop
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }
            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("registered to an event loop already"));
                return;
            }

            ChannelPromise registerPromise = newPromise();
            boolean firstRegistration = neverRegistered;
            registerPromise.addListener(future -> {
                if (future.isSuccess()) {
                    neverRegistered = false;
                    registered = true;

                    safeSetSuccess(promise);
                    pipeline.fireChannelRegistered();
                    // Only fire a channelActive if the channel has never been registered. This prevents firing
                    // multiple channel actives if the channel is deregistered and re-registered.
                    if (isActive()) {
                        if (firstRegistration) {
                            pipeline.fireChannelActive();
                        } else if (config().isAutoRead()) {
                            // This channel was registered before and autoRead() is set. This means we need to
                            // begin read again so that we process inbound data.
                            //
                            // See https://github.com/netty/netty/issues/4805
                            read();
                        }
                    }
                } else {
                    // Close the channel directly to avoid FD leak.
                    close(newPromise());
                    closeFuture.setClosed();
                    safeSetFailure(promise, future.cause());
                }
            });
            doRegister(registerPromise);
        }

        @Override
        public void bind(final SocketAddress localAddress, final ChannelPromise promise) {
            assertEventLoop();

            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            // See: https://github.com/netty/netty/issues/576
            if (Boolean.TRUE.equals(config().getOption(ChannelOption.SO_BROADCAST)) &&
                localAddress instanceof InetSocketAddress &&
                !((InetSocketAddress) localAddress).getAddress().isAnyLocalAddress() &&
                !PlatformDependent.isWindows() && !PlatformDependent.maybeSuperUser()) {
                // Warn a user about the fact that a non-root user can't receive a
                // broadcast packet on *nix if the socket is bound on non-wildcard address.
                logger.warn(
                        "A non-root user can't receive a broadcast packet if the socket " +
                        "is not bound to a wildcard address; binding to a non-wildcard " +
                        "address (" + localAddress + ") anyway as requested.");
            }

            boolean wasActive = isActive();
            ChannelPromise bindPromise = newPromise();
            bindPromise.addListener(f -> {
                if (f.isSuccess()) {
                    if (!wasActive && isActive()) {
                        invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                pipeline.fireChannelActive();
                            }
                        });
                    }
                } else {
                    closeIfClosed();
                }
                safeCascade(f, promise);
            });
            doBind(localAddress, bindPromise);
        }

        @Override
        public void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            assertEventLoop();

            // Don't mark the connect promise as uncancellable as in fact we can cancel it as it is using
            // non-blocking io.
            if (promise.isDone() || !ensureOpen(promise)) {
                return;
            }
            if (connectPromise != null) {
                if (!connectPromise.isDone()) {
                    // Already a connect in process.
                    promise.setFailure(new ConnectionPendingException());
                } else if (connectPromise.isSuccess()) {
                    promise.setFailure(new AlreadyConnectedException());
                } else {
                    promise.setFailure(connectPromise.cause());
                }
                return;
            }

            boolean wasActive = isActive();
            connectPromise = promise;

            ChannelPromise p = newPromise();
            p.addListener(f -> {
                if  (f.isSuccess()) {
                    fulfillConnectPromise(connectPromise, wasActive);
                } else {
                    fulfillConnectPromise(promise, f.cause(), remoteAddress);
                }
            });
            doConnect(remoteAddress, localAddress, p);
            if (!p.isDone()) {
                // Schedule connect timeout.
                final int connectTimeoutMillis = config().getConnectTimeoutMillis();
                if (connectTimeoutMillis > 0) {
                    connectTimeoutFuture = executor().schedule(new Runnable() {
                        @Override
                        public void run() {
                            ChannelPromise connectPromise = AbstractChannel.this.connectPromise;
                            if (connectPromise != null && !connectPromise.isDone()
                                    && connectPromise.tryFailure(new ConnectTimeoutException(
                                    "connection timed out after " + connectTimeoutMillis + " ms: " +
                                            remoteAddress))) {
                                close(newPromise());
                            }
                        }
                    }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                }
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            // Get the state as trySuccess() may trigger an ChannelFutureListener that will close the Channel.
            // We still need to ensure we call fireChannelActive() in this case.
            boolean active = isActive();

            // trySuccess() will return false if a user cancelled the connection attempt.
            boolean promiseSet = promise.trySuccess();

            // Regardless if the connection attempt was cancelled, channelActive() event should be triggered,
            // because what happened is what happened.
            if (!wasActive && active) {
                pipeline().fireChannelActive();
            }

            // If a user cancelled the connection attempt, close the channel, which is followed by channelInactive().
            if (!promiseSet) {
                close(newPromise());
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, Throwable cause, SocketAddress remoteAddress) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(annotateConnectException(cause, remoteAddress));
            closeIfClosed();
        }

        @Override
        public void disconnect(final ChannelPromise promise) {
            assertEventLoop();

            if (!promise.setUncancellable()) {
                return;
            }

            boolean wasActive = isActive();
            ChannelPromise disconnectPromise = newPromise();
            disconnectPromise.addListener(f -> {
                // Reset remoteAddress and localAddress
                remoteAddress = null;
                localAddress = null;
                if (wasActive && !isActive()) {
                    invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            pipeline.fireChannelInactive();
                        }
                    });
                }

                safeCascade(f, promise);
                closeIfClosed(); // doDisconnect() might have closed the channel
            });
            doDisconnect(disconnectPromise);
        }

        @Override
        public void close(final ChannelPromise promise) {
            assertEventLoop();

            ClosedChannelException closedChannelException =
                    StacklessClosedChannelException.newInstance(AbstractChannel.class, "close(ChannelPromise)");
            close(promise, closedChannelException, closedChannelException);
        }

        private void close(final ChannelPromise promise, final Throwable cause,
                           final ClosedChannelException closeCause) {
            if (!promise.setUncancellable()) {
                return;
            }

            if (closeInitiated) {
                if (closeFuture.isDone()) {
                    // Closed already.
                    safeSetSuccess(promise);
                } else {
                    // This means close() was called before so we just register a listener and return
                    closeFuture.addListener(future -> promise.setSuccess());
                }
                return;
            }

            closeInitiated = true;

            final boolean wasActive = isActive();

            ChannelPromise closePromise = newPromise();
            closePromise.addListener(f -> {
                final ChannelOutboundBuffer outboundBuffer = AbstractChannel.this.outboundBuffer;
                // Disallow adding any messages and flushes to outboundBuffer.
                AbstractChannel.this.outboundBuffer = null;
                // Call invokeLater so closeAndDeregister is executed in the EventLoop again!
                invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        // Fail all the queued messages
                        closeAndUpdateWritability(outboundBuffer, cause, closeCause);
                        fireChannelInactiveAndDeregister(wasActive);
                    }
                });
                safeCascade(f, promise);
            });

            Executor closeExecutor = prepareToClose();
            if (closeExecutor != null) {
                closeExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                            // Execute the close.
                        doClose0(closePromise);
                    }
                });
            } else {
                // Close the channel and fail the queued messages in all cases.
                doClose0(closePromise);
            }
        }

        private void doClose0(ChannelPromise promise) {
            ChannelPromise closePromise = newPromise();
            closePromise.addListener(f -> {
                closeFuture.setClosed();
                safeCascade(f, promise);
            });
            doClose(closePromise);
        }

        private void closeAndUpdateWritability(
                ChannelOutboundBuffer outboundBuffer, Throwable cause, Throwable closeCause) {
            if (outboundBuffer != null) {
                // Fail all the queued messages
                outboundBuffer.failFlushedAndClose(cause, closeCause);
                updateWritabilityIfNeeded(WritabilityStateNotification.NONE);
            }
        }

        private void fireChannelInactiveAndDeregister(final boolean wasActive) {
            deregister(newPromise(), wasActive && !isActive());
        }

        @Override
        public void deregister(final ChannelPromise promise) {
            assertEventLoop();

            deregister(promise, false);
        }

        private void deregister(final ChannelPromise promise, final boolean fireChannelInactive) {
            if (!promise.setUncancellable()) {
                return;
            }

            if (!registered) {
                safeSetSuccess(promise);
                return;
            }

            // As a user may call deregister() from within any method while doing processing in the ChannelPipeline,
            // we need to ensure we do the actual deregister operation later. This is needed as for example,
            // we may be in the ByteToMessageDecoder.callDecode(...) method and so still try to do processing in
            // the old EventLoop while the user already registered the Channel to a new EventLoop. Without delay,
            // the deregister operation this could lead to have a handler invoked by different EventLoop and so
            // threads.
            //
            // See:
            // https://github.com/netty/netty/issues/4435
            invokeLater(new Runnable() {
                @Override
                public void run() {
                    ChannelPromise deregisterPromise = newPromise();
                    deregisterPromise.addListener(f -> {
                        if (f.isSuccess()) {
                            if (fireChannelInactive) {
                                pipeline.fireChannelInactive();
                            }
                            // Some transports like local and AIO does not allow the deregistration of
                            // an open channel.  Their doDeregister() calls close(). Consequently,
                            // close() calls deregister() again - no need to fire channelUnregistered, so check
                            // if it was registered.
                            if (registered) {
                                registered = false;
                                pipeline.fireChannelUnregistered();
                            }
                        }
                        safeCascade(f, promise);
                    });
                    doDeregister(deregisterPromise);
                }
            });
        }

        @Override
        public void read() {
            assertEventLoop();

            try {
                doBeginRead();
            } catch (final Exception e) {
                invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.fireExceptionCaught(e);
                    }
                });
                close(newPromise());
            }
        }

        @Override
        public void write(Object msg, ChannelPromise promise) {
            assertEventLoop();

            ChannelOutboundBuffer outboundBuffer = AbstractChannel.this.outboundBuffer;
            if (outboundBuffer == null) {
                try {
                    // release message now to prevent resource-leak
                    ReferenceCountUtil.release(msg);
                } finally {
                    // If the outboundBuffer is null we know the channel was closed or the outbound was shutdown, so
                    // need to fail the future right away. If it is not null the handling of the rest
                    // will be done in flush0()
                    // See https://github.com/netty/netty/issues/2362
                    if (!isActive()) {
                        safeSetFailure(promise, newClosedChannelException(
                                IoTransportImpl.class, initialCloseCause, "write(Object, ChannelPromise)"));
                    } else {
                        safeSetFailure(promise, new ChannelOutputShutdownException("Channel output shutdown"));
                    }
                }
                return;
            }

            int size;
            try {
                msg = filterOutboundMessage(msg);
                size = estimatorHandle().size(msg);
                if (size < 0) {
                    size = 0;
                }
            } catch (Throwable t) {
                try {
                    ReferenceCountUtil.release(msg);
                } finally {
                    safeSetFailure(promise, t);
                }
                return;
            }

            outboundBuffer.addMessage(msg, size, promise);
            updateWritabilityIfNeeded(WritabilityStateNotification.NOW);
        }

        @Override
        public void flush() {
            assertEventLoop();

            ChannelOutboundBuffer outboundBuffer = AbstractChannel.this.outboundBuffer;
            if (outboundBuffer == null) {
                return;
            }

            outboundBuffer.addFlush();
            updateWritabilityIfNeeded(WritabilityStateNotification.NOW);
            writeFlushed();
        }

        private void handleWriteError(Throwable t) {
            if (t instanceof IOException && config().isAutoClose()) {
                /**
                 * Just call {@link #close(ChannelPromise, Throwable, boolean)} here which will take care of
                 * failing all flushed messages and also ensure the actual close of the underlying transport
                 * will happen before the promises are notified.
                 *
                 * This is needed as otherwise {@link #isActive()} , {@link #isOpen()} and {@link #isWritable()}
                 * may still return {@code true} even if the channel should be closed as result of the exception.
                 */
                initialCloseCause = t;
                close(newPromise(), t, newClosedChannelException(IoTransportImpl.class, t, "handleWriteError()"));
            } else {
                try {
                    shutdownOutput(newPromise(), t);
                } catch (Throwable t2) {
                    initialCloseCause = t;
                    close(newPromise(), t2, newClosedChannelException(IoTransportImpl.class, t, "handleWriteError()"));
                }
            }
        }

        private ClosedChannelException newClosedChannelException(Class<?> clazz, Throwable cause, String method) {
            ClosedChannelException exception =
                    StacklessClosedChannelException.newInstance(clazz, method);
            if (cause != null) {
                exception.initCause(cause);
            }
            return exception;
        }

        private boolean ensureOpen(ChannelPromise promise) {
            if (isOpen()) {
                return true;
            }

            safeSetFailure(promise, newClosedChannelException(
                    IoTransportImpl.class, initialCloseCause, "ensureOpen(ChannelPromise)"));
            return false;
        }

        private void safeCascade(Future<?> future, ChannelPromise promise) {
            if (future.isSuccess()) {
                safeSetSuccess(promise);
            } else {
                safeSetFailure(promise, future.cause());
            }
        }

        /**
         * Marks the specified {@code promise} as success.  If the {@code promise} is done already, log a message.
         */
        private void safeSetSuccess(ChannelPromise promise) {
            if (!promise.trySuccess()) {
                logger.warn("Failed to mark a promise as success because it is done already: {}", promise);
            }
        }

        /**
         * Marks the specified {@code promise} as failure.  If the {@code promise} is done already, log a message.
         */
        private void safeSetFailure(ChannelPromise promise, Throwable cause) {
            if (!promise.tryFailure(cause)) {
                logger.warn("Failed to mark a promise as failure because it's done already: {}", promise, cause);
            }
        }

        private void closeIfClosed() {
            if (isOpen()) {
                return;
            }
            close(newPromise());
        }

        private void invokeLater(Runnable task) {
            try {
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
                executor().execute(task);
            } catch (RejectedExecutionException e) {
                logger.warn("Can't invoke task later as EventLoop rejected it", e);
            }
        }

        /**
         * Appends the remote address to the message of the exceptions caused by connection attempt failure.
         */
        private Throwable annotateConnectException(Throwable cause, SocketAddress remoteAddress) {
            if (cause instanceof ConnectException) {
                return new AnnotatedConnectException((ConnectException) cause, remoteAddress);
            }
            if (cause instanceof NoRouteToHostException) {
                return new AnnotatedNoRouteToHostException((NoRouteToHostException) cause, remoteAddress);
            }
            if (cause instanceof SocketException) {
                return new AnnotatedSocketException((SocketException) cause, remoteAddress);
            }

            return cause;
        }
    }

    protected final void handleWriteError(Throwable t) {
        ioTransport.handleWriteError(t);
    }

    /**
     * Prepares to close the {@link Channel}. If this method returns an {@link Executor}, the
     * caller must call the {@link Executor#execute(Runnable)} method with a task that calls
     * {@link #doClose(ChannelPromise)} on the returned {@link Executor}. If this method returns {@code null},
     * {@link #doClose(ChannelPromise)} must be called from the caller thread. (i.e. {@link EventLoop})
     */
    protected Executor prepareToClose() {
        return null;
    }

    /**
     * Shutdown the output portion of the corresponding {@link Channel}.
     * For example this will clean up the {@link ChannelOutboundBuffer} and not allow any more writes.
     * @param cause The cause which may provide rational for the shutdown.
     */
    private void shutdownOutput(final ChannelPromise promise, Throwable cause) {
        final ChannelOutboundBuffer outboundBuffer = AbstractChannel.this.outboundBuffer;
        if (outboundBuffer == null) {
            promise.setFailure(new ClosedChannelException());
            return;
        }

        final Throwable shutdownCause = cause == null ?
                new ChannelOutputShutdownException("Channel output shutdown") :
                new ChannelOutputShutdownException("Channel output shutdown", cause);

        // When a side enables SO_LINGER and calls showdownOutput(...) to start TCP half-closure
        // we can not call doDeregister here because we should ensure this side in fin_wait2 state
        // can still receive and process the data which is send by another side in the close_wait state。
        // See https://github.com/netty/netty/issues/11981

        // The shutdown function does not block regardless of the SO_LINGER setting on the socket
        // so we don't need to use GlobalEventExecutor to execute the shutdown
        ChannelPromise shutdownPromise = newPromise().addListener(f -> {
            if (f.isSuccess()) {
                outputShutdown = true;
            }
            // Disallow adding any messages and flushes to outboundBuffer.
            AbstractChannel.this.outboundBuffer = null;

            ioTransport.safeCascade(f, promise);
            ioTransport.closeAndUpdateWritability(outboundBuffer, shutdownCause, shutdownCause);
            pipeline().fireChannelShutdown(ChannelShutdownType.newOutbound());
        });
        doShutdown(ChannelShutdownType.newOutbound(), shutdownPromise);
    }

    private MessageSizeEstimator.Handle estimatorHandle() {
        if (estimatorHandle == null) {
            estimatorHandle = config().getMessageSizeEstimator().newHandle();
        }
        return estimatorHandle;
    }

    /**
     * Returns the {@link RecvByteBufAllocator.Handle} that should be used while reading from the transport.
     *
     * @return  handle
     */
    protected final RecvByteBufAllocator.Handle recvBufAllocHandle() {
        if (recvHandle == null) {
            recvHandle = newRecvBufAllocHandle();
        }
        return recvHandle;
    }

    /**
     * Create a new {@link RecvByteBufAllocator.Handle} that will be used for reading.
     *
     * @return newHandle.
     */
    protected RecvByteBufAllocator.Handle newRecvBufAllocHandle() {
        return config().getRecvByteBufAllocator().newHandle();
    }

    private void assertEventLoop() {
        assert !registered || eventLoop.inEventLoop();
    }

    /**
     * Returns {@code true} if flushed messages should not be tried to write when calling {@link #flush()}. Instead
     * these will be written once {@link #writeFlushedNow()} is called, which is typically done once the underlying
     * transport becomes writable again.
     *
     * @return {@code true} if write will be done later on by calling {@link #writeFlushedNow()},
     * {@code false} otherwise.
     */
    protected boolean isWriteFlushedScheduled() {
        return false;
    }

    /**
     * Writing previous flushed messages if {@link #isWriteFlushedScheduled()} returns {@code false}, otherwise
     * do nothing.
     */
    protected final void writeFlushed() {
        assertEventLoop();

        if (isWriteFlushedScheduled()) {
            return;
        }
        writeFlushedNow();
    }

    /**
     * Writing previous flushed messages now.
     */
    protected final void writeFlushedNow() {
        assertEventLoop();
        if (inWriteFlushed) {
            // Avoid re-entrance
            return;
        }

        final ChannelOutboundBuffer outboundBuffer = AbstractChannel.this.outboundBuffer;
        if (outboundBuffer == null || outboundBuffer.isEmpty()) {
            return;
        }

        inWriteFlushed = true;

        // Mark all pending write requests as failure if the channel is inactive.
        if (!isActive()) {
            try {
                // Check if we need to generate the exception at all.
                if (!outboundBuffer.isEmpty()) {
                    if (isOpen()) {
                        outboundBuffer.failFlushed(new NotYetConnectedException());
                        updateWritabilityIfNeeded(WritabilityStateNotification.LATER);
                    } else {
                        // Do not trigger channelWritabilityChanged because the channel is closed already.
                        outboundBuffer.failFlushed(ioTransport.newClosedChannelException(
                                AbstractChannel.class, initialCloseCause, "writeFlushedNow()"));
                    }
                }
            } finally {
                inWriteFlushed = false;
            }
            return;
        }

        try {
            doWrite(outboundBuffer);
        } catch (Throwable t) {
            ioTransport.handleWriteError(t);
        } finally {
            // It's important that we call this with WritabilityStateNotification.LATER so we don't get into trouble
            // when flush() is called again in channelWritabilityChanged(...).
            updateWritabilityIfNeeded(WritabilityStateNotification.LATER);
            inWriteFlushed = false;
        }
    }

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
     * Subclasses may override this method
     *
     * @param promise {@link ChannelPromise} that must be notified once done to continue the registration.
     */
    protected abstract void doRegister(ChannelPromise promise);

    /**
     * Bind the {@link Channel} to the {@link SocketAddress}
     */
    protected abstract void doBind(SocketAddress localAddress, ChannelPromise promise);

    /**
     * Connect this {@link Channel} to its remote peer
     */
    protected abstract void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

    /**
     * Disconnect this {@link Channel} from its remote peer
     */
    protected abstract void doDisconnect(ChannelPromise promise);

    /**
     * Close the {@link Channel}
     */
    protected abstract void doClose(ChannelPromise promise);

    /**
     * Called when conditions justify shutting down the output portion of the channel. This may happen if a write
     * operation throws an exception.
     */
    protected abstract void doShutdown(ChannelShutdownType type, ChannelPromise promise);

    /**
     * Deregister the {@link Channel} from its {@link EventLoop}.
     *
     * Sub-classes may override this method
     */
    protected abstract void doDeregister(ChannelPromise promise);

    /**
     * Schedule a read operation.
     */
    protected abstract void doBeginRead() throws Exception;

    /**
     * Flush the content of the given buffer to the remote peer.
     */
    protected abstract void doWrite(ChannelOutboundBuffer in) throws Exception;

    /**
     * Invoked when a new message is added to a {@link ChannelOutboundBuffer} of this {@link AbstractChannel}, so that
     * the {@link Channel} implementation converts the message to another. (e.g. heap buffer -> direct buffer)
     */
    protected Object filterOutboundMessage(Object msg) throws Exception {
        return msg;
    }

    protected final void validateFileRegion(DefaultFileRegion region, long position) throws IOException {
        DefaultFileRegion.validate(region, position);
    }

    static final class CloseFuture extends DefaultChannelPromise {

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
            return super.trySuccess();
        }
    }

    private static final class AnnotatedConnectException extends ConnectException {

        private static final long serialVersionUID = 3901958112696433556L;

        AnnotatedConnectException(ConnectException exception, SocketAddress remoteAddress) {
            super(exception.getMessage() + ": " + remoteAddress);
            initCause(exception);
        }

        // Suppress a warning since this method doesn't need synchronization
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class AnnotatedNoRouteToHostException extends NoRouteToHostException {

        private static final long serialVersionUID = -6801433937592080623L;

        AnnotatedNoRouteToHostException(NoRouteToHostException exception, SocketAddress remoteAddress) {
            super(exception.getMessage() + ": " + remoteAddress);
            initCause(exception);
        }

        // Suppress a warning since this method doesn't need synchronization
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class AnnotatedSocketException extends SocketException {

        private static final long serialVersionUID = 3896743275010454039L;

        AnnotatedSocketException(SocketException exception, SocketAddress remoteAddress) {
            super(exception.getMessage() + ": " + remoteAddress);
            initCause(exception);
        }

        // Suppress a warning since this method doesn't need synchronization
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    protected class DefaultAbstractChannelPipeline extends DefaultChannelPipeline {

        protected DefaultAbstractChannelPipeline(AbstractChannel channel) {
            super(channel, channel.hasDisconnect, channel.ioTransport);
        }

        @Override
        protected void pendingOutboundBytesUpdated(long pendingOutboundBytes) {
            AbstractChannel.this.updateWritabilityIfNeeded(WritabilityStateNotification.NOW);
        }
    }
}
