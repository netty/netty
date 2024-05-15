/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel.nio;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.ReadHandleFactory;
import io.netty5.channel.WriteHandleFactory;

import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on messages.
 */
public abstract class AbstractNioMessageChannel<P extends Channel, L extends SocketAddress, R extends SocketAddress>
        extends AbstractNioChannel<P, L, R> {

    /**
     * @see AbstractNioChannel#AbstractNioChannel(Channel, EventLoop,
     * boolean, ReadHandleFactory, WriteHandleFactory ,SelectableChannel, NioIoOps)
     */
    protected AbstractNioMessageChannel(P parent, EventLoop eventLoop, boolean supportingDisconnect,
                                        ReadHandleFactory defaultReadHandleFactory,
                                        WriteHandleFactory defaultWriteHandleFactory,
                                        SelectableChannel ch, NioIoOps readInterestOp) {
        super(parent, eventLoop, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory,
                ch, readInterestOp);
    }

    @Override
    protected final boolean doReadNow(ReadSink readSink) throws Exception {
        int localRead = doReadMessages(readSink);
        return localRead < 0;
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(ReadSink readSink) throws Exception;

}
