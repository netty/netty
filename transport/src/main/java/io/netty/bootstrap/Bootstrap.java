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
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.ThreadPerChannelEventLoopGroup;
import io.netty.resolver.JdkDomainNameResolver;
import io.netty.resolver.NameResolver;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
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

    /**
     * The default resolver.
     *
     * Note that the {@link GlobalEventExecutor} is only used for the notification of name resolution failures,
     * because we replace the executor after successful resolution in {@link LazyConnectPromise#setChannel(Channel)}.
     */
    private static final NameResolver DEFAULT_RESOLVER = new JdkDomainNameResolver(GlobalEventExecutor.INSTANCE);

    private volatile NameResolver resolver = DEFAULT_RESOLVER;
    private volatile SocketAddress remoteAddress;

    public Bootstrap() { }

    private Bootstrap(Bootstrap bootstrap) {
        super(bootstrap);
        remoteAddress = bootstrap.remoteAddress;
    }

    /**
     * Sets the {@link NameResolver} which will resolve the address of the unresolved named address.
     */
    public Bootstrap resolver(NameResolver resolver) {
        if (resolver == null) {
            throw new NullPointerException("resolver");
        }
        this.resolver = resolver;
        return this;
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
        remoteAddress = InetSocketAddress.createUnresolved(inetHost, inetPort);
        return this;
    }

    /**
     * @see {@link #remoteAddress(SocketAddress)}
     */
    public Bootstrap remoteAddress(InetAddress inetHost, int inetPort) {
        remoteAddress = new InetSocketAddress(inetHost, inetPort);
        return this;
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

        return doResolveAndConnect(remoteAddress, localAddress());
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
        return doResolveAndConnect(remoteAddress, localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validate();
        return doResolveAndConnect(remoteAddress, localAddress);
    }

    /**
     * @see {@link #connect()}
     */
    private ChannelFuture doResolveAndConnect(SocketAddress remoteAddress, final SocketAddress localAddress) {
        final NameResolver resolver = this.resolver;

        if (!resolver.isSupported(remoteAddress) || resolver.isResolved(remoteAddress)) {
            // Resolver has no idea about what to do with the specified remote address or it's resolved already.
            return doConnect(remoteAddress, localAddress, null);
        }

        final Future<SocketAddress> resolveFuture = resolver.resolve(remoteAddress);
        final Throwable resolveFailureCause = resolveFuture.cause();

        if (resolveFailureCause != null) {
            // Failed to resolve immediately
            return new LazyConnectPromise().setFailure(resolveFailureCause);
        }

        if (resolveFuture.isDone()) {
            // Succeeded to resolve immediately; cached? (or did a blocking lookup)
            return doConnect(resolveFuture.getNow(), localAddress, null);
        }

        // Wait until the name resolution is finished.
        final LazyConnectPromise connectPromise = new LazyConnectPromise();
        resolveFuture.addListener(new FutureListener<SocketAddress>() {
            @Override
            public void operationComplete(Future<SocketAddress> future) throws Exception {
                if (future.cause() != null) {
                    connectPromise.setFailure(future.cause());
                } else {
                    doConnect(future.getNow(), localAddress, connectPromise);
                }
            }
        });

        return connectPromise;
    }

    private ChannelFuture doConnect(
            final SocketAddress remoteAddress, final SocketAddress localAddress,
            LazyConnectPromise lazyConnectPromise) {

        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {
            if (lazyConnectPromise != null) {
                lazyConnectPromise.setChannel(channel);
                lazyConnectPromise.setFailure(regFuture.cause());
                return lazyConnectPromise;
            } else {
                return regFuture;
            }
        }

        final ChannelPromise connectPromise;
        if (lazyConnectPromise != null) {
            lazyConnectPromise.setChannel(channel);
            connectPromise = lazyConnectPromise;
        } else {
            connectPromise = channel.newPromise();
        }

        if (regFuture.isDone()) {
            doConnect0(regFuture, channel, remoteAddress, localAddress, connectPromise);
        } else {
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    doConnect0(regFuture, channel, remoteAddress, localAddress, connectPromise);
                }
            });
        }

        return connectPromise;
    }

    private static void doConnect0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (regFuture.isSuccess()) {
                    if (localAddress == null) {
                        channel.connect(remoteAddress, promise);
                    } else {
                        channel.connect(remoteAddress, localAddress, promise);
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

    private final class LazyConnectPromise extends DefaultChannelPromise {

        private volatile Channel channel;
        private volatile EventExecutor executor;

        LazyConnectPromise() {
            super(null);

            EventExecutorGroup group = group();
            if (group instanceof ThreadPerChannelEventLoopGroup) {
                /**
                 * Avoid the cost of instantiating an {@link UnsupportedOperationException} for known implementations.
                 */
                executor = GlobalEventExecutor.INSTANCE;
            } else {
                /**
                 * Most executor implementations implement the next() operation.
                 * However, when unsupported, we have no choice but using {@link GlobalEventExecutor}.
                 * {@link #temporaryExecutor()}
                 */
                try {
                    executor = group.next();
                } catch (UnsupportedOperationException ignore) {
                    executor = GlobalEventExecutor.INSTANCE;
                }
            }
        }

        @Override
        public Channel channel() {
            final Channel channel = this.channel;
            if (channel == null) {
                String msg = isDone()?
                        "channel not instantiated due to name resolution failure: " + cause() :
                        "channel not available until name resolution is in progress";

                throw new IllegalStateException(msg);
            }

            return channel;
        }

        @Override
        protected EventExecutor executor() {
            return executor;
        }

        void setChannel(Channel channel) {
            if (isDone()) {
                // Should never reach here
                throw new Error();
            }

            this.channel = channel;
            executor = channel.eventLoop();
        }
    }
}
