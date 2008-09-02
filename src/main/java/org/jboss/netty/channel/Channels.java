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
package org.jboss.netty.channel;

import java.net.SocketAddress;
import java.util.Map;


/**
 * A helper class which provides various convenience methods related with
 * {@link Channel}, {@link ChannelHandler}, and {@link ChannelPipeline}.
 *
 * <h3>Factory methods</h3>
 * <p>
 * It is always recommended to use the factory methods provided by
 * {@link Channels} rather than calling the constructor of the implementation
 * types.
 * <ul>
 * <li>{@link #pipeline()}</li>
 * <li>{@link #pipeline(ChannelPipeline)}</li>
 * <li>{@link #pipelineFactory(ChannelPipeline)}</li>
 * <li>{@link #succeededFuture(Channel)}</li>
 * <li>{@link #failedFuture(Channel, Throwable)}</li>
 * <li>{@link #messageEvent(Channel, ChannelFuture, Object)}</li>
 * <li>{@link #messageEvent(Channel, ChannelFuture, Object, SocketAddress)}</li>
 * </ul>
 *
 * <h3>Upstream and downstream event generation</h3>
 * <p>
 * Various event generation methods are provided to simplify the generation of
 * upstream events and downstream events.  It is always recommended to use the
 * event generation methods provided by {@link Channels} rather than calling
 * {@link ChannelHandlerContext#sendUpstream(ChannelEvent)} or
 * {@link ChannelHandlerContext#sendDownstream(ChannelEvent)} by yourself.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class Channels {

    // pipeline factory methods

    /**
     * Creates a new {@link ChannelPipeline}.
     */
    public static ChannelPipeline pipeline() {
        return new DefaultChannelPipeline();
    }

    /**
     * Creates a new {@link ChannelPipeline} which contains the same entries
     * with the specified {@code pipeline}.  Please note that only the names
     * and the references of the {@link ChannelHandler}s will be copied; a new
     * {@link ChannelHandler} instance will never be created.
     */
    public static ChannelPipeline pipeline(ChannelPipeline pipeline) {
        ChannelPipeline newPipeline = pipeline();
        for (Map.Entry<String, ChannelHandler> e: pipeline.toMap().entrySet()) {
            newPipeline.addLast(e.getKey(), e.getValue());
        }
        return newPipeline;
    }

    /**
     * Creates a new {@link ChannelPipelineFactory} which creates a new
     * {@link ChannelPipeline} which contains the same entries with the
     * specified {@code pipeline}.  Please note that only the names and the
     * references of the {@link ChannelHandler}s will be copied; a new
     * {@link ChannelHandler} instance will never be created.
     */
    public static ChannelPipelineFactory pipelineFactory(
            final ChannelPipeline pipeline) {
        return new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                return pipeline(pipeline);
            }
        };
    }

    // future factory methods

    /**
     * Creates a new uncancellable {@link ChannelFuture} for the specified
     * {@link Channel}.
     */
    public static ChannelFuture future(Channel channel) {
        return future(channel, false);
    }

    /**
     * Creates a new {@link ChannelFuture} for the specified {@link Channel}.
     *
     * @param cancellable {@code true} if and only if the returned future
     *                    can be canceled by {@link ChannelFuture#cancel()}
     */
    public static ChannelFuture future(Channel channel, boolean cancellable) {
        return new DefaultChannelFuture(channel, cancellable);
    }

    /**
     * Creates a new {@link ChannelFuture} which is already succeeded for the
     * specified {@link Channel}.
     */
    public static ChannelFuture succeededFuture(Channel channel) {
        if (channel instanceof AbstractChannel) {
            return ((AbstractChannel) channel).getSucceededFuture();
        } else {
            return new SucceededChannelFuture(channel);
        }
    }

    /**
     * Creates a new {@link ChannelFuture} which is already failed for the
     * specified {@link Channel}.
     *
     * @param cause the cause of the failure
     */
    public static ChannelFuture failedFuture(Channel channel, Throwable cause) {
        return new FailedChannelFuture(channel, cause);
    }

    // event factory methods

    /**
     * Creates a new {@link MessageEvent}
     *
     * @param channel the channel which is associated with the event
     * @param future  the future which will be notified when a message is sent
     *                (only meaningful when the event is sent downstream)
     * @param message the received or sent message object
     *                ('received' when the event is sent upstream, and
     *                 'sent' when the event is sent downstream)
     */
    public static MessageEvent messageEvent(Channel channel, ChannelFuture future, Object message) {
        return messageEvent(channel, future, message, null);
    }

    /**
     * Creates a new {@link MessageEvent}
     *
     * @param channel the channel which is associated with the event
     * @param future  the future which will be notified when a message is sent
     *                (only meaningful when the event is sent downstream)
     * @param message the received or sent message object
     *                ('received' when the event is sent upstream, and
     *                 'sent' when the event is sent downstream)
     * @param remoteAddress the source or destination address of the message
     *                      ('source' when the event is sent upstream, and
     *                       'destination' when the event is sent downstream)
     */
    public static MessageEvent messageEvent(Channel channel, ChannelFuture future, Object message, SocketAddress remoteAddress) {
        return new DefaultMessageEvent(channel, future, message, remoteAddress);
    }

    // event emission methods

    /**
     * Fires a {@link ChannelUpstreamHandler channelOpen} event to the specified
     * {@link Channel}.  If the specified channel has a parent, a
     * {@link ChannelUpstreamHandler childChannelOpen} event will be fired too.
     */
    public static void fireChannelOpen(Channel channel) {
        if (channel.getParent() != null) {
            fireChildChannelStateChanged(channel.getParent(), channel);
        }
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.OPEN, Boolean.TRUE));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler channelOpen} event to the specified
     * {@link ChannelHandlerContext}.  Please note that this method doesn't
     * fire a {@link ChannelUpstreamHandler childChannelOpen} event unlike
     * {@link #fireChannelOpen(Channel)} method.
     */
    public static void fireChannelOpen(
            ChannelHandlerContext ctx, Channel channel) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, succeededFuture(channel),
                ChannelState.OPEN, Boolean.TRUE));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler channelBound} event to the specified
     * {@link Channel}.
     *
     * @param localAddress
     *        the local address where the specified channel is bound
     */
    public static void fireChannelBound(Channel channel, SocketAddress localAddress) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.BOUND, localAddress));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler channelBound} event to the specified
     * {@link Channel}.
     *
     * @param localAddress
     *        the local address where the specified channel is bound
     */
    public static void fireChannelBound(
            ChannelHandlerContext ctx, Channel channel, SocketAddress localAddress) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, succeededFuture(channel),
                ChannelState.BOUND, localAddress));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler channelConnected} event to the
     * specified {@link Channel}.
     *
     * @param remoteAddress
     *        the remote address where the specified channel is connected
     */
    public static void fireChannelConnected(Channel channel, SocketAddress remoteAddress) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.CONNECTED, remoteAddress));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler channelConnected} event to the
     * specified {@link ChannelHandlerContext}.
     *
     * @param remoteAddress
     *        the remote address where the specified channel is connected
     */
    public static void fireChannelConnected(
            ChannelHandlerContext ctx, Channel channel, SocketAddress remoteAddress) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, succeededFuture(channel),
                ChannelState.CONNECTED, remoteAddress));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler messageReceived} event to the
     * specified {@link Channel}.
     *
     * @param message  the received message
     */
    public static void fireMessageReceived(Channel channel, Object message) {
        fireMessageReceived(channel, message, null);
    }

    /**
     * Fires a {@link ChannelUpstreamHandler messageReceived} event to the
     * specified {@link Channel}.
     *
     * @param message        the received message
     * @param remoteAddress  the remote address where the received message
     *                       came from
     */
    public static void fireMessageReceived(Channel channel, Object message, SocketAddress remoteAddress) {
        channel.getPipeline().sendUpstream(
                new DefaultMessageEvent(
                        channel, succeededFuture(channel),
                        message, remoteAddress));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler messageReceived} event to the
     * specified {@link ChannelHandlerContext}.
     *
     * @param message  the received message
     */
    public static void fireMessageReceived(
            ChannelHandlerContext ctx, Channel channel, Object message) {
        ctx.sendUpstream(new DefaultMessageEvent(
                channel, succeededFuture(channel), message, null));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler messageReceived} event to the
     * specified {@link ChannelHandlerContext}.
     *
     * @param message        the received message
     * @param remoteAddress  the remote address where the received message
     *                       came from
     */
    public static void fireMessageReceived(
            ChannelHandlerContext ctx, Channel channel,
            Object message, SocketAddress remoteAddress) {
        ctx.sendUpstream(new DefaultMessageEvent(
                channel, succeededFuture(channel), message, remoteAddress));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler channelInterestChanged} event to the
     * specified {@link Channel}.
     *
     * @param interestOps the new interestOps
     */
    public static void fireChannelInterestChanged(Channel channel, int interestOps) {
        validateInterestOps(interestOps);
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.INTEREST_OPS, Integer.valueOf(interestOps)));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler channelInterestChanged} event to the
     * specified {@link ChannelHandlerContext}.
     *
     * @param interestOps the new interestOps
     */
    public static void fireChannelInterestChanged(
            ChannelHandlerContext ctx, Channel channel, int interestOps) {

        validateInterestOps(interestOps);
        ctx.sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.INTEREST_OPS, Integer.valueOf(interestOps)));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler channelDisconnected} event to the
     * specified {@link Channel}.
     */
    public static void fireChannelDisconnected(Channel channel) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.CONNECTED, null));
    }

    /**
     * Fires a {@link ChannelUpstreamHandler channelDisconnected} event to the
     * specified {@link ChannelHandlerContext}.
     */
    public static void fireChannelDisconnected(
            ChannelHandlerContext ctx, Channel channel) {
        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, succeededFuture(channel),
                ChannelState.CONNECTED, null));
    }

    public static void fireChannelUnbound(Channel channel) {
        channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                channel, succeededFuture(channel), ChannelState.BOUND, null));
    }

    public static void fireChannelUnbound(
            ChannelHandlerContext ctx, Channel channel) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, succeededFuture(channel), ChannelState.BOUND, null));
    }

    public static void fireChannelClosed(Channel channel) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.OPEN, Boolean.FALSE));
        if (channel.getParent() != null) {
            fireChildChannelStateChanged(channel.getParent(), channel);
        }
    }

    public static void fireChannelClosed(
            ChannelHandlerContext ctx, Channel channel) {
        ctx.sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.OPEN, Boolean.FALSE));
    }

    public static void fireExceptionCaught(Channel channel, Throwable cause) {
        channel.getPipeline().sendUpstream(
                new DefaultExceptionEvent(
                        channel, succeededFuture(channel), cause));
    }

    public static void fireExceptionCaught(
            ChannelHandlerContext ctx, Channel channel, Throwable cause) {
        ctx.sendUpstream(new DefaultExceptionEvent(
                channel, succeededFuture(channel), cause));
    }

    private static void fireChildChannelStateChanged(
            Channel channel, Channel childChannel) {
        channel.getPipeline().sendUpstream(
                new DefaultChildChannelStateEvent(
                        channel, succeededFuture(channel), childChannel));
    }

    public static ChannelFuture bind(Channel channel, SocketAddress localAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.BOUND, localAddress));
        return future;
    }

    public static void bind(
            ChannelHandlerContext ctx, Channel channel,
            ChannelFuture future, SocketAddress localAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        ctx.sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.BOUND, localAddress));
    }

    public static ChannelFuture connect(Channel channel, SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        ChannelFuture future = future(channel, true);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, remoteAddress));
        return future;
    }

    public static void connect(
            ChannelHandlerContext ctx, Channel channel,
            ChannelFuture future, SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        ctx.sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, remoteAddress));
    }

    public static ChannelFuture write(Channel channel, Object message) {
        return write(channel, message, null);
    }

    public static void write(
            ChannelHandlerContext ctx, Channel channel,
            ChannelFuture future, Object message) {
        write(ctx, channel, future, message, null);
    }

    public static ChannelFuture write(Channel channel, Object message, SocketAddress remoteAddress) {
        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(
                new DefaultMessageEvent(channel, future, message, remoteAddress));
        return future;
    }

    public static void write(
            ChannelHandlerContext ctx, Channel channel,
            ChannelFuture future, Object message, SocketAddress remoteAddress) {
        ctx.sendDownstream(
                new DefaultMessageEvent(channel, future, message, remoteAddress));
    }

    public static ChannelFuture setInterestOps(Channel channel, int interestOps) {
        validateInterestOps(interestOps);
        validateDownstreamInterestOps(channel, interestOps);

        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.INTEREST_OPS, Integer.valueOf(interestOps)));
        return future;
    }

    public static void setInterestOps(
            ChannelHandlerContext ctx, Channel channel,
            ChannelFuture future, int interestOps) {
        validateInterestOps(interestOps);
        validateDownstreamInterestOps(channel, interestOps);

        ctx.sendDownstream(
                new DefaultChannelStateEvent(
                        channel, future, ChannelState.INTEREST_OPS,
                        Integer.valueOf(interestOps)));
    }

    public static ChannelFuture disconnect(Channel channel) {
        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, null));
        return future;
    }

    public static void disconnect(
            ChannelHandlerContext ctx, Channel channel, ChannelFuture future) {
        ctx.sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, null));
    }

    /**
     * Sends a close request to the specified {@link Channel}.
     *
     * @param channel the channel to close
     *
     * @return the future which will be notified on closure
     */
    public static ChannelFuture close(Channel channel) {
        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.OPEN, Boolean.FALSE));
        return future;
    }

    /**
     * Sends a close request to the specified {@link ChannelHandlerContext}.
     * This method is a shortcut to the following code:
     * <pre>
     * ctx.sendDownstream(new DefaultChannelStateEvent(
     *         channel, future, ChannelState.OPEN, Boolean.FALSE));
     * </pre>
     *
     * @param ctx     the context
     * @param channel the channel to close
     * @param future  the future which will be notified on closure
     */
    public static void close(
            ChannelHandlerContext ctx, Channel channel, ChannelFuture future) {
        ctx.sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.OPEN, Boolean.FALSE));
    }

    private static void validateInterestOps(int interestOps) {
        switch (interestOps) {
        case Channel.OP_NONE:
        case Channel.OP_READ:
        case Channel.OP_WRITE:
        case Channel.OP_READ_WRITE:
            break;
        default:
            throw new IllegalArgumentException(
                    "Invalid interestOps: " + interestOps);
        }
    }

    private static void validateDownstreamInterestOps(Channel channel, int interestOps) {
        if (((channel.getInterestOps() ^ interestOps) & Channel.OP_WRITE) != 0) {
            throw new IllegalArgumentException("OP_WRITE can't be modified by user.");
        }
    }

    private Channels() {
        // Unused
    }
}
