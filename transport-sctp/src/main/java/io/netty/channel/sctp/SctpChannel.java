/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp;

import com.sun.nio.sctp.Association;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * A SCTP/IP  {@link Channel} which was either accepted by {@link SctpServerChannel} or
 * created by {@link SctpClientSocketChannelFactory}.
 */
public interface SctpChannel extends Channel {

    /**
     * Bind a address to the already bound channel to enable multi-homing.
     * The Channel bust be bound and yet to be connected.
     */
    ChannelFuture bindAddress(InetAddress localAddress);


    /**
     *  Unbind the address from channel's multi-homing address list.
     *  The address should be added already in multi-homing address list.
     */
    ChannelFuture unbindAddress(InetAddress localAddress);

    /**
     * Returns the underlying SCTP association.
     */
    Association association();

    /**
     * Return the (primary) local address of the SCTP channel.
     *
     * Please note that, this return the first local address in the underlying SCTP Channel's
     * local address iterator to support Netty Channel API. In other words, its the application's
     * responsibility to keep track of it's local primary address.
     *
     * (To set a local address as primary, the application can request by calling local SCTP stack,
     * with SctpStandardSocketOption.SCTP_PRIMARY_ADDR option).
     */
    @Override
    InetSocketAddress getLocalAddress();

    /**
     * Return all local addresses of the SCTP  channel.
     * Please note that, it will return more than one address if this channel is using multi-homing
     */
    Set<InetSocketAddress> getAllLocalAddresses();

    /**
     * Returns the {@link SctpChannelConfig} configuration of the channel.
     */
    @Override
    NioSctpChannelConfig getConfig();

    /**
     * Return the (primary) remote address of the SCTP channel.
     *
     * Please note that, this return the first remote address in the underlying SCTP Channel's
     * remote address iterator to support Netty Channel API. In other words, its the application's
     * responsibility to keep track of it's peer's primary address.
     *
     * (The application can request it's remote peer to set a specific address as primary by
     * calling the local SCTP stack with SctpStandardSocketOption.SCTP_SET_PEER_PRIMARY_ADDR option)
     */
    @Override
    InetSocketAddress getRemoteAddress();


    /**
     * Return all remote addresses of the SCTP server channel.
     * Please note that, it will return more than one address if the remote is using multi-homing.
     */
    Set<InetSocketAddress> getAllRemoteAddresses();
}
