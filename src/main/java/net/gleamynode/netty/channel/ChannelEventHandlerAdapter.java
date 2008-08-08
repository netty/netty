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


import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.pipeline.PipeContext;

public class ChannelEventHandlerAdapter extends ChannelEventHandler {

    private static final Logger logger = Logger.getLogger(ChannelEventHandlerAdapter.class.getName());

    @Override
    protected void channelBound(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    @Override
    protected void channelClosed(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    @Override
    protected void channelConnected(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    @Override
    protected void channelInterestChanged(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    @Override
    protected void channelDisconnected(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    @Override
    protected void channelOpen(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    @Override
    protected void channelUnbound(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    @Override
    protected void messageReceived(
            PipeContext<ChannelEvent> ctx, MessageEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    @Override
    protected void exceptionCaught(
            PipeContext<ChannelEvent> ctx, ExceptionEvent e) throws Exception {
        logger.log(
                Level.WARNING,
                "EXCEPTION, please implement " + getClass().getName() +
                ".exceptionCaught() for proper handling.", e.getCause());
        ctx.sendUpstream(e);
    }

    @Override
    protected void childChannelClosed(
            PipeContext<ChannelEvent> ctx, ChildChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    @Override
    protected void childChannelOpen(
            PipeContext<ChannelEvent> ctx, ChildChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }
}
