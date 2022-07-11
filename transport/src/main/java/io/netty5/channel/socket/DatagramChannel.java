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
import io.netty5.util.concurrent.Future;

import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * A UDP/IP {@link Channel}.
 */
public interface DatagramChannel extends Channel {
    @Override
    DatagramChannelConfig config();

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
