/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.bootstrap;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineException;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * A helper class which creates a new client-side {@link Channel} and makes a
 * connection attempt.
 *
 * <h3>Configuring a channel</h3>
 *
 * {@link #setOption(String, Object) Options} are used to configure a channel:
 *
 * <pre>
 * {@link ClientBootstrap} b = ...;
 *
 * // Options for a new channel
 * b.setOption("remoteAddress", new {@link InetSocketAddress}("example.com", 8080));
 * b.setOption("tcpNoDelay", true);
 * b.setOption("receiveBufferSize", 1048576);
 * </pre>
 *
 * For the detailed list of available options, please refer to
 * {@link ChannelConfig} and its sub-types.
 *
 * <h3>Configuring a channel pipeline</h3>
 *
 * Every channel has its own {@link ChannelPipeline} and you can configure it
 * in two ways.
 *
 * The recommended approach is to specify a {@link ChannelPipelineFactory} by
 * calling {@link #setPipelineFactory(ChannelPipelineFactory)}.
 *
 * <pre>
 * {@link ClientBootstrap} b = ...;
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
 * {@link ClientBootstrap} b = ...;
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
 * {@link ClientBootstrap} is just a helper class.  It neither allocates nor
 * manages any resources.  What manages the resources is the
 * {@link ChannelFactory} implementation you specified in the constructor of
 * {@link ClientBootstrap}.  Therefore, it is OK to create as many
 * {@link ClientBootstrap} instances as you want with the same
 * {@link ChannelFactory} to apply different settings for different
 * {@link Channel}s.
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
     * {@link ClientBootstrap} b = ...;
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
     * {@link ClientBootstrap} b = ...;
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
            throw new NullPointerException("remoteAddress");
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

        ChannelPipeline pipeline;
        try {
            pipeline = getPipelineFactory().getPipeline();
        } catch (Exception e) {
            throw new ChannelPipelineException("Failed to initialize a pipeline.", e);
        }

        // Set the options.
        Channel ch = getFactory().newChannel(pipeline);
        boolean success = false;
        try {
            ch.getConfig().setOptions(getOptions());
            success = true;
        } finally {
            if (!success) {
                ch.close();
            }
        }

        // Bind.
        if (localAddress != null) {
            ch.bind(localAddress);
        }

        // Connect.
        return ch.connect(remoteAddress);
    }

    /**
     * Attempts to bind a channel with the specified {@code localAddress}. later the channel can
     * be connected to a remoteAddress by calling {@link Channel#connect(SocketAddress)}.This method
     * is useful where bind and connect need to be done in separate steps.
     * <p>
     * For an instance, a user can set an attachment to the {@link Channel} via
     * {@link Channel#setAttachment(Object)} before beginning a connection attempt so that the user can access
     * the attachment once the connection is established:
     *
     * <pre>
     *  ChannelFuture bindFuture = bootstrap.bind(new InetSocketAddress("192.168.0.15", 0));
     *  Channel channel = bindFuture.getChannel();
     *  channel.setAttachment(dataObj);
     *  channel.connect(new InetSocketAddress("192.168.0.30", 8080));
     * </pre>
     *
     * The attachment can be accessed then in the handler like the following:
     *
     * <pre>
     *  public class YourHandler extends SimpleChannelUpstreamHandler {
     *      public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
     *          Object dataObject = ctx.getChannel().getAttachment();
     *      }
     *  }
     *
     * </pre>
     *
     * @return a future object which notifies when this bind attempt
     *         succeeds or fails
     *
     * @throws ChannelPipelineException
     *         if this bootstrap's {@link #setPipelineFactory(ChannelPipelineFactory) pipelineFactory}
     *            failed to create a new {@link ChannelPipeline}
     */
    public ChannelFuture bind(final SocketAddress localAddress) {

        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }

        ChannelPipeline pipeline;
        try {
            pipeline = getPipelineFactory().getPipeline();
        } catch (Exception e) {
            throw new ChannelPipelineException("Failed to initialize a pipeline.", e);
        }

        // Set the options.
        Channel ch = getFactory().newChannel(pipeline);
        boolean success = false;
        try {
            ch.getConfig().setOptions(getOptions());
            success = true;
        } finally {
            if (!success) {
                ch.close();
            }
        }

        // Bind.
        return ch.bind(localAddress);
    }
}
