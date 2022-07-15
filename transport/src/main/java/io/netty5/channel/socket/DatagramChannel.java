/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel.socket;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOption;
import io.netty5.util.concurrent.Future;

import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * A UDP/IP {@link Channel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link Channel},
 * {@link DatagramChannel} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>{@link ChannelOption}</th>
 * <th>{@code INET}</th>
 * <th>{@code INET6}</th>
 * <th>{@code UNIX}</th>
 * </tr><tr>
 * <td>{@link ChannelOption#DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_BROADCAST}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_REUSEADDR}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_RCVBUF}</td><td>X</td><td>X</td><td>X</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_SNDBUF}</td><td>X</td><td>X</td><td>X</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_MULTICAST_LOOP_DISABLED}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_MULTICAST_IF}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_MULTICAST_TTL}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_TOS}</td><td>X</td><td>X</td><td>-</td>
 * </tr>
 * </table>
 */
public interface DatagramChannel extends Channel {

    /**
     * Return {@code true} if the {@link DatagramChannel} is connected to the remote peer.
     */
    boolean isConnected();

    /**
     * Joins a multicast group and notifies the {@link Future} once the operation completes.
     * <p>
     * If the underlying implementation does not support this operation it will return a {@link Future} which
     * is failed with an {@link UnsupportedOperationException}.
     *
     * @param multicastAddress  the multicast group address.
     * @return                  a {@link Future} which is notified once the operation completes.
     */
    Future<Void> joinGroup(InetAddress multicastAddress);

    /**
     * Joins the specified multicast group at the specified interface and notifies the {@link Future}
     * once the operation completes.
     *
     * <p>
     * If the underlying implementation does not support this operation it will return a {@link Future} which
     * is failed with an {@link UnsupportedOperationException}.
     *
     * @param multicastAddress  the multicast group address.
     * @param networkInterface  the interface to use.
     * @param source            the source address (might be {@code null}).
     * @return                  a {@link Future} which is notified once the operation completes.
     */
    Future<Void> joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source);

    /**
     * Leaves a multicast group and notifies the {@link Future} once the operation completes.
     *
     * <p>
     * If the underlying implementation does not support this operation it will return a {@link Future} which
     * is failed with an {@link UnsupportedOperationException}.
     *
     * @param multicastAddress  the multicast group address.
     * @return                  a {@link Future} which is notified once the operation completes.
     */
    Future<Void> leaveGroup(InetAddress multicastAddress);

    /**
     * Leave the specified multicast group at the specified interface using the specified source and notifies
     * the {@link Future} once the operation completes.
     *
     * <p>
     * If the underlying implementation does not support this operation it will return a {@link Future} which
     * is failed with an {@link UnsupportedOperationException}.
     *
     * @param multicastAddress  the multicast group address.
     * @param networkInterface  the interface to use.
     * @param source            the source address (might be {@code null}).
     * @return                  a {@link Future} which is notified once the operation completes.
     */
    Future<Void> leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source);

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface and notifies
     * the {@link Future} once the operation completes.
     *
     * <p>
     * If the underlying implementation does not support this operation it will return a {@link Future} which
     * is failed with an {@link UnsupportedOperationException}.
     *
     * @param multicastAddress  the multicast group address.
     * @param networkInterface  the interface to use.
     * @param sourceToBlock     the source address.
     * @return                  a {@link Future} which is notified once the operation completes.
     */
    Future<Void> block(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress sourceToBlock);

    /**
     * Block the given sourceToBlock address for the given multicastAddress and notifies the {@link Future} once
     * the operation completes.
     *
     * <p>
     * If the underlying implementation does not support this operation it will return a {@link Future} which
     * is failed with an {@link UnsupportedOperationException}.
     *
     * @param multicastAddress  the multicast group address.
     * @param sourceToBlock     the source address.
     * @return                  a {@link Future} which is notified once the operation completes.
     */
    Future<Void> block(InetAddress multicastAddress, InetAddress sourceToBlock);
}
