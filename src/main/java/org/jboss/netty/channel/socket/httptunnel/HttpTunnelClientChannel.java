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
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;

/**
 * The client end of an HTTP tunnel, created by an {@link HttpTunnelClientChannelFactory}. Channels of
 * this type are designed to emulate a normal TCP based socket channel as far as is feasible within the limitations
 * of the HTTP 1.1 protocol, and the usage patterns permitted by commonly used HTTP proxies and firewalls.
 * 
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @version $Rev$, $Date$
 */
public class HttpTunnelClientChannel extends AbstractChannel implements SocketChannel, HttpTunnelClientWorkerOwner {
    
    private HttpTunnelClientChannelConfig config;

    private SocketChannel sendChannel;
    private SocketChannel pollChannel;
    
    private String tunnelId;
    private ChannelFuture connectFuture;
    private boolean connected;
    private boolean bound;
    
    private InetSocketAddress serverAddress;
    private String serverHostName;


    protected HttpTunnelClientChannel(ChannelFactory factory, ChannelPipeline pipeline, HttpTunnelClientChannelSink sink, ClientSocketChannelFactory outboundFactory, ChannelGroup realConnections) {
        super(null, factory, pipeline, sink);
        
        this.sendChannel = outboundFactory.newChannel(createSendPipeline());
        this.pollChannel = outboundFactory.newChannel(createPollPipeline());
        this.config = new HttpTunnelClientChannelConfig(sendChannel.getConfig(), pollChannel.getConfig());
        this.serverAddress = null;
        
        realConnections.add(sendChannel);
        realConnections.add(pollChannel);
        
        Channels.fireChannelOpen(this);
    }

    public HttpTunnelClientChannelConfig getConfig() {
        return config;
    }

    public boolean isBound() {
        return bound;
    }

    public boolean isConnected() {
        return connected;
    }

    public InetSocketAddress getLocalAddress() {
        return sendChannel.getLocalAddress();
    }

    public InetSocketAddress getRemoteAddress() {
        return serverAddress;
    }
    
    public void onMessageReceived(ChannelBuffer content) {
        Channels.fireMessageReceived(this, content);
    }

    public void onConnectRequest(ChannelFuture connectFuture, InetSocketAddress remoteAddress) {
        this.connectFuture = connectFuture;
        /* if we are using a proxy, the remoteAddress is swapped here for the address of the proxy.
         * The send and poll channels can later ask for the correct server address using
         * getServerHostName().
         */
        this.serverAddress = remoteAddress;
        
        SocketAddress connectTarget;
        if(config.getProxyAddress() != null) {
            connectTarget = config.getProxyAddress();
        } else {
            connectTarget = remoteAddress;
        }
        
        Channels.connect(sendChannel, connectTarget);
    }
    
    public void onDisconnectRequest(final ChannelFuture disconnectFuture) {
        ChannelFutureListener disconnectListener = new ConsolidatingFutureListener(disconnectFuture, 2);
        sendChannel.disconnect().addListener(disconnectListener);
        pollChannel.disconnect().addListener(disconnectListener);

        disconnectFuture.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                serverAddress = null;
            }
        });
    }
    
    public void onBindRequest(SocketAddress localAddress, final ChannelFuture bindFuture) {
        ChannelFutureListener bindListener = new ConsolidatingFutureListener(bindFuture, 2);
        sendChannel.bind(localAddress).addListener(bindListener);
        pollChannel.bind(localAddress).addListener(bindListener);
    }
    
    public void onUnbindRequest(final ChannelFuture unbindFuture) {
        ChannelFutureListener unbindListener = new ConsolidatingFutureListener(unbindFuture, 2);
        sendChannel.unbind().addListener(unbindListener);
        pollChannel.unbind().addListener(unbindListener);
    }

    public void onCloseRequest(final ChannelFuture closeFuture) {
        ChannelFutureListener closeListener = new ConsolidatingFutureListener(closeFuture, 2);
        sendChannel.close().addListener(closeListener);
        pollChannel.close().addListener(closeListener);
    }
    
    public void onTunnelOpened(String tunnelId) {
        this.tunnelId = tunnelId;
        setTunnelIdForPollChannel();
        Channels.connect(pollChannel, sendChannel.getRemoteAddress());
    }

    private ChannelPipeline createSendPipeline() {
        ChannelPipeline pipeline = Channels.pipeline();
        
        pipeline.addLast("reqencoder", new HttpRequestEncoder()); // downstream
        pipeline.addLast("respdecoder", new HttpResponseDecoder()); // upstream
        pipeline.addLast("aggregator", new HttpChunkAggregator(HttpTunnelMessageUtils.MAX_BODY_SIZE)); // upstream
        pipeline.addLast("sendHandler", new HttpTunnelClientSendHandler(this)); // both
        pipeline.addLast("writeFragmenter", new WriteFragmenter(HttpTunnelMessageUtils.MAX_BODY_SIZE));
        
        return pipeline;
    }

    private ChannelPipeline createPollPipeline() {
        ChannelPipeline pipeline = Channels.pipeline();
        
        pipeline.addLast("reqencoder", new HttpRequestEncoder()); // downstream
        pipeline.addLast("respdecoder", new HttpResponseDecoder()); // upstream
        pipeline.addLast("aggregator", new HttpChunkAggregator(HttpTunnelMessageUtils.MAX_BODY_SIZE)); // upstream
        pipeline.addLast(HttpTunnelClientPollHandler.NAME, new HttpTunnelClientPollHandler(this)); // both
        
        return pipeline;
    }
    
    private void setTunnelIdForPollChannel() {
        HttpTunnelClientPollHandler pollHandler = pollChannel.getPipeline().get(HttpTunnelClientPollHandler.class);
        pollHandler.setTunnelId(tunnelId);
    }

    public void fullyEstablished() {
        if(!bound) {
            this.bound = true;
            Channels.fireChannelBound(this, getLocalAddress());
        }
        
        this.connected = true;
        connectFuture.setSuccess();
        Channels.fireChannelConnected(this, getRemoteAddress());
    }

    public void sendData(MessageEvent e) {
        final ChannelFuture originalFuture = e.getFuture();
        Channels.write(sendChannel, e.getMessage()).addListener(new ChannelFutureListener() {

            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.isSuccess()) {
                    originalFuture.setSuccess();
                } else {
                    originalFuture.setFailure(future.getCause());
                }
            }
        });
    }
    
    public String getServerHostName() {
        if(serverHostName == null) {
            serverHostName = HttpTunnelMessageUtils.convertToHostString(serverAddress); 
        }
        
        return serverHostName; 
    }
    
    private class ConsolidatingFutureListener implements ChannelFutureListener {

        private ChannelFuture completionFuture;
        private AtomicInteger eventsLeft;

        public ConsolidatingFutureListener(ChannelFuture completionFuture, int numToConsolidate) {
            this.completionFuture = completionFuture;
            eventsLeft = new AtomicInteger(numToConsolidate);
        }
        
        public void operationComplete(ChannelFuture future) throws Exception {
            if(!future.isSuccess()) {
                completionFuture.setFailure(future.getCause());
            } else if(eventsLeft.decrementAndGet() == 0) {
                completionFuture.setSuccess();
            }
        }
    }
}
