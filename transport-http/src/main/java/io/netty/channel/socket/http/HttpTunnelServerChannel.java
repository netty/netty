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

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineException;
import io.netty.channel.Channels;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;

/**
 */
final class HttpTunnelServerChannel extends AbstractServerChannel implements
        ServerSocketChannel {

    private final ServerSocketChannel realChannel;

    final HttpTunnelServerChannelConfig config;

    final ServerMessageSwitch messageSwitch;

    private final ChannelFutureListener CLOSE_FUTURE_PROXY =
            new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future)
                        throws Exception {
                    HttpTunnelServerChannel.this.setClosed();
                }
            };

    protected static HttpTunnelServerChannel create(
            HttpTunnelServerChannelFactory factory, ChannelPipeline pipeline) {
        HttpTunnelServerChannel instance = new HttpTunnelServerChannel(factory, pipeline);
        Channels.fireChannelOpen(instance);
        return instance;
    }

    private HttpTunnelServerChannel(HttpTunnelServerChannelFactory factory,
            ChannelPipeline pipeline) {
        super(factory, pipeline, new HttpTunnelServerChannelSink());

        messageSwitch = new ServerMessageSwitch(new TunnelCreator());
        realChannel = factory.createRealChannel(this, messageSwitch);
        // TODO fix calling of overrideable getPipeline() from constructor
        HttpTunnelServerChannelSink sink =
                (HttpTunnelServerChannelSink) getPipeline().getSink();
        sink.setRealChannel(realChannel);
        sink.setCloseListener(CLOSE_FUTURE_PROXY);
        config = new HttpTunnelServerChannelConfig(realChannel);
    }

    @Override
    public ServerSocketChannelConfig getConfig() {
        return config;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return realChannel.getLocalAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        // server channels never have a remote address
        return null;
    }

    @Override
    public boolean isBound() {
        return realChannel.isBound();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    /**
     * Used to hide the newChannel method from the public API.
     */
    private final class TunnelCreator implements
            HttpTunnelAcceptedChannelFactory {

        TunnelCreator() {
        }

        @Override
        public HttpTunnelAcceptedChannelReceiver newChannel(
                String newTunnelId, InetSocketAddress remoteAddress) {
            ChannelPipeline childPipeline = null;
            try {
                childPipeline = getConfig().getPipelineFactory().getPipeline();
            } catch (Exception e) {
                throw new ChannelPipelineException(
                        "Failed to initialize a pipeline.", e);
            }
            HttpTunnelAcceptedChannelConfig config =
                    new HttpTunnelAcceptedChannelConfig();
            HttpTunnelAcceptedChannelSink sink =
                    new HttpTunnelAcceptedChannelSink(messageSwitch,
                            newTunnelId, config);
            return HttpTunnelAcceptedChannel.create(HttpTunnelServerChannel.this, getFactory(), childPipeline, sink,
                    remoteAddress, config);
        }

        @Override
        public String generateTunnelId() {
            return config.getTunnelIdGenerator().generateId();
        }
    }
}
