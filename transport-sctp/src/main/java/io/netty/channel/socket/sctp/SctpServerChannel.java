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
package io.netty.channel.socket.sctp;

import io.netty.channel.ServerChannel;

import java.net.SocketAddress;
import java.util.Set;

/**
 * A SCTP/IP {@link ServerChannel} which accepts incoming SCTP/IP connections.
 *
 * The {@link SctpServerChannel} provides the additional operations, available in the
 * underlying JDK SCTP Server Channel like multi-homing etc.
 */
public interface SctpServerChannel extends ServerChannel {

    /**
     * Returns the {@link SctpServerChannelConfig} configuration of the channel.
     */
    @Override
    SctpServerChannelConfig config();

    /**
     * Return the (primary) local address of the SCTP server channel.
     *
     * Please note that, this return the first local address in the underlying SCTP ServerChannel's
     * local address iterator to support Netty Channel API. In other words, its the application's
     * responsibility to keep track of it's local primary address.
     *
     * (To set a local address as primary, the application can request by calling local SCTP stack,
     * with SctpStandardSocketOption.SCTP_PRIMARY_ADDR option).
     */
    @Override
    SocketAddress localAddress();

    /**
     * Return all local addresses of the SCTP server channel.
     * Please note that, it will return more than one address if this channel is using multi-homing
     */
    Set<SocketAddress> allLocalAddresses();
}
