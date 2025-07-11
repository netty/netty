/*
 * Copyright 2022 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

import java.net.InetAddress;

/**
 * Envelope class for IPv4 and IPv6 packets received from/sent to TUN devices.
 *
 * @see Tun4Packet
 * @see Tun6Packet
 */
public abstract class TunPacket extends DefaultByteBufHolder {
    protected TunPacket(ByteBuf data) {
        super(data);
    }

    /**
     * Returns the IP version.
     */
    public abstract InternetProtocolFamily version();

    /**
     * Returns the source address.
     */
    public abstract InetAddress sourceAddress();

    /**
     * Returns the destination address.
     */
    public abstract InetAddress destinationAddress();
}
