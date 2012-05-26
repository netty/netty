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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ServerChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * A SCTP/IP {@link ServerChannel} which accepts incoming SCTP/IP connections.
 * The {@link SctpServerChannel} provides the additional operations, available in the
 * underlying JDK SCTP Server Channel like multi-homing etc.
 */
public interface SctpServerChannel extends ServerChannel {
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
     * Returns the {@link SctpServerChannelConfig} configuration of the channel.
     */
    @Override
    SctpServerChannelConfig getConfig();

    /**
     * Return the (primary) local address of the SCTP server channel.
     *
     * Please note that, this return the first local address in the underlying SCTP ServerChannel's
     * local address iterator. (this method is implemented to support existing Netty API)
     * so application, has to keep track of it's primary address. This can be done by
     * requests the local SCTP stack, using the SctpStandardSocketOption.SCTP_PRIMARY_ADDR.
     */
    @Override
    InetSocketAddress getLocalAddress();

    /**
     * Return all local addresses of the SCTP server channel.
     * Please note that, it will return more than one address if this channel is using multi-homing
     */
    Set<InetSocketAddress> getAllLocalAddresses();
}
