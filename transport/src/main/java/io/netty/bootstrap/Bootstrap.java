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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Bootstrap {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);

    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();
    private EventLoop eventLoop;
    private Channel channel;
    private ChannelHandler handler;
    private SocketAddress localAddress;
    private SocketAddress remoteAddress;

    public Bootstrap eventLoop(EventLoop eventLoop) {
        if (eventLoop == null) {
            throw new NullPointerException("eventLoop");
        }
        if (this.eventLoop != null) {
            throw new IllegalStateException("eventLoop set already");
        }
        this.eventLoop = eventLoop;
        return this;
    }

    public Bootstrap channel(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (this.channel != null) {
            throw new IllegalStateException("channel set already");
        }
        this.channel = channel;
        return this;
    }

    public <T> Bootstrap option(ChannelOption<T> option, T value) {
        if (option == null) {
            throw new NullPointerException("option");
        }
        if (value == null) {
            options.remove(option);
        } else {
            options.put(option, value);
        }
        return this;
    }

    public Bootstrap handler(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.handler = handler;
        return this;
    }

    public Bootstrap localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return this;
    }

    public Bootstrap localAddress(int port) {
        localAddress = new InetSocketAddress(port);
        return this;
    }

    public Bootstrap localAddress(String host, int port) {
        localAddress = new InetSocketAddress(host, port);
        return this;
    }

    public Bootstrap localAddress(InetAddress host, int port) {
        localAddress = new InetSocketAddress(host, port);
        return this;
    }

    public Bootstrap remoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
    }

    public Bootstrap remoteAddress(String host, int port) {
        remoteAddress = new InetSocketAddress(host, port);
        return this;
    }

    public Bootstrap remoteAddress(InetAddress host, int port) {
        remoteAddress = new InetSocketAddress(host, port);
        return this;
    }

    public ChannelFuture bind() {
        validate();
        return bind(channel.newFuture());
    }

    public ChannelFuture bind(ChannelFuture future) {
        validate(future);
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }

        try {
            init();
        } catch (Throwable t) {
            future.setFailure(t);
            return future;
        }

        if (!ensureOpen(future)) {
            return future;
        }

        return channel.bind(localAddress, future).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    public ChannelFuture connect() {
        validate();
        return connect(channel.newFuture());
    }

    public ChannelFuture connect(ChannelFuture future) {
        validate(future);
        if (remoteAddress == null) {
            throw new IllegalStateException("remoteAddress not set");
        }

        try {
            init();
        } catch (Throwable t) {
            future.setFailure(t);
            return future;
        }

        if (!ensureOpen(future)) {
            return future;
        }

        if (localAddress == null) {
            channel.connect(remoteAddress, future);
        } else {
            channel.connect(remoteAddress, localAddress, future);
        }
        return future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    private void init() throws Exception {
        if (channel.isActive()) {
            throw new IllegalStateException("channel already active:: " + channel);
        }
        if (channel.isRegistered()) {
            throw new IllegalStateException("channel already registered: " + channel);
        }
        if (!channel.isOpen()) {
            throw new ClosedChannelException();
        }

        ChannelPipeline p = channel.pipeline();
        p.addLast(handler);

        for (Entry<ChannelOption<?>, Object> e: options.entrySet()) {
            try {
                if (!channel.config().setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                    logger.warn("Unknown channel option: " + e);
                }
            } catch (Throwable t) {
                logger.warn("Failed to set a channel option: " + channel, t);
            }
        }

        eventLoop.register(channel).syncUninterruptibly();
    }

    private static boolean ensureOpen(ChannelFuture future) {
        if (!future.channel().isOpen()) {
            // Registration was successful but the channel was closed due to some failure in
            // handler.
            future.setFailure(new ChannelException("initialization failure"));
            return false;
        }
        return true;
    }

    public void shutdown() {
        if (eventLoop != null) {
            eventLoop.shutdown();
        }
    }

    private void validate() {
        if (eventLoop == null) {
            throw new IllegalStateException("eventLoop not set");
        }
        if (channel == null) {
            throw new IllegalStateException("channel not set");
        }
        if (handler == null) {
            throw new IllegalStateException("handler not set");
        }
    }

    private void validate(ChannelFuture future) {
        if (future == null) {
            throw new NullPointerException("future");
        }

        if (future.channel() != channel) {
            throw new IllegalArgumentException("future.channel() must be the same channel.");
        }
        validate();
    }
}
