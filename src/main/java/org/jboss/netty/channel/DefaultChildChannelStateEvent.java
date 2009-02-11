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

import static org.jboss.netty.channel.Channels.*;

/**
 * The default {@link ChildChannelStateEvent} implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
final class DefaultChildChannelStateEvent implements ChildChannelStateEvent {

    private final Channel parentChannel;
    private final Channel childChannel;

    /**
     * Creates a new instance.
     */
    DefaultChildChannelStateEvent(Channel parentChannel, Channel childChannel) {
        if (parentChannel == null) {
            throw new NullPointerException("parentChannel");
        }
        if (childChannel == null) {
            throw new NullPointerException("childChannel");
        }
        this.parentChannel = parentChannel;
        this.childChannel = childChannel;
    }

    public Channel getChannel() {
        return parentChannel;
    }

    public ChannelFuture getFuture() {
        return succeededFuture(getChannel());
    }

    public Channel getChildChannel() {
        return childChannel;
    }

    @Override
    public String toString() {
        String channelString = getChannel().toString();
        StringBuilder buf = new StringBuilder(channelString.length() + 32);
        buf.append(channelString);
        buf.append(" - (");
        buf.append(getChildChannel().isOpen()? "CHILD_OPEN" : "CHILD_CLOSED");
        buf.append(": ");
        buf.append(getChildChannel().getId());
        buf.append(')');
        return buf.toString();
    }
}
