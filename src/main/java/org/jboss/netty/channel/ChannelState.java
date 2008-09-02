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
 * Represents the current state of a {@link Channel} combined with the
 * {@linkplain ChannelStateEvent#getValue() value} of a {@link ChannelStateEvent}.
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>State</th><th>Value</th><th>Description</th>
 * </tr>
 * <tr>
 * <td>{@link #OPEN}</td><td>{@code true}</td><td>The channel is open.</td>
 * </tr>
 * <tr>
 * <td>{@link #OPEN}</td><td>{@code false}</td><td>The channel is closed.</td>
 * </tr>
 * <tr>
 * <td>{@link #BOUND}</td><td>{@link SocketAddress}</td><td>The channel is bound to a local address.</td>
 * </tr>
 * <tr>
 * <td>{@link #BOUND}</td><td>{@code null}</td><td>The channel is unbound to a local address.</td>
 * </tr>
 * <tr>
 * <td>{@link #CONNECTED}</td><td>{link SocketAddress}</td><td>The channel is connected to a remote address.</td>
 * </tr>
 * <tr>
 * <td>{@link #CONNECTED}</td><td>{@code null}</td><td>The channel is disconnected from a remote address.</td>
 * </tr>
 * <tr>
 * <td>{@link #INTEREST_OPS}</td><td>an integer</td><td>The channel interestOps has been changed.</td>
 * </tr>
 * </table>
 * <p>
 * To see how a {@link ChannelEvent} is interpreted further, please refer to
 * {@link SimpleChannelHandler} and {@link ChannelDownstreamHandler}.
 *
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public enum ChannelState {
    /**
     * Represents a {@link Channel}'s {@link Channel#isOpen() open} property
     */
    OPEN,

    /**
     * Represents a {@link Channel}'s {@link Channel#isBound() bound} property
     */
    BOUND,

    /**
     * Represents a {@link Channel}'s {@link Channel#isConnected() connected}
     * property
     */
    CONNECTED,

    /**
     * Represents a {@link Channel}'s {@link Channel#getInterestOps() interestOps}
     * property
     */
    INTEREST_OPS,
}
