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

import java.net.InetSocketAddress;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.Channels;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;

/**
 * A fake socket channel for use in testing
 */
public class FakeSocketChannel extends AbstractChannel implements SocketChannel {

    public InetSocketAddress localAddress;

    public InetSocketAddress remoteAddress;

    public SocketChannelConfig config = new FakeChannelConfig();

    public boolean bound = false;

    public boolean connected = false;

    public ChannelSink sink;

    public FakeSocketChannel(Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink) {
        super(parent, factory, pipeline, sink);
        this.sink = sink;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public SocketChannelConfig getConfig() {
        return config;
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

    public void emulateConnected(InetSocketAddress localAddress,
            InetSocketAddress remoteAddress, ChannelFuture connectedFuture) {
        if (connected) {
            return;
        }

        emulateBound(localAddress, null);
        this.remoteAddress = remoteAddress;
        connected = true;
        Channels.fireChannelConnected(this, remoteAddress);
        if (connectedFuture != null) {
            connectedFuture.setSuccess();
        }
    }

    public void emulateBound(InetSocketAddress localAddress,
            ChannelFuture boundFuture) {
        if (bound) {
            return;
        }

        bound = true;
        this.localAddress = localAddress;
        Channels.fireChannelBound(this, localAddress);
        if (boundFuture != null) {
            boundFuture.setSuccess();
        }
    }

}
