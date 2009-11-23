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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
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
class HttpTunnelClientPollHandler extends SimpleChannelHandler {

	public static final String NAME = "server2client";
	
	private static final Logger LOG = Logger.getLogger(HttpTunnelClientPollHandler.class.getName());

	private String tunnelId;
    private HttpTunnelClientWorkerOwner tunnelChannel;

    private long pollTime;

	public HttpTunnelClientPollHandler(HttpTunnelClientWorkerOwner tunnelChannel) {
	    this.tunnelChannel = tunnelChannel;
    }
	
	public void setTunnelId(String tunnelId) {
	    this.tunnelId = tunnelId;
	}

    @Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        LOG.log(Level.FINE, "Poll channel for tunnel {0} established", tunnelId);
        tunnelChannel.fullyEstablished();
		sendPoll(ctx);
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		HttpResponse response = (HttpResponse) e.getMessage();
		
		if(HttpTunnelMessageUtils.isOKResponse(response)) {
		    long rtTime = System.nanoTime() - pollTime;
		    LOG.log(Level.FINE, "OK response received for poll on tunnel {0} after {1}ns", new Object[] { tunnelId, rtTime });
		    tunnelChannel.onMessageReceived(response.getContent());
		    sendPoll(ctx);
		} else {
		    LOG.log(Level.WARNING, "non-OK response received for poll on tunnel {0}", tunnelId);
		}
	}

    private void sendPoll(ChannelHandlerContext ctx) {
        pollTime = System.nanoTime();
        LOG.log(Level.FINE, "sending poll request for tunnel {0}", tunnelId);
        HttpRequest request = HttpTunnelMessageUtils.createReceiveDataRequest(tunnelChannel.getServerHostName(), tunnelId);
		Channels.write(ctx, Channels.future(ctx.getChannel()), request);
    }
}
