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

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.SocketChannelConfig;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 */
public class FakeSocketChannel extends AbstractChannel implements SocketChannel
{

   public InetSocketAddress localAddress;

   public InetSocketAddress remoteAddress;

   public SocketChannelConfig config = new FakeChannelConfig();

   public boolean bound = false;

   public boolean connected = false;

   public ChannelSink sink;

   public FakeSocketChannel(Channel parent, ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink)
   {
      super(parent, factory, pipeline, sink);
      this.sink = sink;
   }

   public InetSocketAddress getLocalAddress()
   {
      return localAddress;
   }

   public SocketChannelConfig getConfig()
   {
      return config;
   }

   public InetSocketAddress getRemoteAddress()
   {
      return remoteAddress;
   }

   public boolean isBound()
   {
      return bound;
   }

   public boolean isConnected()
   {
      return connected;
   }

   public void emulateConnected(InetSocketAddress localAddress, InetSocketAddress remoteAddress,
         ChannelFuture connectedFuture)
   {
      if (connected)
      {
         return;
      }

      emulateBound(localAddress, null);
      this.remoteAddress = remoteAddress;
      connected = true;
      Channels.fireChannelConnected(this, remoteAddress);
      if (connectedFuture != null)
      {
         connectedFuture.setSuccess();
      }
   }

   public void emulateBound(InetSocketAddress localAddress, ChannelFuture boundFuture)
   {
      if (bound)
      {
         return;
      }

      bound = true;
      this.localAddress = localAddress;
      Channels.fireChannelBound(this, localAddress);
      if (boundFuture != null)
      {
         boundFuture.setSuccess();
      }
   }

}
