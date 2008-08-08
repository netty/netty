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
package net.gleamynode.netty.channel;

import java.net.SocketAddress;
import java.util.Map;


/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 */
public class ChannelUtil {

    public static ChannelPipeline newPipeline() {
        return new DefaultChannelPipeline();
    }

    public static ChannelPipeline newPipeline(ChannelPipeline pipeline) {
        ChannelPipeline newChain = newPipeline();
        for (Map.Entry<String, ChannelHandler> e: pipeline.toMap().entrySet()) {
            newChain.addLast(e.getKey(), e.getValue());
        }
        return newChain;
    }

    public static ChannelPipelineFactory newPipelineFactory(
            final ChannelPipeline chain) {
        return new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                return newPipeline(chain);
            }
        };
    }

    public static void fireChannelOpen(Channel channel) {
        if (channel.getParent() != null) {
            fireChildChannelStateChanged(channel.getParent(), channel);
        }
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, getSucceededFuture(channel),
                        ChannelState.OPEN, Boolean.TRUE));
    }

    public static void fireChannelOpen(
            ChannelHandlerContext ctx, Channel channel) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, getSucceededFuture(channel),
                ChannelState.OPEN, Boolean.TRUE));
    }

    public static void fireChannelBound(Channel channel, SocketAddress localAddress) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, getSucceededFuture(channel),
                        ChannelState.BOUND, localAddress));
    }

    public static void fireChannelBound(
            ChannelHandlerContext ctx, Channel channel, SocketAddress localAddress) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, getSucceededFuture(channel),
                ChannelState.BOUND, localAddress));
    }

    public static void fireChannelConnected(Channel channel, SocketAddress remoteAddress) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, getSucceededFuture(channel),
                        ChannelState.CONNECTED, remoteAddress));
    }

    public static void fireChannelConnected(
            ChannelHandlerContext ctx, Channel channel, SocketAddress remoteAddress) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, getSucceededFuture(channel),
                ChannelState.CONNECTED, remoteAddress));
    }

    public static void fireMessageReceived(Channel channel, Object message) {
        fireMessageReceived(channel, message, null);
    }

    public static void messageReceived(
            ChannelHandlerContext ctx, Channel channel, Object message) {
        fireMessageReceived(ctx, channel, message, null);
    }

    public static void fireMessageReceived(Channel channel, Object message, SocketAddress remoteAddress) {
        channel.getPipeline().sendUpstream(
                new DefaultMessageEvent(
                        channel, getSucceededFuture(channel),
                        message, remoteAddress));
    }

    public static void fireMessageReceived(
            ChannelHandlerContext ctx, Channel channel, Object message) {
        ctx.sendUpstream(new DefaultMessageEvent(
                channel, getSucceededFuture(channel), message, null));
    }

    public static void fireMessageReceived(
            ChannelHandlerContext ctx, Channel channel,
            Object message, SocketAddress remoteAddress) {
        ctx.sendUpstream(new DefaultMessageEvent(
                channel, getSucceededFuture(channel), message, remoteAddress));
    }

    public static void fireChannelInterestChanged(Channel channel, int interestOps) {
        validateInterestOps(interestOps);
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, getSucceededFuture(channel),
                        ChannelState.INTEREST_OPS, Integer.valueOf(interestOps)));
    }

    public static void fireChannelInterestChanged(
            ChannelHandlerContext ctx, Channel channel, int interestOps) {

        validateInterestOps(interestOps);
        ctx.sendUpstream(
                new DefaultChannelStateEvent(
                        channel, getSucceededFuture(channel),
                        ChannelState.INTEREST_OPS, Integer.valueOf(interestOps)));
    }

    public static void fireChannelDisconnected(Channel channel) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, getSucceededFuture(channel),
                        ChannelState.CONNECTED, null));
    }

    public static void fireChannelDisconnected(
            ChannelHandlerContext ctx, Channel channel) {
        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, getSucceededFuture(channel),
                ChannelState.CONNECTED, null));
    }

    public static void fireChannelUnbound(Channel channel) {
        channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                channel, getSucceededFuture(channel), ChannelState.BOUND, null));
    }

    public static void fireChannelUnbound(
            ChannelHandlerContext ctx, Channel channel) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, getSucceededFuture(channel), ChannelState.BOUND, null));
    }

    public static void fireChannelClosed(Channel channel) {
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, getSucceededFuture(channel),
                        ChannelState.OPEN, Boolean.FALSE));
        if (channel.getParent() != null) {
            fireChildChannelStateChanged(channel.getParent(), channel);
        }
    }

    public static void fireChannelClosed(
            ChannelHandlerContext ctx, Channel channel) {
        ctx.sendUpstream(
                new DefaultChannelStateEvent(
                        channel, getSucceededFuture(channel),
                        ChannelState.OPEN, Boolean.FALSE));
    }

    public static void fireExceptionCaught(Channel channel, Throwable cause) {
        channel.getPipeline().sendUpstream(
                new DefaultExceptionEvent(
                        channel, getSucceededFuture(channel), cause));
    }

    public static void fireExceptionCaught(
            ChannelHandlerContext ctx, Channel channel, Throwable cause) {
        ctx.sendUpstream(new DefaultExceptionEvent(
                channel, getSucceededFuture(channel), cause));
    }

    private static void fireChildChannelStateChanged(
            Channel channel, Channel childChannel) {
        channel.getPipeline().sendUpstream(
                new DefaultChildChannelStateEvent(
                        channel, getSucceededFuture(channel), childChannel));
    }

    private static ChannelFuture getSucceededFuture(Channel channel) {
        if (channel instanceof AbstractChannel) {
            return ((AbstractChannel) channel).getSucceededFuture();
        } else {
            return new SucceededChannelFuture(channel);
        }
    }

    public static ChannelFuture bind(Channel channel, SocketAddress localAddress) {
        ChannelFuture future = new DefaultChannelFuture(channel, false);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.BOUND, localAddress));
        return future;
    }

    public static void bind(
            ChannelHandlerContext ctx, Channel channel,
            ChannelFuture future, SocketAddress localAddress) {

        ctx.sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.BOUND, localAddress));
    }

    public static ChannelFuture connect(Channel channel, SocketAddress remoteAddress) {
        ChannelFuture future = new DefaultChannelFuture(channel, true);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, remoteAddress));
        return future;
    }

    public static void connect(
            ChannelHandlerContext ctx, Channel channel,
            ChannelFuture future, SocketAddress remoteAddress) {

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
        ChannelFuture future = new DefaultChannelFuture(channel, false);
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

        ChannelFuture future = new DefaultChannelFuture(channel, false);
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
        ChannelFuture future = new DefaultChannelFuture(channel, false);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, null));
        return future;
    }

    public static void disconnect(
            ChannelHandlerContext ctx, Channel channel, ChannelFuture future) {
        ctx.sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, null));
    }

    public static ChannelFuture close(Channel channel) {
        ChannelFuture future = new DefaultChannelFuture(channel, false);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.OPEN, Boolean.FALSE));
        return future;
    }

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

    private ChannelUtil() {
        // Unused
    }
}
