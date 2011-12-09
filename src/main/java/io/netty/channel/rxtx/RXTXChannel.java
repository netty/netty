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
package io.netty.channel.rxtx;


import java.net.SocketAddress;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;

/**
 * A channel to a serial device using the RXTX library.
 */
public class RXTXChannel extends AbstractChannel {

    RXTXChannel(final Channel parent, final ChannelFactory factory, final ChannelPipeline pipeline,
                       final ChannelSink sink) {
        super(parent, factory, pipeline, sink);
    }

    @Override
    public ChannelConfig getConfig() {
        return ((RXTXChannelSink) getPipeline().getSink()).getConfig();
    }

    @Override
    public boolean isBound() {
        return ((RXTXChannelSink) getPipeline().getSink()).isBound();
    }

    @Override
    public boolean isConnected() {
        return ((RXTXChannelSink) getPipeline().getSink()).isConnected();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ((RXTXChannelSink) getPipeline().getSink()).getRemoteAddress();
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture unbind() {
        throw new UnsupportedOperationException();
    }

    void doSetClosed() {
        setClosed();
    }
}
