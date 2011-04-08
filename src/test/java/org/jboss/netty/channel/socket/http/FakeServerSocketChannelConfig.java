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

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.socket.ServerSocketChannelConfig;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 */
public class FakeServerSocketChannelConfig extends DefaultChannelConfig implements ServerSocketChannelConfig
{

   public int backlog = 5;

   public int receiveBufferSize = 1024;

   public boolean reuseAddress = false;

   public int connectionTimeout = 5000;

   public ChannelPipelineFactory pipelineFactory;

   public int writeTimeout = 5000;

   public ChannelBufferFactory bufferFactory = new HeapChannelBufferFactory();

   public int getBacklog()
   {
      return backlog;
   }

   public void setBacklog(int backlog)
   {
      this.backlog = backlog;
   }

   public int getReceiveBufferSize()
   {
      return receiveBufferSize;
   }

   public void setReceiveBufferSize(int receiveBufferSize)
   {
      this.receiveBufferSize = receiveBufferSize;
   }

   public boolean isReuseAddress()
   {
      return reuseAddress;
   }

   public void setReuseAddress(boolean reuseAddress)
   {
      this.reuseAddress = reuseAddress;
   }

   public void setPerformancePreferences(int connectionTime, int latency, int bandwidth)
   {
      // ignore
   }
}
