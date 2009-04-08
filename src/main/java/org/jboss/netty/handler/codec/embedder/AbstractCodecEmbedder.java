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

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public abstract class AbstractCodecEmbedder<T> implements CodecEmbedder<T> {

    private final Channel channel;
    private final ChannelPipeline pipeline;
    private final EmbeddedChannelSink sink = new EmbeddedChannelSink();

    CodecEmbedderException exception;
    final Queue<Object> productQueue = new LinkedList<Object>();

    protected AbstractCodecEmbedder(ChannelHandler... handlers) {
        pipeline = new EmbeddedChannelPipeline();
        configurePipeline(handlers);
        channel = new EmbeddedChannel(pipeline, sink);
        fireInitialEvents();
    }

    protected AbstractCodecEmbedder(ChannelBufferFactory bufferFactory, ChannelHandler... handlers) {
        this(handlers);
        getChannel().getConfig().setBufferFactory(bufferFactory);
    }

    private void fireInitialEvents() {
        // Fire the typical initial events.
        fireChannelOpen(channel);
        fireChannelBound(channel, channel.getLocalAddress());
        fireChannelConnected(channel, channel.getRemoteAddress());
    }

    private void configurePipeline(ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        if (handlers.length == 0) {
            throw new IllegalArgumentException(
                    "handlers should contain at least one " +
                    ChannelHandler.class.getSimpleName() + '.');
        }

        for (int i = 0; i < handlers.length; i ++) {
            ChannelHandler h = handlers[i];
            if (h == null) {
                throw new NullPointerException("handlers[" + i + "]");
            }
            pipeline.addLast(String.valueOf(i), handlers[i]);
        }
        pipeline.addLast("SINK", sink);
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

    @ChannelPipelineCoverage("all")
    private final class EmbeddedChannelSink implements ChannelSink, ChannelUpstreamHandler {
        EmbeddedChannelSink() {
            super();
        }

        public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) {
            handleEvent(e);
        }

        public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) {
            handleEvent(e);
        }

        private void handleEvent(ChannelEvent e) {
            if (e instanceof MessageEvent) {
                boolean offered = productQueue.offer(((MessageEvent) e).getMessage());
                assert offered;
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

    private static final class EmbeddedChannelPipeline extends DefaultChannelPipeline {

        EmbeddedChannelPipeline() {
            super();
        }

        @Override
        protected void notifyHandlerException(ChannelEvent e, Throwable t) {
            while (t instanceof ChannelPipelineException && t.getCause() != null) {
                t = t.getCause();
            }
            if (t instanceof CodecEmbedderException) {
                throw (CodecEmbedderException) t;
            } else {
                throw new CodecEmbedderException(t);
            }
        }
    }
}
