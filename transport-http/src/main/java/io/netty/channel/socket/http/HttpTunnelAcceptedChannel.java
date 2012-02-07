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

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channels;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;

/**
 * Represents the server end of an HTTP tunnel, created after a legal tunnel creation
 * request is received from a client. The server end of a tunnel does not have any
 * directly related TCP connections - the connections used by a client are likely
 * to change over the lifecycle of a tunnel, especially when an HTTP proxy is in
 * use.
 */
final class HttpTunnelAcceptedChannel extends AbstractChannel implements
        SocketChannel, HttpTunnelAcceptedChannelReceiver {

    private final HttpTunnelAcceptedChannelConfig config;

    private final HttpTunnelAcceptedChannelSink sink;

    private final InetSocketAddress remoteAddress;

    protected static HttpTunnelAcceptedChannel create(
            HttpTunnelServerChannel parent, ChannelFactory factory,
            ChannelPipeline pipeline, HttpTunnelAcceptedChannelSink sink,
            InetSocketAddress remoteAddress,
            HttpTunnelAcceptedChannelConfig config) {
        HttpTunnelAcceptedChannel instance = new HttpTunnelAcceptedChannel(parent, factory, pipeline, sink,
                        remoteAddress, config);
        fireChannelOpen(instance);
        fireChannelBound(instance, instance.getLocalAddress());
        fireChannelConnected(instance, instance.getRemoteAddress());
        return instance;
    }

    private HttpTunnelAcceptedChannel(HttpTunnelServerChannel parent,
            ChannelFactory factory, ChannelPipeline pipeline,
            HttpTunnelAcceptedChannelSink sink,
            InetSocketAddress remoteAddress,
            HttpTunnelAcceptedChannelConfig config) {
        super(parent, factory, pipeline, sink);
        this.config = config;
        this.sink = sink;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public SocketChannelConfig getConfig() {
        return config;
    }

    @Override
    public InetSocketAddress getLocalAddress() {

        return ((HttpTunnelServerChannel) getParent()).getLocalAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public boolean isBound() {
        return sink.isActive();
    }

    @Override
    public boolean isConnected() {
        return sink.isActive();
    }

    @Override
    public void clientClosed() {
        this.setClosed();
        Channels.fireChannelClosed(this);
    }

    @Override
    public void dataReceived(ChannelBuffer data) {
        Channels.fireMessageReceived(this, data);
    }

    @Override
    public void updateInterestOps(SaturationStateChange transition) {
        switch (transition) {
        case SATURATED:
            fireWriteEnabled(false);
            break;
        case DESATURATED:
            fireWriteEnabled(true);
            break;
        case NO_CHANGE:
            break;
        }
    }

    private void fireWriteEnabled(boolean enabled) {
        int ops = OP_READ;
        if (!enabled) {
            ops |= OP_WRITE;
        }

        setInterestOpsNow(ops);
        Channels.fireChannelInterestChanged(this);
    }
}
