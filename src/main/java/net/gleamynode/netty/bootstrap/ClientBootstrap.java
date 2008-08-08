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
package net.gleamynode.netty.bootstrap;

import static net.gleamynode.netty.channel.Channels.*;

import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.ChannelPipelineCoverage;
import net.gleamynode.netty.channel.ChannelPipelineException;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.SimpleChannelHandler;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class ClientBootstrap extends Bootstrap {

    public ClientBootstrap() {
        super();
    }

    public ClientBootstrap(ChannelFactory channelFactory) {
        super(channelFactory);
    }

    public ChannelFuture connect() {
        SocketAddress remoteAddress = (SocketAddress) getOption("remoteAddress");
        if (remoteAddress == null) {
            throw new IllegalStateException("remoteAddress option is not set.");
        }
        SocketAddress localAddress = (SocketAddress) getOption("localAddress");
        return connect(remoteAddress, localAddress);
    }

    public ChannelFuture connect(SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remotedAddress");
        }
        return connect(remoteAddress, null);
    }

    public ChannelFuture connect(final SocketAddress remoteAddress, final SocketAddress localAddress) {

        final BlockingQueue<ChannelFuture> futureQueue =
            new LinkedBlockingQueue<ChannelFuture>();

        ChannelPipeline pipeline;
        try {
            pipeline = getPipelineFactory().getPipeline();
        } catch (Exception e) {
            throw new ChannelPipelineException("Failed to initialize a pipeline.", e);
        }

        pipeline.addFirst("connector", new Connector(remoteAddress, localAddress, futureQueue));

        getFactory().newChannel(pipeline);

        // Wait until the future is available.
        ChannelFuture future = null;
        do {
            try {
                future = futureQueue.poll(Integer.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Ignore
            }
        } while (future == null);

        pipeline.remove(pipeline.get("connector"));

        return future;
    }

    @ChannelPipelineCoverage("one")
    private final class Connector extends SimpleChannelHandler {
        private final SocketAddress localAddress;
        private final BlockingQueue<ChannelFuture> futureQueue;
        private final SocketAddress remoteAddress;
        private volatile boolean finished = false;

        Connector(SocketAddress remoteAddress,
                SocketAddress localAddress,
                BlockingQueue<ChannelFuture> futureQueue) {
            this.localAddress = localAddress;
            this.futureQueue = futureQueue;
            this.remoteAddress = remoteAddress;
        }

        @Override
        public void channelOpen(
                ChannelHandlerContext context,
                ChannelStateEvent event) {
            context.sendUpstream(event);

            // Apply options.
            event.getChannel().getConfig().setOptions(getOptions());

            // Bind or connect.
            if (localAddress != null) {
                event.getChannel().bind(localAddress);
            } else {
                futureQueue.offer(event.getChannel().connect(remoteAddress));
                finished = true;
            }
        }

        @Override
        public void channelBound(
                ChannelHandlerContext context,
                ChannelStateEvent event) {
            context.sendUpstream(event);

            // Connect if not connected yet.
            if (localAddress != null) {
                futureQueue.offer(event.getChannel().connect(remoteAddress));
                finished = true;
            }
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            ctx.sendUpstream(e);
            if (!finished) {
                e.getChannel().close();
                futureQueue.offer(failedFuture(e.getChannel(), e.getCause()));
                finished = true;
            }
        }
    }
}
