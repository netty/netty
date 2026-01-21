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
package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.NameResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;

/**
 * A {@link Bootstrap} that makes it easy to bootstrap a {@link Channel} to use
 * for clients.
 *
 * <p>The {@link #bind()} methods are useful in combination with connectionless transports such as datagram (UDP).
 * For regular TCP connections, please use the provided {@link #connect()} methods.</p>
 */
public class Bootstrap extends AbstractBootstrap<Bootstrap, Channel> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);

    private final BootstrapConfig config = new BootstrapConfig(this);

    private ExternalAddressResolver externalResolver;
    private volatile boolean disableResolver;
    private volatile SocketAddress remoteAddress;
    volatile ChannelFactory<? extends Channel> channelFactory;

    public Bootstrap() { }

    private Bootstrap(Bootstrap bootstrap) {
        super(bootstrap);
        externalResolver = bootstrap.externalResolver;
        disableResolver = bootstrap.disableResolver;
        remoteAddress = bootstrap.remoteAddress;
        channelFactory = bootstrap.channelFactory;
    }

    /**
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(io.netty.channel.ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    public Bootstrap channel(Class<? extends Channel> channelClass) {
        ObjectUtil.checkNotNull(channelClass, "channelClass");
        return channelFactory(new ReflectiveChannelFactory<>(channelClass));
    }

    /**
     * {@link io.netty.channel.ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} to
     * simplify your code.
     */
    public Bootstrap channelFactory(ChannelFactory<? extends Channel> channelFactory) {
        ObjectUtil.checkNotNull(channelFactory, "channelFactory");
        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }

        this.channelFactory = channelFactory;
        return this;
    }

    /**
     * Sets the {@link NameResolver} which will resolve the address of the unresolved named address.
     *
     * @param resolver the {@link NameResolver} for this {@code Bootstrap}; may be {@code null}, in which case a default
     *                 resolver will be used
     *
     * @see io.netty.resolver.DefaultAddressResolverGroup
     */
    public Bootstrap resolver(AddressResolverGroup<?> resolver) {
        externalResolver = resolver == null ? null : new ExternalAddressResolver(resolver);
        disableResolver = false;
        return this;
    }

    /**
     * Disables address name resolution. Name resolution may be re-enabled with
     * {@link Bootstrap#resolver(AddressResolverGroup)}
     */
    public Bootstrap disableResolver() {
        externalResolver = null;
        disableResolver = true;
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
        ObjectUtil.checkNotNull(remoteAddress, "remoteAddress");
        validate();
        return doResolveAndConnect(remoteAddress, config.localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public Future<Channel> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        ObjectUtil.checkNotNull(remoteAddress, "remoteAddress");
        validate();
        return doResolveAndConnect(remoteAddress, localAddress);
    }

    /**
     * @see #connect()
     */
    private Future<Channel> doResolveAndConnect(final SocketAddress remoteAddress, final SocketAddress localAddress) {
        final Future<Channel> regFuture = initAndRegister();
        if (regFuture.isDone()) {
            if (!regFuture.isSuccess()) {
                return regFuture;
            }
            return doResolveAndConnect0(regFuture.getNow(), remoteAddress, localAddress,
                    regFuture.executor().newPromise());
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            final Promise<Channel> promise = regFuture.executor().newPromise();
            regFuture.addListener((FutureListener<Channel>) future -> {
                // Directly obtain the cause and do a null check so we only need one volatile read in case of a
                // failure.
                Throwable cause = future.cause();
                if (cause != null) {
                    // Registration on the EventLoop failed so fail the Promise<Void> directly to not cause an
                    // IllegalStateException once we try to access the EventLoop of the Channel.
                    promise.setFailure(cause);
                } else {
                    doResolveAndConnect0(future.getNow(), remoteAddress, localAddress, promise);
                }
            });
            return promise;
        }
    }

    private Future<Channel> doResolveAndConnect0(final Channel channel, SocketAddress remoteAddress,
                                              final SocketAddress localAddress, final Promise<Channel> promise) {
        try {
            if (disableResolver) {
                doConnect(channel, remoteAddress, localAddress, promise);
                return promise;
            }

            final EventLoop eventLoop = channel.executor();
            AddressResolver<SocketAddress> resolver;
            try {
                resolver = ExternalAddressResolver.getOrDefault(externalResolver).getResolver(eventLoop);
            } catch (Throwable cause) {
                channel.close();
                return promise.setFailure(cause);
            }

            if (!resolver.isSupported(remoteAddress) || resolver.isResolved(remoteAddress)) {
                // Resolver has no idea about what to do with the specified remote address or it's resolved already.
                doConnect(channel, remoteAddress, localAddress, promise);
                return promise;
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
                    doConnect(channel, resolveFuture.getNow(), localAddress, promise);
                }
                return promise;
            }

            // Wait until the name resolution is finished.
            resolveFuture.addListener((FutureListener<SocketAddress>) future -> {
                if (future.cause() != null) {
                    promise.setFailure(future.cause());
                    channel.close();
                } else {
                    doConnect(channel, future.getNow(), localAddress, promise);
                }
            });
        } catch (Throwable cause) {
            promise.tryFailure(cause);
        }
        return promise;
    }

    private static void doConnect(Channel channel, final SocketAddress remoteAddress, final SocketAddress localAddress,
                                  final Promise<Channel> connectPromise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.executor().execute(new Runnable() {
            @Override
            public void run() {
                final Future<Void> cf;
                if (localAddress == null) {
                    cf = channel.connect(remoteAddress);
                } else {
                    cf = channel.connect(remoteAddress, localAddress);
                }
                cf.addListener(f -> {
                    if (f.isSuccess()) {
                        connectPromise.setSuccess(channel);
                    } else {
                        connectPromise.setFailure(f.cause());
                        channel.close();
                    }
                });
            }
        });
    }

    @Override
    void init(Channel channel) throws Throwable {
        ChannelPipeline p = channel.pipeline();
        p.addLast(config.handler());

        setChannelOptions(channel, newOptionsArray(), logger);

        setAttributes(channel, newAttributesArray());
        Collection<ChannelInitializerExtension> extensions = getInitializerExtensions();
        if (!extensions.isEmpty()) {
            for (ChannelInitializerExtension extension : extensions) {
                try {
                    extension.postInitializeClientChannel(channel);
                } catch (Exception e) {
                    logger.warn("Exception thrown from postInitializeClientChannel", e);
                }
            }
        }
    }

    @Override
    protected Channel newChannel(EventLoop eventLoop) {
        return channelFactory.newChannel(eventLoop);
    }

    @Override
    public Bootstrap validate() {
        super.validate();
        if (config.handler() == null) {
            throw new IllegalStateException("handler not set");
        }
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
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
        if (disableResolver) {
            return null;
        }
        return ExternalAddressResolver.getOrDefault(externalResolver);
    }

    final ChannelFactory<? extends Channel> channelFactory() {
        return channelFactory;
    }

    /* Holder to avoid NoClassDefFoundError in case netty-resolver dependency is excluded
       (e.g. some address families do not need name resolution) */
    static final class ExternalAddressResolver {
        final AddressResolverGroup<SocketAddress> resolverGroup;

        @SuppressWarnings("unchecked")
        ExternalAddressResolver(AddressResolverGroup<?> resolverGroup) {
            this.resolverGroup = (AddressResolverGroup<SocketAddress>) resolverGroup;
        }

        @SuppressWarnings("unchecked")
        static AddressResolverGroup<SocketAddress> getOrDefault(ExternalAddressResolver externalResolver) {
            if (externalResolver == null) {
                AddressResolverGroup<?> defaultResolverGroup = DefaultAddressResolverGroup.INSTANCE;
                return (AddressResolverGroup<SocketAddress>) defaultResolverGroup;
            }
            return externalResolver.resolverGroup;
        }
    }
}
