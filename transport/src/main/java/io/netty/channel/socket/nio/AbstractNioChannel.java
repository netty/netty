/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractNioChannel extends AbstractChannel {

    /**
     * Indicates if there is a {@link WriteTask} in the task queue.
     */
    final AtomicBoolean writeTaskInTaskQueue = new AtomicBoolean();

    /**
     * Keeps track of the number of bytes that the {@link WriteRequestQueue} currently
     * contains.
     */
    final AtomicInteger writeBufferSize = new AtomicInteger();

    /**
     * Keeps track of the highWaterMark.
     */
    final AtomicInteger highWaterMarkCounter = new AtomicInteger();

    /**
     * Boolean that indicates that write operation is in progress.
     */
    protected boolean inWriteNowLoop;
    protected boolean writeSuspended;


    private volatile InetSocketAddress localAddress;
    volatile InetSocketAddress remoteAddress;

    private final SelectableChannel ch;

    protected AbstractNioChannel(Integer id, Channel parent, SelectableChannel ch) {
        super(id, parent);
        this.ch = ch;
    }

    protected AbstractNioChannel(Channel parent, SelectableChannel ch)  {
        super(parent);
        this.ch = ch;
    }

    @Override
    protected SelectableChannel javaChannel() {
        return ch;
    }

    @Override
    public InetSocketAddress localAddress() {
        InetSocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress =
                    (InetSocketAddress) unsafe().localAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        InetSocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress =
                    (InetSocketAddress) unsafe().remoteAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    @Override
    public abstract NioChannelConfig config();
}
