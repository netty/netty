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

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * Upstream handler which is responsible for determining whether a received HTTP request is a legal
 * tunnel request, and if so, invoking the appropriate request method on the
 * {@link ServerMessageSwitch} to service the request.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
class AcceptedServerChannelRequestDispatch extends SimpleChannelUpstreamHandler {

    public static final String NAME = "AcceptedServerChannelRequestDispatch";

    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(AcceptedServerChannelRequestDispatch.class);

    private final ServerMessageSwitchUpstreamInterface messageSwitch;

    public AcceptedServerChannelRequestDispatch(ServerMessageSwitchUpstreamInterface messageSwitch) {
        this.messageSwitch = messageSwitch;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();

        if(HttpTunnelMessageUtils.isOpenTunnelRequest(request)) {
            handleOpenTunnel(ctx);
        } else if(HttpTunnelMessageUtils.isSendDataRequest(request)) {
            handleSendData(ctx, request);
        } else if(HttpTunnelMessageUtils.isReceiveDataRequest(request)) {
            handleReceiveData(ctx, request);
        } else if(HttpTunnelMessageUtils.isCloseTunnelRequest(request)) {
            handleCloseTunnel(ctx, request);
        } else {
            LOG.warn("Invalid request received on http tunnel channel");
            respondWith(ctx, HttpTunnelMessageUtils.createRejection(request, "invalid request to netty HTTP tunnel gateway"));
        }
    }

    private void respondWith(ChannelHandlerContext ctx, HttpResponse response) {
        Channels.write(ctx, Channels.future(ctx.getChannel()), response);
    }

    private void handleOpenTunnel(ChannelHandlerContext ctx) {
        String tunnelId = messageSwitch.createTunnel(ctx.getChannel().getRemoteAddress());
        if (LOG.isInfoEnabled()) {
            LOG.info("open tunnel request received from " + ctx.getChannel().getRemoteAddress() + " - allocated ID " + tunnelId);
        }
        respondWith(ctx, HttpTunnelMessageUtils.createTunnelOpenResponse(tunnelId));
    }

    private void handleCloseTunnel(ChannelHandlerContext ctx, HttpRequest request) {
        String tunnelId = checkTunnelId(request, ctx);
        if(tunnelId == null) {
            return;
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("close tunnel request received for tunnel " + tunnelId);
        }
        messageSwitch.closeTunnel(tunnelId);
        respondWith(ctx, HttpTunnelMessageUtils.createTunnelCloseResponse());
    }

    private void handleSendData(ChannelHandlerContext ctx, HttpRequest request) {
        String tunnelId = checkTunnelId(request, ctx);
        if(tunnelId == null) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("send data request received for tunnel " + tunnelId);
        }

        if(request.getContentLength() == 0 || request.getContent() == null || request.getContent().readableBytes() == 0) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("send data request contained no data on tunnel " + tunnelId);
            }
            respondWith(ctx, HttpTunnelMessageUtils.createRejection(request, "Send data requests must contain data"));
            return;
        }

        messageSwitch.routeInboundData(tunnelId, request.getContent());
        respondWith(ctx, HttpTunnelMessageUtils.createSendDataResponse());
    }

    private void handleReceiveData(ChannelHandlerContext ctx, HttpRequest request) {
        String tunnelId = checkTunnelId(request, ctx);
        if(tunnelId == null) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("poll data request received for tunnel " + tunnelId);
        }
        messageSwitch.pollOutboundData(tunnelId, ctx.getChannel());
    }

    private String checkTunnelId(HttpRequest request, ChannelHandlerContext ctx) {
        String tunnelId = HttpTunnelMessageUtils.extractTunnelId(request);
        if(tunnelId == null) {
            LOG.warn("request without a tunnel id received - rejecting");
            Channels.write(ctx, Channels.future(ctx.getChannel()), HttpTunnelMessageUtils.createRejection(request, "no tunnel id specified in send data request"));
        } else if(!messageSwitch.isOpenTunnel(tunnelId)) {
            LOG.warn("request for unknown tunnel id " + tunnelId + " received - rejecting");
            Channels.write(ctx, Channels.future(ctx.getChannel()), HttpTunnelMessageUtils.createRejection(request, "tunnel id \"" + tunnelId + "\" is either closed or does not exist"));
            return null;
        }

        return tunnelId;
    }
}
