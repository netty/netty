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

public class DefaultChannelStateEvent extends DefaultChannelEvent implements
        ChannelStateEvent {

    private final ChannelState state;
    private final Object value;

    public DefaultChannelStateEvent(
            Channel channel, ChannelFuture future,
            ChannelState state, Object value) {

        super(channel, future);
        if (state == null) {
            throw new NullPointerException("state");
        }
        this.state = state;
        this.value = value;
    }

    public ChannelState getState() {
        return state;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        String parentString = super.toString();
        StringBuilder buf = new StringBuilder(parentString.length() + 64);
        buf.append(parentString);
        buf.append(" - (state:");
        switch (getState()) {
        case OPEN:
            if (Boolean.TRUE.equals(getValue())) {
                buf.append("OPEN");
            } else {
                buf.append("CLOSED");
            }
            break;
        case BOUND:
            if (getValue() != null) {
                buf.append("BOUND");
            } else {
                buf.append("UNBOUND");
            }
            break;
        case CONNECTED:
            if (getValue() != null) {
                buf.append("CONNECTED");
            } else {
                buf.append("DISCONNECTED");
            }
            break;
        }
        buf.append(')');
        return buf.toString();
    }
}
