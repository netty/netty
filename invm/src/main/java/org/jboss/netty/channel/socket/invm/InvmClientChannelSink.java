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
package org.jboss.netty.channel.socket.invm;

import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.Channel;
import static org.jboss.netty.channel.Channels.fireMessageReceived;

import java.net.SocketAddress;
import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class InvmClientChannelSink implements ChannelSink
{
   private final Executor excecutor;

   private final Channel serverChannel;

   private ChannelSink serverSink;

   public InvmClientChannelSink(Executor executor, Channel channel, ChannelSink sink)
   {
      this.excecutor = executor;
      this.serverChannel = channel;
      this.serverSink = sink;
   }

   public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception
   {
      if (e instanceof ChannelStateEvent)
      {
         ChannelStateEvent event = (ChannelStateEvent) e;

         InvmClientChannel channel =
               (InvmClientChannel) event.getChannel();
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
               }
               else
               {
               }
               break;
            case CONNECTED:
               connect(channel, future, (InvmSocketAddress) value);
               break;
            case INTEREST_OPS:
               break;
         }
      }
      else if (e instanceof MessageEvent)
      {
         MessageEvent event = (MessageEvent) e;
         InvmClientChannel channel = (InvmClientChannel) event.getChannel();
         channel.writeBuffer.put(event);
      }
   }

   private void connect(InvmClientChannel channel, ChannelFuture future, InvmSocketAddress invmSocketAddress) throws Exception
   {
      future.setSuccess();
      ChannelPipeline pipeline = serverChannel.getConfig().getPipelineFactory().getPipeline();
      InvmAcceptedChannel acceptedChannel = new InvmAcceptedChannel(serverChannel.getFactory(), pipeline, serverSink);
      Channels.fireChannelConnected(channel, invmSocketAddress);
      excecutor.execute(new ClientWorker(channel, acceptedChannel));
      excecutor.execute(new ServerWorker(channel, acceptedChannel));
   }

   public void exceptionCaught(ChannelPipeline pipeline, ChannelEvent e, ChannelPipelineException cause) throws Exception
   {
   }

   class ClientWorker implements Runnable
   {
      final InvmClientChannel channel;

      private InvmAcceptedChannel acceptedChannel;

      public ClientWorker(InvmClientChannel channel, InvmAcceptedChannel acceptedChannel)
      {
         this.channel = channel;
         this.acceptedChannel = acceptedChannel;
      }

      public void run()
      {
         do
         {
            try
            {
               MessageEvent event = channel.writeBuffer.take();
               fireMessageReceived(acceptedChannel, event.getMessage());
            }
            catch (InterruptedException e)
            {
               //todo
            }
         }
         while (true);
      }
   }

   class ServerWorker implements Runnable
   {
      final Channel clientChannel;
      final InvmAcceptedChannel acceptedChannel;

      public ServerWorker(Channel clientChannel, InvmAcceptedChannel acceptedChannel)
      {
         this.clientChannel = clientChannel;
         this.acceptedChannel = acceptedChannel;
      }

      public void run()
      {
         do
         {
            try
            {
               MessageEvent event = acceptedChannel.writeBuffer.take();
               fireMessageReceived(clientChannel, event.getMessage());
            }
            catch (InterruptedException e)
            {
               //todo
            }
         }
         while (true);
      }
   }
}
