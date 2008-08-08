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

public class DefaultMessageEvent extends DefaultChannelEvent implements
        MessageEvent {

    private final Object message;
    private final SocketAddress remoteAddress;

    public DefaultMessageEvent(
            Channel channel, ChannelFuture future,
            Object message, SocketAddress remoteAddress) {

        super(channel, future);
        if (message == null) {
            throw new NullPointerException("message");
        }
        this.message = message;
        this.remoteAddress = remoteAddress;
    }

    public Object getMessage() {
        return message;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public String toString() {
        if (remoteAddress == null) {
            return super.toString() + " - (message: " + message + ')';
        } else {
            return super.toString() + " - (message: " + message + ", " + remoteAddress + ')';
        }
    }
}
