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
package io.netty.bootstrap;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;

import java.net.SocketAddress;

class TestChannel extends AbstractChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final ChannelConfig config;
    private volatile boolean closed;

    TestChannel() {
        this(null);
    }

    TestChannel(Channel parent) {
        super(parent);
        config = new TestConfig(this);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new AbstractUnsafe() {
            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                promise.setSuccess();
            }
        };
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return true;
    }

    @Override
    protected SocketAddress localAddress0() {
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) {
        // NOOP
    }

    @Override
    protected void doDisconnect() {
        closed = true;
    }

    @Override
    protected void doClose() {
        closed = true;
    }

    @Override
    protected void doBeginRead() {
        // NOOP
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) {
        // NOOP
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public boolean isActive() {
        return !closed;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    private static final class TestConfig extends DefaultChannelConfig {
        TestConfig(Channel channel) {
            super(channel);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            throw new UnsupportedOperationException("Unsupported channel option: " + option);
        }
    }
}
