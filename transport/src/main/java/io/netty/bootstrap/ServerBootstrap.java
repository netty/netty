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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.ServerChannel;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.SocketAddresses;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

public class ServerBootstrap {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ServerBootstrap.class);
    private static final InetSocketAddress DEFAULT_LOCAL_ADDR = new InetSocketAddress(SocketAddresses.LOCALHOST, 0);

    private final ChannelHandler acceptor = new ChannelInitializer<Channel>() {
        @Override
        public void initChannel(Channel ch) throws Exception {
            ch.pipeline().addLast(new Acceptor());
        }
    };

    private final Map<ChannelOption<?>, Object> parentOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    private EventLoop parentEventLoop;
    private EventLoop childEventLoop;
    private ServerChannel channel;
    private ChannelHandler handler;
    private ChannelHandler childHandler;
    private SocketAddress localAddress;

    public ServerBootstrap eventLoop(EventLoop parentEventLoop, EventLoop childEventLoop) {
        if (parentEventLoop == null) {
            throw new NullPointerException("parentEventLoop");
        }
        if (this.parentEventLoop != null) {
            throw new IllegalStateException("eventLoop set already");
        }
        this.parentEventLoop = parentEventLoop;
        this.childEventLoop = childEventLoop;
        return this;
    }

    public ServerBootstrap channel(ServerChannel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (this.channel != null) {
            throw new IllegalStateException("channel set already");
        }
        this.channel = channel;
        return this;
    }

    public <T> ServerBootstrap option(ChannelOption<T> parentOption, T value) {
        if (parentOption == null) {
            throw new NullPointerException("parentOption");
        }
        if (value == null) {
            parentOptions.remove(parentOption);
        } else {
            parentOptions.put(parentOption, value);
        }
        return this;
    }

    public <T> ServerBootstrap childOption(ChannelOption<T> childOption, T value) {
        if (childOption == null) {
            throw new NullPointerException("childOption");
        }
        if (value == null) {
            childOptions.remove(childOption);
        } else {
            childOptions.put(childOption, value);
        }
        return this;
    }

    public ServerBootstrap handler(ChannelHandler handler) {
        this.handler = handler;
        return this;
    }

    public ServerBootstrap childHandler(ChannelHandler childHandler) {
        if (childHandler == null) {
            throw new NullPointerException("childHandler");
        }
        this.childHandler = childHandler;
        return this;
    }

    public ServerBootstrap localAddress(SocketAddress localAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        this.localAddress = localAddress;
        return this;
    }

    public ServerBootstrap localAddress(int port) {
        localAddress = new InetSocketAddress(port);
        return this;
    }

    public ServerBootstrap localAddress(String host, int port) {
        localAddress = new InetSocketAddress(host, port);
        return this;
    }

    public ServerBootstrap localAddress(InetAddress host, int port) {
        localAddress = new InetSocketAddress(host, port);
        return this;
    }

    public ChannelFuture bind() {
        validate();
        return bind(channel.newFuture());
    }

    public ChannelFuture bind(ChannelFuture future) {
        validate(future);
        if (channel.isActive()) {
            future.setFailure(new IllegalStateException("channel already bound: " + channel));
            return future;
        }
        if (channel.isRegistered()) {
            future.setFailure(new IllegalStateException("channel already registered: " + channel));
            return future;
        }
        if (!channel.isOpen()) {
            future.setFailure(new ClosedChannelException());
            return future;
        }

        ChannelPipeline p = channel.pipeline();
        if (handler != null) {
            p.addLast(handler);
        }
        p.addLast(acceptor);

        ChannelFuture f = parentEventLoop.register(channel).awaitUninterruptibly();
        if (!f.isSuccess()) {
            future.setFailure(f.cause());
            return future;
        }

        if (!channel.isOpen()) {
            // Registration was successful but the channel was closed due to some failure in
            // handler.
            future.setFailure(new ChannelException("initialization failure"));
            return future;
        }

        channel.bind(localAddress, future).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        return future;
    }

    public void shutdown() {
        if (parentEventLoop != null) {
            parentEventLoop.shutdown();
        }
        if (childEventLoop != null) {
            childEventLoop.shutdown();
        }
    }

    private void validate() {
        if (parentEventLoop == null) {
            throw new IllegalStateException("eventLoop not set");
        }
        if (channel == null) {
            throw new IllegalStateException("channel not set");
        }
        if (childHandler == null) {
            throw new IllegalStateException("childHandler not set");
        }
        if (childEventLoop == null) {
            logger.warn("childEventLoop is not set. Using eventLoop instead.");
            childEventLoop = parentEventLoop;
        }
        if (localAddress == null) {
            logger.warn("localAddress is not set. Using " + DEFAULT_LOCAL_ADDR + " instead.");
            localAddress = DEFAULT_LOCAL_ADDR;
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

    private class Acceptor extends ChannelInboundMessageHandlerAdapter<Channel> {

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) {
            Queue<Channel> in = ctx.inboundMessageBuffer();
            for (;;) {
                Channel child = in.poll();
                if (child == null) {
                    break;
                }

                child.pipeline().addLast(childHandler);

                for (Entry<ChannelOption<?>, Object> e: childOptions.entrySet()) {
                    try {
                        if (!child.config().setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                            logger.warn("Unknown channel option: " + e);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to set a channel option: " + child, t);
                    }
                }

                try {
                    childEventLoop.register(child);
                } catch (Throwable t) {
                    logger.warn("Failed to register an accepted channel: " + child, t);
                }
            }
        }
    }
}
