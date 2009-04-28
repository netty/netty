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
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * A helper class which creates a new server-side {@link Channel} for a
 * connectionless transport.
 *
 * <h3>Only for connectionless transports</h3>
 *
 * Use {@link ServerBootstrap} instead for connection oriented transports.
 * Do not use this helper if you are using a connection oriented transport such
 * as TCP/IP and local transport which accepts an incoming connection and lets
 * the accepted child channels handle received messages.
 *
 * <h3>Configuring channels</h3>
 *
 * {@link #setOption(String, Object) Options} are used to configure a channel:
 *
 * <pre>
 * ConnectionlessBootstrap b = ...;
 *
 * // Options for a new channel
 * b.setOption("localAddress", new {@link InetSocketAddress}(8080));
 * b.setOption("tcpNoDelay", true);
 * b.setOption("receiveBufferSize", 1048576);
 * </pre>
 *
 * For the detailed list of available options, please refer to
 * {@link ChannelConfig} and its sub-types
 *
 * <h3>Configuring a channel pipeline</h3>
 *
 * Every channel has its own {@link ChannelPipeline} and you can configure it
 * in two ways.
 * <p>
 * {@linkplain #setPipeline(ChannelPipeline) The first approach} is to use
 * the default pipeline and let the bootstrap to shallow-copy the default
 * pipeline for each new channel:
 *
 * <pre>
 * ConnectionlessBootstrap b = ...;
 * {@link ChannelPipeline} p = b.getPipeline();
 *
 * // Add handlers to the pipeline.
 * p.addLast("encoder", new EncodingHandler());
 * p.addLast("decoder", new DecodingHandler());
 * p.addLast("logic",   new LogicHandler());
 * </pre>
 *
 * Please note 'shallow-copy' here means that the added {@link ChannelHandler}s
 * are not cloned but only their references are added to the new pipeline.
 * Therefore, you have to choose the second approach if you are going to open
 * more than one {@link Channel} whose {@link ChannelPipeline} contains any
 * {@link ChannelHandler} whose {@link ChannelPipelineCoverage} is {@code "one"}.
 *
 * <p>
 * {@linkplain #setPipelineFactory(ChannelPipelineFactory) The second approach}
 * is to specify a {@link ChannelPipelineFactory} by yourself and have full
 * control over how a new pipeline is created.  This approach is more complex:
 *
 * <pre>
 * ConnectionlessBootstrap b = ...;
 * b.setPipelineFactory(new MyPipelineFactory());
 *
 * public class MyPipelineFactory implements {@link ChannelPipelineFactory} {
 *   // Create a new pipeline for a new channel and configure it here ...
 * }
 * </pre>
 *
 * <h3>Applying different settings for different {@link Channel}s</h3>
 *
 * {@link ConnectionlessBootstrap} is just a helper class.  It neither
 * allocates nor manages any resources.  What manages the resources is the
 * {@link ChannelFactory} implementation you specified in the constructor of
 * {@link ConnectionlessBootstrap}.  Therefore, it is OK to create as
 * many {@link ConnectionlessBootstrap} instances as you want to apply
 * different settings for different {@link Channel}s.
 *
 * TODO: Show how to shut down a service.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class ConnectionlessBootstrap extends Bootstrap {

    /**
     * Creates a new instance with no {@link ChannelFactory} set.
     * {@link #setFactory(ChannelFactory)} must be called before any I/O
     * operation is requested.
     */
    public ConnectionlessBootstrap() {
        super();
    }

    /**
     * Creates a new instance with the specified initial {@link ChannelFactory}.
     */
    public ConnectionlessBootstrap(ChannelFactory channelFactory) {
        super(channelFactory);
    }

    /**
     * Creates a new channel which is bound to the local address which was
     * specified in the current {@code "localAddress"} option.  This method is
     * similar to the following code:
     *
     * <pre>
     * ServerBootstrap b = ...;
     * b.connect(b.getOption("localAddress"));
     * </pre>
     *
     * @return a new bound channel which accepts incoming connections
     *
     * @throws IllegalStateException
     *         if {@code "localAddress"} option was not set
     * @throws ClassCastException
     *         if {@code "localAddress"} option's value is
     *         neither a {@link SocketAddress} nor {@code null}
     * @throws ChannelException
     *         if failed to create a new channel and
     *                      bind it to the local address
     */
    public Channel bind() {
        SocketAddress localAddress = (SocketAddress) getOption("localAddress");
        if (localAddress == null) {
            throw new IllegalStateException("localAddress option is not set.");
        }
        return bind(localAddress);
    }

    /**
     * Creates a new channel which is bound to the specified local address.
     *
     * @return a new bound channel which accepts incoming connections
     *
     * @throws ChannelException
     *         if failed to create a new channel and
     *                      bind it to the local address
     */
    public Channel bind(final SocketAddress localAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }

        final BlockingQueue<ChannelFuture> futureQueue =
            new LinkedBlockingQueue<ChannelFuture>();

        ChannelPipeline pipeline;
        try {
            pipeline = getPipelineFactory().getPipeline();
        } catch (Exception e) {
            throw new ChannelPipelineException("Failed to initialize a pipeline.", e);
        }

        pipeline.addFirst("binder", new ConnectionlessBinder(localAddress, futureQueue));

        Channel channel = getFactory().newChannel(pipeline);

        // Wait until the future is available.
        ChannelFuture future = null;
        do {
            try {
                future = futureQueue.poll(Integer.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Ignore
            }
        } while (future == null);

        pipeline.remove("binder");

        // Wait for the future.
        future.awaitUninterruptibly();
        if (!future.isSuccess()) {
            future.getChannel().close().awaitUninterruptibly();
            throw new ChannelException("Failed to bind to: " + localAddress, future.getCause());
        }

        return channel;
    }

    /**
     * Creates a new connected channel with the current {@code "remoteAddress"}
     * and {@code "localAddress"} option.  If the {@code "localAddress"} option
     * is not set, the local address of a new channel is determined
     * automatically. This method is similar to the following code:
     *
     * <pre>
     * ConnectionlessBootstrap b = ...;
     * b.connect(b.getOption("remoteAddress"), b.getOption("localAddress"));
     * </pre>
     *
     * @return a future object which notifies when the creation of the connected
     *         channel succeeds or fails
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
     * Creates a new connected channel with the specified
     * {@code "remoteAddress"} and the current {@code "localAddress"} option.
     * If the {@code "localAddress"} option is not set, the local address of
     * a new channel is determined automatically.  This method is identical
     * with the following code:
     *
     * <pre>
     * ClientBootstrap b = ...;
     * b.connect(remoteAddress, b.getOption("localAddress"));
     * </pre>
     *
     * @return a future object which notifies when the creation of the connected
     *         channel succeeds or fails
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
     * Creates a new connected channel with the specified
     * {@code "remoteAddress"} and the specified {@code "localAddress"}.
     * If the specified local address is {@code null}, the local address of a
     * new channel is determined automatically.
     *
     * @return a future object which notifies when the creation of the connected
     *         channel succeeds or fails
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

        pipeline.addFirst(
                "connector", new ClientBootstrap.Connector(
                        this, remoteAddress, localAddress, futureQueue));

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
    private final class ConnectionlessBinder extends SimpleChannelUpstreamHandler {
    
        private final SocketAddress localAddress;
        private final BlockingQueue<ChannelFuture> futureQueue;
    
        ConnectionlessBinder(SocketAddress localAddress, BlockingQueue<ChannelFuture> futureQueue) {
            this.localAddress = localAddress;
            this.futureQueue = futureQueue;
        }
    
        @Override
        public void channelOpen(
                ChannelHandlerContext ctx,
                ChannelStateEvent evt) {
            evt.getChannel().getConfig().setPipelineFactory(getPipelineFactory());
    
            // Apply options.
            evt.getChannel().getConfig().setOptions(getOptions());
    
            boolean finished = futureQueue.offer(evt.getChannel().bind(localAddress));
            assert finished;
            ctx.sendUpstream(evt);
        }
    
        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            boolean finished = futureQueue.offer(failedFuture(e.getChannel(), e.getCause()));
            assert finished;
            ctx.sendUpstream(e);
        }
    }
}
