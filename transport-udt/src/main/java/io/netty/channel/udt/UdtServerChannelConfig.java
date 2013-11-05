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
package io.netty.channel.udt;

import com.barchart.udt.TypeUDT;
import com.barchart.udt.nio.KindUDT;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;

/**
 * A {@link ChannelConfig} for a {@link UdtServerChannel}.
 * <p>
 * Note that {@link TypeUDT#DATAGRAM} message oriented channels treat
 * {@code "receiveBufferSize"} and {@code "sendBufferSize"} as maximum message
 * size. If received or sent message does not fit specified sizes,
 * {@link ChannelException} will be thrown.
 */
public interface UdtServerChannelConfig extends UdtChannelConfig {

    /**
     * Gets {@link KindUDT#ACCEPTOR} channel backlog via
     * {@link ChannelOption#SO_BACKLOG}.
     */
    int getBacklog();

    /**
     * Sets {@link KindUDT#ACCEPTOR} channel backlog via
     * {@link ChannelOption#SO_BACKLOG}.
     */
    UdtServerChannelConfig setBacklog(int backlog);

    @Override
    UdtServerChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    UdtServerChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    UdtServerChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    UdtServerChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    UdtServerChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    UdtServerChannelConfig setAutoRead(boolean autoRead);

    @Override
    UdtServerChannelConfig setAutoClose(boolean autoClose);

    @Override
    UdtServerChannelConfig setProtocolReceiveBufferSize(int size);

    @Override
    UdtServerChannelConfig setProtocolSendBufferSize(int size);

    @Override
    UdtServerChannelConfig setReceiveBufferSize(int receiveBufferSize);

    @Override
    UdtServerChannelConfig setReuseAddress(boolean reuseAddress);

    @Override
    UdtServerChannelConfig setSendBufferSize(int sendBufferSize);

    @Override
    UdtServerChannelConfig setSoLinger(int soLinger);

    @Override
    UdtServerChannelConfig setSystemReceiveBufferSize(int size);

    @Override
    UdtServerChannelConfig setSystemSendBufferSize(int size);

    @Override
    UdtServerChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    @Override
    UdtServerChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    @Override
    UdtServerChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);
}
