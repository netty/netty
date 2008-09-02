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
 * Represents the current or future state of a {@link Channel}.
 * <p>
 * The state of a {@link Channel} is interpreted differently depending on the
 * {@linkplain ChannelStateEvent#getValue() value} of a {@link ChannelStateEvent}
 * and the direction of the event propagation in a {@link ChannelPipeline}:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Direction</th><th>State</th><th>Value</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>Upstream</td><td>{@link #OPEN}</td><td>{@code true}</td><td>The channel is open.</td>
 * </tr>
 * <tr>
 * <td>Upstream</td><td>{@link #OPEN}</td><td>{@code false}</td><td>The channel is closed.</td>
 * </tr>
 * <tr>
 * <td>Upstream</td><td>{@link #BOUND}</td><td>{@link SocketAddress}</td><td>The channel is bound to a local address.</td>
 * </tr>
 * <tr>
 * <td>Upstream</td><td>{@link #BOUND}</td><td>{@code null}</td><td>The channel is unbound to a local address.</td>
 * </tr>
 * <tr>
 * <td>Upstream</td><td>{@link #CONNECTED}</td><td>{@link SocketAddress}</td><td>The channel is connected to a remote address.</td>
 * </tr>
 * <tr>
 * <td>Upstream</td><td>{@link #CONNECTED}</td><td>{@code null}</td><td>The channel is disconnected from a remote address.</td>
 * </tr>
 * <tr>
 * <td>Upstream</td><td>{@link #INTEREST_OPS}</td><td>an integer</td><td>The channel interestOps has been changed.</td>
 * </tr>
 * <tr>
 * <td>Downstream</td><td>{@link #OPEN}</td><td>{@code true}</td><td>N/A</td>
 * </tr>
 * <tr>
 * <td>Downstream</td><td>{@link #OPEN}</td><td>{@code false}</td><td>Close the channel.</td>
 * </tr>
 * <tr>
 * <td>Downstream</td><td>{@link #BOUND}</td><td>{@link SocketAddress}</td><td>Bind the channel to the specified local address.</td>
 * </tr>
 * <tr>
 * <td>Downstream</td><td>{@link #BOUND}</td><td>{@code null}</td><td>Unbind the channel from the current local address.</td>
 * </tr>
 * <tr>
 * <td>Downstream</td><td>{@link #CONNECTED}</td><td>{@link SocketAddress}</td><td>Connect the channel to the specified remote address.</td>
 * </tr>
 * <tr>
 * <td>Downstream</td><td>{@link #CONNECTED}</td><td>{@code null}</td><td>Disconnect the channel from the current remote address.</td>
 * </tr>
 * <tr>
 * <td>Downstream</td><td>{@link #INTEREST_OPS}</td><td>an integer</td><td>Change the interestOps of the channel.</td>
 * </tr>
 * </table>
 * <p>
 * To see how a {@link ChannelEvent} is interpreted further, please refer to
 * {@link ChannelUpstreamHandler} and {@link ChannelDownstreamHandler}.
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
