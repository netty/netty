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
package org.jboss.netty.bootstrap;

import static org.jboss.netty.channel.Channels.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * A helper class which creates a new client-side {@link Channel} and make a
 * connection attempt.
 *
 * <h3>Configuring a channel</h3>
 *
 * {@link #setOption(String, Object) Options} are used to configure a channel:
 *
 * <pre>
 * ClientBootstrap b = ...;
 *
 * // Options for a new channel
 * b.setOption("remoteAddress", new {@link InetSocketAddress}("example.com", 8080));
 * b.setOption("tcpNoDelay", true);
 * b.setOption("receiveBufferSize", 1048576);
 * </pre>
 *
 * <h3>Configuring a channel pipeline</h3>
 *
 * Every channel has its own {@link ChannelPipeline} and you can configure it
 * in two ways.
 *
 * {@linkplain #setPipeline(ChannelPipeline) The first approach} is to use
 * the default pipeline and let the bootstrap to clone it for each new channel:
 *
 * <pre>
 * ClientBootstrap b = ...;
 * {@link ChannelPipeline} p = b.getPipeline();
 *
 * // Add handlers to the pipeline.
 * p.addLast("encoder", new EncodingHandler());
 * p.addLast("decoder", new DecodingHandler());
 * p.addLast("logic",   new LogicHandler());
 * </pre>
 *
 * {@linkplain #setPipelineFactory(ChannelPipelineFactory) The second approach}
 * is to specify a {@link ChannelPipelineFactory} by yourself and have full
 * control over how a new pipeline is created, at the cost of more complexity:
 *
 * <pre>
 * ClientBootstrap b = ...;
 * b.setPipelineFactory(new MyPipelineFactory());
 *
 * public class MyPipelineFactory implements {@link ChannelPipelineFactory} {
 *   // Create a new pipeline for a new channel and configure it here ...
 * }
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class ClientBootstrap extends Bootstrap {

    /**
     * Creates a new instance with no {@link ChannelFactory} set.
     * {@link #setFactory(ChannelFactory)} must be called before any I/O
     * operation is requested.
     */
    public ClientBootstrap() {
        super();
    }

    /**
     * Creates a new instance with the specified initial {@link ChannelFactory}.
     */
    public ClientBootstrap(ChannelFactory channelFactory) {
        super(channelFactory);
    }

    /**
     * Attempts a new connection with the current {@code "remoteAddress"} and
     * {@code "localAddress"} option.  If the {@code "localAddress"} option is
     * not set, the local address of a new channel is determined automatically.
     * This method is similar to the following code:
     *
     * <pre>
     * ClientBootstrap b = ...;
     * b.connect(b.getOption("remoteAddress"), b.getOption("localAddress"));
     * </pre>
     *
     * @return a future object which notifies when this connection attempt
     *         succeeds or fails
     *
     * @throws IllegalStateException
     *         if {@code "remoteAddress"} option was not set
     * @throws ClassCastException
     *         if {@code "remoteAddress"} or {@code "localAddress"} option's
     *            value is neither a {@link SocketAddress} nor {@code null}
     * @throws ChannelPipelineException
     *         if this bootstrap's {@link #setPipelineFactory(ChannelPipelineFactory) pipelineFactory}
     *            failed to create a new {@link ChannelPipeline}
     */
    public ChannelFuture connect() {
        SocketAddress remoteAddress = (SocketAddress) getOption("remoteAddress");
        if (remoteAddress == null) {
            throw new IllegalStateException("remoteAddress option is not set.");
        }
        return connect(remoteAddress);
    }

    /**
     * Attempts a new connection with the specified {@code remoteAddress} and
     * the current {@code "localAddress"} option. If the {@code "localAddress"}
     * option is not set, the local address of a new channel is determined
     * automatically.  This method is identical with the following code:
     *
     * <pre>
     * ClientBootstrap b = ...;
     * b.connect(remoteAddress, b.getOption("localAddress"));
     * </pre>
     *
     * @return a future object which notifies when this connection attempt
     *         succeeds or fails
     *
     * @throws ClassCastException
     *         if {@code "localAddress"} option's value is
     *            neither a {@link SocketAddress} nor {@code null}
     * @throws ChannelPipelineException
     *         if this bootstrap's {@link #setPipelineFactory(ChannelPipelineFactory) pipelineFactory}
     *            failed to create a new {@link ChannelPipeline}
     */
    public ChannelFuture connect(SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remotedAddress");
        }
        SocketAddress localAddress = (SocketAddress) getOption("localAddress");
        return connect(remoteAddress, localAddress);
    }

    /**
     * Attempts a new connection with the specified {@code remoteAddress} and
     * the specified {@code localAddress}.  If the specified local address is
     * {@code null}, the local address of a new channel is determined
     * automatically.
     *
     * @return a future object which notifies when this connection attempt
     *         succeeds or fails
     *
     * @throws ChannelPipelineException
     *         if this bootstrap's {@link #setPipelineFactory(ChannelPipelineFactory) pipelineFactory}
     *            failed to create a new {@link ChannelPipeline}
     */
    public ChannelFuture connect(final SocketAddress remoteAddress, final SocketAddress localAddress) {

        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }

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

        pipeline.remove("connector");

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
