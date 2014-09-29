/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.socket.aio;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.ChannelOption.*;

/**
 * The Async {@link ServerSocketChannelConfig} implementation.
 */
final class AioServerSocketChannelConfig extends DefaultChannelConfig implements ServerSocketChannelConfig {

    private static final int DEFAULT_SND_BUF_SIZE = 32 * 1024;
    private static final boolean DEFAULT_SO_REUSEADDR = false;
    private final AtomicReference<AsynchronousServerSocketChannel> javaChannel
            = new AtomicReference<AsynchronousServerSocketChannel>();
    private volatile int backlog = NetUtil.SOMAXCONN;
    private Map<SocketOption<?>, Object> options = PlatformDependent.newConcurrentHashMap();

    /**
     * Creates a new instance with no {@link AsynchronousServerSocketChannel} assigned to it.
     * <p/>
     * You should call {@link #assign(AsynchronousServerSocketChannel)} to assign a {@link
     * AsynchronousServerSocketChannel} to it and have the configuration set on it.
     */
    AioServerSocketChannelConfig(AioServerSocketChannel channel) {
        super(channel);
    }

    /**
     * Creates a new instance with the given {@link AsynchronousServerSocketChannel} assigned to it.
     */
    AioServerSocketChannelConfig(AioServerSocketChannel channel, AsynchronousServerSocketChannel javaChannel) {
        super(channel);
        this.javaChannel.set(javaChannel);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), SO_RCVBUF, SO_REUSEADDR, SO_BACKLOG);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == SO_REUSEADDR) {
            return (T) Boolean.valueOf(isReuseAddress());
        }
        if (option == SO_BACKLOG) {
            return (T) Integer.valueOf(getBacklog());
        }

        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == SO_BACKLOG) {
            setBacklog((Integer) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public boolean isReuseAddress() {
        return (Boolean) getOption(StandardSocketOptions.SO_REUSEADDR, DEFAULT_SO_REUSEADDR);
    }

    @Override
    public AioServerSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        setOption(StandardSocketOptions.SO_REUSEADDR, reuseAddress);
        return this;
    }

    @Override
    public int getReceiveBufferSize() {
        return (Integer) getOption(StandardSocketOptions.SO_RCVBUF, DEFAULT_SND_BUF_SIZE);
    }

    @Override
    public AioServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
        return this;
    }

    @Override
    public AioServerSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getBacklog() {
        return backlog;
    }

    @Override
    public AioServerSocketChannelConfig setBacklog(int backlog) {
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog: " + backlog);
        }
        this.backlog = backlog;
        return this;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object getOption(SocketOption option, Object defaultValue) {
        if (javaChannel.get() == null) {
            Object value = options.get(option);
            if (value == null) {
                return defaultValue;
            } else {
                return value;
            }
        }

        try {
            return javaChannel.get().getOption(option);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void setOption(SocketOption option, Object defaultValue) {
        if (javaChannel.get() == null) {
            options.put(option, defaultValue);
            return;
        }
        try {
            javaChannel.get().setOption(option, defaultValue);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Assing the given {@link AsynchronousServerSocketChannel} to this instance
     */
    void assign(AsynchronousServerSocketChannel javaChannel) {
        if (javaChannel == null) {
            throw new NullPointerException("javaChannel");
        }
        if (this.javaChannel.compareAndSet(null, javaChannel)) {
            propagateOptions();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void propagateOptions() {
        for (SocketOption option : options.keySet()) {
            Object value = options.remove(option);
            if (value != null) {
                try {
                    javaChannel.get().setOption(option, value);
                } catch (IOException e) {
                    throw new ChannelException(e);
                }
            }
        }
        // not needed anymore
        options = null;
    }

    @Override
    public AioServerSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    public AioServerSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public AioServerSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public AioServerSocketChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public AioServerSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public AioServerSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public AioServerSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }
}
