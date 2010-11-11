/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import java.net.SocketAddress;

/**
 * The current or future state of a {@link Channel}.
 * <p>
 * The state of a {@link Channel} is interpreted differently depending on the
 * {@linkplain ChannelStateEvent#getValue() value} of a {@link ChannelStateEvent}
 * and the direction of the event in a {@link ChannelPipeline}:
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
 * To see how an event is interpreted further, please refer to {@link ChannelEvent}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
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
    INTEREST_OPS;
}
