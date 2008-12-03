/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
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
package org.jboss.netty.handler.codec.embedder;

import static org.jboss.netty.channel.Channels.*;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
abstract class AbstractCodecEmbedder<T> implements CodecEmbedder<T> {

    final EmbeddedChannelHandlerContext context;

    AbstractCodecEmbedder(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        this.context = new EmbeddedChannelHandlerContext(handler);

        // Fire the typical initial events.
        Channel channel = context.getChannel();
        fireChannelOpen(context, channel);
        fireChannelBound(context, channel, channel.getLocalAddress());
        fireChannelConnected(context, channel, channel.getRemoteAddress());
    }

    public boolean finish() {
        Channel channel = context.getChannel();
        fireChannelDisconnected(context, channel);
        fireChannelUnbound(context, channel);
        fireChannelClosed(context, channel);
        return context.productQueue.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public T poll() {
        return (T) context.productQueue.poll();
    }

    @SuppressWarnings("unchecked")
    public T peek() {
        return (T) context.productQueue.peek();
    }
}
