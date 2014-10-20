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
package io.netty.channel;

import io.netty.util.internal.EndpointType;

import java.net.SocketAddress;

/**
 * Represents the properties of a {@link Channel} implementation.
 */
public final class ChannelMetadata {

    private final boolean hasDisconnect;

    private final EndpointType endpointType;

    /**
     * Create a new instance
     *
     * @param hasDisconnect     {@code true} if and only if the channel has the {@code disconnect()} operation
     *                          that allows a user to disconnect and then call {@link Channel#connect(SocketAddress)}
     *                                      again, such as UDP/IP.
     * @param endpointType      {@link io.netty.util.internal.EndpointType#SERVER} if and only if the channel extends
     *                          the {@link io.netty.channel.ServerChannel} interface, otherwise this must be set to
     *                          {@link io.netty.util.internal.EndpointType#CLIENT}
     */
    public ChannelMetadata(boolean hasDisconnect, EndpointType endpointType) {
        this.hasDisconnect = hasDisconnect;
        this.endpointType = endpointType;
    }

    /**
     * Returns {@code true} if and only if the channel has the {@code disconnect()} operation
     * that allows a user to disconnect and then call {@link Channel#connect(SocketAddress)} again,
     * such as UDP/IP.
     */
    public boolean hasDisconnect() {
        return hasDisconnect;
    }

    /**
     * Checks to see if the {@link io.netty.channel.Channel} associated with this
     * {@link io.netty.channel.ChannelMetadata} is a {@link io.netty.channel.ServerChannel}
     *
     * @return True if a {@link io.netty.channel.ServerChannel}, otherwise false
     */
    public boolean isServerChannel() {
        return endpointType.equals(EndpointType.SERVER);
    }
}
