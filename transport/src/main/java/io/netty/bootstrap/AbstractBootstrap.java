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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link AbstractBootstrap} is a helper class that makes it easy to bootstrap a {@link Channel}. It support
 * method-chaining to provide an easy way to configure the {@link AbstractBootstrap}.
 *
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<?>> {

    private EventLoopGroup group;
    private ChannelFactory factory;
    private SocketAddress localAddress;
    private final Map<ChannelOption<?>, Object> options = new ConcurrentHashMap<ChannelOption<?>, Object>();
    private final Map<AttributeKey<?>, Object> attrs = new ConcurrentHashMap<AttributeKey<?>, Object>();
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
     * @see {@link #localAddress(SocketAddress)}
     */
    public B localAddress(int port) {
        return localAddress(new InetSocketAddress(port));
    }

    /**
     * @see {@link #localAddress(SocketAddress)}
     */
    public B localAddress(String host, int port) {
        return localAddress(new InetSocketAddress(host, port));
    }

    /**
     * @see {@link #localAddress(SocketAddress)}
     */
    public B localAddress(InetAddress host, int port) {
        return localAddress(new InetSocketAddress(host, port));
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
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

        @SuppressWarnings("unchecked")
        B b = (B) this;
        return b;
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
    public void validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (factory == null) {
            throw new IllegalStateException("factory not set");
        }
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind() {
        validate();
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        return doBind(localAddress);
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        validate();
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        return doBind(localAddress);
    }

    abstract ChannelFuture doBind(SocketAddress localAddress);

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

    final SocketAddress localAddress() {
        return localAddress;
    }

    final ChannelFactory factory() {
        return factory;
    }

    final ChannelHandler handler() {
        return handler;
    }

    final EventLoopGroup group() {
        return group;
    }

    final Map<ChannelOption<?>, Object> options() {
        return options;
    }

    final Map<AttributeKey<?>, Object> attrs() {
        return attrs;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append('(');
        if (group != null) {
            buf.append("group: ");
            buf.append(group.getClass().getSimpleName());
            buf.append(", ");
        }
        if (factory != null) {
            buf.append("factory: ");
            buf.append(factory);
            buf.append(", ");
        }
        if (localAddress != null) {
            buf.append("localAddress: ");
            buf.append(localAddress);
            buf.append(", ");
        }
        if (options != null && !options.isEmpty()) {
            buf.append("options: ");
            buf.append(options);
            buf.append(", ");
        }
        if (attrs != null && !attrs.isEmpty()) {
            buf.append("attrs: ");
            buf.append(attrs);
            buf.append(", ");
        }
        if (handler != null) {
            buf.append("handler: ");
            buf.append(handler);
            buf.append(", ");
        }
        if (buf.charAt(buf.length() - 1) == '(') {
            buf.append(')');
        } else {
            buf.setCharAt(buf.length() - 2, ')');
            buf.setLength(buf.length() - 1);
        }
        return buf.toString();
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

        @Override
        public String toString() {
            return clazz.getSimpleName() + ".class";
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
