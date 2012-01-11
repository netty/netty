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

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;

/**
 * The interface from a TCP channel which is being used to communicate with the client
 * end of an http tunnel and the server message switch.
 * 
 * This primarily exists for mock testing purposes.
 */
interface ServerMessageSwitchUpstreamInterface {

    String createTunnel(InetSocketAddress remoteAddress);

    boolean isOpenTunnel(String tunnelId);

    void clientCloseTunnel(String tunnelId);

    /**
     * Passes some received data from a client for forwarding to the server's view
     * of the tunnel.
     * @return the current status of the tunnel. ALIVE indicates the tunnel is still
     * functional, CLOSED indicates it is closed and the client should be notified
     * of this (and will be forgotten after this notification).
     */
    TunnelStatus routeInboundData(String tunnelId,
                                  ChannelBuffer inboundData);

    void pollOutboundData(String tunnelId, Channel responseChannel);

    enum TunnelStatus {
        ALIVE, CLOSED
    }

}
