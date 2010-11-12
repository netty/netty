/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.netty.handler.ipfilter;

import java.net.InetSocketAddress;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;

// TODO: Auto-generated Javadoc
/**
 * General class that handle Ip Filtering.
 * 
 * @author frederic bregier
 */
public abstract class IpFilteringHandlerImpl implements ChannelUpstreamHandler, IpFilteringHandler
{

   private IpFilterListener listener = null;

   /**
    * Called when the channel is connected. It returns True if the corresponding connection
    * is to be allowed. Else it returns False.
    * @param ctx
    * @param e
    * @param inetSocketAddress the remote {@link InetSocketAddress} from client
    * @return True if the corresponding connection is allowed, else False.
    * @throws Exception
    */
   protected abstract boolean accept(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress)
         throws Exception;

   /**
    * Called when the channel has the CONNECTED status and the channel was refused by a previous call to accept().
    * This method enables your implementation to send a message back to the client before closing
    * or whatever you need. This method returns a ChannelFuture on which the implementation
    * will wait uninterruptibly before closing the channel.<br>
    * For instance, If a message is sent back, the corresponding ChannelFuture has to be returned.
    * @param ctx
    * @param e
    * @param inetSocketAddress the remote {@link InetSocketAddress} from client
    * @return the associated ChannelFuture to be waited for before closing the channel. Null is allowed.
    * @throws Exception
    */
   protected ChannelFuture handleRefusedChannel(ChannelHandlerContext ctx, ChannelEvent e,
         InetSocketAddress inetSocketAddress) throws Exception
   {
      if (listener == null)
         return null;
      ChannelFuture result = listener.refused(ctx, e, inetSocketAddress);
      return result;
   }

   protected ChannelFuture handleAllowedChannel(ChannelHandlerContext ctx, ChannelEvent e,
         InetSocketAddress inetSocketAddress) throws Exception
   {
      if (listener == null)
         return null;
      ChannelFuture result = listener.allowed(ctx, e, inetSocketAddress);
      return result;
   }

   /**
    * Internal method to test if the current channel is blocked. Should not be overridden.
    * @param ctx
    * @return True if the current channel is blocked, else False
    */
   protected boolean isBlocked(ChannelHandlerContext ctx)
   {
      return ctx.getAttachment() != null;
   }

   /**
    * Called in handleUpstream, if this channel was previously blocked,
    * to check if whatever the event, it should be passed to the next entry in the pipeline.<br>
    * If one wants to not block events, just overridden this method by returning always true.<br><br>
    * <b>Note that OPENED and BOUND events are still passed to the next entry in the pipeline since
    * those events come out before the CONNECTED event and so the possibility to filter the connection.</b>
    * @param ctx
    * @param e
    * @return True if the event should continue, False if the event should not continue
    *          since this channel was blocked by this filter
    * @throws Exception
    */
   protected boolean continues(ChannelHandlerContext ctx, ChannelEvent e) throws Exception
   {
      if (listener != null)
         return listener.continues(ctx, e);
      else
         return false;
   }

   /* (non-Javadoc)
    * @see org.jboss.netty.channel.ChannelUpstreamHandler#handleUpstream(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelEvent)
    */
   @Override
public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception
   {
      if (e instanceof ChannelStateEvent)
      {
         ChannelStateEvent evt = (ChannelStateEvent) e;
         switch (evt.getState())
         {
            case OPEN :
            case BOUND :
               // Special case: OPEND and BOUND events are before CONNECTED,
               // but CLOSED and UNBOUND events are after DISCONNECTED: should those events be blocked too?
               if (isBlocked(ctx) && !continues(ctx, evt))
               {
                  // don't pass to next level since channel was blocked early
                  return;
               }
               else
               {
                  ctx.sendUpstream(e);
                  return;
               }
            case CONNECTED :
               if (evt.getValue() != null)
               {
                  // CONNECTED
                  InetSocketAddress inetSocketAddress = (InetSocketAddress) e.getChannel().getRemoteAddress();
                  if (!accept(ctx, e, inetSocketAddress))
                  {
                     ctx.setAttachment(Boolean.TRUE);
                     ChannelFuture future = handleRefusedChannel(ctx, e, inetSocketAddress);
                     if (future != null)
                     {
                        future.addListener(ChannelFutureListener.CLOSE);
                     }
                     else
                     {
                        Channels.close(e.getChannel());
                     }
                     if (isBlocked(ctx) && !continues(ctx, evt))
                     {
                        // don't pass to next level since channel was blocked early
                        return;
                     }
                  }
                  else
                  {
                     handleAllowedChannel(ctx, e, inetSocketAddress);
                  }
                  // This channel is not blocked
                  ctx.setAttachment(null);
               }
               else
               {
                  // DISCONNECTED
                  if (isBlocked(ctx) && !continues(ctx, evt))
                  {
                     // don't pass to next level since channel was blocked early
                     return;
                  }
               }
               break;
         }
      }
      if (isBlocked(ctx) && !continues(ctx, e))
      {
         // don't pass to next level since channel was blocked early
         return;
      }
      // Whatever it is, if not blocked, goes to the next level
      ctx.sendUpstream(e);
   }

   /* (non-Javadoc)
    * @see org.jboss.netty.handler.ipfilter.IpFilteringHandler#setIpFilterListener(org.jboss.netty.handler.ipfilter.IpFilterListener)
    */
   @Override
public void setIpFilterListener(IpFilterListener listener)
   {
      this.listener = listener;
   }

   /* (non-Javadoc)
    * @see org.jboss.netty.handler.ipfilter.IpFilteringHandler#removeIpFilterListener()
    */
   @Override
public void removeIpFilterListener()
   {
      this.listener = null;

   }

}
