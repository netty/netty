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

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;
import org.jboss.netty.util.HttpCodecUtil;

import java.nio.charset.Charset;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class HttpResponseDecoder extends ReplayingDecoder<HttpResponseDecoder.ResponseState>
{
   private HttpResponse response;

   private ChannelBuffer content;

   private int chunkSize;

   public enum ResponseState
   {
      READ_INITIAL,
      READ_HEADER,
      READ_CONTENT,
      READ_FIXED_LENGTH_CONTENT,
      READ_CHUNK_SIZE,
      READ_CHUNKED_CONTENT,
      READ_CRLF,
      HANDLE,;
   }

   private StringBuffer currentLine;

   private ResponseState nextState;

   public HttpResponseDecoder()
   {
      super(ResponseState.READ_INITIAL);
   }

   protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, ResponseState state) throws Exception
   {
      switch (state)
      {
         case READ_INITIAL:
         {
            readInitial(buffer);
         }
         case READ_HEADER:
         {
            readHeaders(buffer);
            return null;
         }
         case READ_CONTENT:
         {
            if(content == null)
            {
               content = ChannelBuffers.dynamicBuffer();
            }
            content.writeBytes(buffer.readBytes(buffer.readableBytes()));
            response.setContent(content);
            content = null;
            currentLine = null;
            nextState = null;
            return response;
         }
         case READ_FIXED_LENGTH_CONTENT:
         {
            readFixedLengthContent(buffer);
            return null;
         }
         case READ_CHUNK_SIZE:
         {
            readChunkSize(buffer);
            return null;
         }
         case READ_CHUNKED_CONTENT:
         {
            readChunkedContent(buffer);
         }
         case READ_CRLF:
         {
            byte next = buffer.readByte();
            if(next == HttpCodecUtil.CR)
            {
               buffer.readByte();
            }
            checkpoint(nextState);
            return null;
         }
         case HANDLE:
         {
            byte next = buffer.readByte();
            if(next == HttpCodecUtil.CR)
            {
               buffer.readByte();
            }
            response.setContent(content);
            content = null;
            currentLine = null;
            nextState = null;
            return response;
         }
         default:
         {
            throw new Error("Shouldn't reach here.");
         }

      }
   }

   private void readChunkedContent(ChannelBuffer buffer)
   {
      if(content == null)
      {
         content = ChannelBuffers.dynamicBuffer(chunkSize);
      }
      content.writeBytes(buffer, chunkSize);
      nextState = ResponseState.READ_CHUNK_SIZE;
      checkpoint(ResponseState.READ_CRLF);
   }

   private void readChunkSize(ChannelBuffer buffer)
   {
      readIntoCurrentLine(buffer);

      chunkSize = getChunkSize(currentLine);
      currentLine = null;
      if(chunkSize == 0)
      {
         checkpoint(ResponseState.HANDLE);
      }
      else
      {
         checkpoint(ResponseState.READ_CHUNKED_CONTENT);
      }
   }

   private void readFixedLengthContent(ChannelBuffer buffer)
   {
      int length = response.getContentLength();
      if(content == null)
      {
         content = ChannelBuffers.buffer(length);
      }
      content.writeBytes(buffer.readBytes(length));
      checkpoint(ResponseState.HANDLE);
   }

   private void readHeaders(ChannelBuffer buffer)
   {
      readIntoCurrentLine(buffer);
      while(!currentLine.toString().equals(""))
            {
               String[] header = splitHeader(currentLine);
         response.addHeader(header[0], header[1]);
         currentLine = null;
         readIntoCurrentLine(buffer);
      }
      currentLine = null;
      ResponseState nextState;
      if(response.getContentLength() > 0)
      {
            nextState = ResponseState.READ_FIXED_LENGTH_CONTENT;
      }
      else if(response.isChunked())
      {
         nextState = ResponseState.READ_CHUNK_SIZE;
      }
      else
      {
         nextState = ResponseState.READ_CONTENT;
      }
      checkpoint(nextState);
   }

   private void readInitial(ChannelBuffer buffer)
   {
      readIntoCurrentLine(buffer);
      checkpoint(ResponseState.READ_HEADER);
      String[] split = splitInitial(currentLine);
      currentLine = null;
      response = new HttpResponseImpl(HttpProtocol.getProtocol(split[0]), HttpResponseStatusCode.getResponseStatusCode(Integer.valueOf(split[1])));
   }

   private int getChunkSize(StringBuffer buffer)
   {
      String hex = buffer.toString();
      return Integer.valueOf(hex, 16).intValue();
   }

   private boolean readIntoCurrentLine(ChannelBuffer channel)
   {
      if(currentLine == null)
      {
         currentLine = new StringBuffer();
      }
      while(true)
      {
         byte nextByte = channel.readByte();
         if(nextByte == HttpCodecUtil.CR)
         {
            channel.readByte();
            return true;
         }
         else if(nextByte == HttpCodecUtil.LF)
         {
            return true;
         }
         else
         {
            currentLine.append((char)nextByte);
         }
      }
   }


   private String[] splitInitial(StringBuffer sb)
   {
      String[] split = sb.toString().split(" ");
      if(split.length != 3)
      {
         throw new IllegalArgumentException(sb.toString() + "does not contain all 3 parts");
      }
      return split;
   }

   private String[] splitHeader(StringBuffer sb)
   {
      String[] split = sb.toString().split(":", 2);
      if(split.length != 2)
      {
         throw new IllegalArgumentException(sb.toString() + "does not contain all 2 parts");
      }
      for (int i = 0; i < split.length; i++)
      {
         split[i] = split[i].trim();
      }
      return split;
   }
}
