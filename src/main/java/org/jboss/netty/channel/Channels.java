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
     * Creates a new non-cancellable {@link ChannelFuture} for the specified
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
     * Creates a new {@link ChannelFuture} which has failed already for the
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
     * Sends a {@code "channelOpen"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.  If the specified channel has a parent,
     * a {@code "childChannelOpen"} event will be sent, too.
     */
    public static void fireChannelOpen(Channel channel) {
        if (channel.getParent() != null) {
            fireChildChannelStateChanged(channel.getParent(), channel);
        }
        notifyState(channel);
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.OPEN, Boolean.TRUE));
    }

    /**
     * Sends a {@code "channelOpen"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.  Please note that
     * this method does not send a {@code "childChannelOpen"} event unlike
     * {@link #fireChannelOpen(Channel)} method.
     */
    public static void fireChannelOpen(ChannelHandlerContext ctx) {
        ctx.sendUpstream(new DefaultChannelStateEvent(
                ctx.getChannel(), succeededFuture(ctx.getChannel()),
                ChannelState.OPEN, Boolean.TRUE));
    }

    /**
     * @deprecated Use {@link #fireChannelOpen(ChannelHandlerContext)} instead.
     *
     * Sends a {@code "channelOpen"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.  Please note that
     * this method does not send a {@code "childChannelOpen"} event unlike
     * {@link #fireChannelOpen(Channel)} method.
     */
    @Deprecated
    public static void fireChannelOpen(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel) {
        fireChannelOpen(ctx);
    }

    /**
     * Sends a {@code "channelBound"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
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
     * Sends a {@code "channelBound"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param localAddress
     *        the local address where the specified channel is bound
     */
    public static void fireChannelBound(ChannelHandlerContext ctx, SocketAddress localAddress) {
        ctx.sendUpstream(new DefaultChannelStateEvent(
                ctx.getChannel(), succeededFuture(ctx.getChannel()),
                ChannelState.BOUND, localAddress));
    }

    /**
     * @deprecated Use {@link #fireChannelBound(ChannelHandlerContext, SocketAddress)} instead.
     *
     * Sends a {@code "channelBound"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param localAddress
     *        the local address where the specified channel is bound
     */
    @Deprecated
    public static void fireChannelBound(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            SocketAddress localAddress) {
        fireChannelBound(ctx, localAddress);
    }

    /**
     * Sends a {@code "channelConnected"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
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
     * Sends a {@code "channelConnected"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param remoteAddress
     *        the remote address where the specified channel is connected
     */
    public static void fireChannelConnected(ChannelHandlerContext ctx, SocketAddress remoteAddress) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                ctx.getChannel(), succeededFuture(ctx.getChannel()),
                ChannelState.CONNECTED, remoteAddress));
    }

    /**
     * @deprecated Use {@link #fireChannelConnected(ChannelHandlerContext, SocketAddress)} instead.
     *
     * Sends a {@code "channelConnected"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param remoteAddress
     *        the remote address where the specified channel is connected
     */
    @Deprecated
    public static void fireChannelConnected(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            SocketAddress remoteAddress) {
        fireChannelConnected(ctx, remoteAddress);
    }

    /**
     * Sends a {@code "messageReceived"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param message  the received message
     */
    public static void fireMessageReceived(Channel channel, Object message) {
        fireMessageReceived(channel, message, null);
    }

    /**
     * Sends a {@code "messageReceived"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel} belongs.
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
     * Sends a {@code "messageReceived"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param message  the received message
     */
    public static void fireMessageReceived(ChannelHandlerContext ctx, Object message) {
        ctx.sendUpstream(new DefaultMessageEvent(
                ctx.getChannel(), succeededFuture(ctx.getChannel()), message, null));
    }

    /**
     * @deprecated Use {@link #fireMessageReceived(ChannelHandlerContext, Object)} instead.
     *
     * Sends a {@code "messageReceived"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param message  the received message
     */
    @Deprecated
    public static void fireMessageReceived(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            Object message) {
        fireMessageReceived(ctx, message);
    }

    /**
     * Sends a {@code "messageReceived"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param message        the received message
     * @param remoteAddress  the remote address where the received message
     *                       came from
     */
    public static void fireMessageReceived(
            ChannelHandlerContext ctx, Object message, SocketAddress remoteAddress) {
        ctx.sendUpstream(new DefaultMessageEvent(
                ctx.getChannel(), succeededFuture(ctx.getChannel()), message, remoteAddress));
    }

    /**
     * @deprecated Use {@link #fireMessageReceived(ChannelHandlerContext, Object, SocketAddress)} instead.
     *
     * Sends a {@code "messageReceived"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param message        the received message
     * @param remoteAddress  the remote address where the received message
     *                       came from
     */
    @Deprecated
    public static void fireMessageReceived(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            Object message, SocketAddress remoteAddress) {
        fireMessageReceived(ctx, message, remoteAddress);
    }

    /**
     * Sends a {@code "channelInterestChanged"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireChannelInterestChanged(Channel channel) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.INTEREST_OPS, Channel.OP_READ));
    }

    /**
     * @deprecated Use {@link #fireChannelInterestChanged(Channel)} instead.
     *
     * Sends a {@code "channelInterestChanged"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param interestOps the new interestOps
     */
    @Deprecated
    public static void fireChannelInterestChanged(
            Channel channel, @SuppressWarnings("unused") int interestOps) {
        fireChannelInterestChanged(channel);
    }

    /**
     * Sends a {@code "channelInterestChanged"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     */
    public static void fireChannelInterestChanged(
            ChannelHandlerContext ctx) {

        ctx.sendUpstream(
                new DefaultChannelStateEvent(
                        ctx.getChannel(), succeededFuture(ctx.getChannel()),
                        ChannelState.INTEREST_OPS, Channel.OP_READ));
    }

    /**
     * @deprecated Use {@link #fireChannelInterestChanged(ChannelHandlerContext)} instead.
     *
     * Sends a {@code "channelInterestChanged"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param interestOps the new interestOps
     */
    @Deprecated
    public static void fireChannelInterestChanged(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            @SuppressWarnings("unused") int interestOps) {

        fireChannelInterestChanged(ctx);
    }

    /**
     * Sends a {@code "channelDisconnected"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireChannelDisconnected(Channel channel) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.CONNECTED, null));
    }

    /**
     * Sends a {@code "channelDisconnected"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     */
    public static void fireChannelDisconnected(ChannelHandlerContext ctx) {
        ctx.sendUpstream(new DefaultChannelStateEvent(
                ctx.getChannel(), succeededFuture(ctx.getChannel()),
                ChannelState.CONNECTED, null));
    }

    /**
     * @deprecated Use {@link #fireChannelDisconnected(ChannelHandlerContext)} instead.
     *
     * Sends a {@code "channelDisconnected"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     */
    @Deprecated
    public static void fireChannelDisconnected(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel) {
        fireChannelDisconnected(ctx);
    }

    /**
     * Sends a {@code "channelUnbound"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireChannelUnbound(Channel channel) {
        channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                channel, succeededFuture(channel), ChannelState.BOUND, null));
    }

    /**
     * Sends a {@code "channelUnbound"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     */
    public static void fireChannelUnbound(ChannelHandlerContext ctx) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                ctx.getChannel(), succeededFuture(ctx.getChannel()), ChannelState.BOUND, null));
    }

    /**
     * @deprecated Use {@link #fireChannelUnbound(ChannelHandlerContext)} instead.
     *
     * Sends a {@code "channelUnbound"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     */
    @Deprecated
    public static void fireChannelUnbound(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel) {
        fireChannelUnbound(ctx);
    }

    /**
     * Sends a {@code "channelClosed"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireChannelClosed(Channel channel) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, succeededFuture(channel),
                        ChannelState.OPEN, Boolean.FALSE));
        notifyState(channel);
        if (channel.getParent() != null) {
            fireChildChannelStateChanged(channel.getParent(), channel);
        }
    }

    /**
     * Sends a {@code "channelClosed"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     */
    public static void fireChannelClosed(ChannelHandlerContext ctx) {
        ctx.sendUpstream(
                new DefaultChannelStateEvent(
                        ctx.getChannel(), succeededFuture(ctx.getChannel()),
                        ChannelState.OPEN, Boolean.FALSE));
    }

    /**
     * @deprecated Use {@link #fireChannelClosed(ChannelHandlerContext)}.
     *
     * Sends a {@code "channelClosed"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     */
    @Deprecated
    public static void fireChannelClosed(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel) {
        fireChannelClosed(ctx);
    }

    /**
     * Sends a {@code "exceptionCaught"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireExceptionCaught(Channel channel, Throwable cause) {
        channel.getPipeline().sendUpstream(
                new DefaultExceptionEvent(
                        channel, succeededFuture(channel), cause));
    }

    /**
     * Sends a {@code "exceptionCaught"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     */
    public static void fireExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.sendUpstream(new DefaultExceptionEvent(
                ctx.getChannel(), succeededFuture(ctx.getChannel()), cause));
    }

    /**
     * @deprecated Use {@link #fireExceptionCaught(ChannelHandlerContext, Throwable)} instead.
     *
     * Sends a {@code "exceptionCaught"} event to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     */
    @Deprecated
    public static void fireExceptionCaught(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            Throwable cause) {
        fireExceptionCaught(ctx, cause);
    }

    private static void fireChildChannelStateChanged(
            Channel channel, Channel childChannel) {
        channel.getPipeline().sendUpstream(
                new DefaultChildChannelStateEvent(
                        channel, succeededFuture(channel), childChannel));
    }

    /**
     * Sends a {@code "bind"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param channel  the channel to bind
     * @param localAddress  the local address to bind to
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         bind operation is done
     */
    public static ChannelFuture bind(Channel channel, SocketAddress localAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.BOUND, localAddress));
        return future;
    }

    /**
     * Sends a {@code "bind"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param future  the future which will be notified when the bind
     *                operation is done
     * @param localAddress the local address to bind to
     */
    public static void bind(
            ChannelHandlerContext ctx, ChannelFuture future, SocketAddress localAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        ctx.sendDownstream(new DefaultChannelStateEvent(
                ctx.getChannel(), future, ChannelState.BOUND, localAddress));
    }

    /**
     * @deprecated Use {@link #bind(ChannelHandlerContext, ChannelFuture, SocketAddress)} instead.
     *
     * Sends a {@code "bind"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param channel the channel to bind
     * @param future  the future which will be notified when the bind
     *                operation is done
     * @param localAddress the local address to bind to
     */
    @Deprecated
    public static void bind(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            ChannelFuture future, SocketAddress localAddress) {
        bind(ctx, future, localAddress);
    }

    /**
     * Sends a {@code "unbind"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param future  the future which will be notified when the unbind
     *                operation is done
     */
    public static void unbind(ChannelHandlerContext ctx, ChannelFuture future) {
        ctx.sendDownstream(new DefaultChannelStateEvent(
                ctx.getChannel(), future, ChannelState.BOUND, null));
    }

    /**
     * @deprecated Use {@link #unbind(ChannelHandlerContext, ChannelFuture)} instead.
     *
     * Sends a {@code "unbind"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param channel the channel to unbind
     * @param future  the future which will be notified when the unbind
     *                operation is done
     */
    @Deprecated
    public static void unbind(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            ChannelFuture future) {
        unbind(ctx, future);
    }

    /**
     * Sends a {@code "unbind"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param channel  the channel to unbind
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         unbind operation is done
     */
    public static ChannelFuture unbind(Channel channel) {
        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.BOUND, null));
        return future;
    }

    /**
     * Sends a {@code "connect"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param channel  the channel to attempt a connection
     * @param remoteAddress  the remote address to connect to
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         connection attempt is done
     */
    public static ChannelFuture connect(Channel channel, SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        ChannelFuture future = future(channel, true);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, remoteAddress));
        return future;
    }

    /**
     * Sends a {@code "connect"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param future  the future which will be notified when the connection
     *                attempt is done
     * @param remoteAddress the remote address to connect to
     */
    public static void connect(
            ChannelHandlerContext ctx, ChannelFuture future, SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        ctx.sendDownstream(new DefaultChannelStateEvent(
                ctx.getChannel(), future, ChannelState.CONNECTED, remoteAddress));
    }

    /**
     * @deprecated Use {@link #connect(ChannelHandlerContext, ChannelFuture, SocketAddress)} instead.
     *
     * Sends a {@code "connect"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param channel the channel to attempt a connection
     * @param future  the future which will be notified when the connection
     *                attempt is done
     * @param remoteAddress the remote address to connect to
     */
    @Deprecated
    public static void connect(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            ChannelFuture future, SocketAddress remoteAddress) {
        connect(ctx, future, remoteAddress);
    }

    /**
     * Sends a {@code "write"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param channel  the channel to write a message
     * @param message  the message to write to the channel
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         write operation is done
     */
    public static ChannelFuture write(Channel channel, Object message) {
        return write(channel, message, null);
    }

    /**
     * Sends a {@code "write"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param future  the future which will be notified when the write
     *                operation is done
     */
    public static void write(
            ChannelHandlerContext ctx, ChannelFuture future, Object message) {
        write(ctx, future, message, null);
    }

    /**
     * @deprecated Use {@link #write(ChannelHandlerContext, ChannelFuture, Object)} instead.
     *
     * Sends a {@code "write"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param channel the channel to write a message
     * @param future  the future which will be notified when the write
     *                operation is done
     */
    @Deprecated
    public static void write(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            ChannelFuture future, Object message) {
        write(ctx, future, message, null);
    }

    /**
     * Sends a {@code "write"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param channel  the channel to write a message
     * @param message  the message to write to the channel
     * @param remoteAddress  the destination of the message.
     *                       {@code null} to use the default remote address
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         write operation is done
     */
    public static ChannelFuture write(Channel channel, Object message, SocketAddress remoteAddress) {
        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(
                new DefaultMessageEvent(channel, future, message, remoteAddress));
        return future;
    }

    /**
     * Sends a {@code "write"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param future  the future which will be notified when the write
     *                operation is done
     * @param message the message to write to the channel
     * @param remoteAddress  the destination of the message.
     *                       {@code null} to use the default remote address.
     */
    public static void write(
            ChannelHandlerContext ctx, ChannelFuture future,
            Object message, SocketAddress remoteAddress) {
        ctx.sendDownstream(
                new DefaultMessageEvent(ctx.getChannel(), future, message, remoteAddress));
    }

    /**
     * @deprecated Use {@link #write(ChannelHandlerContext, ChannelFuture, Object, SocketAddress)} instead.
     *
     * Sends a {@code "write"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param channel the channel to write a message
     * @param future  the future which will be notified when the write
     *                operation is done
     * @param message the message to write to the channel
     * @param remoteAddress  the destination of the message.
     *                       {@code null} to use the default remote address.
     */
    @Deprecated
    public static void write(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            ChannelFuture future, Object message, SocketAddress remoteAddress) {
        write(ctx, future, message, remoteAddress);
    }

    /**
     * Sends a {@code "setInterestOps"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param channel     the channel to change its interestOps
     * @param interestOps the new interestOps
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         interestOps is changed
     */
    public static ChannelFuture setInterestOps(Channel channel, int interestOps) {
        validateInterestOps(interestOps);
        interestOps = filterDownstreamInterestOps(interestOps);

        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.INTEREST_OPS, Integer.valueOf(interestOps)));
        return future;
    }

    /**
     * Sends a {@code "setInterestOps"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param future  the future which will be notified when the interestOps is
     *                changed.
     */
    public static void setInterestOps(
            ChannelHandlerContext ctx, ChannelFuture future, int interestOps) {
        validateInterestOps(interestOps);
        interestOps = filterDownstreamInterestOps(interestOps);

        ctx.sendDownstream(
                new DefaultChannelStateEvent(
                        ctx.getChannel(), future, ChannelState.INTEREST_OPS,
                        Integer.valueOf(interestOps)));
    }

    /**
     * @deprecated Use {@link #setInterestOps(ChannelHandlerContext, ChannelFuture, int)} instead.
     *
     * Sends a {@code "setInterestOps"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param channel the channel to change the interestOps
     * @param future  the future which will be notified when the interestOps is
     *                changed.
     */
    @Deprecated
    public static void setInterestOps(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel,
            ChannelFuture future, int interestOps) {
        setInterestOps(ctx, future, interestOps);
    }

    /**
     * Sends a {@code "disconnect"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param channel  the channel to disconnect
     *
     * @return the {@link ChannelFuture} which will be notified on disconnection
     */
    public static ChannelFuture disconnect(Channel channel) {
        ChannelFuture future = future(channel);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, null));
        return future;
    }

    /**
     * Sends a {@code "disconnect"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param future  the future which will be notified on disconnection
     */
    public static void disconnect(
            ChannelHandlerContext ctx, ChannelFuture future) {
        ctx.sendDownstream(new DefaultChannelStateEvent(
                ctx.getChannel(), future, ChannelState.CONNECTED, null));
    }

    /**
     * @deprecated Use {@link #disconnect(ChannelHandlerContext, ChannelFuture)} instead.
     *
     * Sends a {@code "disconnect"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param channel the channel to disconnect
     * @param future  the future which will be notified on disconnection
     */
    @Deprecated
    public static void disconnect(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel, ChannelFuture future) {
        disconnect(ctx, future);
    }

    /**
     * Sends a {@code "close"} request to the last
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     *
     * @param channel  the channel to close
     *
     * @return the {@link ChannelFuture} which will be notified on closure
     */
    public static ChannelFuture close(Channel channel) {
        ChannelFuture future = channel.getCloseFuture();
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.OPEN, Boolean.FALSE));
        return future;
    }

    /**
     * Sends a {@code "close"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param future  the future which will be notified on closure
     */
    public static void close(
            ChannelHandlerContext ctx, ChannelFuture future) {
        ctx.sendDownstream(new DefaultChannelStateEvent(
                ctx.getChannel(), future, ChannelState.OPEN, Boolean.FALSE));
    }

    /**
     * @deprecated Use {@link #close(ChannelHandlerContext, ChannelFuture)} instead.
     *
     * Sends a {@code "close"} request to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} where
     * the specified {@link ChannelHandlerContext} belongs.
     *
     * @param ctx     the context
     * @param channel the channel to close
     * @param future  the future which will be notified on closure
     */
    @Deprecated
    public static void close(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") Channel channel, ChannelFuture future) {
        close(ctx, future);
    }

    private static void notifyState(Channel channel) {
        ChannelFactory factory = channel.getFactory();
        if (factory instanceof AbstractChannelFactory) {
            ((AbstractChannelFactory) factory).notifyState(channel);
        }
    }

    public static void notifyInflow(Channel channel, int amount) {
        if (amount <= 0) {
            return;
        }
        ChannelFactory factory = channel.getFactory();
        if (factory instanceof AbstractChannelFactory) {
            ((AbstractChannelFactory) factory).notifyInflow(channel, amount);
        }
    }

    public static void notifyOutflow(Channel channel, int amount) {
        if (amount <= 0) {
            return;
        }
        ChannelFactory factory = channel.getFactory();
        if (factory instanceof AbstractChannelFactory) {
            ((AbstractChannelFactory) factory).notifyOutflow(channel, amount);
        }
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

    private static int filterDownstreamInterestOps(int interestOps) {
        return interestOps & ~Channel.OP_WRITE;
    }

    private Channels() {
        // Unused
    }
}
