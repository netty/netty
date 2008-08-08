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


import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.UpstreamHandler;

public abstract class ChannelEventHandler implements UpstreamHandler<ChannelEvent> {

    public void handleUpstream(
            PipeContext<ChannelEvent> ctx, ChannelEvent e) throws Exception {

        if (e instanceof MessageEvent) {
            messageReceived(ctx, (MessageEvent) e);
        } else if (e instanceof ChildChannelStateEvent) {
            ChildChannelStateEvent evt = (ChildChannelStateEvent) e;
            if (evt.getChildChannel().isOpen()) {
                childChannelOpen(ctx, evt);
            } else {
                childChannelClosed(ctx, evt);
            }
        } else if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
            case OPEN:
                if (Boolean.TRUE.equals(evt.getValue())) {
                    channelOpen(ctx, evt);
                } else {
                    channelClosed(ctx, evt);
                }
                break;
            case BOUND:
                if (evt.getValue() != null) {
                    channelBound(ctx, evt);
                } else {
                    channelUnbound(ctx, evt);
                }
                break;
            case CONNECTED:
                if (evt.getValue() != null) {
                    channelConnected(ctx, evt);
                } else {
                    channelDisconnected(ctx, evt);
                }
                break;
            case INTEREST_OPS:
                channelInterestChanged(ctx, evt);
                break;
            }
        } else if (e instanceof ExceptionEvent) {
            exceptionCaught(ctx, (ExceptionEvent) e);
        } else {
            ctx.sendUpstream(e);
        }
    }

    protected abstract void channelOpen(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception;

    protected abstract void channelBound(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception;

    protected abstract void channelConnected(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception;

    protected abstract void messageReceived(
            PipeContext<ChannelEvent> ctx, MessageEvent e) throws Exception;

    protected abstract void channelInterestChanged(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception;

    protected abstract void channelDisconnected(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception;

    protected abstract void channelUnbound(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception;

    protected abstract void channelClosed(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception;

    protected abstract void exceptionCaught(
            PipeContext<ChannelEvent> ctx, ExceptionEvent e) throws Exception;

    protected abstract void childChannelOpen(
            PipeContext<ChannelEvent> ctx, ChildChannelStateEvent e) throws Exception;

    protected abstract void childChannelClosed(
            PipeContext<ChannelEvent> ctx, ChildChannelStateEvent e) throws Exception;
}
