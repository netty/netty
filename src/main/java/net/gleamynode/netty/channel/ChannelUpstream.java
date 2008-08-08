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

import net.gleamynode.netty.pipeline.PipeContext;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 */
public class ChannelUpstream {

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
            PipeContext<ChannelEvent> ctx, Channel channel) {

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
            PipeContext<ChannelEvent> ctx, Channel channel, SocketAddress localAddress) {

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
            PipeContext<ChannelEvent> ctx, Channel channel, SocketAddress remoteAddress) {

        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, getSucceededFuture(channel),
                ChannelState.CONNECTED, remoteAddress));
    }

    public static void fireMessageReceived(Channel channel, Object message) {
        fireMessageReceived(channel, message, null);
    }

    public static void messageReceived(
            PipeContext<ChannelEvent> ctx, Channel channel, Object message) {
        fireMessageReceived(ctx, channel, message, null);
    }

    public static void fireMessageReceived(Channel channel, Object message, SocketAddress remoteAddress) {
        channel.getPipeline().sendUpstream(
                new DefaultMessageEvent(
                        channel, getSucceededFuture(channel),
                        message, remoteAddress));
    }

    public static void fireMessageReceived(
            PipeContext<ChannelEvent> ctx, Channel channel, Object message) {
        ctx.sendUpstream(new DefaultMessageEvent(
                channel, getSucceededFuture(channel), message, null));
    }

    public static void fireMessageReceived(
            PipeContext<ChannelEvent> ctx, Channel channel,
            Object message, SocketAddress remoteAddress) {
        ctx.sendUpstream(new DefaultMessageEvent(
                channel, getSucceededFuture(channel), message, remoteAddress));
    }

    public static void fireChannelInterestChanged(Channel channel, int interestOps) {
        ChannelDownstream.validateInterestOps(interestOps);
        channel.getPipeline().sendUpstream(
                new DefaultChannelStateEvent(
                        channel, getSucceededFuture(channel),
                        ChannelState.INTEREST_OPS, Integer.valueOf(interestOps)));
    }

    public static void fireChannelInterestChanged(
            PipeContext<ChannelEvent> ctx, Channel channel, int interestOps) {

        ChannelDownstream.validateInterestOps(interestOps);
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
            PipeContext<ChannelEvent> ctx, Channel channel) {
        ctx.sendUpstream(new DefaultChannelStateEvent(
                channel, getSucceededFuture(channel),
                ChannelState.CONNECTED, null));
    }

    public static void fireChannelUnbound(Channel channel) {
        channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                channel, getSucceededFuture(channel), ChannelState.BOUND, null));
    }

    public static void fireChannelUnbound(
            PipeContext<ChannelEvent> ctx, Channel channel) {

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
            PipeContext<ChannelEvent> ctx, Channel channel) {
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
            PipeContext<ChannelEvent> ctx, Channel channel, Throwable cause) {
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

    private ChannelUpstream() {
        // Unused
    }
}
