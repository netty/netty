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

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.jboss.netty.channel.AbstractServerChannel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelConfig;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @version $Rev$, $Date$
 */
public class HttpTunnelServerChannel extends AbstractServerChannel implements ServerSocketChannel {

    private final ServerSocketChannel realChannel;
    private final HttpTunnelServerChannelConfig config;
    private final ServerMessageSwitch messageSwitch;

    protected HttpTunnelServerChannel(HttpTunnelServerChannelFactory factory, ChannelPipeline pipeline) {
        super(factory, pipeline, new HttpTunnelServerChannelSink());

        messageSwitch = new ServerMessageSwitch(new TunnelCreator());
        realChannel = factory.createRealChannel(this, messageSwitch);
        HttpTunnelServerChannelSink sink = (HttpTunnelServerChannelSink) getPipeline().getSink();
        sink.setRealChannel(realChannel);
        config = new HttpTunnelServerChannelConfig(realChannel);
        Channels.fireChannelOpen(this);
    }

    public ServerSocketChannelConfig getConfig() {
        return config;
    }

    public InetSocketAddress getLocalAddress() {
        return realChannel.getLocalAddress();
    }

    public InetSocketAddress getRemoteAddress() {
        // server channels never have a remote address
        return null;
    }

    public boolean isBound() {
        return realChannel.isBound();
    }

    /**
     * Used to hide the newChannel method from the public API.
     */
    private final class TunnelCreator implements HttpTunnelAcceptedChannelFactory {
    
        public HttpTunnelAcceptedChannel newChannel(String newTunnelId, SocketAddress remoteAddress) {
            ChannelPipeline childPipeline = null;
            try {
                childPipeline = getConfig().getPipelineFactory().getPipeline();
            } catch(Exception e) {
                throw new ChannelPipelineException("Failed to initialize a pipeline.", e);
            }
            HttpTunnelAcceptedChannelSink sink = new HttpTunnelAcceptedChannelSink(messageSwitch, newTunnelId);
            return new HttpTunnelAcceptedChannel(HttpTunnelServerChannel.this, getFactory(), childPipeline, sink, remoteAddress);
        }
        
        public String generateTunnelId() {
            return config.getTunnelIdGenerator().generateId();
        }
    }
}
