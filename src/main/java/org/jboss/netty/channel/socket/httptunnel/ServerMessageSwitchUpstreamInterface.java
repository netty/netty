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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;

/**
 * The interface from a TCP channel which is being used to communicate with the client
 * end of an http tunnel and the server message switch.
 * 
 * This primarily exists for mock testing purposes.
 * 
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
interface ServerMessageSwitchUpstreamInterface
{

   public String createTunnel(InetSocketAddress remoteAddress);

   public boolean isOpenTunnel(String tunnelId);

   public void clientCloseTunnel(String tunnelId);

   /**
    * Passes some received data from a client for forwarding to the server's view
    * of the tunnel.
    * @return the current status of the tunnel. ALIVE indicates the tunnel is still
    * functional, CLOSED indicates it is closed and the client should be notified
    * of this (and will be forgotten after this notification).
    */
   public TunnelStatus routeInboundData(String tunnelId, ChannelBuffer inboundData);

   public void pollOutboundData(String tunnelId, Channel responseChannel);

   public static enum TunnelStatus {
      ALIVE, CLOSED
   }

}