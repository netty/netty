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
package io.netty5.bootstrap;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFactory;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.ReflectiveChannelFactory;
import io.netty5.resolver.AddressResolver;
import io.netty5.resolver.AddressResolverGroup;
import io.netty5.resolver.DefaultAddressResolverGroup;
import io.netty5.resolver.NameResolver;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Bootstrap} that makes it easy to bootstrap a {@link Channel} to use
 * for clients.
 *
 * <p>The {@link #bind()} methods are useful in combination with connectionless transports such as datagram (UDP).
 * For regular TCP connections, please use the provided {@link #connect()} methods.</p>
 */
public class Bootstrap extends AbstractBootstrap<Bootstrap, Channel, ChannelFactory<? extends Channel>> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);

    private static final AddressResolverGroup<?> DEFAULT_RESOLVER = DefaultAddressResolverGroup.INSTANCE;

    private final BootstrapConfig config = new BootstrapConfig(this);

    @SuppressWarnings("unchecked")
    private volatile AddressResolverGroup<SocketAddress> resolver =
            (AddressResolverGroup<SocketAddress>) DEFAULT_RESOLVER;
    private volatile SocketAddress remoteAddress;
    volatile ChannelFactory<? extends Channel> channelFactory;

    public Bootstrap() { }

    private Bootstrap(Bootstrap bootstrap) {
        super(bootstrap);
        resolver = bootstrap.resolver;
        remoteAddress = bootstrap.remoteAddress;
        channelFactory = bootstrap.channelFactory;
    }

    /**
     * Sets the {@link NameResolver} which will resolve the address of the unresolved named address.
     *
     * @param resolver the {@link NameResolver} for this {@code Bootstrap}; may be {@code null}, in which case a default
     *                 resolver will be used
     *
     * @see DefaultAddressResolverGroup
     */
    @SuppressWarnings("unchecked")
    public Bootstrap resolver(AddressResolverGroup<?> resolver) {
        this.resolver = (AddressResolverGroup<SocketAddress>) (resolver == null ? DEFAULT_RESOLVER : resolver);
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
     * @see #remoteAddress(SocketAddress)
     */
    public Bootstrap remoteAddress(String inetHost, int inetPort) {
        remoteAddress = InetSocketAddress.createUnresolved(inetHost, inetPort);
        return this;
    }

    /**
     * @see #remoteAddress(SocketAddress)
     */
    public Bootstrap remoteAddress(InetAddress inetHost, int inetPort) {
        remoteAddress = new InetSocketAddress(inetHost, inetPort);
        return this;
    }

    /**
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    public Bootstrap channel(Class<? extends Channel> channelClass) {
        requireNonNull(channelClass, "channelClass");
        return channelFactory(new ReflectiveChannelFactory<Channel>(channelClass));
    }

    /**
     * {@link ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} to
     * simplify your code.
     */
    public Bootstrap channelFactory(ChannelFactory<? extends Channel> channelFactory) {
        requireNonNull(channelFactory, "channelFactory");
        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }

        this.channelFactory = channelFactory;
        return this;
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public Future<Channel> connect() {
        validate();
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            throw new IllegalStateException("remoteAddress not set");
        }

        return doResolveAndConnect(remoteAddress, config.localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public Future<Channel> connect(String inetHost, int inetPort) {
        return connect(InetSocketAddress.createUnresolved(inetHost, inetPort));
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public Future<Channel> connect(InetAddress inetHost, int inetPort) {
        return connect(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public Future<Channel> connect(SocketAddress remoteAddress) {
        requireNonNull(remoteAddress, "remoteAddress");

        validate();
        return doResolveAndConnect(remoteAddress, config.localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public Future<Channel> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        requireNonNull(remoteAddress, "remoteAddress");
        validate();
        return doResolveAndConnect(remoteAddress, localAddress);
    }

    /**
     * @see #connect()
     */
    private Future<Channel> doResolveAndConnect(final SocketAddress remoteAddress, final SocketAddress localAddress) {
        EventLoop loop = group.next();
        final Future<Channel> regFuture = initAndRegister(loop);

        Promise<Channel> resolveAndConnectPromise = loop.newPromise();
        if (regFuture.isDone()) {
            if (regFuture.isFailed()) {
                return regFuture;
            }
            Channel channel = regFuture.getNow();
            doResolveAndConnect0(channel, remoteAddress, localAddress, resolveAndConnectPromise);
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            regFuture.addListener(future -> {
                // Directly obtain the cause and do a null check, so we only need one volatile read in case of a
                // failure.
                Throwable cause = future.cause();
                if (cause != null) {
                    // Registration on the EventLoop failed so fail the Promise directly to not cause an
                    // IllegalStateException once we try to access the EventLoop of the Channel.
                    resolveAndConnectPromise.setFailure(cause);
                } else {
                    Channel channel = future.getNow();
                    doResolveAndConnect0(channel, remoteAddress, localAddress, resolveAndConnectPromise);
                }
            });
        }
        return resolveAndConnectPromise.asFuture();
    }

    private void doResolveAndConnect0(final Channel channel, SocketAddress remoteAddress,
                                      final SocketAddress localAddress, final Promise<Channel> promise) {
        try {
            final EventLoop eventLoop = channel.executor();
            final AddressResolver<SocketAddress> resolver = this.resolver.getResolver(eventLoop);

            if (!resolver.isSupported(remoteAddress) || resolver.isResolved(remoteAddress)) {
                // Resolver has no idea about what to do with the specified remote address, or it's resolved already.
                doConnect(remoteAddress, localAddress, channel, promise);
                return;
            }

            final Future<SocketAddress> resolveFuture = resolver.resolve(remoteAddress);

            if (resolveFuture.isDone()) {
                final Throwable resolveFailureCause = resolveFuture.cause();

                if (resolveFailureCause != null) {
                    // Failed to resolve immediately
                    channel.close();
                    promise.setFailure(resolveFailureCause);
                } else {
                    // Succeeded to resolve immediately; cached? (or did a blocking lookup)
                    doConnect(resolveFuture.getNow(), localAddress, channel, promise);
                    return;
                }
            }

            // Wait until the name resolution is finished.
            resolveFuture.addListener(future -> {
                if (future.cause() != null) {
                    channel.close();
                    promise.setFailure(future.cause());
                } else {
                    doConnect(future.getNow(), localAddress, channel, promise);
                }
            });
        } catch (Throwable cause) {
            promise.tryFailure(cause);
        }
    }

    private static void doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress, Channel channel, Promise<Channel> promise) {
        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.executor().execute(() -> {
            final Future<Void> future;
            if (localAddress == null) {
                future = channel.connect(remoteAddress);
            } else {
                future = channel.connect(remoteAddress, localAddress);
            }
            future.addListener(channel, ChannelFutureListeners.CLOSE_ON_FAILURE);
            future.map(v -> channel).cascadeTo(promise);
        });
    }

    @Override
    Future<Channel> init(Channel channel) {
        ChannelPipeline p = channel.pipeline();

        setChannelOptions(channel, newOptionsArray(), logger);
        setAttributes(channel, newAttributesArray());

        p.addLast(config.handler());

        return channel.executor().newSucceededFuture(channel);
    }

    @Override
    Channel newChannel(EventLoop eventLoop) throws Exception {
        return channelFactory.newChannel(eventLoop);
    }

    @Override
    public Bootstrap validate() {
        super.validate();
        if (config.handler() == null) {
            throw new IllegalStateException("handler not set");
        }
        if (config.channelFactory() == null) {
            throw new IllegalStateException("channelFactory not set");
        }
        return this;
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Bootstrap clone() {
        return new Bootstrap(this);
    }

    /**
     * Returns a deep clone of this bootstrap which has the identical configuration except that it uses
     * the given {@link EventLoopGroup}. This method is useful when making multiple {@link Channel}s with similar
     * settings.
     */
    public Bootstrap clone(EventLoopGroup group) {
        Bootstrap bs = new Bootstrap(this);
        bs.group = group;
        return bs;
    }

    @Override
    public final BootstrapConfig config() {
        return config;
    }

    final SocketAddress remoteAddress() {
        return remoteAddress;
    }

    final AddressResolverGroup<?> resolver() {
        return resolver;
    }
}
