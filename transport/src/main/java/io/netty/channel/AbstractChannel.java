/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.internal.ConcurrentHashMap;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A skeletal {@link Channel} implementation.
 */
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannel.class);

    static final ConcurrentMap<Integer, Channel> allChannels = new ConcurrentHashMap<Integer, Channel>();

    /**
     * Generates a negative unique integer ID.  This method generates only
     * negative integers to avoid conflicts with user-specified IDs where only
     * non-negative integers are allowed.
     */
    private static Integer allocateId(Channel channel) {
        int idVal = System.identityHashCode(channel);
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
    private final ChannelPipeline pipeline = new DefaultChannelPipeline(this);
    private final ChannelFuture succeededFuture = new SucceededChannelFuture(this);
    private final ChannelFuture voidFuture = new VoidChannelFuture(this);
    private final CloseFuture closeFuture = new CloseFuture(this);

    private volatile EventLoop eventLoop;
    private volatile boolean registered;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelFuture connectFuture;
    private ScheduledFuture<?> connectTimeoutFuture;
    private ConnectException connectTimeoutException;

    private long flushedAmount;
    private FlushFutureEntry flushFuture;
    private FlushFutureEntry lastFlushFuture;
    private ClosedChannelException closedChannelException;

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
        unsafe = new DefaultUnsafe();

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
    public EventLoop eventLoop() {
        if (eventLoop == null) {
            throw new IllegalStateException("channel not registered to an event loop");
        }
        return eventLoop;
    }

    @Override
    public boolean isOpen() {
        return unsafe().ch().isOpen();
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline().bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline().connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline().connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return pipeline().disconnect();
    }

    @Override
    public ChannelFuture close() {
        return pipeline().close();
    }

    @Override
    public ChannelFuture deregister() {
        return pipeline().deregister();
    }

    @Override
    public ChannelFuture flush() {
        return pipeline().flush();
    }

    @Override
    public ChannelFuture write(Object message) {
        return pipeline().write(message);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelFuture future) {
        return pipeline().bind(localAddress, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelFuture future) {
        return pipeline().connect(remoteAddress, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
        return pipeline().connect(remoteAddress, localAddress, future);
    }

    @Override
    public ChannelFuture disconnect(ChannelFuture future) {
        return pipeline().disconnect(future);
    }

    @Override
    public ChannelFuture close(ChannelFuture future) {
        return pipeline().close(future);
    }

    @Override
    public ChannelFuture deregister(ChannelFuture future) {
        return pipeline().deregister(future);
    }

    @Override
    public ChannelBufferHolder<Object> out() {
        return pipeline().out();
    }

    @Override
    public ChannelFuture flush(ChannelFuture future) {
        return pipeline().flush(future);
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

    /**
     * Returns the {@linkplain System#identityHashCode(Object) identity hash code}
     * of this channel.
     */
    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
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

    private class DefaultUnsafe implements Unsafe {

        @Override
        public java.nio.channels.Channel ch() {
            return javaChannel();
        }

        @Override
        public ChannelBufferHolder<Object> out() {
            return firstOut();
        }

        @Override
        public ChannelFuture voidFuture() {
            return voidFuture;
        }

        @Override
        public SocketAddress localAddress() {
            return localAddress0();
        }

        @Override
        public SocketAddress remoteAddress() {
            return remoteAddress0();
        }

        @Override
        public void register(EventLoop eventLoop, ChannelFuture future) {
            if (eventLoop == null) {
                throw new NullPointerException("eventLoop");
            }
            if (AbstractChannel.this.eventLoop != null) {
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

            try {
                doRegister();
                registered = true;
                future.setSuccess();
                pipeline().fireChannelRegistered();
                if (isActive()) {
                    pipeline().fireChannelActive();
                }
            } catch (Throwable t) {
                // Close the channel directly to avoid FD leak.
                try {
                    doClose();
                } catch (Throwable t2) {
                    logger.warn("Failed to close a channel", t2);
                }

                future.setFailure(t);
                pipeline().fireExceptionCaught(t);
                closeFuture().setSuccess();
            }
        }

        @Override
        public void bind(final SocketAddress localAddress, final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(future)) {
                    return;
                }

                try {
                    boolean wasActive = isActive();
                    doBind(localAddress);
                    future.setSuccess();
                    if (!wasActive && isActive()) {
                        pipeline().fireChannelActive();
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    pipeline().fireExceptionCaught(t);
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
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(future)) {
                    return;
                }

                try {
                    if (connectFuture != null) {
                        throw new IllegalStateException("connection attempt already made");
                    }

                    boolean wasActive = isActive();
                    if (doConnect(remoteAddress, localAddress)) {
                        future.setSuccess();
                        if (!wasActive && isActive()) {
                            pipeline().fireChannelActive();
                        }
                    } else {
                        connectFuture = future;

                        // Schedule connect timeout.
                        int connectTimeoutMillis = config().getConnectTimeoutMillis();
                        if (connectTimeoutMillis > 0) {
                            connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                                @Override
                                public void run() {
                                    if (connectTimeoutException == null) {
                                        connectTimeoutException = new ConnectException("connection timed out");
                                    }
                                    ChannelFuture connectFuture = AbstractChannel.this.connectFuture;
                                    if (connectFuture == null) {
                                        return;
                                    } else {
                                        if (connectFuture.setFailure(connectTimeoutException)) {
                                            pipeline().fireExceptionCaught(connectTimeoutException);
                                            close(voidFuture());
                                        }
                                    }
                                }
                            }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                        }
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    pipeline().fireExceptionCaught(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        connect(remoteAddress, localAddress, future);
                    }
                });
            }
        }

        @Override
        public void finishConnect() {
            assert eventLoop().inEventLoop();
            assert connectFuture != null;
            try {
                boolean wasActive = isActive();
                doFinishConnect();
                connectFuture.setSuccess();
                if (!wasActive && isActive()) {
                    pipeline().fireChannelActive();
                }
            } catch (Throwable t) {
                connectFuture.setFailure(t);
                pipeline().fireExceptionCaught(t);
                closeIfClosed();
            } finally {
                connectTimeoutFuture.cancel(false);
                connectFuture = null;
            }
        }

        @Override
        public void disconnect(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                try {
                    boolean wasActive = isActive();
                    doDisconnect();
                    future.setSuccess();
                    if (wasActive && !isActive()) {
                        pipeline().fireChannelInactive();
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
        public void close(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (closeFuture.setClosed()) {
                    boolean wasActive = isActive();
                    try {
                        doClose();
                        future.setSuccess();
                    } catch (Throwable t) {
                        future.setFailure(t);
                    }

                    if (closedChannelException != null) {
                        closedChannelException = new ClosedChannelException();
                    }

                    notifyFlushFutures(closedChannelException);

                    if (wasActive && !isActive()) {
                        pipeline().fireChannelInactive();
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
        public void deregister(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                try {
                    doDeregister();
                } catch (Throwable t) {
                    logger.warn("Unexpected exception occurred while deregistering a channel.", t);
                } finally {
                    future.setSuccess();
                    registered = false;
                    pipeline().fireChannelUnregistered();
                    eventLoop = null;
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
        public void read() {
            assert eventLoop().inEventLoop();
            long readAmount = 0;
            boolean closed = false;
            try {
                for (;;) {
                    int localReadAmount = doRead();
                    if (localReadAmount > 0) {
                        readAmount += localReadAmount;
                        continue;
                    }
                    if (localReadAmount == 0) {
                        break;
                    }
                    if (localReadAmount < 0) {
                        closed = true;
                        break;
                    }
                }

                if (readAmount > 0) {
                    pipeline.fireInboundBufferUpdated();
                }
            } catch (Throwable t) {
                pipeline().fireExceptionCaught(t);
                if (t instanceof IOException) {
                    close(voidFuture());
                }
            } finally {
                if (closed && isOpen()) {
                    close(voidFuture());
                }
            }
        }

        @Override
        public void flush(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                // Append flush future to the notification list.
                if (future != voidFuture) {
                    FlushFutureEntry newEntry = new FlushFutureEntry(future, flushedAmount + out().size(), null);
                    if (flushFuture == null) {
                        flushFuture = lastFlushFuture = newEntry;
                    } else {
                        lastFlushFuture.next = newEntry;
                        lastFlushFuture = newEntry;
                    }
                }

                // Attempt/perform outbound I/O if:
                // - the channel is inactive - flush0() will fail the futures.
                // - the event loop has no plan to call flushForcibly().
                if (!isActive() || !inEventLoopDrivenFlush()) {
                    // Note that we don't call flushForcibly() because otherwise its stack trace
                    // will be confusing.
                    flush0();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        flush(future);
                    }
                });
            }
        }

        @Override
        public void flushForcibly() {
            flush0();
        }

        private void flush0() {
            // Perform outbound I/O.
            try {
                for (int i = config().getWriteSpinCount() - 1; i >= 0; i --) {
                    int localFlushedAmount = doFlush(i == 0);
                    if (localFlushedAmount > 0) {
                        flushedAmount += localFlushedAmount;
                        notifyFlushFutures();
                        break;
                    }
                    if (out().isEmpty()) {
                        // Reset reader/writerIndex to 0 if the buffer is empty.
                        if (out().hasByteBuffer()) {
                            out().byteBuffer().clear();
                        }
                        break;
                    }
                }
            } catch (Throwable t) {
                notifyFlushFutures(t);
                pipeline().fireExceptionCaught(t);
                close(voidFuture());
            } finally {
                if (!isActive()) {
                    close(voidFuture());
                }
            }
        }

        private void notifyFlushFutures() {
            FlushFutureEntry e = flushFuture;
            if (e == null) {
                return;
            }

            final long flushedAmount = AbstractChannel.this.flushedAmount;
            do {
                if (e.expectedFlushedAmount > flushedAmount) {
                    break;
                }
                e.future.setSuccess();
                e = e.next;
            } while (e != null);

            flushFuture = e;

            // Avoid overflow
            if (e == null) {
                // Reset the counter if there's nothing in the notification list.
                AbstractChannel.this.flushedAmount = 0;
            } else if (flushedAmount >= 0x1000000000000000L) {
                // Otherwise, reset the counter only when the counter grew pretty large
                // so that we can reduce the cost of updating all entries in the notification list.
                AbstractChannel.this.flushedAmount = 0;
                do {
                    e.expectedFlushedAmount -= flushedAmount;
                    e = e.next;
                } while (e != null);
            }
        }

        private void notifyFlushFutures(Throwable cause) {
            FlushFutureEntry e = flushFuture;
            if (e == null) {
                return;
            }

            do {
                e.future.setFailure(cause);
                e = e.next;
            } while (e != null);

            flushFuture = null;
        }

        private boolean ensureOpen(ChannelFuture future) {
            if (isOpen()) {
                return true;
            }

            Exception e = new ClosedChannelException();
            future.setFailure(e);
            pipeline().fireExceptionCaught(e);
            return false;
        }

        private void closeIfClosed() {
            if (isOpen()) {
                return;
            }
            close(voidFuture());
        }
    }

    private static class FlushFutureEntry {
        private final ChannelFuture future;
        private long expectedFlushedAmount;
        private FlushFutureEntry next;

        FlushFutureEntry(ChannelFuture future, long expectedWrittenAmount, FlushFutureEntry next) {
            this.future = future;
            expectedFlushedAmount = expectedWrittenAmount;
            this.next = next;
        }
    }

    private static final class CloseFuture extends DefaultChannelFuture implements ChannelFuture.Unsafe {

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
            boolean set = super.setSuccess();
            assert set;
            return set;
        }
    }

    protected abstract boolean isCompatible(EventLoop loop);

    protected abstract java.nio.channels.Channel javaChannel();
    protected abstract ChannelBufferHolder<Object> firstOut();

    protected abstract SocketAddress localAddress0();
    protected abstract SocketAddress remoteAddress0();

    protected abstract void doRegister() throws Exception;
    protected abstract void doBind(SocketAddress localAddress) throws Exception;
    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;
    protected abstract void doFinishConnect() throws Exception;
    protected abstract void doDisconnect() throws Exception;
    protected abstract void doClose() throws Exception;
    protected abstract void doDeregister() throws Exception;

    protected abstract int doRead() throws Exception;
    protected abstract int doFlush(boolean lastSpin) throws Exception;
    protected abstract boolean inEventLoopDrivenFlush();
}
