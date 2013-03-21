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
package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A {@link Bootstrap} that makes it easy to bootstrap a {@link Channel} to use
 * for clients.
 *
 * <p>The {@link #bind()} methods are useful in combination with connectionless transports such as datagram (UDP).
 * For regular TCP connections, please use the provided {@link #connect()} methods.</p>
 */
public final class Bootstrap extends AbstractBootstrap<Bootstrap, Channel> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);

    private volatile SocketAddress remoteAddress;

    public Bootstrap() { }

    private Bootstrap(Bootstrap bootstrap) {
        super(bootstrap);
        remoteAddress = bootstrap.remoteAddress;
    }

    /**
     * The {@link SocketAddress} to connect to once the {@link #connect()} method
     * is called.
     */
    public Bootstrap remoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
    }

    /**
     * @see {@link #remoteAddress(SocketAddress)}
     */
    public Bootstrap remoteAddress(String inetHost, int inetPort) {
        remoteAddress = new InetSocketAddress(inetHost, inetPort);
        return this;
    }

    /**
     * @see {@link #remoteAddress(SocketAddress)}
     */
    public Bootstrap remoteAddress(InetAddress inetHost, int inetPort) {
        remoteAddress = new InetSocketAddress(inetHost, inetPort);
        return this;
    }

    @Override
    ChannelFuture doBind(final SocketAddress localAddress) {
        final Channel channel = channelFactory().newChannel();
        ChannelPromise initPromise = init(channel);
        if (initPromise.cause() != null) {
            return initPromise;
        }

        final ChannelPromise promise = channel.newPromise();
        if (initPromise.isDone()) {
            doBind0(initPromise, channel, localAddress, promise);
        } else {
            initPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    doBind0(future, channel, localAddress, promise);
                }
            });
        }

        return promise;
    }

    private static void doBind0(
            ChannelFuture initFuture, Channel channel, SocketAddress localAddress, ChannelPromise promise) {

        if (initFuture.isSuccess()) {
            channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        } else {
            promise.setFailure(initFuture.cause());
        }
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect() {
        validate();
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            throw new IllegalStateException("remoteAddress not set");
        }

        return doConnect(remoteAddress, localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(String inetHost, int inetPort) {
        return connect(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(InetAddress inetHost, int inetPort) {
        return connect(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }

        validate();
        return doConnect(remoteAddress, localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validate();
        return doConnect(remoteAddress, localAddress);
    }

    /**
     * @see {@link #connect()}
     */
    private ChannelFuture doConnect(final SocketAddress remoteAddress, final SocketAddress localAddress) {
        final Channel channel = channelFactory().newChannel();
        ChannelPromise initPromise = init(channel);
        if (initPromise.cause() != null) {
            return initPromise;
        }

        final ChannelPromise promise = channel.newPromise();
        if (initPromise.isDone()) {
            doConnect0(initPromise, channel, remoteAddress, localAddress, promise);
        } else {
            initPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    doConnect0(future, channel, remoteAddress, localAddress, promise);
                }
            });
        }

        return promise;
    }

    private static void doConnect0(
            ChannelFuture initFuture, Channel channel,
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {

        if (initFuture.isSuccess()) {
            if (localAddress == null) {
                channel.connect(remoteAddress, promise);
            } else {
                channel.connect(remoteAddress, localAddress, promise);
            }
            promise.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        } else {
            promise.setFailure(initFuture.cause());
        }
    }

    @SuppressWarnings("unchecked")
    private ChannelPromise init(Channel channel) {
        ChannelPromise promise = channel.newPromise();
        try {
            ChannelPipeline p = channel.pipeline();
            p.addLast(handler());

            final Map<ChannelOption<?>, Object> options = options();
            synchronized (options) {
                for (Entry<ChannelOption<?>, Object> e: options.entrySet()) {
                    try {
                        if (!channel.config().setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                            logger.warn("Unknown channel option: " + e);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to set a channel option: " + channel, t);
                    }
                }
            }

            final Map<AttributeKey<?>, Object> attrs = attrs();
            synchronized (attrs) {
                for (Entry<AttributeKey<?>, Object> e: attrs.entrySet()) {
                    channel.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
                }
            }

            group().register(channel, promise);
        } catch (Throwable t) {
            promise.setFailure(t);
        }

        if (promise.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }

        // If we are here and the promise is not failed, it's one of the following cases:
        // 1) If we attempted registration from the event loop, the registration has been completed at this point.
        //    i.e. It's safe to attempt bind() or connect() now beause the channel has been registered.
        // 2) If we attempted registration from the other thread, the registration request has been successfully
        //    added to the event loop's task queue for later execution.
        //    i.e. It's safe to attempt bind() or connect() now:
        //         because bind() or connect() will be executed *after* the scheduled registration task is executed
        //         because register(), bind(), and connect() are all bound to the same thread.

        return promise;
    }

    @Override
    public Bootstrap validate() {
        super.validate();
        if (handler() == null) {
            throw new IllegalStateException("handler not set");
        }
        return this;
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Bootstrap clone() {
        return new Bootstrap(this);
    }

    @Override
    public String toString() {
        if (remoteAddress == null) {
            return super.toString();
        }

        StringBuilder buf = new StringBuilder(super.toString());
        buf.setLength(buf.length() - 1);
        buf.append(", remoteAddress: ");
        buf.append(remoteAddress);
        buf.append(')');

        return buf.toString();
    }
}
