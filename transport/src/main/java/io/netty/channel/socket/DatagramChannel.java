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
package io.netty.channel.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;

/**
 * A Datagram {@link Channel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options supported by {@link Channel},
 * {@link DatagramChannel} allows the following options in the
 * option map via {@link io.netty.channel.ChannelOption}:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>ChannelOption</th>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_BROADCAST}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_MULTICAST_ADDR}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_MULTICAST_LOOP_DISABLED}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_MULTICAST_IF}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_REUSEADDR}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_RCVBUF}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_SNDBUF}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_MULTICAST_TTL}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_TOS}</td>
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
     */
    default Future<Void> joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    /**
     * Joins a multicast group and notifies the {@link Future} once the operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    Future<Void> joinGroup(InetAddress multicastAddress, Promise<Void> future);

    /**
     * Joins the specified multicast group at the specified interface and notifies the {@link Future}
     * once the operation completes.
     */
    default Future<Void> joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress, networkInterface, newPromise());
    }

    /**
     * Joins the specified multicast group at the specified interface and notifies the {@link Future}
     * once the operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    Future<Void> joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface, Promise<Void> future);

    /**
     * Joins the specified multicast group at the specified interface and notifies the {@link Future}
     * once the operation completes.
     */
    default Future<Void> joinGroup(InetAddress multicastAddress,
                                   NetworkInterface networkInterface, InetAddress source) {
        return  joinGroup(multicastAddress, networkInterface, source, newPromise());
    }

    /**
     * Joins the specified multicast group at the specified interface and notifies the {@link Future}
     * once the operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    Future<Void> joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Promise<Void> future);

    /**
     * Leaves a multicast group and notifies the {@link Future} once the operation completes.
     */
    default Future<Void> leaveGroup(InetAddress multicastAddress) {
        return leaveGroup(multicastAddress, newPromise());
    }

    /**
     * Leaves a multicast group and notifies the {@link Future} once the operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    Future<Void> leaveGroup(InetAddress multicastAddress, Promise<Void> future);

    /**
     * Leaves a multicast group on a specified local interface and notifies the {@link Future} once the
     * operation completes.
     */
    default Future<Void> leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return leaveGroup(multicastAddress, networkInterface, newPromise());
    }

    /**
     * Leaves a multicast group on a specified local interface and notifies the {@link Future} once the
     * operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    Future<Void> leaveGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface, Promise<Void> future);

    /**
     * Leave the specified multicast group at the specified interface using the specified source and notifies
     * the {@link Future} once the operation completes.
     *
     */
    default Future<Void> leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return leaveGroup(multicastAddress, networkInterface, source, newPromise());
    }

    /**
     * Leave the specified multicast group at the specified interface using the specified source and notifies
     * the {@link Future} once the operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    Future<Void> leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
            Promise<Void> future);

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface and notifies
     * the {@link Future} once the operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    default Future<Void> block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newPromise());
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface and notifies
     * the {@link Future} once the operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    Future<Void> block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock, Promise<Void> future);

    /**
     * Block the given sourceToBlock address for the given multicastAddress and notifies the {@link Future} once
     * the operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    default Future<Void> block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    /**
     * Block the given sourceToBlock address for the given multicastAddress and notifies the {@link Future} once
     * the operation completes.
     * <p>
     * The given {@link Future} will be notified and also returned.
     */
    Future<Void> block(
                    InetAddress multicastAddress, InetAddress sourceToBlock, Promise<Void> future);

    @Override
    DatagramChannel read();

    @Override
    DatagramChannel flush();
}
