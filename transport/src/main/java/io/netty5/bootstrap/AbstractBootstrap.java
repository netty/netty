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
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.util.AttributeKey;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SocketUtils;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.logging.InternalLogger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * {@link AbstractBootstrap} is a helper class that makes it easy to bootstrap a {@link Channel}. It support
 * method-chaining to provide an easy way to configure the {@link AbstractBootstrap}.
 *
 * <p>When not used in a {@link ServerBootstrap} context, the {@link #bind()} methods are useful for connectionless
 * transports such as datagram (UDP).</p>
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C, F>, C extends Channel, F>
        implements Cloneable {
    @SuppressWarnings("unchecked")
    private static final Map.Entry<ChannelOption<?>, Object>[] EMPTY_OPTION_ARRAY = new Map.Entry[0];
    @SuppressWarnings("unchecked")
    private static final Map.Entry<AttributeKey<?>, Object>[] EMPTY_ATTRIBUTE_ARRAY = new Map.Entry[0];

    volatile EventLoopGroup group;
    private volatile SocketAddress localAddress;

    // The order in which ChannelOptions are applied is important they may depend on each other for validation
    // purposes.
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<>();
    private final Map<AttributeKey<?>, Object> attrs = new ConcurrentHashMap<>();

    private volatile ChannelHandler handler;

    AbstractBootstrap() {
        // Disallow extending from a different package.
    }

    AbstractBootstrap(AbstractBootstrap<B, C, F> bootstrap) {
        group = bootstrap.group;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        synchronized (bootstrap.options) {
            options.putAll(bootstrap.options);
        }
        attrs.putAll(bootstrap.attrs);
    }

    /**
     * The {@link EventLoopGroup} which is used to handle all the events for the to-be-created
     * {@link Channel}
     */
    public B group(EventLoopGroup group) {
        requireNonNull(group, "group");
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return self();
    }

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /**
     * The {@link SocketAddress} which is used to bind the local "end" to.
     */
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }

    /**
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }

    /**
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(String inetHost, int inetPort) {
        return localAddress(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * @see #localAddress(SocketAddress)
     */
    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    public <T> B option(ChannelOption<T> option, T value) {
        requireNonNull(option, "option");
        synchronized (options) {
            if (value == null) {
                options.remove(option);
            } else {
                options.put(option, value);
            }
        }
        return self();
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link Channel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> B attr(AttributeKey<T> key, T value) {
        requireNonNull(key, "key");
        if (value == null) {
            attrs.remove(key);
        } else {
            attrs.put(key, value);
        }
        return self();
    }

    /**
     * Validate all the parameters. Sub-classes may override this, but should
     * call the super method in that case.
     */
    public B validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        return self();
    }

    /**
     * Returns a deep clone of this bootstrap which has the identical configuration.  This method is useful when making
     * multiple {@link Channel}s with similar settings.  Please note that this method does not clone the
     * {@link EventLoopGroup} deeply but shallowly, making the group a shared resource.
     */
    @Override
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public abstract B clone();

    /**
     * Create a new {@link Channel} and register it with an {@link EventLoop}.
     */
    public Future<Channel> register() {
        validate();
        return initAndRegister(group.next());
    }

    /**
     * Create a new unregistered channel.
     * <p>
     * The channel must then be {@linkplain Channel#register() registered} separately.
     *
     * @return A new unregistered channel.
     * @throws Exception If the channel cannot be created.
     */
    public Channel createUnregistered() throws Exception {
        validate();
        return initWithoutRegister();
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public Future<Channel> bind() {
        validate();
        SocketAddress localAddress = this.localAddress;
        requireNonNull(localAddress, "localAddress");
        return doBind(localAddress);
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public Future<Channel> bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public Future<Channel> bind(String inetHost, int inetPort) {
        return bind(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public Future<Channel> bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public Future<Channel> bind(SocketAddress localAddress) {
        validate();
        requireNonNull(localAddress, "localAddress");
        return doBind(localAddress);
    }

    private Future<Channel> doBind(final SocketAddress localAddress) {
        EventLoop loop = group.next();
        final Future<Channel> regFuture = initAndRegister(loop);
        if (regFuture.isFailed()) {
            return regFuture;
        }

        Promise<Channel> bindPromise = loop.newPromise();
        if (regFuture.isDone()) {
            // At this point we know that the registration was complete and successful.
            Channel channel = regFuture.getNow();
            Promise<Void> promise = channel.newPromise();
            promise.asFuture().map(v -> channel).cascadeTo(bindPromise);
            doBind0(regFuture, channel, localAddress, promise);
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            regFuture.addListener(future -> {
                Throwable cause = future.cause();
                if (cause != null) {
                    // Registration on the EventLoop failed so fail the Promise directly to not cause an
                    // IllegalStateException once we try to access the EventLoop of the Channel.
                    bindPromise.setFailure(cause);
                } else {
                    Channel channel = future.getNow();
                    Promise<Void> promise = channel.newPromise();
                    promise.asFuture().map(v -> channel).cascadeTo(bindPromise);
                    doBind0(regFuture, channel, localAddress, promise);
                }
            });
        }
        return bindPromise.asFuture();
    }

    final Future<Channel> initAndRegister(EventLoop loop) {
        final Channel channel;
        try {
            channel = newChannel(loop);
        } catch (Throwable t) {
            return loop.newFailedFuture(t);
        }

        Promise<Channel> promise = loop.newPromise();
        loop.execute(() -> init(channel).addListener(future -> {
            if (future.isSuccess()) {
                // TODO eventually I think we'd like to be able to either pass the generic promise down,
                //  or return the future from register().
                channel.register().addListener(f -> {
                    promise.setSuccess(channel);
                });
            } else {
                channel.unsafe().closeForcibly();
                promise.setFailure(future.cause());
            }
        }));

        return promise.asFuture();
    }

    final Channel initWithoutRegister() throws Exception {
        EventLoop loop = group.next();
        Channel channel = newChannel(loop);

        init(channel).addListener(future -> {
            if (future.isFailed()) {
                channel.unsafe().closeForcibly();
            }
        });
        return channel;
    }

    abstract C newChannel(EventLoop loop) throws Exception;

    abstract Future<Channel> init(Channel channel);

    private static void doBind0(
            final Future<Channel> regFuture, final Channel channel,
            final SocketAddress localAddress, final Promise<Void> promise) {
        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.executor().execute(() -> {
            if (regFuture.isSuccess()) {
               channel.bind(localAddress).cascadeTo(promise)
                        .addListener(channel, ChannelFutureListeners.CLOSE_ON_FAILURE);
            } else {
                promise.setFailure(regFuture.cause());
            }
        });
    }

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     */
    public B handler(ChannelHandler handler) {
        requireNonNull(handler, "handler");
        this.handler = handler;
        return self();
    }

    /**
     * Returns the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     *
     * @deprecated Use {@link #config()} instead.
     */
    @Deprecated
    public final EventLoopGroup group() {
        return group;
    }

    /**
     * Returns the {@link AbstractBootstrapConfig} object that can be used to obtain the current config
     * of the bootstrap.
     */
    public abstract AbstractBootstrapConfig<B, C, F> config();

    final Map.Entry<ChannelOption<?>, Object>[] newOptionsArray() {
        return newOptionsArray(options);
    }

    static Map.Entry<ChannelOption<?>, Object>[] newOptionsArray(Map<ChannelOption<?>, Object> options) {
        synchronized (options) {
            return new LinkedHashMap<ChannelOption<?>, Object>(options).entrySet().toArray(EMPTY_OPTION_ARRAY);
        }
    }

    final Map.Entry<AttributeKey<?>, Object>[] newAttributesArray() {
        return newAttributesArray(attrs0());
    }

    static Map.Entry<AttributeKey<?>, Object>[] newAttributesArray(Map<AttributeKey<?>, Object> attributes) {
        return attributes.entrySet().toArray(EMPTY_ATTRIBUTE_ARRAY);
    }

    final Map<ChannelOption<?>, Object> options0() {
        return options;
    }

    final Map<AttributeKey<?>, Object> attrs0() {
        return attrs;
    }

    final SocketAddress localAddress() {
        return localAddress;
    }

    final ChannelHandler handler() {
        return handler;
    }

    final Map<ChannelOption<?>, Object> options() {
        synchronized (options) {
            return copiedMap(options);
        }
    }

    final Map<AttributeKey<?>, Object> attrs() {
        return copiedMap(attrs);
    }

    static <K, V> Map<K, V> copiedMap(Map<K, V> map) {
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Map.copyOf(map);
    }

    static void setAttributes(Channel channel, Map.Entry<AttributeKey<?>, Object>[] attrs) {
        for (Map.Entry<AttributeKey<?>, Object> e: attrs) {
            @SuppressWarnings("unchecked")
            AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
            channel.attr(key).set(e.getValue());
        }
    }

    static void setChannelOptions(
            Channel channel, Map.Entry<ChannelOption<?>, Object>[] options, InternalLogger logger) {
        for (Map.Entry<ChannelOption<?>, Object> e: options) {
            setChannelOption(channel, e.getKey(), e.getValue(), logger);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setChannelOption(
            Channel channel, ChannelOption<?> option, Object value, InternalLogger logger) {
        try {
            if (!channel.config().setOption((ChannelOption<Object>) option, value)) {
                logger.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            logger.warn(
                    "Failed to set channel option '{}' with value '{}' for channel '{}'", option, value, channel, t);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append('(').append(config()).append(')');
        return buf.toString();
    }
}
