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
package io.netty.channel.pool;

import io.netty.channel.EventLoop;

import java.net.SocketAddress;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Default implementation of a {@link ChannelPoolKey}.
 */
public final class DefaultChannelPoolKey implements ChannelPoolKey {

    private final SocketAddress remoteAddress;
    private final EventLoop loop;
    private final int hash;

    /**
     * @see {@link #DefaultChannelPoolKey(SocketAddress, EventLoop)}
     */
    public DefaultChannelPoolKey(SocketAddress remoteAddress) {
        this(remoteAddress, null);
    }

    /**
     * Create a new instance.
     *
     * @param remoteAddress     The {@link SocketAddress} of the remote peer.
     * @param loop              The {@link EventLoop} that should be used or {@code null} if not specified.
     */
    public DefaultChannelPoolKey(SocketAddress remoteAddress, EventLoop loop) {
        this.remoteAddress = checkNotNull(remoteAddress, "address");
        this.loop = loop;
        hash = (loop == null) ? remoteAddress.hashCode() : (37 * remoteAddress.hashCode() + loop.hashCode());
    }

    @Override
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public EventLoop eventLoop() {
        return loop;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ChannelPoolKey)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        ChannelPoolKey key = (ChannelPoolKey) obj;
        if (!remoteAddress.equals(key.remoteAddress()) ||
            (loop != null && (!loop.equals(key.eventLoop()))) &&
            key.eventLoop() != null || key.eventLoop() == null && loop != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
