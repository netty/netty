/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

import java.io.IOException;
import java.util.Map;

public class EpollChannelConfig extends DefaultChannelConfig {
    final AbstractEpollChannel channel;

    EpollChannelConfig(AbstractEpollChannel channel) {
        super(channel);
        this.channel = channel;
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), EpollChannelOption.EPOLL_MODE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == EpollChannelOption.EPOLL_MODE) {
            return (T) getEpollMode();
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);
        if (option == EpollChannelOption.EPOLL_MODE) {
            setEpollMode((EpollMode) value);
        } else {
            return super.setOption(option, value);
        }
        return true;
    }

    @Override
    public EpollChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public EpollChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public EpollChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public EpollChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public EpollChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public EpollChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    @Deprecated
    public EpollChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    @Deprecated
    public EpollChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public EpollChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public EpollChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    /**
     * Return the {@link EpollMode} used. Default is
     * {@link EpollMode#EDGE_TRIGGERED}. If you want to use {@link #isAutoRead()} {@code false} or
     * {@link #getMaxMessagesPerRead()} and have an accurate behaviour you should use
     * {@link EpollMode#LEVEL_TRIGGERED}.
     */
    public EpollMode getEpollMode() {
        return channel.isFlagSet(Native.EPOLLET)
                ? EpollMode.EDGE_TRIGGERED : EpollMode.LEVEL_TRIGGERED;
    }

    /**
     * Set the {@link EpollMode} used. Default is
     * {@link EpollMode#EDGE_TRIGGERED}. If you want to use {@link #isAutoRead()} {@code false} or
     * {@link #getMaxMessagesPerRead()} and have an accurate behaviour you should use
     * {@link EpollMode#LEVEL_TRIGGERED}.
     *
     * <strong>Be aware this config setting can only be adjusted before the channel was registered.</strong>
     */
    public EpollChannelConfig setEpollMode(EpollMode mode) {
        if (mode == null) {
            throw new NullPointerException("mode");
        }
        try {
            switch (mode) {
            case EDGE_TRIGGERED:
                checkChannelNotRegistered();
                channel.setFlag(Native.EPOLLET);
                break;
            case LEVEL_TRIGGERED:
                checkChannelNotRegistered();
                channel.clearFlag(Native.EPOLLET);
                break;
            default:
                throw new Error();
            }
        } catch (IOException e) {
            throw new ChannelException(e);
        }
        return this;
    }

    private void checkChannelNotRegistered() {
        if (channel.isRegistered()) {
            throw new IllegalStateException("EpollMode can only be changed before channel is registered");
        }
    }

    @Override
    protected final void autoReadCleared() {
        channel.clearEpollIn();
    }
}
