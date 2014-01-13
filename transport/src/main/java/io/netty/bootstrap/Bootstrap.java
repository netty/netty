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
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.ServerChannel;
import io.netty.util.AttributeKey;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Constructor;
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

    private volatile ChannelFactory<? extends Channel> channelFactory;

    private volatile SocketAddress remoteAddress;

    public Bootstrap() { }

    private Bootstrap(Bootstrap bootstrap) {
        super(bootstrap);
        channelFactory = bootstrap.channelFactory;
        remoteAddress = bootstrap.remoteAddress;
    }

    /**
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    public Bootstrap channel(Class<? extends Channel> channelClass) {
        if (channelClass == null) {
            throw new NullPointerException("channelClass");
        }
        return channelFactory(new BootstrapChannelFactory<Channel>(channelClass));
    }

    /**
     * {@link ServerChannelFactory} which is used to create {@link ServerChannel} instances when calling
     * {@link #bind()}. This method is usually only used if {@link #channel(Class)} is not working for you because of
     * some more complex needs. If your {@link Channel} implementation has a no-args constructor, its highly recommend
     * to just use {@link #channel(Class)} for simplify your code.
     */
    public Bootstrap channelFactory(ChannelFactory<? extends Channel> channelFactory) {
        if (channelFactory == null) {
            throw new NullPointerException("channelFactory");
        }
        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }

        this.channelFactory = channelFactory;
        return this;
    }

    ChannelFactory<? extends Channel> channelFactory() {
        return channelFactory;
    }

    @Override
    Channel createChannel() {
        EventLoop eventLoop = group().next();
        return channelFactory().newChannel(eventLoop);
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
        remoteAddress = new InetSocketAddress(inetHost, inetPort);
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

        return doConnect(remoteAddress, localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(String inetHost, int inetPort) {
        return connect(new InetSocketAddress(inetHost, inetPort));
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
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {
            return regFuture;
        }

        final ChannelPromise promise = channel.newPromise();
        if (regFuture.isDone()) {
            doConnect0(regFuture, channel, remoteAddress, localAddress, promise);
        } else {
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    doConnect0(regFuture, channel, remoteAddress, localAddress, promise);
                }
            });
        }

        return promise;
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

    private static final class BootstrapChannelFactory<T extends Channel> implements ChannelFactory<T> {

        private final Class<? extends T> clazz;

        BootstrapChannelFactory(Class<? extends T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T newChannel(EventLoop eventLoop) {
            try {
                Constructor<? extends T> constructor = clazz.getConstructor(EventLoop.class);
                return constructor.newInstance(eventLoop);
            } catch (Throwable t) {
                throw new ChannelException("Unable to create Channel from class " + clazz, t);
            }
        }

        @Override
        public String toString() {
            return StringUtil.simpleClassName(clazz) + ".class";
        }
    }
}
