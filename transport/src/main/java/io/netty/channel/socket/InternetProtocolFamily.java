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

import io.netty.util.NetUtil;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Internet Protocol (IP) families used by the {@link DatagramChannel}
 *
 * @deprecated use {@link SocketProtocolFamily}.
 */
@Deprecated
public enum InternetProtocolFamily {
    IPv4(Inet4Address.class, 1),
    IPv6(Inet6Address.class, 2);

    private final Class<? extends InetAddress> addressType;
    private final int addressNumber;

    InternetProtocolFamily(Class<? extends InetAddress> addressType, int addressNumber) {
        this.addressType = addressType;
        this.addressNumber = addressNumber;
    }

    /**
     * Returns the address type of this protocol family.
     */
    public Class<? extends InetAddress> addressType() {
        return addressType;
    }

    /**
     * Returns the
     * <a href="https://www.iana.org/assignments/address-family-numbers/address-family-numbers.xhtml">address number</a>
     * of the family.
     */
    public int addressNumber() {
        return addressNumber;
    }

    /**
     * Returns the {@link InetAddress} that represent the {@code LOCALHOST} for the family.
     */
    public InetAddress localhost() {
        switch (this) {
            case IPv4:
                return NetUtil.LOCALHOST4;
            case IPv6:
                return NetUtil.LOCALHOST6;
            default:
                throw new IllegalStateException("Unsupported family " + this);
        }
    }

    /**
     * Returns the {@link InternetProtocolFamily} for the given {@link InetAddress}.
     */
    public static InternetProtocolFamily of(InetAddress address) {
        if (address instanceof Inet4Address) {
            return IPv4;
        }
        if (address instanceof Inet6Address) {
            return IPv6;
        }
        throw new IllegalArgumentException("address " + address + " not supported");
    }

    public SocketProtocolFamily toSocketProtocolFamily() {
        switch (this) {
            case IPv4:
                return SocketProtocolFamily.INET;
            case IPv6:
                return SocketProtocolFamily.INET6;
            default:
                throw new IllegalStateException();
        }
    }
}
