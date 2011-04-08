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

import java.net.SocketAddress;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ChildChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class TunnelWrappedServerChannelHandler extends SimpleChannelUpstreamHandler {

    public static final String NAME = "TunnelWrappedServerChannelHandler";

    private final HttpTunnelServerChannel tunnelChannel;

    private final AcceptedServerChannelPipelineFactory pipelineFactory;

    private final ChannelGroup allChannels;

    public TunnelWrappedServerChannelHandler(
            HttpTunnelServerChannel tunnelChannel,
            AcceptedServerChannelPipelineFactory pipelineFactory,
            ChannelGroup allChannels) {
        this.tunnelChannel = tunnelChannel;
        this.pipelineFactory = pipelineFactory;
        this.allChannels = allChannels;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        e.getChannel().getConfig().setPipelineFactory(pipelineFactory);
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        Channels.fireChannelBound(tunnelChannel, (SocketAddress) e.getValue());
        super.channelBound(ctx, e);
    }

    @Override
    public void channelUnbound(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        Channels.fireChannelUnbound(tunnelChannel);
        super.channelUnbound(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        Channels.fireChannelClosed(tunnelChannel);
        super.channelClosed(ctx, e);
    }

    @Override
    public void childChannelOpen(ChannelHandlerContext ctx,
            ChildChannelStateEvent e) throws Exception {
        allChannels.add(e.getChildChannel());
    }
}
