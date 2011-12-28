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

package io.netty.channel.socket.http;

import static io.netty.channel.Channels.fireChannelBound;
import static io.netty.channel.Channels.fireChannelConnected;
import static io.netty.channel.Channels.fireChannelOpen;

import java.net.InetSocketAddress;

import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;

/**
 * A fake server socket channel for use in testing
 */
public class FakeServerSocketChannel extends AbstractChannel implements
        ServerSocketChannel {

    public boolean bound;

    public boolean connected;

    public InetSocketAddress remoteAddress;

    public InetSocketAddress localAddress;

    public ServerSocketChannelConfig config =
            new FakeServerSocketChannelConfig();

    public FakeServerSocketChannel(ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink) {
        super(null, factory, pipeline, sink);
    }

    @Override
    public ServerSocketChannelConfig getConfig() {
        return config;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public boolean isBound() {
        return bound;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    public FakeSocketChannel acceptNewConnection(
            InetSocketAddress remoteAddress, ChannelSink sink) throws Exception {
        ChannelPipeline newPipeline =
                getConfig().getPipelineFactory().getPipeline();
        FakeSocketChannel newChannel =
                new FakeSocketChannel(this, getFactory(), newPipeline, sink);
        newChannel.localAddress = localAddress;
        newChannel.remoteAddress = remoteAddress;
        fireChannelOpen(newChannel);
        fireChannelBound(newChannel, newChannel.localAddress);
        fireChannelConnected(this, newChannel.remoteAddress);

        return newChannel;
    }

}
