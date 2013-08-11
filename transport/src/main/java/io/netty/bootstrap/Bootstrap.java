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
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

/**
 * A {@link Bootstrap} that makes it easy to bootstrap a {@link Channel} to use for clients.
 *
 * <p>
 * The {@link #bind()} methods are useful in combination with connectionless transports such as datagram (UDP). For
 * regular TCP connections, please use the provided {@link #connect()} methods.
 * </p>
 */
public final class Bootstrap extends AbstractBootstrap<Bootstrap, Channel> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);

    private volatile SocketAddress remoteAddress;

    public Bootstrap() {
    }

    private Bootstrap(Bootstrap bootstrap) {
        super(bootstrap);
        remoteAddress = bootstrap.remoteAddress;
    }

    /**
     * The {@link SocketAddress} to connect to once the {@link #connect()} method is called.
     */
    public Bootstrap remoteAddress(SocketAddress remoteAddress) {
        pendingQueries.remove(this.remoteAddress);
        this.remoteAddress = remoteAddress;
        queryDns(remoteAddress, true);
        return this;
    }

    /**
     * @see {@link #remoteAddress(SocketAddress)}
     */
    public Bootstrap remoteAddress(String inetHost, int inetPort) {
        return remoteAddress(InetSocketAddress.createUnresolved(inetHost, inetPort));
    }

    /**
     * @see {@link #remoteAddress(SocketAddress)}
     */
    public Bootstrap remoteAddress(InetAddress inetHost, int inetPort) {
        return remoteAddress(new InetSocketAddress(inetHost, inetPort));
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
        return connect(InetSocketAddress.createUnresolved(inetHost, inetPort));
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
        final Future<InetAddress> asyncRemote = queryDns(remoteAddress, false);
        pendingQueries.remove(remoteAddress);
        final Future<InetAddress> asyncLocal = queryDns(localAddress, false);
        pendingQueries.remove(localAddress);
        final int remotePort, localPort;
        if (asyncRemote != null && remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) remoteAddress;
            remotePort = address.getPort();
        } else {
            remotePort = -1;
        }
        if (asyncLocal != null && localAddress instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) localAddress;
            localPort = address.getPort();
        } else {
            localPort = -1;
        }
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {
            return regFuture;
        }

        final ChannelPromise promise = channel.newPromise();
        if (regFuture.isDone()) {
            doConnect0(regFuture, channel, asyncRemote == null ? remoteAddress : asyncRemote, remotePort,
                    asyncLocal == null ? localAddress : asyncLocal, localPort, promise);
        } else {
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    doConnect0(regFuture, channel, asyncRemote == null ? remoteAddress : asyncRemote, remotePort,
                            asyncLocal == null ? localAddress : asyncLocal, localPort, promise);
                }
            });
        }

        return promise;
    }

    private static void doConnect0(final ChannelFuture regFuture, final Channel channel, final Object remoteAddress,
            final int remotePort, final Object localAddress, final int localPort, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered. Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.eventLoop().execute(new Runnable() {
            @SuppressWarnings("unchecked")
            @Override
            public void run() {
                if (regFuture.isSuccess()) {
                    SocketAddress remote;
                    if (remoteAddress instanceof Future) {
                        try {
                            remote = new InetSocketAddress(((Future<InetAddress>) remoteAddress).get(), remotePort);
                        } catch (Exception e) {
                            remote = null;
                            logger.error("Future failed when resolving address", e);
                        }
                    } else {
                        remote = (SocketAddress) remoteAddress;
                    }
                    if (localAddress == null) {
                        channel.connect(remote, promise);
                    } else {
                        SocketAddress local;
                        if (localAddress instanceof Future) {
                            try {
                                local = new InetSocketAddress(((Future<InetAddress>) localAddress).get(), localPort);
                            } catch (Exception e) {
                                local = null;
                                logger.error("Future failed when resolving address", e);
                            }
                        } else {
                            local = (SocketAddress) localAddress;
                        }
                        channel.connect(remote, local, promise);
                    }
                    promise.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    void init(Channel channel) throws Exception {
        ChannelPipeline p = channel.pipeline();
        p.addLast(handler());

        final Map<ChannelOption<?>, Object> options = options();
        synchronized (options) {
            for (Entry<ChannelOption<?>, Object> e : options.entrySet()) {
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
            for (Entry<AttributeKey<?>, Object> e : attrs.entrySet()) {
                channel.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
            }
        }
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
