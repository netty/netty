/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.bootstrap;

import static org.jboss.netty.channel.Channels.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ChildChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.ServerChannelFactory;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * A helper class which creates a new server-side {@link Channel} and accepts
 * incoming connections.
 *
 * <h3>Only for connection oriented transports</h3>
 *
 * This bootstrap is for connection oriented transports only such as TCP/IP
 * and local transport.  Use {@link ConnectionlessBootstrap} instead for
 * connectionless transports.  Do not use this helper if you are using a
 * connectionless transport such as UDP/IP which does not accept an incoming
 * connection but receives messages by itself without creating a child channel.
 *
 * <h3>Parent channel and its children</h3>
 *
 * A parent channel is a channel which is supposed to accept incoming
 * connections.  It is created by this bootstrap's {@link ChannelFactory} via
 * {@link #bind()} and {@link #bind(SocketAddress)}.
 * <p>
 * Once successfully bound, the parent channel starts to accept incoming
 * connections, and the accepted connections become the children of the
 * parent channel.
 *
 * <h3>Configuring channels</h3>
 *
 * {@link #setOption(String, Object) Options} are used to configure both a
 * parent channel and its child channels.  To configure the child channels,
 * prepend {@code "child."} prefix to the actual option names of a child
 * channel:
 *
 * <pre>
 * {@link ServerBootstrap} b = ...;
 *
 * // Options for a parent channel
 * b.setOption("localAddress", new {@link InetSocketAddress}(8080));
 * b.setOption("reuseAddress", true);
 *
 * // Options for its children
 * b.setOption("child.tcpNoDelay", true);
 * b.setOption("child.receiveBufferSize", 1048576);
 * </pre>
 *
 * For the detailed list of available options, please refer to
 * {@link ChannelConfig} and its sub-types.
 *
 * <h3>Configuring a parent channel pipeline</h3>
 *
 * It is rare to customize the pipeline of a parent channel because what it is
 * supposed to do is very typical.  However, you might want to add a handler
 * to deal with some special needs such as degrading the process
 * <a href="http://en.wikipedia.org/wiki/User_identifier_(Unix)">UID</a> from
 * a <a href="http://en.wikipedia.org/wiki/Superuser">superuser</a> to a
 * normal user and changing the current VM security manager for better
 * security.  To support such a case,
 * the {@link #setParentHandler(ChannelHandler) parentHandler} property is
 * provided.
 *
 * <h3>Configuring a child channel pipeline</h3>
 *
 * Every channel has its own {@link ChannelPipeline} and you can configure it
 * in two ways.
 *
 * The recommended approach is to specify a {@link ChannelPipelineFactory} by
 * calling {@link #setPipelineFactory(ChannelPipelineFactory)}.
 *
 * <pre>
 * {@link ServerBootstrap} b = ...;
 * b.setPipelineFactory(new MyPipelineFactory());
 *
 * public class MyPipelineFactory implements {@link ChannelPipelineFactory} {
 *   public {@link ChannelPipeline} getPipeline() throws Exception {
 *     // Create and configure a new pipeline for a new channel.
 *     {@link ChannelPipeline} p = {@link Channels}.pipeline();
 *     p.addLast("encoder", new EncodingHandler());
 *     p.addLast("decoder", new DecodingHandler());
 *     p.addLast("logic",   new LogicHandler());
 *     return p;
 *   }
 * }
 * </pre>

 * <p>
 * The alternative approach, which works only in a certain situation, is to use
 * the default pipeline and let the bootstrap to shallow-copy the default
 * pipeline for each new channel:
 *
 * <pre>
 * {@link ServerBootstrap} b = ...;
 * {@link ChannelPipeline} p = b.getPipeline();
 *
 * // Add handlers to the default pipeline.
 * p.addLast("encoder", new EncodingHandler());
 * p.addLast("decoder", new DecodingHandler());
 * p.addLast("logic",   new LogicHandler());
 * </pre>
 *
 * Please note 'shallow-copy' here means that the added {@link ChannelHandler}s
 * are not cloned but only their references are added to the new pipeline.
 * Therefore, you cannot use this approach if you are going to open more than
 * one {@link Channel}s or run a server that accepts incoming connections to
 * create its child channels.
 *
 * <h3>Applying different settings for different {@link Channel}s</h3>
 *
 * {@link ServerBootstrap} is just a helper class.  It neither allocates nor
 * manages any resources.  What manages the resources is the
 * {@link ChannelFactory} implementation you specified in the constructor of
 * {@link ServerBootstrap}.  Therefore, it is OK to create as many
 * {@link ServerBootstrap} instances as you want with the same
 * {@link ChannelFactory} to apply different settings for different
 * {@link Channel}s.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2344 $, $Date: 2010-07-07 16:55:37 +0900 (Wed, 07 Jul 2010) $
 *
 * @apiviz.landmark
 */
public class ServerBootstrap extends Bootstrap {

    private volatile ChannelHandler parentHandler;

    /**
     * Creates a new instance with no {@link ChannelFactory} set.
     * {@link #setFactory(ChannelFactory)} must be called before any I/O
     * operation is requested.
     */
    public ServerBootstrap() {
        super();
    }

    /**
     * Creates a new instance with the specified initial {@link ChannelFactory}.
     */
    public ServerBootstrap(ChannelFactory channelFactory) {
        super(channelFactory);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException
     *         if the specified {@code factory} is not a
     *         {@link ServerChannelFactory}
     */
    @Override
    public void setFactory(ChannelFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        if (!(factory instanceof ServerChannelFactory)) {
            throw new IllegalArgumentException(
                    "factory must be a " +
                    ServerChannelFactory.class.getSimpleName() + ": " +
                    factory.getClass());
        }
        super.setFactory(factory);
    }

    /**
     * Returns an optional {@link ChannelHandler} which intercepts an event
     * of a newly bound server-side channel which accepts incoming connections.
     *
     * @return the parent channel handler.
     *         {@code null} if no parent channel handler is set.
     */
    public ChannelHandler getParentHandler() {
        return parentHandler;
    }

    /**
     * Sets an optional {@link ChannelHandler} which intercepts an event of
     * a newly bound server-side channel which accepts incoming connections.
     *
     * @param parentHandler
     *        the parent channel handler.
     *        {@code null} to unset the current parent channel handler.
     */
    public void setParentHandler(ChannelHandler parentHandler) {
        this.parentHandler = parentHandler;
    }

    /**
     * Creates a new channel which is bound to the local address which was
     * specified in the current {@code "localAddress"} option.  This method is
     * similar to the following code:
     *
     * <pre>
     * {@link ServerBootstrap} b = ...;
     * b.bind(b.getOption("localAddress"));
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

        ChannelHandler binder = new Binder(localAddress, futureQueue);
        ChannelHandler parentHandler = getParentHandler();

        ChannelPipeline bossPipeline = pipeline();
        bossPipeline.addLast("binder", binder);
        if (parentHandler != null) {
            bossPipeline.addLast("userHandler", parentHandler);
        }

        Channel channel = getFactory().newChannel(bossPipeline);

        // Wait until the future is available.
        ChannelFuture future = null;
        boolean interrupted = false;
        do {
            try {
                future = futureQueue.poll(Integer.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        } while (future == null);

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        // Wait for the future.
        future.awaitUninterruptibly();
        if (!future.isSuccess()) {
            future.getChannel().close().awaitUninterruptibly();
            throw new ChannelException("Failed to bind to: " + localAddress, future.getCause());
        }

        return channel;
    }

    private final class Binder extends SimpleChannelUpstreamHandler {

        private final SocketAddress localAddress;
        private final BlockingQueue<ChannelFuture> futureQueue;
        private final Map<String, Object> childOptions =
            new HashMap<String, Object>();

        Binder(SocketAddress localAddress, BlockingQueue<ChannelFuture> futureQueue) {
            this.localAddress = localAddress;
            this.futureQueue = futureQueue;
        }

        @Override
        public void channelOpen(
                ChannelHandlerContext ctx,
                ChannelStateEvent evt) {

            try {
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
            } finally {
                ctx.sendUpstream(evt);
            }

            boolean finished = futureQueue.offer(evt.getChannel().bind(localAddress));
            assert finished;
        }

        @Override
        public void childChannelOpen(
                ChannelHandlerContext ctx,
                ChildChannelStateEvent e) throws Exception {
            // Apply child options.
            e.getChildChannel().getConfig().setOptions(childOptions);
            ctx.sendUpstream(e);
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
