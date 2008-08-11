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

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChildChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * Helper class which helps a user create a new server-side {@link Channel}
 * and accept incoming connections.
 *
 * <h3>Parent channel and its children</h3>
 *
 * A parent channel is a channel which is supposed to accept incoming
 * connections.  It is created by this bootstrap's
 * {@link #setPipelineFactory(ChannelPipelineFactory) pipelineFactory} or
 * {@link #setPipeline(ChannelPipeline) pipeline} property via {@link #bind()}
 * and {@link #bind(SocketAddress)}.
 * <p>
 * Once successfully bound, the parent channel starts to accept incoming
 * connections, and the accepted connections becomes the children of the
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
 * ServerBootstrap b = ...;
 *
 * // Options for a parent channel
 * b.setOption("localAddress", new InetSocketAddress(8080));
 * b.setOption("reuseAddress", true);
 *
 * // Options for its children
 * b.setOption("child.tcpNoDelay", true);
 * b.setOption("child.receiveBufferSize", 1048576);
 * </pre>
 *
 * <h3>Configuring a parent channel pipeline</h3>
 *
 * It is rare to configure the pipeline of a parent channel because what it's
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
 * Every child channel has its own {@link ChannelPipeline} and you can
 * configure it in two ways.
 *
 * {@linkplain #setPipeline(ChannelPipeline) The first approach} is to use
 * the default pipeline property and let the bootstrap to clone it for each
 * new child channel:
 *
 * <pre>
 * ServerBootstrap b = ...;
 * ChannelPipeline p = b.getPipeline();
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
 * ServerBootstrap b = ...;
 * b.setPipelineFactory(new MyPipelineFactory());
 *
 * public class MyPipelineFactory implements ChannelPipelineFactory {
 *   // Create a new pipeline for a new child channel and configure it here ...
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
public class ServerBootstrap extends Bootstrap {

    private volatile ChannelHandler parentHandler;

    /**
     * Creates a new instance with no {@link ChannelFactory} set.
     * {@link #setFactory(ChannelFactory)} must be called at once before any
     * I/O operation is requested.
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
     * Returns an optional {@link ChannelHandler} which intercepts an event
     * of a new bound server-side channel which accepts incoming connections.
     *
     * @return the parent channel handler.
     *         {@code null} if no parent channel handler is set.
     */
    public ChannelHandler getParentHandler() {
        return parentHandler;
    }

    /**
     * Sets an optional {@link ChannelHandler} which intercepts an event of
     * a new bound server-side channel which accepts incoming connections.
     *
     * @param parentHandler
     *        the parent channel handler.
     *        {@code null} to unset the current parent channel handler.
     */
    public void setParentHandler(ChannelHandler parentHandler) {
        this.parentHandler = parentHandler;
    }

    /**
     * Creates a new channel which is bound to the local address which were
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
        final BlockingQueue<ChannelFuture> futureQueue =
            new LinkedBlockingQueue<ChannelFuture>();

        ChannelPipeline bossPipeline = pipeline();
        bossPipeline.addLast("binder", new Binder(localAddress, futureQueue));

        ChannelHandler parentHandler = getParentHandler();
        if (parentHandler != null) {
            bossPipeline.addLast("userHandler", parentHandler);
        }

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

    @ChannelPipelineCoverage("one")
    private final class Binder extends SimpleChannelHandler {

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
            ctx.sendUpstream(e);
        }
    }
}
