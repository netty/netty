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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * A skeletal {@link Channel} implementation.
 */
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannel.class);

    static final ConcurrentMap<Integer, Channel> allChannels = new ConcurrentHashMap<Integer, Channel>();

    private static Integer allocateId(Channel channel) {
        Integer id = Integer.valueOf(System.identityHashCode(channel));
        for (;;) {
            // Loop until a unique ID is acquired.
            // It should be found in one loop practically.
            if (allChannels.putIfAbsent(id, channel) == null) {
                // Successfully acquired.
                return id;
            } else {
                // Taken by other channel at almost the same moment.
                id = Integer.valueOf(id.intValue() + 1);
            }
        }
    }

    private final Integer id;
    private final Channel parent;
    private final Unsafe unsafe;
    private final ChannelPipeline pipeline = new DefaultChannelPipeline(this);
    private final List<ChannelFutureListener> closureListeners = new ArrayList<ChannelFutureListener>(4);
    private final ChannelFuture succeededFuture = new SucceededChannelFuture(this);

    private volatile EventLoop eventLoop;
    private volatile boolean registered;
    private volatile boolean notifiedClosureListeners;
    private ChannelFuture connectFuture;

    /** Cache for the string representation of this channel */
    private boolean strValActive;
    private String strVal;

    /**
     * Creates a new instance.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(Channel parent) {
        id = allocateId(this);
        this.parent = parent;
        unsafe = new DefaultUnsafe();

        closureListeners.add(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                allChannels.remove(id());
            }
        });
    }

    /**
     * (Internal use only) Creates a new temporary instance with the specified
     * ID.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(Integer id, Channel parent) {
        this.id = id;
        this.parent = parent;
        unsafe = new DefaultUnsafe();
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
        return eventLoop;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public void bind(SocketAddress localAddress, ChannelFuture future) {
        pipeline().bind(localAddress, future);
    }

    @Override
    public void connect(SocketAddress remoteAddress, ChannelFuture future) {
        pipeline().connect(remoteAddress, future);
    }

    @Override
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
        pipeline().connect(remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelFuture future) {
        pipeline().disconnect(future);
    }

    @Override
    public void close(ChannelFuture future) {
        pipeline().close(future);
    }

    @Override
    public void deregister(ChannelFuture future) {
        pipeline().deregister(future);
    }

    @Override
    public ChannelBufferHolder<Object> out() {
        return pipeline().nextOut();
    }

    @Override
    public void flush(ChannelFuture future) {
        pipeline().flush(future);
    }

    @Override
    public void write(Object message, ChannelFuture future) {
        pipeline.write(message, future);
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

        StringBuilder buf = new StringBuilder(128);
        buf.append("[id: 0x");
        buf.append(getIdString());

        SocketAddress localAddress = localAddress();
        SocketAddress remoteAddress = remoteAddress();
        if (remoteAddress != null) {
            buf.append(", ");
            if (parent() == null) {
                buf.append(localAddress);
                buf.append(active? " => " : " :> ");
                buf.append(remoteAddress);
            } else {
                buf.append(remoteAddress);
                buf.append(active? " => " : " :> ");
                buf.append(localAddress);
            }
        } else if (localAddress != null) {
            buf.append(", ");
            buf.append(localAddress);
        }

        buf.append(']');

        String strVal = buf.toString();
        this.strVal = strVal;
        strValActive = active;
        return strVal;
    }

    private String getIdString() {
        String answer = Integer.toHexString(id.intValue());
        switch (answer.length()) {
        case 0:
            answer = "00000000";
            break;
        case 1:
            answer = "0000000" + answer;
            break;
        case 2:
            answer = "000000" + answer;
            break;
        case 3:
            answer = "00000" + answer;
            break;
        case 4:
            answer = "0000" + answer;
            break;
        case 5:
            answer = "000" + answer;
            break;
        case 6:
            answer = "00" + answer;
            break;
        case 7:
            answer = "0" + answer;
            break;
        }
        return answer;
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
        public SocketAddress localAddress() {
            return localAddress0();
        }

        @Override
        public SocketAddress remoteAddress() {
            // TODO Auto-generated method stub
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
            doRegister(future);
            assert future.isDone();
            if (registered = future.isSuccess()) {
                pipeline().fireChannelRegistered();
            }
        }

        @Override
        public void bind(final SocketAddress localAddress, final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                doBind(localAddress, future);
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        doBind(localAddress, future);
                    }
                });
            }
        }

        @Override
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelFuture future) {
            // XXX: What if a user makes a connection attempt twice?
            if (eventLoop().inEventLoop()) {
                doConnect(remoteAddress, localAddress, future);
                if (!future.isDone()) {
                    connectFuture = future;
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        doConnect(remoteAddress, localAddress, future);
                        if (!future.isDone()) {
                            connectFuture = future;
                        }
                    }
                });
            }
        }

        @Override
        public void finishConnect() {
            assert eventLoop().inEventLoop();
            assert connectFuture != null;
            doFinishConnect(connectFuture);
        }

        @Override
        public void disconnect(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                doDisconnect(future);
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        doDisconnect(future);
                    }
                });
            }
        }

        @Override
        public void close(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                doClose(future);
                notifyClosureListeners();
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        doClose(future);
                        notifyClosureListeners();
                    }
                });
            }
        }

        @Override
        public void deregister(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                try {
                    doDeregister(future);
                } finally {
                    registered = false;
                    pipeline().fireChannelUnregistered();
                    eventLoop = null;
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            doDeregister(future);
                        } finally {
                            registered = false;
                            pipeline().fireChannelUnregistered();
                            eventLoop = null;
                        }
                    }
                });
            }
        }

        @Override
        public int read() throws IOException {
            assert eventLoop().inEventLoop();
            return doRead();
        }

        @Override
        public int flush(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                return doFlush(future);
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        doFlush(future);
                    }
                });
                return -1; // Unknown
            }
        }
    }

    protected abstract java.nio.channels.Channel javaChannel();
    protected abstract ChannelBufferHolder<Object> firstOut();

    protected abstract SocketAddress localAddress0();
    protected abstract SocketAddress remoteAddress0();

    protected abstract void doRegister(ChannelFuture future);
    protected abstract void doBind(SocketAddress localAddress, ChannelFuture future);
    protected abstract void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future);
    protected abstract void doFinishConnect(ChannelFuture future);
    protected abstract void doDisconnect(ChannelFuture future);
    protected abstract void doClose(ChannelFuture future);
    protected abstract void doDeregister(ChannelFuture future);

    protected abstract int doRead();
    protected abstract int doFlush(ChannelFuture future);
}
