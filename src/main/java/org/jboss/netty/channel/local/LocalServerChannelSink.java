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

import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.AbstractChannelSink;
import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireMessageReceived;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelClosed;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class LocalServerChannelSink extends AbstractChannelSink
{
   private Executor executor;

   public LocalServerChannelSink(Executor executor)
   {
      this.executor = executor;
   }

   public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception
   {
      if (e instanceof ChannelStateEvent)
      {
         ChannelStateEvent event = (ChannelStateEvent) e;
         LocalServerChannel serverChannel =
               (LocalServerChannel) event.getChannel();
         ChannelFuture future = event.getFuture();
         ChannelState state = event.getState();
         Object value = event.getValue();
         switch (state)
         {
            case OPEN:
               if (Boolean.FALSE.equals(value))
               {
                  
               }
               break;
            case BOUND:
               if (value != null)
               {
                  bind(future, serverChannel);
               }
               break;
         }
      }
      else if(e instanceof MessageEvent)
      {
         final MessageEvent event = (MessageEvent) e;
         final LocalChannel channel = (LocalChannel) event.getChannel();
         executor.execute(new Runnable()
         {
            public void run()
            {
               fireMessageReceived(channel.pairedChannel, event.getMessage());
            }
         });
         event.getFuture().setSuccess();
      }

   }

   private void bind(ChannelFuture future, LocalServerChannel serverChannel)
   {
      future.setSuccess();
      fireChannelBound(serverChannel, serverChannel.getLocalAddress());
   }

}
