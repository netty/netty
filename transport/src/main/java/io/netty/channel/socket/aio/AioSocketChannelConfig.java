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
package io.netty.channel.socket.aio;

import static io.netty.channel.ChannelOption.*;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.socket.SocketChannelConfig;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.NetworkChannel;
import java.util.Map;

/**
 * The default {@link SocketChannelConfig} implementation.
 */
final class AioSocketChannelConfig extends DefaultChannelConfig
                                        implements SocketChannelConfig {

    private final NetworkChannel channel;
    private volatile boolean allowHalfClosure;
    private volatile long readTimeoutInMillis;
    private volatile long writeTimeoutInMillis;

    /**
     * Creates a new instance.
     */
    AioSocketChannelConfig(NetworkChannel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }

        this.channel = channel;
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS,
                AIO_READ_TIMEOUT, AIO_WRITE_TIMEOUT, ALLOW_HALF_CLOSURE);
    }

    @Override
    @SuppressWarnings("unchecked")
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
        try {
            return channel.getOption(StandardSocketOptions.SO_RCVBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSendBufferSize() {
        try {
            return channel.getOption(StandardSocketOptions.SO_SNDBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSoLinger() {
        try {
            return channel.getOption(StandardSocketOptions.SO_LINGER);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getTrafficClass() {
        try {
            return channel.getOption(StandardSocketOptions.IP_TOS);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isKeepAlive() {
        try {
            return channel.getOption(StandardSocketOptions.SO_KEEPALIVE);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isReuseAddress() {
        try {
            return channel.getOption(StandardSocketOptions.SO_REUSEADDR);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isTcpNoDelay() {
        try {
            return channel.getOption(StandardSocketOptions.SO_REUSEADDR);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setKeepAlive(boolean keepAlive) {
        try {
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, keepAlive);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setPerformancePreferences(
            int connectionTime, int latency, int bandwidth) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        try {
            channel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setReuseAddress(boolean reuseAddress) {
        try {
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        try {
            channel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSoLinger(int soLinger) {
        try {
            channel.setOption(StandardSocketOptions.SO_LINGER, soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        try {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setTrafficClass(int trafficClass) {
        try {
            channel.setOption(StandardSocketOptions.IP_TOS, trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Return the read timeout in milliseconds after which a {@link InterruptedByTimeoutException} will get thrown.
     * Once such an exception was detected it will get propagated to the handlers first. After that the channel
     * will get closed as it may be in an unknown state.
     *
     * To disable it just use <code>0</code>.
     */
    public void setReadTimeout(long readTimeoutInMillis) {
        if (readTimeoutInMillis < 0) {
            throw new IllegalArgumentException("readTimeoutInMillis: " + readTimeoutInMillis);
        }
        this.readTimeoutInMillis = readTimeoutInMillis;
    }

    /**
     * Return the write timeout in milliseconds after which a {@link InterruptedByTimeoutException} will get thrown.
     * Once such an exception was detected it will get propagated to the handlers first. After that the channel
     * will get closed as it may be in an unknown state.
     *
     * To disable it just use <code>0</code>.
     */
    public void setWriteTimeout(long writeTimeoutInMillis) {
        if (writeTimeoutInMillis < 0) {
            throw new IllegalArgumentException("writeTimeoutInMillis: " + writeTimeoutInMillis);
        }
        this.writeTimeoutInMillis = writeTimeoutInMillis;
    }

    /**
     * Return the read timeout in milliseconds after which a {@link InterruptedByTimeoutException} will get thrown.
     *
     * The default is <code>0</code>
     */
    public long getReadTimeout() {
        return readTimeoutInMillis;
    }

    /**
     * Return the write timeout in milliseconds after which a {@link InterruptedByTimeoutException} will get thrown.
     *
     * The default is <code>0</code>
     */
    public long getWriteTimeout() {
        return writeTimeoutInMillis;
    }

    @Override
    public boolean isAllowHalfClosure() {
        return allowHalfClosure;
    }

    @Override
    public void setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
    }
}
