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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;

/**
 * Sink for the server end of an http tunnel. Data sent down through the server end is dispatched
 * from here to the ServerMessageSwitch, which queues the data awaiting a poll request from the
 * client end of the tunnel.
 * 
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @version $Rev$, $Date$
 */
class HttpTunnelAcceptedChannelSink extends AbstractChannelSink {

    private ServerMessageSwitchDownstreamInterface messageSwitch;
    private String tunnelId;
    private boolean active;

    public HttpTunnelAcceptedChannelSink(ServerMessageSwitchDownstreamInterface messageSwitch, String tunnelId) {
        this.messageSwitch = messageSwitch;
        this.tunnelId = tunnelId;
    }
    
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        if(e instanceof MessageEvent) {
            handleMessageEvent((MessageEvent)e);
        } else if(e instanceof ChannelStateEvent) {
            handleStateEvent((ChannelStateEvent)e);
        }
    }

    private void handleMessageEvent(MessageEvent ev) {
        if(!(ev.getMessage() instanceof ChannelBuffer)) {
            throw new IllegalArgumentException("Attempt to send data which is not a ChannelBuffer:" + ev.getMessage());
        }
        messageSwitch.routeOutboundData(tunnelId, (ChannelBuffer)ev.getMessage(), ev.getFuture());
    }

    private void handleStateEvent(ChannelStateEvent ev) {
        Channel owner = (Channel) ev.getChannel();
        switch(ev.getState()) {
        case OPEN:
            if(Boolean.FALSE.equals(ev.getValue())) {
                messageSwitch.closeTunnel(tunnelId);
                this.active = false;
                Channels.fireChannelClosed(owner);
            }
            break;
        case BOUND:
            if(ev.getValue() == null) {
                messageSwitch.closeTunnel(tunnelId);
                this.active = false;
                Channels.fireChannelUnbound(owner);
            }
        case CONNECTED:
            if(ev.getValue() == null) {
                messageSwitch.closeTunnel(tunnelId);
                this.active = false;
                Channels.fireChannelDisconnected(owner);
            }
        }
    }
    
    public boolean isActive() {
        return active;
    }
}
