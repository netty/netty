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
package org.jboss.netty.channel.socket.http;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

/**
 * Factory used to create new client channels.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
public class HttpTunnelClientChannelFactory implements
        ClientSocketChannelFactory {

    private final ClientSocketChannelFactory factory;

    private final ChannelGroup realConnections = new DefaultChannelGroup();

    public HttpTunnelClientChannelFactory(ClientSocketChannelFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        this.factory = factory;
    }

    @Override
    public HttpTunnelClientChannel newChannel(ChannelPipeline pipeline) {
        return new HttpTunnelClientChannel(this, pipeline,
                new HttpTunnelClientChannelSink(), factory, realConnections);
    }

    @Override
    public void releaseExternalResources() {
        realConnections.close().awaitUninterruptibly();
        factory.releaseExternalResources();
    }

}
