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

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;

/**
 * Sink of a client channel, deals with sunk events and then makes appropriate calls
 * on the channel itself to push data.
 * 
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelClientChannelSink extends AbstractChannelSink
{

   public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception
   {
      if (e instanceof ChannelStateEvent)
      {
         handleChannelStateEvent((ChannelStateEvent) e);
      }
      else if (e instanceof MessageEvent)
      {
         handleMessageEvent((MessageEvent) e);
      }
   }

   private void handleMessageEvent(MessageEvent e)
   {
      HttpTunnelClientChannel channel = (HttpTunnelClientChannel) e.getChannel();
      channel.sendData(e);
   }

   private void handleChannelStateEvent(ChannelStateEvent e)
   {
      HttpTunnelClientChannel channel = (HttpTunnelClientChannel) e.getChannel();

      switch (e.getState())
      {
         case CONNECTED :
            if (e.getValue() != null)
            {
               channel.onConnectRequest(e.getFuture(), (InetSocketAddress) e.getValue());
            }
            else
            {
               channel.onDisconnectRequest(e.getFuture());
            }
            break;
         case BOUND :
            if (e.getValue() != null)
            {
               channel.onBindRequest((InetSocketAddress) e.getValue(), e.getFuture());
            }
            else
            {
               channel.onUnbindRequest(e.getFuture());
            }
            break;
         case OPEN :
            if (Boolean.FALSE.equals(e.getValue()))
            {
               channel.onCloseRequest(e.getFuture());
            }
            break;
      }
   }
}
