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
package net.gleamynode.netty.handler.execution;

import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelHandlerContext;

public class ChannelEventRunnable implements Runnable {
    private final ChannelHandlerContext ctx;
    private final ChannelEvent e;

    public ChannelEventRunnable(ChannelHandlerContext ctx, ChannelEvent e) {
        this.ctx = ctx;
        this.e = e;
    }

    public ChannelHandlerContext getContext() {
        return ctx;
    }

    public ChannelEvent getEvent() {
        return e;
    }

    public void run() {
        ctx.sendUpstream(e);
    }
}