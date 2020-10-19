/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.udt;

import com.barchart.udt.nio.ChannelUDT;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

import java.io.IOException;
import java.util.Map;

import static io.netty.channel.ChannelOption.SO_BACKLOG;

/**
 * The default {@link UdtServerChannelConfig} implementation.
 *
 * @deprecated The UDT transport is no longer maintained and will be removed.
 */
@Deprecated
public class DefaultUdtServerChannelConfig extends DefaultUdtChannelConfig
        implements UdtServerChannelConfig {

    private volatile int backlog = 64;

    public DefaultUdtServerChannelConfig(
            final UdtChannel channel, final ChannelUDT channelUDT, final boolean apply) throws IOException {
        super(channel, channelUDT, apply);
        if (apply) {
            apply(channelUDT);
        }
    }

    @Override
    protected void apply(final ChannelUDT channelUDT) throws IOException {
        // nothing to apply for now.
    }

    @Override
    public int getBacklog() {
        return backlog;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(final ChannelOption<T> option) {
        if (option == SO_BACKLOG) {
            return (T) Integer.valueOf(getBacklog());
        }
        return super.getOption(option);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), SO_BACKLOG);
    }

    @Override
    public UdtServerChannelConfig setBacklog(final int backlog) {
        this.backlog = backlog;
        return this;
    }

    @Override
    public <T> boolean setOption(final ChannelOption<T> option, final T value) {
        validate(option, value);
        if (option == SO_BACKLOG) {
            setBacklog((Integer) value);
        } else {
            return super.setOption(option, value);
        }
        return true;
    }

    @Override
    public UdtServerChannelConfig setProtocolReceiveBufferSize(
            final int protocolReceiveBufferSize) {
        super.setProtocolReceiveBufferSize(protocolReceiveBufferSize);
        return this;
    }

    @Override
    public UdtServerChannelConfig setProtocolSendBufferSize(
            final int protocolSendBufferSize) {
        super.setProtocolSendBufferSize(protocolSendBufferSize);
        return this;
    }

    @Override
    public UdtServerChannelConfig setReceiveBufferSize(
            final int receiveBufferSize) {
        super.setReceiveBufferSize(receiveBufferSize);
        return this;
    }

    @Override
    public UdtServerChannelConfig setReuseAddress(final boolean reuseAddress) {
        super.setReuseAddress(reuseAddress);
        return this;
    }

    @Override
    public UdtServerChannelConfig setSendBufferSize(final int sendBufferSize) {
        super.setSendBufferSize(sendBufferSize);
        return this;
    }

    @Override
    public UdtServerChannelConfig setSoLinger(final int soLinger) {
        super.setSoLinger(soLinger);
        return this;
    }

    @Override
    public UdtServerChannelConfig setSystemReceiveBufferSize(
            final int systemSendBufferSize) {
        super.setSystemReceiveBufferSize(systemSendBufferSize);
        return this;
    }

    @Override
    public UdtServerChannelConfig setSystemSendBufferSize(
            final int systemReceiveBufferSize) {
        super.setSystemSendBufferSize(systemReceiveBufferSize);
        return this;
    }

    @Override
    public UdtServerChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public UdtServerChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public UdtServerChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public UdtServerChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public UdtServerChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public UdtServerChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public UdtServerChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    public UdtServerChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public UdtServerChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public UdtServerChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public UdtServerChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }
}
