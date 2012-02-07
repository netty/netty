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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.Channels;
import io.netty.channel.DownstreamMessageEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

/**
 * Pipeline component which deals with sending data from the client to server.
 */
class HttpTunnelClientSendHandler extends SimpleChannelHandler {

    public static final String NAME = "client2server";

    private static final InternalLogger LOG = InternalLoggerFactory
            .getInstance(HttpTunnelClientSendHandler.class);

    private final HttpTunnelClientWorkerOwner tunnelChannel;

    private String tunnelId;

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
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (tunnelId == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("connection to " + e.getValue() +
                        " succeeded - sending open tunnel request");
            }
            HttpRequest request =
                    HttpTunnelMessageUtils
                            .createOpenTunnelRequest(tunnelChannel
                                    .getServerHostName());
            Channel thisChannel = ctx.getChannel();
            DownstreamMessageEvent event =
                    new DownstreamMessageEvent(thisChannel,
                            Channels.future(thisChannel), request,
                            thisChannel.getRemoteAddress());
            queuedWrites.offer(event);
            pendingRequestCount.incrementAndGet();
            sendQueuedData(ctx);
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        HttpResponse response = (HttpResponse) e.getMessage();

        if (HttpTunnelMessageUtils.isOKResponse(response)) {
            long roundTripTime = System.nanoTime() - sendRequestTime;
            if (LOG.isDebugEnabled()) {
                LOG.debug("OK response received for tunnel " + tunnelId +
                        ", after " + roundTripTime + " ns");
            }
            sendNextAfterResponse(ctx);
        } else if (HttpTunnelMessageUtils.isTunnelOpenResponse(response)) {
            tunnelId = HttpTunnelMessageUtils.extractCookie(response);
            if (LOG.isDebugEnabled()) {
                LOG.debug("tunnel open request accepted - id " + tunnelId);
            }
            tunnelChannel.onTunnelOpened(tunnelId);
            sendNextAfterResponse(ctx);
        } else if (HttpTunnelMessageUtils.isTunnelCloseResponse(response)) {
            if (LOG.isDebugEnabled()) {
                if (disconnecting.get()) {
                    LOG.debug("server acknowledged disconnect for tunnel " +
                            tunnelId);
                } else {
                    LOG.debug("server closed tunnel " + tunnelId);
                }
            }
            ctx.sendDownstream(postShutdownEvent);
        } else {
            // TODO: kill connection
            if (LOG.isWarnEnabled()) {
                LOG.warn("unknown response received for tunnel " + tunnelId +
                        ", closing connection");
            }
            Channels.close(ctx, ctx.getChannel().getCloseFuture());
        }
    }

    private void sendNextAfterResponse(ChannelHandlerContext ctx) {
        if (pendingRequestCount.decrementAndGet() > 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Immediately sending next send request for tunnel " +
                        tunnelId);
            }
            sendQueuedData(ctx);
        }
    }

    private synchronized void sendQueuedData(ChannelHandlerContext ctx) {
        if (disconnecting.get()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("sending close request for tunnel " + tunnelId);
            }
            HttpRequest closeRequest =
                    HttpTunnelMessageUtils.createCloseTunnelRequest(
                            tunnelChannel.getServerHostName(), tunnelId);
            Channels.write(ctx, Channels.future(ctx.getChannel()), closeRequest);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("sending next request for tunnel " + tunnelId);
            }
            MessageEvent nextWrite = queuedWrites.poll();
            sendRequestTime = System.nanoTime();
            ctx.sendDownstream(nextWrite);
        }
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("request to send data for tunnel " + tunnelId);
        }
        if (disconnecting.get()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("rejecting write request for tunnel " + tunnelId +
                        " received after disconnect requested");
            }
            e.getFuture().setFailure(
                    new IllegalStateException("tunnel is closing"));
            return;
        }
        ChannelBuffer data = (ChannelBuffer) e.getMessage();
        HttpRequest request =
                HttpTunnelMessageUtils.createSendDataRequest(
                        tunnelChannel.getServerHostName(), tunnelId, data);
        DownstreamMessageEvent translatedEvent =
                new DownstreamMessageEvent(ctx.getChannel(), e.getFuture(),
                        request, ctx.getChannel().getRemoteAddress());
        queuedWrites.offer(translatedEvent);
        if (pendingRequestCount.incrementAndGet() == 1) {
            sendQueuedData(ctx);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("write request for tunnel " + tunnelId + " queued");
            }
        }
    }

    @Override
    public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        shutdownTunnel(ctx, e);
    }

    @Override
    public void disconnectRequested(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        shutdownTunnel(ctx, e);
    }

    @Override
    public void unbindRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        shutdownTunnel(ctx, e);
    }

    private void shutdownTunnel(ChannelHandlerContext ctx,
            ChannelStateEvent postShutdownEvent) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("tunnel shutdown requested for send channel of tunnel " +
                    tunnelId);
        }
        if (!ctx.getChannel().isConnected()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("send channel of tunnel " + tunnelId +
                        " is already disconnected");
            }
            ctx.sendDownstream(postShutdownEvent);
            return;
        }

        if (!disconnecting.compareAndSet(false, true)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("tunnel shutdown process already initiated for tunnel " +
                        tunnelId);
            }
            return;
        }

        this.postShutdownEvent = postShutdownEvent;

        // if the channel is idle, send a close request immediately
        if (pendingRequestCount.incrementAndGet() == 1) {
            sendQueuedData(ctx);
        }
    }

    public String getTunnelId() {
        return tunnelId;
    }
}
