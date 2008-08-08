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

public class DefaultChildChannelStateEvent extends DefaultChannelEvent implements
        ChildChannelStateEvent {

    private final Channel childChannel;

    public DefaultChildChannelStateEvent(
            Channel channel, ChannelFuture future, Channel childChannel) {

        super(channel, future);
        if (childChannel == null) {
            throw new NullPointerException("childChannel");
        }
        this.childChannel = childChannel;
    }

    public Channel getChildChannel() {
        return childChannel;
    }

    @Override
    public String toString() {
        String parentString = super.toString();
        StringBuilder buf = new StringBuilder(parentString.length() + 32);
        buf.append(parentString);
        buf.append(" - (childId: ");
        buf.append(getChildChannel().getId().toString());
        buf.append(", childState: ");
        buf.append(getChildChannel().isOpen()? "OPEN" : "CLOSE");
        buf.append(')');
        return buf.toString();
    }
}
