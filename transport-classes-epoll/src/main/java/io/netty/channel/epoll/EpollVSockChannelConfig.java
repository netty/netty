/*
 * Copyright 2025 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.DuplexChannelConfig;
import io.netty.channel.unix.VSockChannelConfig;

import java.io.IOException;
import java.util.Map;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_SNDBUF;

public class EpollVSockChannelConfig extends EpollChannelConfig implements VSockChannelConfig, DuplexChannelConfig {
    private volatile boolean allowHalfClosure;

    EpollVSockChannelConfig(Channel channel) {
        super(channel);
    }

    public Map<ChannelOption<?>, Object> getOptions() {
        return this.getOptions(super.getOptions(), new ChannelOption[]{
                ChannelOption.ALLOW_HALF_CLOSURE,
                ChannelOption.SO_SNDBUF,
                ChannelOption.SO_RCVBUF
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == ALLOW_HALF_CLOSURE) {
            return (T) Boolean.valueOf(isAllowHalfClosure());
        }
        if (option == SO_SNDBUF) {
            return (T) Integer.valueOf(getSendBufferSize());
        }
        if (option == SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == ALLOW_HALF_CLOSURE) {
            setAllowHalfClosure((Boolean) value);
        } else if (option == SO_SNDBUF) {
            setSendBufferSize((Integer) value);
        } else if (option == SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public EpollVSockChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    public EpollVSockChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    public EpollVSockChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    public EpollVSockChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    public EpollVSockChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    public EpollVSockChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    public EpollVSockChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public EpollVSockChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public EpollVSockChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    public EpollVSockChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    public EpollVSockChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    public boolean isAllowHalfClosure() {
        return this.allowHalfClosure;
    }

    public EpollVSockChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
        return this;
    }

    public int getSendBufferSize() {
        try {
            return ((EpollVSockChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public EpollVSockChannelConfig setSendBufferSize(int sendBufferSize) {
        try {
            ((EpollVSockChannel) channel).socket.setSendBufferSize(sendBufferSize);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getReceiveBufferSize() {
        try {
            return ((EpollVSockChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public EpollVSockChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((EpollVSockChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
