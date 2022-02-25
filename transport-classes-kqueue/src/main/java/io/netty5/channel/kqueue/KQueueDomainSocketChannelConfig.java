/*
 * Copyright 2016 The Netty Project
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
package io.netty5.channel.kqueue;

import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.MessageSizeEstimator;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.WriteBufferWaterMark;
import io.netty5.channel.unix.DomainSocketChannelConfig;
import io.netty5.channel.unix.DomainSocketReadMode;
import io.netty5.util.internal.UnstableApi;

import java.io.IOException;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static io.netty5.channel.ChannelOption.SO_RCVBUF;
import static io.netty5.channel.ChannelOption.SO_SNDBUF;
import static io.netty5.channel.unix.UnixChannelOption.DOMAIN_SOCKET_READ_MODE;

@UnstableApi
public final class KQueueDomainSocketChannelConfig extends KQueueDuplexChannelConfig
        implements DomainSocketChannelConfig {
    private volatile DomainSocketReadMode mode = DomainSocketReadMode.BYTES;

    KQueueDomainSocketChannelConfig(AbstractKQueueChannel channel) {
        super(channel);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), DOMAIN_SOCKET_READ_MODE, SO_SNDBUF, SO_RCVBUF);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == DOMAIN_SOCKET_READ_MODE) {
            return (T) getReadMode();
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

        if (option == DOMAIN_SOCKET_READ_MODE) {
            setReadMode((DomainSocketReadMode) value);
        } else if (option == SO_SNDBUF) {
            setSendBufferSize((Integer) value);
        } else if (option == SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public KQueueDomainSocketChannelConfig setRcvAllocTransportProvidesGuess(boolean transportProvidesGuess) {
        super.setRcvAllocTransportProvidesGuess(transportProvidesGuess);
        return this;
    }

    @Override
    @Deprecated
    public KQueueDomainSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setRecvBufferAllocator(RecvBufferAllocator allocator) {
        super.setRecvBufferAllocator(allocator);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setBufferAllocator(BufferAllocator bufferAllocator) {
        super.setBufferAllocator(bufferAllocator);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    @Override
    @Deprecated
    public KQueueDomainSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    @Deprecated
    public KQueueDomainSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public KQueueDomainSocketChannelConfig setReadMode(DomainSocketReadMode mode) {
        requireNonNull(mode, "mode");
        this.mode = mode;
        return this;
    }

    @Override
    public DomainSocketReadMode getReadMode() {
        return mode;
    }

    @Override
    public int getSendBufferSize() {
        try {
            return ((KQueueDomainSocketChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public KQueueDomainSocketChannelConfig setSendBufferSize(int sendBufferSize) {
        try {
            ((KQueueDomainSocketChannel) channel).socket.setSendBufferSize(sendBufferSize);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return ((KQueueDomainSocketChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public KQueueDomainSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((KQueueDomainSocketChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public KQueueDomainSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        super.setAllowHalfClosure(allowHalfClosure);
        return this;
    }
}
