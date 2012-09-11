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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelException;


public abstract class Bootstrap<B extends Bootstrap<?>> {
    private EventLoopGroup group;
    private ChannelFactory factory;
    private SocketAddress localAddress;
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();
    private ChannelHandler handler;

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

    public B channel(Class<? extends Channel> channelClass) {
        if (channelClass == null) {
            throw new NullPointerException("channelClass");
        }
        return channelFactory(new BootstrapChannelFactory(channelClass));
    }

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

    @SuppressWarnings("unchecked")
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B localAddress(int port) {
        localAddress = new InetSocketAddress(port);
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B localAddress(String host, int port) {
        localAddress = new InetSocketAddress(host, port);
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B localAddress(InetAddress host, int port) {
        localAddress = new InetSocketAddress(host, port);
        return (B) this;
    }

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

    public void shutdown() {
        if (group != null) {
            group.shutdown();
        }
    }

    protected void validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (factory == null) {
            throw new IllegalStateException("factory not set");
        }
        if (handler == null) {
            throw new IllegalStateException("handler not set");
        }
    }

    protected final void validate(ChannelFuture future) {
        if (future == null) {
            throw new NullPointerException("future");
        }
        validate();
    }

    public ChannelFuture bind() {
        validate();
        Channel channel = factory().newChannel();
        return bind(channel.newFuture());
    }

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

    private final class BootstrapChannelFactory implements ChannelFactory {
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

    public interface ChannelFactory {
        Channel newChannel();
    }
}
