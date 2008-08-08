/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel;

import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.gleamynode.netty.pipeline.DefaultPipe;
import net.gleamynode.netty.pipeline.DefaultPipeline;
import net.gleamynode.netty.pipeline.Pipe;
import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.PipeHandler;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.pipeline.PipelineFactory;

public class Channels {

    public static Pipeline<ChannelEvent> newPipeline() {
        return new DefaultPipeline<ChannelEvent>();
    }

    public static Pipeline<ChannelEvent> newPipeline(Pipeline<ChannelEvent> pipeline) {
        Pipeline<ChannelEvent> newPipeline = newPipeline();
        for (Pipe<ChannelEvent> p: pipeline) {
            newPipeline.addLast(p);
        }
        return newPipeline;
    }

    public static PipelineFactory<ChannelEvent> newPipelineFactory(
            final Pipeline<ChannelEvent> pipeline) {
        return new PipelineFactory<ChannelEvent>() {
            public Pipeline<ChannelEvent> getPipeline() {
                return newPipeline(pipeline);
            }
        };
    }

    public static Pipe<ChannelEvent> newPipe(
            String name, PipeHandler<ChannelEvent> handler) {
        return new DefaultPipe<ChannelEvent>(name, handler);
    }

    public static Channel newServerChannel(
            ChannelFactory channelFactory, final SocketAddress localAddress,
            final PipelineFactory<ChannelEvent> pipelineFactory) {

        final BlockingQueue<ChannelFuture> futureQueue =
            new LinkedBlockingQueue<ChannelFuture>();

        Pipeline<ChannelEvent> bossPipeline = newPipeline();
        bossPipeline.addLast(newPipe(
                "binder", new ChannelEventHandlerAdapter() {
                    @Override
                    protected void channelOpen(
                            PipeContext<ChannelEvent> context,
                            ChannelStateEvent event) {
                        context.sendUpstream(event);
                        event.getChannel().getConfig().setPipelineFactory(pipelineFactory);
                        futureQueue.offer(event.getChannel().bind(localAddress));
                    }
                }));
        Channel channel = channelFactory.newChannel(bossPipeline);

        // Wait until the future is available.
        ChannelFuture future = null;
        do {
            try {
                future = futureQueue.poll(Integer.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Ignore
            }
        } while (future == null);

        // Wait for the future.
        future.awaitUninterruptibly();
        return channel;
    }

    public static Channel newServerChannel(
            ChannelFactory channelFactory, SocketAddress localAddress,
            Pipeline<ChannelEvent> pipeline) {

        return newServerChannel(
                channelFactory, localAddress, newPipelineFactory(pipeline));
    }

    public static ChannelFuture newClientChannel(
            ChannelFactory channelFactory, final SocketAddress remoteAddress,
            Pipeline<ChannelEvent> pipeline) {

        final BlockingQueue<ChannelFuture> futureQueue =
            new LinkedBlockingQueue<ChannelFuture>();

        pipeline = newPipeline(pipeline);
        pipeline.addFirst(newPipe(
                "connector", new ChannelEventHandlerAdapter() {
                    @Override
                    protected void channelOpen(
                            PipeContext<ChannelEvent> context,
                            ChannelStateEvent event) {
                        context.sendUpstream(event);
                        futureQueue.offer(event.getChannel().connect(remoteAddress));
                    }
                }));
        channelFactory.newChannel(pipeline);

        // Wait until the future is available.
        ChannelFuture future = null;
        do {
            try {
                future = futureQueue.poll(Integer.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Ignore
            }
        } while (future == null);

        return future;
    }

    private Channels() {
        // Unused
    }
}
