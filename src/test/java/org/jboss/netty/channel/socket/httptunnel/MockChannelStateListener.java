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
import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 */
public class MockChannelStateListener implements HttpTunnelClientWorkerOwner
{

   public boolean fullyEstablished = false;

   public List<ChannelBuffer> messages = new ArrayList<ChannelBuffer>();

   public String tunnelId = null;

   public String serverHostName = null;

   public void fullyEstablished()
   {
      fullyEstablished = true;
   }

   public void onConnectRequest(ChannelFuture connectFuture, InetSocketAddress remoteAddress)
   {
      // not relevant for test
   }

   public void onMessageReceived(ChannelBuffer content)
   {
      messages.add(content);
   }

   public void onTunnelOpened(String tunnelId)
   {
      this.tunnelId = tunnelId;
   }

   public String getServerHostName()
   {
      return serverHostName;
   }

}
