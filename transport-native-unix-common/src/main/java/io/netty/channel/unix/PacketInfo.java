/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.unix;

import io.netty.util.internal.ObjectUtil;

import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * The value of the {@link ControlMessage#IP_PKTINFO} and {@link ControlMessage#IPV6_PKTINFO} control messages.
 * <p>
 * When receiving, the {@link #address()} is the destination address of the datagram and {@link #interfaceIndex()} is
 * the index of the interface on which the datagram was received.
 * <p>
 * When sending, the {@link #address()} is the source address that should be used and {@link #interfaceIndex()} is
 * the index of the interface that should be used to send the datagram.
 */
public final class PacketInfo {

    private final InetAddress address;
    private final int interfaceIndex;

    /**
     * Creates a new instance.
     *
     * @param address           the address, which may be {@code null} when sending if the kernel should select the
     *                          source address.
     * @param interfaceIndex    the index of the interface (see {@link NetworkInterface#getIndex()}), which may be
     *                          {@code 0} when sending if the kernel should select the interface.
     */
    public PacketInfo(InetAddress address, int interfaceIndex) {
        this.address = address;
        this.interfaceIndex = ObjectUtil.checkPositiveOrZero(interfaceIndex, "interfaceIndex");
    }

    /**
     * Returns the address, which is the destination address of the datagram when receiving and the source address
     * when sending. This may be {@code null} when sending if the kernel should select the source address.
     */
    public InetAddress address() {
        return address;
    }

    /**
     * Returns the index of the interface (see {@link NetworkInterface#getIndex()}), which is the interface on which
     * the datagram was received when receiving, and the interface that should be used when sending. This may be
     * {@code 0} when sending if the kernel should select the interface.
     */
    public int interfaceIndex() {
        return interfaceIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PacketInfo)) {
            return false;
        }
        PacketInfo that = (PacketInfo) o;
        return interfaceIndex == that.interfaceIndex &&
                (address == null ? that.address == null : address.equals(that.address));
    }

    @Override
    public int hashCode() {
        return 31 * (address == null ? 0 : address.hashCode()) + interfaceIndex;
    }

    @Override
    public String toString() {
        return "PacketInfo(address: " + (address == null ? null : address.getHostAddress()) +
                ", interfaceIndex: " + interfaceIndex + ')';
    }
}
