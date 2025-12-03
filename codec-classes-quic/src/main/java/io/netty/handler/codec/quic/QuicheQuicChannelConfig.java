/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Default {@link io.netty.channel.ChannelConfig} implementation.
 */
final class QuicheQuicChannelConfig extends DefaultChannelConfig {

    private volatile QLogConfiguration qLogConfiguration;
    private volatile SegmentedDatagramPacketAllocator segmentedDatagramPacketAllocator =
            SegmentedDatagramPacketAllocator.NONE;

    QuicheQuicChannelConfig(Channel channel) {
        super(channel);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(),
                QuicChannelOption.QLOG, QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == QuicChannelOption.QLOG) {
            return (T) getQLogConfiguration();
        }
        if (option == QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR) {
            return (T) getSegmentedDatagramPacketAllocator();
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == QuicChannelOption.QLOG) {
            setQLogConfiguration((QLogConfiguration) value);
            return true;
        }
        if (option == QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR) {
            setSegmentedDatagramPacketAllocator((SegmentedDatagramPacketAllocator) value);
            return true;
        }
        return super.setOption(option, value);
    }

    @Nullable
    QLogConfiguration getQLogConfiguration() {
        return qLogConfiguration;
    }

    private void setQLogConfiguration(QLogConfiguration qLogConfiguration) {
        if (channel.isRegistered()) {
            throw new IllegalStateException("QLOG can only be enabled before the Channel was registered");
        }
        this.qLogConfiguration = qLogConfiguration;
    }

    SegmentedDatagramPacketAllocator getSegmentedDatagramPacketAllocator() {
        return segmentedDatagramPacketAllocator;
    }

    private void setSegmentedDatagramPacketAllocator(
            SegmentedDatagramPacketAllocator segmentedDatagramPacketAllocator) {
        this.segmentedDatagramPacketAllocator = segmentedDatagramPacketAllocator;
    }
}
