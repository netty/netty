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

import java.net.SocketAddress;

/**
 * The default {@link MessageEvent} implementation.  It is recommended to
 * use {@link Channels#messageEvent(Channel, ChannelFuture, Object)} and
 * {@link Channels#messageEvent(Channel, ChannelFuture, Object, SocketAddress)}
 * to create a new {@link MessageEvent} instance rather than calling the
 * constructor explicitly.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class DefaultMessageEvent extends DefaultChannelEvent implements
        MessageEvent {

    private final Object message;
    private final SocketAddress remoteAddress;

    /**
     * Creates a new instance.
     */
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
