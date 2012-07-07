/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;

/**
 * A UDP/IP {@link Channel} which is created by {@link DatagramChannelFactory}.
 * @apiviz.landmark
 * @apiviz.composedOf io.netty.channel.socket.DatagramChannelConfig
 */
public interface DatagramChannel extends Channel {
    @Override
    DatagramChannelConfig config();
    @Override
    InetSocketAddress localAddress();
    @Override
    InetSocketAddress remoteAddress();

    boolean isConnected();

    /**
     * Joins a multicast group.
     */
    ChannelFuture joinGroup(InetAddress multicastAddress);
    ChannelFuture joinGroup(InetAddress multicastAddress, ChannelFuture future);

    /**
     * Joins the specified multicast group at the specified interface.
     */
    ChannelFuture joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface);
    ChannelFuture joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface, ChannelFuture future);

    ChannelFuture joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source);
    ChannelFuture joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, ChannelFuture future);

    /**
     * Leaves a multicast group.
     */
    ChannelFuture leaveGroup(InetAddress multicastAddress);
    ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelFuture future);

    /**
     * Leaves a multicast group on a specified local interface.
     */
    ChannelFuture leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface);
    ChannelFuture leaveGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface, ChannelFuture future);

    /**
     * Leave the specified multicast group at the specified interface using the specified source.
     */
    ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source);
    ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source,
            ChannelFuture future);

        /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     */
    ChannelFuture block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock);

    /**
     * Block the given sourceToBlock address for the given multicastAddress on the given networkInterface
     */
    ChannelFuture block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock, ChannelFuture future);

    /**
     * Block the given sourceToBlock address for the given multicastAddress
     *
     */
    ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock);

    /**
     * Block the given sourceToBlock address for the given multicastAddress
     *
     */
    ChannelFuture block(
            InetAddress multicastAddress, InetAddress sourceToBlock, ChannelFuture future);
}
