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

/**
 * The default {@link ChannelStateEvent} implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class DownstreamChannelStateEvent implements ChannelStateEvent {

    private final Channel channel;
    private final ChannelFuture future;
    private final ChannelState state;
    private final Object value;

    /**
     * Creates a new instance.
     */
    public DownstreamChannelStateEvent(
            Channel channel, ChannelFuture future,
            ChannelState state, Object value) {

        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (future == null) {
            throw new NullPointerException("future");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
        this.channel = channel;
        this.future = future;
        this.state = state;
        this.value = value;
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelFuture getFuture() {
        return future;
    }

    public ChannelState getState() {
        return state;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        String channelString = getChannel().toString();
        StringBuilder buf = new StringBuilder(channelString.length() + 64);
        buf.append(channelString);
        buf.append(" - (");
        switch (getState()) {
        case OPEN:
            if (Boolean.TRUE.equals(getValue())) {
                buf.append("OPEN");
            } else {
                buf.append("CLOSE");
            }
            break;
        case BOUND:
            if (getValue() != null) {
                buf.append("BIND: ");
                buf.append(getValue());
            } else {
                buf.append("UNBIND");
            }
            break;
        case CONNECTED:
            if (getValue() != null) {
                buf.append("CONNECT: ");
                buf.append(getValue());
            } else {
                buf.append("DISCONNECT");
            }
            break;
        case INTEREST_OPS:
            buf.append("CHANGE_INTEREST: ");
            buf.append(getValue());
            break;
        default:
            buf.append(getState().name());
            buf.append(": ");
            buf.append(getValue());
        }
        buf.append(')');
        return buf.toString();
    }
}
