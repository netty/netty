/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.socket.oio;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.DefaultServerSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannel;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import static io.netty.channel.ChannelOption.*;

/**
 * Default {@link OioServerSocketChannelConfig} implementation
 */
public class DefaultOioServerSocketChannelConfig extends DefaultServerSocketChannelConfig implements
        OioServerSocketChannelConfig {

    @Deprecated
    public DefaultOioServerSocketChannelConfig(ServerSocketChannel channel, ServerSocket javaSocket) {
        super(channel, javaSocket);
    }

    DefaultOioServerSocketChannelConfig(OioServerSocketChannel channel, ServerSocket javaSocket) {
        super(channel, javaSocket);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(), SO_TIMEOUT);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == SO_TIMEOUT) {
            return (T) Integer.valueOf(getSoTimeout());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == SO_TIMEOUT) {
            setSoTimeout((Integer) value);
        } else {
            return super.setOption(option, value);
        }
        return true;
    }

    @Override
    public OioServerSocketChannelConfig setSoTimeout(int timeout) {
        try {
            javaSocket.setSoTimeout(timeout);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
        return this;
    }

    @Override
    public int getSoTimeout() {
        try {
            return javaSocket.getSoTimeout();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public OioServerSocketChannelConfig setBacklog(int backlog) {
        super.setBacklog(backlog);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        super.setReuseAddress(reuseAddress);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        super.setReceiveBufferSize(receiveBufferSize);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        super.setPerformancePreferences(connectionTime, latency, bandwidth);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    protected void autoReadCleared() {
        if (channel instanceof OioServerSocketChannel) {
            ((OioServerSocketChannel) channel).setReadPending(false);
        }
    }

    @Override
    public OioServerSocketChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public OioServerSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }
}
