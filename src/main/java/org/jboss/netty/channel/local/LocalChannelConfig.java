/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.local;

import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.buffer.ChannelBufferFactory;

import java.util.Map;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class LocalChannelConfig implements ChannelConfig
{
   ChannelPipelineFactory pipelineFactory;
   ChannelBufferFactory bufferFactory;
   public void setOptions(Map<String, Object> options)
   {
   }

   public ChannelBufferFactory getBufferFactory()
   {
      return bufferFactory;
   }

   public void setBufferFactory(ChannelBufferFactory bufferFactory)
   {
      this.bufferFactory = bufferFactory;
   }

   public ChannelPipelineFactory getPipelineFactory()
   {
      return pipelineFactory;
   }

   public void setPipelineFactory(ChannelPipelineFactory pipelineFactory)
   {
      this.pipelineFactory = pipelineFactory;
   }

   public int getConnectTimeoutMillis()
   {
      return 0;
   }

   public void setConnectTimeoutMillis(int connectTimeoutMillis)
   {
   }

   public int getWriteTimeoutMillis()
   {
      return 0;
   }

   public void setWriteTimeoutMillis(int writeTimeoutMillis)
   {
   }
}
