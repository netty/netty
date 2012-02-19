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
 * A SCTP {@link io.netty.channel.ServerChannel} which accepts incoming SCTP connections.
 */
public interface SctpServerChannel extends ServerChannel {
    /**
     * Bind a multi-homing address to the already bound channel
     */
    ChannelFuture bindAddress(InetAddress localAddress);


    /**
     *  Unbind a multi-homing address from a already established channel
     */
    ChannelFuture unbindAddress(InetAddress localAddress);

    /**
     * Returns the configuration of this channel.
     */
    @Override
    SctpServerChannelConfig getConfig();

    /**
     * Return the primary local address of the SCTP server channel.
     */
    @Override
    InetSocketAddress getLocalAddress();

    /**
     * Return all local addresses of the SCTP server channel.
     */
    Set<InetSocketAddress> getAllLocalAddresses();

    /**
     * Return the primary remote address of the server SCTP channel.
     */
    @Override
    InetSocketAddress getRemoteAddress();


    /**
     * Return all remote addresses of the SCTP server channel.
     */
    Set<InetSocketAddress> getAllRemoteAddresses();
}
