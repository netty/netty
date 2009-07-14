/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.example.local;

import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

/**
 * @author frederic
 *
 */
public class LocalServerPipelineFactory implements ChannelPipelineFactory {
    OrderedMemoryAwareThreadPoolExecutor orderedMemoryAwareThreadPoolExecutor;
    public LocalServerPipelineFactory(OrderedMemoryAwareThreadPoolExecutor orderedMemoryAwareThreadPoolExecutor) {
        this.orderedMemoryAwareThreadPoolExecutor =
            orderedMemoryAwareThreadPoolExecutor;
    }
    public ChannelPipeline getPipeline() throws Exception {
        final ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("decoder", new StringDecoder());
        pipeline.addLast("encoder", new StringEncoder());
        pipeline.addLast("pipelineExecutor", new ExecutionHandler(
                orderedMemoryAwareThreadPoolExecutor));
        pipeline.addLast("handler", new EchoCloseServerHandler());
        return pipeline;
    }

    @ChannelPipelineCoverage("all")
    static class EchoCloseServerHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler {
        public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
                throws Exception {
            if (e instanceof MessageEvent) {
                final MessageEvent evt = (MessageEvent) e;
                String msg = (String) evt.getMessage();
                if (msg.equalsIgnoreCase("quit")) {
                    // TRY COMMENT HERE, then it works
                    Channels.close(e.getChannel());
                    return;
                }
            }
            ctx.sendUpstream(e);
        }

        public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
                throws Exception {
            if (e instanceof MessageEvent) {
                final MessageEvent evt = (MessageEvent) e;
                String msg = (String) evt.getMessage();
                if (msg.equalsIgnoreCase("quit")) {
                    // COMMENT OR NOT, NO PROBLEM
                    Channels.close(e.getChannel());
                    return;
                }
                System.err.println("SERVER:"+msg);
                // Write back
                Channels.write(e.getChannel(), msg);
            }
            ctx.sendDownstream(e);
        }
    }
}
