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
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.AttributeKey;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map.Entry;

/**
 * A {@link Bootstrap} that makes it easy to bootstrap a {@link Channel} to use
 * for clients.
 *
 */
public class Bootstrap extends AbstractBootstrap<Bootstrap> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);
    private SocketAddress remoteAddress;

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

    @Override
    ChannelFuture doBind(SocketAddress localAddress) {
        Channel channel = factory().newChannel();
        try {
            init(channel);
        } catch (Throwable t) {
            channel.close();
            return channel.newFailedFuture(t);
        }

        return channel.bind(localAddress).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
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
    private ChannelFuture doConnect(SocketAddress remoteAddress, SocketAddress localAddress) {
        final Channel channel = factory().newChannel();

        try {
            init(channel);
        } catch (Throwable t) {
            channel.close();
            return channel.newFailedFuture(t);
        }

        final ChannelFuture future;
        if (localAddress == null) {
            future = channel.connect(remoteAddress);
        } else {
            future = channel.connect(remoteAddress, localAddress);
        }

        return future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @SuppressWarnings("unchecked")
    private void init(Channel channel) throws Exception {
        ChannelPipeline p = channel.pipeline();
        p.addLast(handler());

        for (Entry<ChannelOption<?>, Object> e: options().entrySet()) {
            try {
                if (!channel.config().setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                    logger.warn("Unknown channel option: " + e);
                }
            } catch (Throwable t) {
                logger.warn("Failed to set a channel option: " + channel, t);
            }
        }

        for (Entry<AttributeKey<?>, Object> e: attrs().entrySet()) {
            channel.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
        }

        group().register(channel).syncUninterruptibly();
    }

    @Override
    public void validate() {
        super.validate();
        if (handler() == null) {
            throw new IllegalStateException("handler not set");
        }
    }

    /**
     * Create a new {@link Bootstrap} which has the identical configuration with this {@link Bootstrap}.
     * This method is useful when you make multiple connections with similar settings.
     *
     * Be aware that if you call {@link #shutdown()} on one of them it will shutdown shared resources.
     */
    public Bootstrap duplicate() {
        validate();
        Bootstrap b = new Bootstrap()
                .group(group()).channelFactory(factory()).handler(handler())
                .localAddress(localAddress()).remoteAddress(remoteAddress);
        b.options().putAll(options());
        b.attrs().putAll(attrs());
        return b;
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
}
