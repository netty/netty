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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.List;

/**
 * A servlet that acts as a proxy for a netty channel
 *
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class NettyServlet extends HttpServlet
{

   final static String CHANNEL_PROP = "channel";

   final static String HANDLER_PROP = "handler";

   protected void doRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
   {
      HttpSession session = request.getSession();
      Channel channel = (Channel) session.getAttribute(CHANNEL_PROP);
      ServletChannelHandler handler = (ServletChannelHandler) session.getAttribute(HANDLER_PROP);
      if (handler.isStreaming())
      {
         streamResponse(request, response, session, handler, channel);
      }
      else
      {
         pollResponse(channel, request, response, session, handler);
      }
   }

   private void streamResponse(final HttpServletRequest request, final HttpServletResponse response, HttpSession session, ServletChannelHandler handler, Channel channel) throws IOException
   {

      response.setHeader("jsessionid", session.getId());
      response.setHeader("Content-Type", "application/octet-stream");
      response.setContentLength(-1);
      response.setStatus(HttpServletResponse.SC_OK);
      response.getOutputStream().flush();
      handler.setOutputStream(response.getOutputStream());
      
      PushbackInputStream in = new PushbackInputStream(request.getInputStream());
      do
      {
         try
         {
            ChannelBuffer buffer = read(in);
            if (buffer == null)
            {
               break;
            }
            channel.write(buffer);
         }
         catch (IOException e)
         {
            // this is ok, the client can reconnect.
            break;
         }
      }
      while (true);

      if (!handler.awaitReconnect())
      {
         channel.close();
      }
   }

   private ChannelBuffer read(PushbackInputStream in) throws IOException
   {
      byte[] buf;
      int readBytes;

      do
      {
         int bytesToRead = in.available();
         if (bytesToRead > 0)
         {
            buf = new byte[bytesToRead];
            readBytes = in.read(buf);
            break;
         }
         else if (bytesToRead == 0)
         {
            int b = in.read();
            if (b < 0 || in.available() < 0)
            {
               return null;
            }
            if (b == 13)
            {
               in.read();
            }
            else
            {
               in.unread(b);
            }

         }
         else
         {
            return null;
         }
      }
      while (true);
      ChannelBuffer buffer;
      if (readBytes == buf.length)
      {
         buffer = ChannelBuffers.wrappedBuffer(buf);
      }
      else
      {
         // A rare case, but it sometimes happen.
         buffer = ChannelBuffers.wrappedBuffer(buf, 0, readBytes);
      }
      return buffer;
   }

   private void pollResponse(Channel channel, HttpServletRequest request, HttpServletResponse response, HttpSession session, ServletChannelHandler handler) throws IOException
   {
      int length = request.getContentLength();
      if (length > 0)
      {
         byte[] bytes = new byte[length];
         request.getInputStream().read(bytes);
         ChannelBuffer cb = ChannelBuffers.copiedBuffer(bytes);
         channel.write(cb);
      }
      handler.setOutputStream(response.getOutputStream());
      List<MessageEvent> buffers = handler.getAwaitingEvents();
      length = 0;
      if (buffers.size() > 0)
      {
         for (MessageEvent buffer : buffers)
         {
            length += ((ChannelBuffer)buffer.getMessage()).readableBytes();
         }
      }
      response.setHeader("jsessionid", session.getId());
      response.setContentLength(length);
      response.setStatus(HttpServletResponse.SC_OK);
      for (MessageEvent event : buffers)
      {
          ChannelBuffer buffer = (ChannelBuffer) event.getMessage();
          byte[] b = new byte[buffer.readableBytes()];
         buffer.readBytes(b);
          try {
              response.getOutputStream().write(b);
              event.getFuture().setSuccess();
          }
          catch (IOException e) {
              event.getFuture().setFailure(e);
          }
      }
   }

   protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException
   {
      doRequest(httpServletRequest, httpServletResponse);
   }

   protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException
   {
      doRequest(httpServletRequest, httpServletResponse);
   }
}

