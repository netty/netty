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
package org.jboss.netty.channel.socket.http;

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;

/**
 * Interface which is used by the send and poll "worker" channels
 * to notify the virtual tunnel channel of key events, and to get
 * access to higher level information required for correct
 * operation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
interface HttpTunnelClientWorkerOwner {
    /**
     * The HTTP tunnel client sink invokes this when the application code requests the connection
     * of an HTTP tunnel to the specified remote address.
     */
    public void onConnectRequest(ChannelFuture connectFuture,
            InetSocketAddress remoteAddress);

    /**
     * The send channel handler calls this method when the server accepts the open tunnel request,
     * returning a unique tunnel ID.
     *
     * @param tunnelId the server allocated tunnel ID
     */
    public void onTunnelOpened(String tunnelId);

    /**
     * The poll channel handler calls this method when the poll channel is connected, indicating
     * that full duplex communications are now possible.
     */
    public void fullyEstablished();

    /**
     * The poll handler calls this method when some data is received and decoded from the server.
     * @param content the data received from the server
     */
    public void onMessageReceived(ChannelBuffer content);

    /**
     * @return the name of the server with whom we are communicating with - this is used within
     * the HOST HTTP header for all requests. This is particularly important for operation behind
     * a proxy, where the HOST string is used to route the request.
     */
    public String getServerHostName();

}