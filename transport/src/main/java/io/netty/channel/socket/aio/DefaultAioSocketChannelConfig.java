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
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.ChannelOption.*;

/**
 * The default {@link AioSocketChannelConfig} implementation.
 */
final class DefaultAioSocketChannelConfig extends DefaultChannelConfig
        implements AioSocketChannelConfig {

    private static final int DEFAULT_RCV_BUF_SIZE = 32 * 1024;
    private static final int DEFAULT_SND_BUF_SIZE = 32 * 1024;
    private static final int DEFAULT_SO_LINGER = -1;
    private static final boolean DEFAULT_SO_KEEP_ALIVE = false;
    private static final int DEFAULT_IP_TOS = 0;
    private static final boolean DEFAULT_SO_REUSEADDR = false;
    private static final boolean DEFAULT_TCP_NODELAY = false;
    private final AtomicReference<NetworkChannel> javaChannel = new AtomicReference<NetworkChannel>();
    private volatile boolean allowHalfClosure;
    private volatile long readTimeoutInMillis;
    private volatile long writeTimeoutInMillis;
    private Map<SocketOption<?>, Object> options = PlatformDependent.newConcurrentHashMap();

    /**
     * Creates a new instance with no {@link NetworkChannel} assigned to it.
     * <p/>
     * You should call {@link #assign(NetworkChannel)} to assign a {@link NetworkChannel} to it and have the
     * configuration set on it.
     */
    DefaultAioSocketChannelConfig(AioSocketChannel channel) {
        super(channel);
        enableTcpNoDelay();
    }

    /**
     * Creates a new instance with the given {@link NetworkChannel} assigned to it.
     */
    DefaultAioSocketChannelConfig(AioSocketChannel channel, NetworkChannel javaChannel) {
        super(channel);
        this.javaChannel.set(javaChannel);
        enableTcpNoDelay();
    }

    private void enableTcpNoDelay() {
        // Enable TCP_NODELAY by default if possible.
        if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
            try {
                setTcpNoDelay(true);
            } catch (Exception e) {
                // Ignore.
            }
        }
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS,
                AIO_READ_TIMEOUT, AIO_WRITE_TIMEOUT, ALLOW_HALF_CLOSURE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == SO_SNDBUF) {
            return (T) Integer.valueOf(getSendBufferSize());
        }
        if (option == TCP_NODELAY) {
            return (T) Boolean.valueOf(isTcpNoDelay());
        }
        if (option == SO_KEEPALIVE) {
            return (T) Boolean.valueOf(isKeepAlive());
        }
        if (option == SO_REUSEADDR) {
            return (T) Boolean.valueOf(isReuseAddress());
        }
        if (option == SO_LINGER) {
            return (T) Integer.valueOf(getSoLinger());
        }
        if (option == IP_TOS) {
            return (T) Integer.valueOf(getTrafficClass());
        }
        if (option == AIO_READ_TIMEOUT) {
            return (T) Long.valueOf(getReadTimeout());
        }
        if (option == AIO_WRITE_TIMEOUT) {
            return (T) Long.valueOf(getWriteTimeout());
        }
        if (option == ALLOW_HALF_CLOSURE) {
            return (T) Boolean.valueOf(isAllowHalfClosure());
        }

        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == SO_SNDBUF) {
            setSendBufferSize((Integer) value);
        } else if (option == TCP_NODELAY) {
            setTcpNoDelay((Boolean) value);
        } else if (option == SO_KEEPALIVE) {
            setKeepAlive((Boolean) value);
        } else if (option == SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == SO_LINGER) {
            setSoLinger((Integer) value);
        } else if (option == IP_TOS) {
            setTrafficClass((Integer) value);
        } else if (option == AIO_READ_TIMEOUT) {
            setReadTimeout((Long) value);
        } else if (option == AIO_WRITE_TIMEOUT) {
            setWriteTimeout((Long) value);
        } else if (option == ALLOW_HALF_CLOSURE) {
            setAllowHalfClosure((Boolean) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public int getReceiveBufferSize() {
        return (Integer) getOption(StandardSocketOptions.SO_RCVBUF, DEFAULT_RCV_BUF_SIZE);
    }

    @Override
    public int getSendBufferSize() {
        return (Integer) getOption(StandardSocketOptions.SO_SNDBUF, DEFAULT_SND_BUF_SIZE);
    }

    @Override
    public int getSoLinger() {
        return (Integer) getOption(StandardSocketOptions.SO_LINGER, DEFAULT_SO_LINGER);
    }

    @Override
    public int getTrafficClass() {
        return (Integer) getOption(StandardSocketOptions.IP_TOS, DEFAULT_IP_TOS);
    }

    @Override
    public boolean isKeepAlive() {
        return (Boolean) getOption(StandardSocketOptions.SO_KEEPALIVE, DEFAULT_SO_KEEP_ALIVE);
    }

    @Override
    public boolean isReuseAddress() {
        return (Boolean) getOption(StandardSocketOptions.SO_REUSEADDR, DEFAULT_SO_REUSEADDR);
    }

    @Override
    public boolean isTcpNoDelay() {
        return (Boolean) getOption(StandardSocketOptions.TCP_NODELAY, DEFAULT_TCP_NODELAY);
    }

    @Override
    public AioSocketChannelConfig setKeepAlive(boolean keepAlive) {
        setOption(StandardSocketOptions.SO_KEEPALIVE, keepAlive);
        return this;
    }

    @Override
    public AioSocketChannelConfig setPerformancePreferences(
            int connectionTime, int latency, int bandwidth) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AioSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
        return this;
    }

    @Override
    public AioSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        setOption(StandardSocketOptions.SO_REUSEADDR, reuseAddress);
        return this;
    }

    @Override
    public AioSocketChannelConfig setSendBufferSize(int sendBufferSize) {
        setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
        return this;
    }

    @Override
    public AioSocketChannelConfig setSoLinger(int soLinger) {
        setOption(StandardSocketOptions.SO_LINGER, soLinger);
        return this;
    }

    @Override
    public AioSocketChannelConfig setTcpNoDelay(boolean tcpNoDelay) {
        setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
        return this;
    }

    @Override
    public AioSocketChannelConfig setTrafficClass(int trafficClass) {
        setOption(StandardSocketOptions.IP_TOS, trafficClass);
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

    @Override
    public AioSocketChannelConfig setReadTimeout(long readTimeoutInMillis) {
        if (readTimeoutInMillis < 0) {
            throw new IllegalArgumentException("readTimeoutInMillis: " + readTimeoutInMillis);
        }
        this.readTimeoutInMillis = readTimeoutInMillis;
        return this;
    }

    @Override
    public AioSocketChannelConfig setWriteTimeout(long writeTimeoutInMillis) {
        if (writeTimeoutInMillis < 0) {
            throw new IllegalArgumentException("writeTimeoutInMillis: " + writeTimeoutInMillis);
        }
        this.writeTimeoutInMillis = writeTimeoutInMillis;
        return this;
    }

    @Override
    public long getReadTimeout() {
        return readTimeoutInMillis;
    }

    @Override
    public long getWriteTimeout() {
        return writeTimeoutInMillis;
    }

    @Override
    public boolean isAllowHalfClosure() {
        return allowHalfClosure;
    }

    @Override
    public AioSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
        return this;
    }

    /**
     * Assing the given {@link NetworkChannel} to this instance
     */
    void assign(NetworkChannel javaChannel) {
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
    public AioSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        return (AioSocketChannelConfig) super.setConnectTimeoutMillis(connectTimeoutMillis);
    }

    @Override
    public AioSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        return (AioSocketChannelConfig) super.setWriteSpinCount(writeSpinCount);
    }

    @Override
    public AioSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        return (AioSocketChannelConfig) super.setAllocator(allocator);
    }

    @Override
    public AioSocketChannelConfig setAutoRead(boolean autoRead) {
        return (AioSocketChannelConfig) super.setAutoRead(autoRead);
    }

    @Override
    public AioSocketChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    public AioSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public AioSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public AioSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    @Override
    public AioSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public AioSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

}
