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

import java.util.LinkedList;
import java.util.Queue;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
class EmbeddedChannelHandlerContext implements ChannelHandlerContext, ChannelSink {

    private static final String NAME = "__embedded__";

    private final Channel channel;
    private final ChannelHandler handler;
    private final ChannelPipeline pipeline;
    final Queue<Object> productQueue = new LinkedList<Object>();

    EmbeddedChannelHandlerContext(ChannelHandler handler) {
        this.handler = handler;
        pipeline = Channels.pipeline();
        pipeline.addLast(NAME, handler);
        channel = new EmbeddedChannel(this, pipeline);
    }

    public boolean canHandleDownstream() {
        return handler instanceof ChannelDownstreamHandler;
    }

    public boolean canHandleUpstream() {
        return handler instanceof ChannelUpstreamHandler;
    }

    public ChannelHandler getHandler() {
        return handler;
    }

    public String getName() {
        return NAME;
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelPipeline getPipeline() {
        return pipeline;
    }

    public void sendDownstream(ChannelEvent e) {
        handleEvent(e);
    }

    public void sendUpstream(ChannelEvent e) {
        handleEvent(e);
    }

    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) {
        handleEvent(e);
    }

    private void handleEvent(ChannelEvent e) {
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
