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
        buf.append(" - (state: ");
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
        case INTEREST_OPS:
            switch (((Integer) getValue()).intValue()) {
            case Channel.OP_NONE:
                buf.append("OP_NONE");
                break;
            case Channel.OP_READ:
                buf.append("OP_READ");
                break;
            case Channel.OP_WRITE:
                buf.append("OP_WRITE");
                break;
            case Channel.OP_READ_WRITE:
                buf.append("OP_READ_WRITE");
                break;
            default:
                buf.append("OP_");
                buf.append(getValue());
                buf.append(" (?)");
            }
        }
        buf.append(')');
        return buf.toString();
    }
}
