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
package net.gleamynode.netty.bootstrap;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelException;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ChannelUtil;
import net.gleamynode.netty.channel.ChildChannelStateEvent;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.pipeline.PipelineCoverage;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ServerBootstrap extends Bootstrap {

    public ServerBootstrap() {
        super();
    }

    public ServerBootstrap(ChannelFactory channelFactory) {
        super(channelFactory);
    }

    public Channel bind() {
        SocketAddress localAddress = (SocketAddress) getOption("localAddress");
        if (localAddress == null) {
            throw new IllegalStateException("localAddress option is not set.");
        }
        return bind(localAddress);
    }

    public Channel bind(final SocketAddress localAddress) {
        final BlockingQueue<ChannelFuture> futureQueue =
            new LinkedBlockingQueue<ChannelFuture>();

        Pipeline<ChannelEvent> bossPipeline = ChannelUtil.newPipeline();
        bossPipeline.addLast("binder", new Binder(localAddress, futureQueue));

        Channel channel = getFactory().newChannel(bossPipeline);

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
        if (!future.isSuccess()) {
            future.getChannel().close().awaitUninterruptibly();
            throw new ChannelException("Failed to bind to: " + localAddress, future.getCause());
        }

        return channel;
    }

    @PipelineCoverage("one")
    private final class Binder extends ChannelEventHandlerAdapter {

        private final SocketAddress localAddress;
        private final BlockingQueue<ChannelFuture> futureQueue;
        private final Map<String, Object> childOptions =
            new HashMap<String, Object>();

        Binder(SocketAddress localAddress, BlockingQueue<ChannelFuture> futureQueue) {
            this.localAddress = localAddress;
            this.futureQueue = futureQueue;
        }

        @Override
        protected void channelOpen(
                PipeContext<ChannelEvent> ctx,
                ChannelStateEvent evt) {
            evt.getChannel().getConfig().setPipelineFactory(getPipelineFactory());

            // Split options into two categories: parent and child.
            Map<String, Object> allOptions = getOptions();
            Map<String, Object> parentOptions = new HashMap<String, Object>();
            for (Entry<String, Object> e: allOptions.entrySet()) {
                if (e.getKey().startsWith("child.")) {
                    childOptions.put(
                            e.getKey().substring(6),
                            e.getValue());
                } else if (!e.getKey().equals("pipelineFactory")) {
                    parentOptions.put(e.getKey(), e.getValue());
                }
            }

            // Apply parent options.
            evt.getChannel().getConfig().setOptions(parentOptions);

            futureQueue.offer(evt.getChannel().bind(localAddress));
            ctx.sendUpstream(evt);
        }

        @Override
        protected void childChannelOpen(
                PipeContext<ChannelEvent> ctx,
                ChildChannelStateEvent e) throws Exception {
            // Apply child options.
            e.getChildChannel().getConfig().setOptions(childOptions);
            ctx.sendUpstream(e);
        }

        @Override
        protected void exceptionCaught(
                PipeContext<ChannelEvent> ctx, ExceptionEvent e)
                throws Exception {
            ctx.sendUpstream(e);
        }
    }
}
