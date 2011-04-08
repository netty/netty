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

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 */
public class FakeClientSocketChannelFactory implements ClientSocketChannelFactory
{

   public List<FakeSocketChannel> createdChannels;

   public FakeClientSocketChannelFactory()
   {
      createdChannels = new ArrayList<FakeSocketChannel>();
   }

   public SocketChannel newChannel(ChannelPipeline pipeline)
   {
      FakeSocketChannel channel = new FakeSocketChannel(null, this, pipeline, new FakeChannelSink());
      createdChannels.add(channel);
      return channel;
   }

   public void releaseExternalResources()
   {
      // nothing to do
   }

}
