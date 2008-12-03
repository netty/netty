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

import java.util.LinkedList;
import java.util.Queue;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
abstract class AbstractCodecEmbedder<T> implements CodecEmbedder<T> {

    private static final String NAME = "__embedded__";

    private final Channel channel;
    private final ChannelPipeline pipeline;
    final Queue<Object> productQueue = new LinkedList<Object>();

    AbstractCodecEmbedder(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        pipeline = Channels.pipeline();
        pipeline.addLast(NAME, handler);
        channel = new EmbeddedChannel(pipeline, new EmbeddedChannelSink());

        // Fire the typical initial events.
        fireChannelOpen(channel);
        fireChannelBound(channel, channel.getLocalAddress());
        fireChannelConnected(channel, channel.getRemoteAddress());
    }

    public boolean finish() {
        fireChannelDisconnected(channel);
        fireChannelUnbound(channel);
        fireChannelClosed(channel);
        return !productQueue.isEmpty();
    }

    protected final Channel getChannel() {
        return channel;
    }

    protected final boolean isEmpty() {
        return productQueue.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public final T poll() {
        return (T) productQueue.poll();
    }

    @SuppressWarnings("unchecked")
    public final T peek() {
        return (T) productQueue.peek();
    }

    private final class EmbeddedChannelSink implements ChannelSink {
        EmbeddedChannelSink() {
            super();
        }

        public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) {
            if (e instanceof MessageEvent) {
                productQueue.offer(((MessageEvent) e).getMessage());
            } else if (e instanceof ExceptionEvent) {
                throw new CodecEmbedderException(((ExceptionEvent) e).getCause());
            }

            // Swallow otherwise.
        }

        public void exceptionCaught(
                ChannelPipeline pipeline, ChannelEvent e,
                ChannelPipelineException cause) throws Exception {
            Throwable actualCause = cause.getCause();
            if (actualCause == null) {
                actualCause = cause;
            }

            throw new CodecEmbedderException(actualCause);
        }
    }
}
