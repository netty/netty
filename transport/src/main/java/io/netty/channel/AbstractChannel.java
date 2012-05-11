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
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

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
    private final List<ChannelFutureListener> closureListeners = new ArrayList<ChannelFutureListener>(4);
    private final ChannelFuture succeededFuture = new SucceededChannelFuture(this);

    private volatile EventLoop eventLoop;
    private volatile boolean registered;
    private volatile boolean notifiedClosureListeners;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelFuture connectFuture;
    private long writtenAmount;

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

        closureListeners.add(new ChannelFutureListener() {
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
    public void addClosureListener(final ChannelFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        synchronized (closureListeners) {
            if (notifiedClosureListeners) {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        notifyClosureListener(listener);
                    }
                });
            } else {
                closureListeners.add(listener);
            }
        }
    }

    @Override
    public void removeClosureListener(ChannelFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        synchronized (closureListeners) {
            if (!notifiedClosureListeners) {
                closureListeners.remove(listener);
            }
        }
    }

    private void notifyClosureListeners() {
        final ChannelFutureListener[] array;
        synchronized (closureListeners) {
            if (notifiedClosureListeners) {
                return;
            }
            notifiedClosureListeners = true;
            array = closureListeners.toArray(new ChannelFutureListener[closureListeners.size()]);
            closureListeners.clear();
        }

        eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                for (ChannelFutureListener l: array) {
                    notifyClosureListener(l);
                }
            }
        });
    }

    private void notifyClosureListener(final ChannelFutureListener listener) {
        try {
            listener.operationComplete(newSucceededFuture());
        } catch (Exception e) {
            logger.warn("Failed to notify a closure listener.", e);
        }
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

        private final ChannelFuture voidFuture = new VoidChannelFuture(AbstractChannel.this);

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
                doFinishConnect();
                connectFuture.setSuccess();
            } catch (Throwable t) {
                connectFuture.setFailure(t);
                pipeline().fireExceptionCaught(t);
                closeIfClosed();
            } finally {
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
                if (isOpen()) {
                    boolean wasActive = isActive();
                    try {
                        doClose();
                        future.setSuccess();
                    } catch (Throwable t) {
                        future.setFailure(t);
                    }

                    notifyClosureListeners();
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
            try {
                boolean closed = false;
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

                if (closed) {
                    close(voidFuture());
                }
            } catch (Throwable t) {
                pipeline().fireExceptionCaught(t);
                if (t instanceof IOException) {
                    close(voidFuture());
                }
            }
        }

        @Override
        public void flush(final ChannelFuture future) {
            // FIXME: Notify future properly using writtenAmount.
            if (eventLoop().inEventLoop()) {
                try {
                    writtenAmount += doFlush();
                } catch (Exception e) {
                    future.setFailure(e);
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            writtenAmount += doFlush();
                        } catch (Exception e) {
                            future.setFailure(e);
                        }
                    }
                });
            }
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
    protected abstract int doFlush() throws Exception;
}
