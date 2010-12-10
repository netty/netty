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

import java.util.Map;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelConfig;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
public class HttpTunnelServerChannelConfig implements ServerSocketChannelConfig
{

   private ChannelPipelineFactory pipelineFactory;

   private final ServerSocketChannel realChannel;

   private TunnelIdGenerator tunnelIdGenerator = new DefaultTunnelIdGenerator();

   public HttpTunnelServerChannelConfig(ServerSocketChannel realChannel)
   {
      this.realChannel = realChannel;
   }

   private ServerSocketChannelConfig getWrappedConfig()
   {
      return realChannel.getConfig();
   }

   public int getBacklog()
   {
      return getWrappedConfig().getBacklog();
   }

   public int getReceiveBufferSize()
   {
      return getWrappedConfig().getReceiveBufferSize();
   }

   public boolean isReuseAddress()
   {
      return getWrappedConfig().isReuseAddress();
   }

   public void setBacklog(int backlog)
   {
      getWrappedConfig().setBacklog(backlog);
   }

   public void setPerformancePreferences(int connectionTime, int latency, int bandwidth)
   {
      getWrappedConfig().setPerformancePreferences(connectionTime, latency, bandwidth);
   }

   public void setReceiveBufferSize(int receiveBufferSize)
   {
      getWrappedConfig().setReceiveBufferSize(receiveBufferSize);
   }

   public void setReuseAddress(boolean reuseAddress)
   {
      getWrappedConfig().setReuseAddress(reuseAddress);
   }

   public ChannelBufferFactory getBufferFactory()
   {
      return getWrappedConfig().getBufferFactory();
   }

   public int getConnectTimeoutMillis()
   {
      return getWrappedConfig().getConnectTimeoutMillis();
   }

   public ChannelPipelineFactory getPipelineFactory()
   {
      return pipelineFactory;
   }

   public void setBufferFactory(ChannelBufferFactory bufferFactory)
   {
      getWrappedConfig().setBufferFactory(bufferFactory);
   }

   public void setConnectTimeoutMillis(int connectTimeoutMillis)
   {
      getWrappedConfig().setConnectTimeoutMillis(connectTimeoutMillis);
   }

   public boolean setOption(String name, Object value)
   {
      if (name.equals("pipelineFactory"))
      {
         setPipelineFactory((ChannelPipelineFactory) value);
         return true;
      }
      else if (name.equals("tunnelIdGenerator"))
      {
         setTunnelIdGenerator((TunnelIdGenerator) value);
         return true;
      }
      else
      {
         return getWrappedConfig().setOption(name, value);
      }
   }

   public void setOptions(Map<String, Object> options)
   {
      for (Entry<String, Object> e : options.entrySet())
      {
         setOption(e.getKey(), e.getValue());
      }
   }

   public void setPipelineFactory(ChannelPipelineFactory pipelineFactory)
   {
      this.pipelineFactory = pipelineFactory;
   }

   public void setTunnelIdGenerator(TunnelIdGenerator tunnelIdGenerator)
   {
      this.tunnelIdGenerator = tunnelIdGenerator;
   }

   public TunnelIdGenerator getTunnelIdGenerator()
   {
      return tunnelIdGenerator;
   }
}
