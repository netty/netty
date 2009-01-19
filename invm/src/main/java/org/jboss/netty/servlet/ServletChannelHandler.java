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
package org.jboss.netty.servlet;

import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.buffer.ChannelBuffer;

import javax.servlet.ServletOutputStream;
import java.util.List;
import java.util.ArrayList;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
@ChannelPipelineCoverage("one")
class ServletChannelHandler extends SimpleChannelHandler
{
   List<ChannelBuffer> buffers = new ArrayList<ChannelBuffer>();

   private ServletOutputStream outputStream;

   final boolean stream;

   public ServletChannelHandler(boolean stream)
   {
      this.stream = stream;
   }

   public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
   {

      ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
      if (stream)
      {
         byte[] b = new byte[buffer.readableBytes()];
         buffer.readBytes(b);
         outputStream.write(b);
      }
      else
      {
         buffers.add(buffer);
      }

   }

   public synchronized List<ChannelBuffer> getBuffers()
   {
      List<ChannelBuffer> list = new ArrayList<ChannelBuffer>();
      list.addAll(buffers);
      buffers.clear();
      return list;
   }

   public void setOutputStream(ServletOutputStream outputStream)
   {
      this.outputStream = outputStream;
   }

   public boolean isStreaming()
   {
      return stream;
   }

   public ServletOutputStream getOutputStream()
   {
      return outputStream;
   }
}

