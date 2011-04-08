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

import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireChannelOpen;

import java.net.InetSocketAddress;

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelConfig;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 */
public class FakeServerSocketChannel extends AbstractChannel implements ServerSocketChannel
{

   public boolean bound;

   public boolean connected;

   public InetSocketAddress remoteAddress;

   public InetSocketAddress localAddress;

   public ServerSocketChannelConfig config = new FakeServerSocketChannelConfig();

   public FakeServerSocketChannel(ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink)
   {
      super(null, factory, pipeline, sink);
   }

   public ServerSocketChannelConfig getConfig()
   {
      return config;
   }

   public InetSocketAddress getLocalAddress()
   {
      return localAddress;
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

   public FakeSocketChannel acceptNewConnection(InetSocketAddress remoteAddress, ChannelSink sink) throws Exception
   {
      ChannelPipeline newPipeline = getConfig().getPipelineFactory().getPipeline();
      FakeSocketChannel newChannel = new FakeSocketChannel(this, getFactory(), newPipeline, sink);
      newChannel.localAddress = localAddress;
      newChannel.remoteAddress = remoteAddress;
      fireChannelOpen(newChannel);
      fireChannelBound(newChannel, newChannel.localAddress);
      fireChannelConnected(this, newChannel.remoteAddress);

      return newChannel;
   }

}
