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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import static org.jboss.netty.util.HttpCodecUtil.*;

import java.util.Set;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
@ChannelPipelineCoverage("one")
public class HttpRequestEncoder extends HttpMessageEncoder
{

   /**
    * writes the initial line i.e. 'GET /path/to/file/index.html HTTP/1.0'
    * @param buf
    * @param message
    */
   public void encodeInitialLine(ChannelBuffer buf, HttpMessage message)
   {
      HttpRequest request = (HttpRequest) message;
      buf.writeBytes(request.getMethod().getMethod().getBytes());
      buf.writeByte(SPACE);
      buf.writeBytes(request.getURI().toASCIIString().getBytes());
      buf.writeByte(SPACE);
      buf.writeBytes(request.getProtocol().getProtocol().getBytes());
      buf.writeBytes(CRLF);
   }

}
