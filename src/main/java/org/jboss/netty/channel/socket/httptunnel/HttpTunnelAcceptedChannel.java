/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.httptunnel;

import static org.jboss.netty.channel.Channels.*;

import java.net.SocketAddress;

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.DefaultChannelConfig;

/**
 * Represents the server end of an HTTP tunnel, created after a legal tunnel creation
 * request is received from a client. The server end of a tunnel does not have any
 * directly related TCP connections - the connections used by a client are likely
 * to change over the lifecycle of a tunnel, especially when an HTTP proxy is in
 * use.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @version $Rev$, $Date$
 */
class HttpTunnelAcceptedChannel extends AbstractChannel {

    private final ChannelConfig config;
    private final HttpTunnelAcceptedChannelSink sink;
    private final SocketAddress remoteAddress;

    protected HttpTunnelAcceptedChannel(HttpTunnelServerChannel parent, ChannelFactory factory, ChannelPipeline pipeline, HttpTunnelAcceptedChannelSink sink, SocketAddress remoteAddress) {
        super(parent, factory, pipeline, sink);
        config = new DefaultChannelConfig();
        this.sink = sink;
        this.remoteAddress = remoteAddress;
        fireChannelOpen(this);
        fireChannelBound(this, getLocalAddress());
        fireChannelConnected(this, getRemoteAddress());
    }

    public ChannelConfig getConfig() {
        return config;
    }

    public SocketAddress getLocalAddress() {
        return getParent().getLocalAddress();
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isBound() {
        return sink.isActive();
    }

    public boolean isConnected() {
        return sink.isActive();
    }
}
