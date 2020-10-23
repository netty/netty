/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ServerChannel;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.util.Set;

/**
 * A SCTP/IP {@link ServerChannel} which accepts incoming SCTP/IP associations.
 *
 * <p>
 * Multi-homing address binding/unbinding can done through bindAddress/unbindAddress methods.
 * </p>
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
    InetSocketAddress localAddress();

    /**
     * Return all local addresses of the SCTP server channel.
     * Please note that, it will return more than one address if this channel is using multi-homing
     */
    Set<InetSocketAddress> allLocalAddresses();

    /**
     * Bind a address to the already bound channel to enable multi-homing.
     * The Channel must be bound and yet to be connected.
     */
    ChannelFuture bindAddress(InetAddress localAddress);

    /**
     * Bind a address to the already bound channel to enable multi-homing.
     * The Channel must be bound and yet to be connected.
     *
     * Will notify the given {@link ChannelPromise} and return a {@link ChannelFuture}
     */
    ChannelFuture bindAddress(InetAddress localAddress, ChannelPromise promise);

    /**
     *  Unbind the address from channel's multi-homing address list.
     *  The address should be added already in multi-homing address list.
     */
    ChannelFuture unbindAddress(InetAddress localAddress);

    /**
     *  Unbind the address from channel's multi-homing address list.
     *  The address should be added already in multi-homing address list.
     *
     * Will notify the given {@link ChannelPromise} and return a {@link ChannelFuture}
     */
    ChannelFuture unbindAddress(InetAddress localAddress, ChannelPromise promise);
}
