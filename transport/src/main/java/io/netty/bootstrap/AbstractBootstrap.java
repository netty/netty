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
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.util.AttributeKey;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link AbstractBootstrap} is a helper class that makes it easy to bootstrap a {@link Channel}. It support
 * method-chaining to provide an easy way to configure the {@link AbstractBootstrap}.
 *
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<?>> {
    private EventLoopGroup group;
    private ChannelFactory factory;
    private SocketAddress localAddress;
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<AttributeKey<?>, Object> attrs = new LinkedHashMap<AttributeKey<?>, Object>();
    private ChannelHandler handler;

    /**
     * The {@link EventLoopGroup} which is used to handle all the events for the to-be-creates
     * {@link Channel}
     */
    @SuppressWarnings("unchecked")
    public B group(EventLoopGroup group) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return (B) this;
    }

    /**
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    public B channel(Class<? extends Channel> channelClass) {
        if (channelClass == null) {
            throw new NullPointerException("channelClass");
        }
        return channelFactory(new BootstrapChannelFactory(channelClass));
    }

    /**
     * {@link ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} for
     * simplify your code.
     */
    @SuppressWarnings("unchecked")
    public B channelFactory(ChannelFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        if (this.factory != null) {
            throw new IllegalStateException("factory set already");
        }

        this.factory = factory;
        return (B) this;
    }

    /**
     * The {@link SocketAddress} which is used to bind the local "end" to.
     *
     */
    @SuppressWarnings("unchecked")
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return (B) this;
    }

    /**
     * See {@link #localAddress(SocketAddress)}
     */
    public B localAddress(int port) {
        return localAddress(new InetSocketAddress(port));
    }

    /**
     * See {@link #localAddress(SocketAddress)}
     */
    public B localAddress(String host, int port) {
        return localAddress(new InetSocketAddress(host, port));
    }

    /**
     * See {@link #localAddress(SocketAddress)}
     */
    public B localAddress(InetAddress host, int port) {
        return localAddress(new InetSocketAddress(host, port));
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they got
     * created. Use a value of <code>null</code> to remove a previous set {@link ChannelOption}.
     */
    @SuppressWarnings("unchecked")
    public <T> B option(ChannelOption<T> option, T value) {
        if (option == null) {
            throw new NullPointerException("option");
        }
        if (value == null) {
            options.remove(option);
        } else {
            options.put(option, value);
        }
        return (B) this;
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link Channel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> B attr(AttributeKey<T> key, T value) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (value == null) {
            attrs.remove(key);
        } else {
            attrs.put(key, value);
        }

        return (B) this;
    }

    /**
     * Shutdown the {@link AbstractBootstrap} and the {@link EventLoopGroup} which is
     * used by it. Only call this if you don't share the {@link EventLoopGroup}
     * between different {@link AbstractBootstrap}'s.
     */
    public void shutdown() {
        if (group != null) {
            group.shutdown();
        }
    }

    /**
     * Validate all the parameters. Sub-classes may override this, but should
     * call the super method in that case.
     */
    protected void validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (factory == null) {
            throw new IllegalStateException("factory not set");
        }
    }

    protected final void validate(ChannelFuture future) {
        if (future == null) {
            throw new NullPointerException("future");
        }
        validate();
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind() {
        validate();
        Channel channel = factory().newChannel();
        return bind(channel.newFuture());
    }

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     */
    @SuppressWarnings("unchecked")
    public B handler(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.handler = handler;
        return (B) this;
    }

    protected static boolean ensureOpen(ChannelFuture future) {
        if (!future.channel().isOpen()) {
            // Registration was successful but the channel was closed due to some failure in
            // handler.
            future.setFailure(new ChannelException("initialization failure"));
            return false;
        }
        return true;
    }

    /**
     * Bind the {@link Channel} of the given {@link ChannelFactory}.
     */
    public abstract ChannelFuture bind(ChannelFuture future);

    protected final SocketAddress localAddress() {
        return localAddress;
    }

    protected final ChannelFactory factory() {
        return factory;
    }

    protected final ChannelHandler handler() {
        return handler;
    }

    protected final EventLoopGroup group() {
        return group;
    }

    protected final Map<ChannelOption<?>, Object> options() {
        return options;
    }

    protected final Map<AttributeKey<?>, Object> attrs() {
        return attrs;
    }

    private static final class BootstrapChannelFactory implements ChannelFactory {
        private final Class<? extends Channel> clazz;

        BootstrapChannelFactory(Class<? extends Channel> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Channel newChannel() {
            try {
                return clazz.newInstance();
            } catch (Throwable t) {
                throw new ChannelException("Unable to create Channel from class " + clazz, t);
            }
        }

    }

    /**
     * Factory that is responsible to create new {@link Channel}'s on {@link AbstractBootstrap#bind()}
     * requests.
     *
     */
    public interface ChannelFactory {
        /**
         * {@link Channel} to use in the {@link AbstractBootstrap}
         */
        Channel newChannel();
    }
}
