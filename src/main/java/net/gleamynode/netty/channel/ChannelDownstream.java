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
public class ChannelDownstream {

    public static ChannelFuture bind(Channel channel, SocketAddress localAddress) {
        ChannelFuture future = new DefaultChannelFuture(channel, false);
        channel.getPipeline().sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.BOUND, localAddress));
        return future;
    }

    public static void bind(
            PipeContext<ChannelEvent> ctx, Channel channel,
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
            PipeContext<ChannelEvent> ctx, Channel channel,
            ChannelFuture future, SocketAddress remoteAddress) {

        ctx.sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.CONNECTED, remoteAddress));
    }

    public static ChannelFuture write(Channel channel, Object message) {
        return write(channel, message, null);
    }

    public static void write(
            PipeContext<ChannelEvent> ctx, Channel channel,
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
            PipeContext<ChannelEvent> ctx, Channel channel,
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
            PipeContext<ChannelEvent> ctx, Channel channel,
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
            PipeContext<ChannelEvent> ctx, Channel channel, ChannelFuture future) {
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
            PipeContext<ChannelEvent> ctx, Channel channel, ChannelFuture future) {
        ctx.sendDownstream(new DefaultChannelStateEvent(
                channel, future, ChannelState.OPEN, Boolean.FALSE));
    }

    static void validateInterestOps(int interestOps) {
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

    private ChannelDownstream() {
        // Unused
    }
}
