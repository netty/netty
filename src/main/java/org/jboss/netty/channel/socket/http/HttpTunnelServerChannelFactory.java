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
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
public class HttpTunnelServerChannelFactory implements
        ServerSocketChannelFactory {

    private final ServerSocketChannelFactory realConnectionFactory;

    private final ChannelGroup realConnections;

    public HttpTunnelServerChannelFactory(
            ServerSocketChannelFactory realConnectionFactory) {
        this.realConnectionFactory = realConnectionFactory;
        realConnections = new DefaultChannelGroup();
    }

    @Override
    public HttpTunnelServerChannel newChannel(ChannelPipeline pipeline) {
        return new HttpTunnelServerChannel(this, pipeline);
    }

    ServerSocketChannel createRealChannel(HttpTunnelServerChannel channel,
            ServerMessageSwitch messageSwitch) {
        ChannelPipeline realChannelPipeline = Channels.pipeline();
        AcceptedServerChannelPipelineFactory realPipelineFactory =
                new AcceptedServerChannelPipelineFactory(messageSwitch);
        realChannelPipeline.addFirst(TunnelWrappedServerChannelHandler.NAME,
                new TunnelWrappedServerChannelHandler(channel,
                        realPipelineFactory, realConnections));
        ServerSocketChannel newChannel =
                realConnectionFactory.newChannel(realChannelPipeline);
        realConnections.add(newChannel);
        return newChannel;
    }

    @Override
    public void releaseExternalResources() {
        realConnections.close().awaitUninterruptibly();
        realConnectionFactory.releaseExternalResources();
    }

}
