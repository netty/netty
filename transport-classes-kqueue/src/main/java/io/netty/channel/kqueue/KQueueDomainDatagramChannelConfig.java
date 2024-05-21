/*
 * Copyright 2021 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.unix.DomainDatagramChannelConfig;

import java.io.IOException;
import java.util.Map;

import static io.netty.channel.ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION;
import static io.netty.channel.ChannelOption.SO_SNDBUF;

public final class KQueueDomainDatagramChannelConfig
        extends KQueueChannelConfig implements DomainDatagramChannelConfig {

    private boolean activeOnOpen;

    KQueueDomainDatagramChannelConfig(KQueueDomainDatagramChannel channel) {
        super(channel, new FixedRecvByteBufAllocator(2048));
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(),
                DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, SO_SNDBUF);
    }

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    public <T> T getOption(ChannelOption<T> option) {
        if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            return (T) Boolean.valueOf(activeOnOpen);
        }
        if (option == SO_SNDBUF) {
            return (T) Integer.valueOf(getSendBufferSize());
        }
        return super.getOption(option);
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            setActiveOnOpen((Boolean) value);
        } else if (option == SO_SNDBUF) {
            setSendBufferSize((Integer) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    private void setActiveOnOpen(boolean activeOnOpen) {
        if (channel.isRegistered()) {
            throw new IllegalStateException("Can only changed before channel was registered");
        }
        this.activeOnOpen = activeOnOpen;
    }

    boolean getActiveOnOpen() {
        return activeOnOpen;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public KQueueDomainDatagramChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setMaxMessagesPerWrite(int maxMessagesPerWrite) {
        super.setMaxMessagesPerWrite(maxMessagesPerWrite);
        return this;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setRcvAllocTransportProvidesGuess(boolean transportProvidesGuess) {
        super.setRcvAllocTransportProvidesGuess(transportProvidesGuess);
        return this;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setSendBufferSize(int sendBufferSize) {
        try {
            ((KQueueDomainDatagramChannel) channel).socket.setSendBufferSize(sendBufferSize);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSendBufferSize() {
        try {
            return ((KQueueDomainDatagramChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public KQueueDomainDatagramChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public KQueueDomainDatagramChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }
}
