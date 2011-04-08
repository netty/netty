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

import java.util.Map;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.util.internal.ConversionUtil;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 */
public class FakeChannelConfig implements SocketChannelConfig
{

   private int receiveBufferSize = 1024;

   private int sendBufferSize = 1024;

   private int soLinger = 500;

   private int trafficClass = 0;

   private boolean keepAlive = true;

   private boolean reuseAddress = true;

   private boolean tcpNoDelay = false;

   private ChannelBufferFactory bufferFactory = new HeapChannelBufferFactory();

   private int connectTimeout = 5000;

   private ChannelPipelineFactory pipelineFactory = new ChannelPipelineFactory()
   {
      public ChannelPipeline getPipeline() throws Exception
      {
         return Channels.pipeline();
      }
   };

   private int writeTimeout = 3000;

   public int getReceiveBufferSize()
   {
      return receiveBufferSize;
   }

   public void setReceiveBufferSize(int receiveBufferSize)
   {
      this.receiveBufferSize = receiveBufferSize;
   }

   public int getSendBufferSize()
   {
      return sendBufferSize;
   }

   public void setSendBufferSize(int sendBufferSize)
   {
      this.sendBufferSize = sendBufferSize;
   }

   public int getSoLinger()
   {
      return soLinger;
   }

   public void setSoLinger(int soLinger)
   {
      this.soLinger = soLinger;
   }

   public int getTrafficClass()
   {
      return trafficClass;
   }

   public void setTrafficClass(int trafficClass)
   {
      this.trafficClass = trafficClass;
   }

   public boolean isKeepAlive()
   {
      return keepAlive;
   }

   public void setKeepAlive(boolean keepAlive)
   {
      this.keepAlive = keepAlive;
   }

   public boolean isReuseAddress()
   {
      return reuseAddress;
   }

   public void setReuseAddress(boolean reuseAddress)
   {
      this.reuseAddress = reuseAddress;
   }

   public boolean isTcpNoDelay()
   {
      return tcpNoDelay;
   }

   public void setTcpNoDelay(boolean tcpNoDelay)
   {
      this.tcpNoDelay = tcpNoDelay;
   }

   public void setPerformancePreferences(int connectionTime, int latency, int bandwidth)
   {
      // do nothing
   }

   public ChannelBufferFactory getBufferFactory()
   {
      return bufferFactory;
   }

   public void setBufferFactory(ChannelBufferFactory bufferFactory)
   {
      this.bufferFactory = bufferFactory;
   }

   public int getConnectTimeoutMillis()
   {
      return connectTimeout;
   }

   public void setConnectTimeoutMillis(int connectTimeoutMillis)
   {
      connectTimeout = connectTimeoutMillis;
   }

   public ChannelPipelineFactory getPipelineFactory()
   {
      return pipelineFactory;
   }

   public void setPipelineFactory(ChannelPipelineFactory pipelineFactory)
   {
      this.pipelineFactory = pipelineFactory;
   }

   public int getWriteTimeoutMillis()
   {
      return writeTimeout;
   }

   public void setWriteTimeoutMillis(int writeTimeoutMillis)
   {
      writeTimeout = writeTimeoutMillis;
   }

   public boolean setOption(String key, Object value)
   {
      if (key.equals("pipelineFactory"))
      {
         setPipelineFactory((ChannelPipelineFactory) value);
      }
      else if (key.equals("connectTimeoutMillis"))
      {
         setConnectTimeoutMillis(ConversionUtil.toInt(value));
      }
      else if (key.equals("bufferFactory"))
      {
         setBufferFactory((ChannelBufferFactory) value);
      }
      else if (key.equals("receiveBufferSize"))
      {
         setReceiveBufferSize(ConversionUtil.toInt(value));
      }
      else if (key.equals("sendBufferSize"))
      {
         setSendBufferSize(ConversionUtil.toInt(value));
      }
      else if (key.equals("tcpNoDelay"))
      {
         setTcpNoDelay(ConversionUtil.toBoolean(value));
      }
      else if (key.equals("keepAlive"))
      {
         setKeepAlive(ConversionUtil.toBoolean(value));
      }
      else if (key.equals("reuseAddress"))
      {
         setReuseAddress(ConversionUtil.toBoolean(value));
      }
      else if (key.equals("soLinger"))
      {
         setSoLinger(ConversionUtil.toInt(value));
      }
      else if (key.equals("trafficClass"))
      {
         setTrafficClass(ConversionUtil.toInt(value));
      }
      else
      {
         return false;
      }
      return true;
   }

   public void setOptions(Map<String, Object> options)
   {
      for (Entry<String, Object> e : options.entrySet())
      {
         setOption(e.getKey(), e.getValue());
      }
   }

}
