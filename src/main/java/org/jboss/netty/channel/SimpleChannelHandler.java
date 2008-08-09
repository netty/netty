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

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;


/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class SimpleChannelHandler implements ChannelUpstreamHandler {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SimpleChannelHandler.class.getName());

    public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

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

    public void channelBound(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelInterestChanged(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelDisconnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelOpen(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void channelUnbound(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (this == ctx.getPipeline().getLast()) {
            logger.warn(
                    "EXCEPTION, please implement " + getClass().getName() +
                    ".exceptionCaught() for proper handling.", e.getCause());
        }
        ctx.sendUpstream(e);
    }

    public void childChannelClosed(
            ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    public void childChannelOpen(
            ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }
}
