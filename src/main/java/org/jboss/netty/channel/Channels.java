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
package org.jboss.netty.channel;

import java.net.SocketAddress;
import java.util.Map;

import org.jboss.netty.util.internal.ConversionUtil;


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
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @apiviz.landmark
 *
 * @version $Rev: 2210 $, $Date: 2010-03-04 08:11:39 +0900 (Thu, 04 Mar 2010) $
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
     * Creates a new {@link ChannelPipeline} which contains the specified
     * {@link ChannelHandler}s.  The names of the specified handlers are
     * generated automatically; the first handler's name is {@code "0"},
     * the second handler's name is {@code "1"}, the third handler's name is
     * {@code "2"}, and so on.
     */
    public static ChannelPipeline pipeline(ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        ChannelPipeline newPipeline = pipeline();
        for (int i = 0; i < handlers.length; i ++) {
            ChannelHandler h = handlers[i];
            if (h == null) {
                break;
            }
            newPipeline.addLast(ConversionUtil.toString(i), h);
        }
        return newPipeline;
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

    // event emission methods

    /**
     * Sends a {@code "channelOpen"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.  If the specified channel has a parent,
     * a {@code "childChannelOpen"} event will be sent, too.
     */
    public static void fireChannelOpen(Channel channel) {
        // Notify the parent handler.
        if (channel.getParent() != null) {
            fireChildChannelStateChanged(channel.getParent(), channel);
        }

        channel.getPipeline().sendUpstream(
                new UpstreamChannelStateEvent(
                        channel, ChannelState.OPEN, Boolean.TRUE));
    }

    /**
     * Sends a {@code "channelOpen"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     * <p>
     * Please note that this method does not trigger a
     * {@code "childChannelOpen"} event unlike {@link #fireChannelOpen(Channel)}
     * method.
     */
    public static void fireChannelOpen(ChannelHandlerContext ctx) {
        ctx.sendUpstream(new UpstreamChannelStateEvent(
                ctx.getChannel(), ChannelState.OPEN, Boolean.TRUE));
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
                new UpstreamChannelStateEvent(
                        channel, ChannelState.BOUND, localAddress));
    }

    /**
     * Sends a {@code "channelBound"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     *
     * @param localAddress
     *        the local address where the specified channel is bound
     */
    public static void fireChannelBound(ChannelHandlerContext ctx, SocketAddress localAddress) {
        ctx.sendUpstream(new UpstreamChannelStateEvent(
                ctx.getChannel(), ChannelState.BOUND, localAddress));
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
                new UpstreamChannelStateEvent(
                        channel, ChannelState.CONNECTED, remoteAddress));
    }

    /**
     * Sends a {@code "channelConnected"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     *
     * @param remoteAddress
     *        the remote address where the specified channel is connected
     */
    public static void fireChannelConnected(ChannelHandlerContext ctx, SocketAddress remoteAddress) {

        ctx.sendUpstream(new UpstreamChannelStateEvent(
                ctx.getChannel(), ChannelState.CONNECTED, remoteAddress));
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
                new UpstreamMessageEvent(channel, message, remoteAddress));
    }

    /**
     * Sends a {@code "messageReceived"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     *
     * @param message  the received message
     */
    public static void fireMessageReceived(ChannelHandlerContext ctx, Object message) {
        ctx.sendUpstream(new UpstreamMessageEvent(ctx.getChannel(), message, null));
    }

    /**
     * Sends a {@code "messageReceived"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     *
     * @param message        the received message
     * @param remoteAddress  the remote address where the received message
     *                       came from
     */
    public static void fireMessageReceived(
            ChannelHandlerContext ctx, Object message, SocketAddress remoteAddress) {
        ctx.sendUpstream(new UpstreamMessageEvent(
                ctx.getChannel(), message, remoteAddress));
    }

    /**
     * Sends a {@code "writeComplete"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireWriteComplete(Channel channel, long amount) {
        if (amount == 0) {
            return;
        }

        channel.getPipeline().sendUpstream(
                new DefaultWriteCompletionEvent(channel, amount));
    }

    /**
     * Sends a {@code "writeComplete"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     */
    public static void fireWriteComplete(ChannelHandlerContext ctx, long amount) {
        ctx.sendUpstream(new DefaultWriteCompletionEvent(ctx.getChannel(), amount));
    }
    /**
     * Sends a {@code "channelInterestChanged"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireChannelInterestChanged(Channel channel) {
        channel.getPipeline().sendUpstream(
                new UpstreamChannelStateEvent(
                        channel, ChannelState.INTEREST_OPS, Channel.OP_READ));
    }

    /**
     * Sends a {@code "channelInterestChanged"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     */
    public static void fireChannelInterestChanged(
            ChannelHandlerContext ctx) {

        ctx.sendUpstream(
                new UpstreamChannelStateEvent(
                        ctx.getChannel(), ChannelState.INTEREST_OPS, Channel.OP_READ));
    }

    /**
     * Sends a {@code "channelDisconnected"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireChannelDisconnected(Channel channel) {
        channel.getPipeline().sendUpstream(
                new UpstreamChannelStateEvent(
                        channel, ChannelState.CONNECTED, null));
    }

    /**
     * Sends a {@code "channelDisconnected"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     */
    public static void fireChannelDisconnected(ChannelHandlerContext ctx) {
        ctx.sendUpstream(new UpstreamChannelStateEvent(
                ctx.getChannel(), ChannelState.CONNECTED, null));
    }

    /**
     * Sends a {@code "channelUnbound"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireChannelUnbound(Channel channel) {
        channel.getPipeline().sendUpstream(new UpstreamChannelStateEvent(
                channel, ChannelState.BOUND, null));
    }

    /**
     * Sends a {@code "channelUnbound"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     */
    public static void fireChannelUnbound(ChannelHandlerContext ctx) {

        ctx.sendUpstream(new UpstreamChannelStateEvent(
                ctx.getChannel(), ChannelState.BOUND, null));
    }

    /**
     * Sends a {@code "channelClosed"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireChannelClosed(Channel channel) {
        channel.getPipeline().sendUpstream(
                new UpstreamChannelStateEvent(
                        channel, ChannelState.OPEN, Boolean.FALSE));

        // Notify the parent handler.
        if (channel.getParent() != null) {
            fireChildChannelStateChanged(channel.getParent(), channel);
        }
    }

    /**
     * Sends a {@code "channelClosed"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     */
    public static void fireChannelClosed(ChannelHandlerContext ctx) {
        ctx.sendUpstream(
                new UpstreamChannelStateEvent(
                        ctx.getChannel(), ChannelState.OPEN, Boolean.FALSE));
    }

    /**
     * Sends a {@code "exceptionCaught"} event to the first
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline} of
     * the specified {@link Channel}.
     */
    public static void fireExceptionCaught(Channel channel, Throwable cause) {
        channel.getPipeline().sendUpstream(
                new DefaultExceptionEvent(channel, cause));
    }

    /**
     * Sends a {@code "exceptionCaught"} event to the
     * {@link ChannelUpstreamHandler} which is placed in the closest upstream
     * from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     */
    public static void fireExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.sendUpstream(new DefaultExceptionEvent(ctx.getChannel(), cause));
    }

    private static void fireChildChannelStateChanged(
            Channel channel, Channel childChannel) {
        channel.getPipeline().sendUpstream(
                new DefaultChildChannelStateEvent(channel, childChannel));
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
        channel.getPipeline().sendDownstream(new DownstreamChannelStateEvent(
                channel, future, ChannelState.BOUND, localAddress));
        return future;
    }

    /**
     * Sends a {@code "bind"} request to the
     * {@link ChannelDownstreamHandler} which is placed in the closest
     * downstream from the handler associated with the specified
     * {@link ChannelHandlerContext}.
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
        ctx.sendDownstream(new DownstreamChannelStateEvent(
                ctx.getChannel(), future, ChannelState.BOUND, localAddress));
    }

    /**
     * Sends a {@code "unbind"} request to the
     * {@link ChannelDownstreamHandler} which is placed in the closest
     * downstream from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     *
     * @param ctx     the context
     * @param future  the future which will be notified when the unbind
     *                operation is done
     */
    public static void unbind(ChannelHandlerContext ctx, ChannelFuture future) {
        ctx.sendDownstream(new DownstreamChannelStateEvent(
                ctx.getChannel(), future, ChannelState.BOUND, null));
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
        channel.getPipeline().sendDownstream(new DownstreamChannelStateEvent(
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
        channel.getPipeline().sendDownstream(new DownstreamChannelStateEvent(
                channel, future, ChannelState.CONNECTED, remoteAddress));
        return future;
    }

    /**
     * Sends a {@code "connect"} request to the
     * {@link ChannelDownstreamHandler} which is placed in the closest
     * downstream from the handler associated with the specified
     * {@link ChannelHandlerContext}.
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
        ctx.sendDownstream(new DownstreamChannelStateEvent(
                ctx.getChannel(), future, ChannelState.CONNECTED, remoteAddress));
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
     * Sends a {@code "write"} request to the
     * {@link ChannelDownstreamHandler} which is placed in the closest
     * downstream from the handler associated with the specified
     * {@link ChannelHandlerContext}.
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
                new DownstreamMessageEvent(channel, future, message, remoteAddress));
        return future;
    }

    /**
     * Sends a {@code "write"} request to the
     * {@link ChannelDownstreamHandler} which is placed in the closest
     * downstream from the handler associated with the specified
     * {@link ChannelHandlerContext}.
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
                new DownstreamMessageEvent(ctx.getChannel(), future, message, remoteAddress));
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
        channel.getPipeline().sendDownstream(new DownstreamChannelStateEvent(
                channel, future, ChannelState.INTEREST_OPS, Integer.valueOf(interestOps)));
        return future;
    }

    /**
     * Sends a {@code "setInterestOps"} request to the
     * {@link ChannelDownstreamHandler} which is placed in the closest
     * downstream from the handler associated with the specified
     * {@link ChannelHandlerContext}.
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
                new DownstreamChannelStateEvent(
                        ctx.getChannel(), future, ChannelState.INTEREST_OPS,
                        Integer.valueOf(interestOps)));
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
        channel.getPipeline().sendDownstream(new DownstreamChannelStateEvent(
                channel, future, ChannelState.CONNECTED, null));
        return future;
    }

    /**
     * Sends a {@code "disconnect"} request to the
     * {@link ChannelDownstreamHandler} which is placed in the closest
     * downstream from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     *
     * @param ctx     the context
     * @param future  the future which will be notified on disconnection
     */
    public static void disconnect(
            ChannelHandlerContext ctx, ChannelFuture future) {
        ctx.sendDownstream(new DownstreamChannelStateEvent(
                ctx.getChannel(), future, ChannelState.CONNECTED, null));
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
        channel.getPipeline().sendDownstream(new DownstreamChannelStateEvent(
                channel, future, ChannelState.OPEN, Boolean.FALSE));
        return future;
    }

    /**
     * Sends a {@code "close"} request to the
     * {@link ChannelDownstreamHandler} which is placed in the closest
     * downstream from the handler associated with the specified
     * {@link ChannelHandlerContext}.
     *
     * @param ctx     the context
     * @param future  the future which will be notified on closure
     */
    public static void close(
            ChannelHandlerContext ctx, ChannelFuture future) {
        ctx.sendDownstream(new DownstreamChannelStateEvent(
                ctx.getChannel(), future, ChannelState.OPEN, Boolean.FALSE));
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
