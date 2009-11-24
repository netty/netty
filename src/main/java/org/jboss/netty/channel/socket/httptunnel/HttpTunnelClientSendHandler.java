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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
class HttpTunnelClientSendHandler extends SimpleChannelHandler {

    public static final String NAME = "client2server";

    private static final Logger LOG = Logger.getLogger(HttpTunnelClientSendHandler.class.getName());

    private final HttpTunnelClientWorkerOwner tunnelChannel;

    private String tunnelId = null;
    // FIXME Unused field - safe to remove?
    private String host;

    private final AtomicBoolean disconnecting;
    private ChannelStateEvent postShutdownEvent;
    private final ConcurrentLinkedQueue<MessageEvent> queuedWrites;
    private final AtomicInteger pendingRequestCount;

    private long sendRequestTime;

    public HttpTunnelClientSendHandler(HttpTunnelClientWorkerOwner tunnelChannel) {
        this.tunnelChannel = tunnelChannel;
        queuedWrites = new ConcurrentLinkedQueue<MessageEvent>();
        pendingRequestCount = new AtomicInteger(0);
        disconnecting = new AtomicBoolean(false);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if(tunnelId == null) {
            LOG.log(Level.FINE, "connection to {0} succeeded - sending open tunnel request", e.getValue());
            HttpRequest request = HttpTunnelMessageUtils.createOpenTunnelRequest(tunnelChannel.getServerHostName());
            Channel thisChannel = ctx.getChannel();
            DownstreamMessageEvent event = new DownstreamMessageEvent(thisChannel, Channels.future(thisChannel), request, thisChannel.getRemoteAddress());
            queuedWrites.offer(event);
            pendingRequestCount.incrementAndGet();
            sendQueuedData(ctx);
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpResponse response = (HttpResponse)e.getMessage();

        if(HttpTunnelMessageUtils.isOKResponse(response)) {
            long roundTripTime = System.nanoTime() - sendRequestTime;
            LOG.log(Level.FINE, "OK response received for tunnel {0}, after {1}ns", new Object[] { tunnelId, roundTripTime });
            sendNextAfterResponse(ctx);
        } else if(HttpTunnelMessageUtils.isTunnelOpenResponse(response)) {
            tunnelId = HttpTunnelMessageUtils.extractCookie(response);
            LOG.log(Level.INFO, "tunnel open request accepted - id {0}", tunnelId);
            tunnelChannel.onTunnelOpened(tunnelId);
            sendNextAfterResponse(ctx);
        } else if(HttpTunnelMessageUtils.isTunnelCloseResponse(response)) {
            if(disconnecting.get()) {
                LOG.log(Level.INFO, "server acknowledged disconnect for tunnel {0}", tunnelId);
            } else {
                LOG.log(Level.INFO, "server closed tunnel {0}", tunnelId);
            }
            ctx.sendDownstream(postShutdownEvent);
        } else {
            // TODO: kill connection
            LOG.log(Level.WARNING, "unknown response received for tunnel {0}, closing connection", tunnelId);
            Channels.close(ctx, ctx.getChannel().getCloseFuture());
        }
    }

    private void sendNextAfterResponse(ChannelHandlerContext ctx) {
        if(pendingRequestCount.decrementAndGet() > 0) {
            LOG.log(Level.FINE, "Immediately sending next send request for tunnel {0}", tunnelId);
            sendQueuedData(ctx);
        }
    }

    private synchronized void sendQueuedData(ChannelHandlerContext ctx) {
        if(disconnecting.get()) {
            LOG.log(Level.FINE, "sending close request for tunnel {0}", tunnelId);
            HttpRequest closeRequest = HttpTunnelMessageUtils.createCloseTunnelRequest(tunnelChannel.getServerHostName(), tunnelId);
            Channels.write(ctx, Channels.future(ctx.getChannel()), closeRequest);
        } else {
            LOG.log(Level.FINE, "sending next request for tunnel {0}", tunnelId);
            MessageEvent nextWrite = queuedWrites.poll();
            sendRequestTime = System.nanoTime();
            ctx.sendDownstream(nextWrite);
        }
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        LOG.log(Level.FINER, "request to send data for tunnel {0}", tunnelId);
        if(disconnecting.get()) {
            LOG.log(Level.WARNING, "rejecting write request for tunnel {0} received after disconnect requested", tunnelId);
            e.getFuture().setFailure(new IllegalStateException("tunnel is closing"));
            return;
        }
        ChannelBuffer data = (ChannelBuffer) e.getMessage();
        HttpRequest request = HttpTunnelMessageUtils.createSendDataRequest(tunnelChannel.getServerHostName(), tunnelId, data);
        DownstreamMessageEvent translatedEvent = new DownstreamMessageEvent(ctx.getChannel(), e.getFuture(), request, ctx.getChannel().getRemoteAddress());
        queuedWrites.offer(translatedEvent);
        if(pendingRequestCount.incrementAndGet() == 1) {
            sendQueuedData(ctx);
        } else {
            LOG.log(Level.FINE, "write request for tunnel {0} queued", tunnelId);
        }
    }

    @Override
    public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        shutdownTunnel(ctx, e);
    }

    @Override
    public void disconnectRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        shutdownTunnel(ctx, e);
    }

    @Override
    public void unbindRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        shutdownTunnel(ctx, e);
    }

    private void shutdownTunnel(ChannelHandlerContext ctx, ChannelStateEvent postShutdownEvent) {
        LOG.log(Level.INFO, "tunnel shutdown requested for send channel of tunnel {0}", tunnelId);
        if(!ctx.getChannel().isConnected()) {
            LOG.log(Level.FINE, "send channel of tunnel {0} is already disconnected", tunnelId);
            ctx.sendDownstream(postShutdownEvent);
            return;
        }

        if(!disconnecting.compareAndSet(false, true)) {
            LOG.log(Level.WARNING, "tunnel shutdown process already initiated for tunnel {0}", tunnelId);
            return;
        }

        this.postShutdownEvent = postShutdownEvent;

        // if the channel is idle, send a close request immediately
        if(pendingRequestCount.incrementAndGet() == 1) {
            sendQueuedData(ctx);
        }
    }

    public String getTunnelId() {
        return tunnelId;
    }
}
