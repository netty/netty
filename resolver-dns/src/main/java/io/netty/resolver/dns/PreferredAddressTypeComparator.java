/*
 * Copyright 2019 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.channel.socket.SocketProtocolFamily;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Comparator;

final class PreferredAddressTypeComparator implements Comparator<InetAddress> {

    private static final PreferredAddressTypeComparator IPv4 = new PreferredAddressTypeComparator(Inet4Address.class);
    private static final PreferredAddressTypeComparator IPv6 = new PreferredAddressTypeComparator(Inet6Address.class);

    static PreferredAddressTypeComparator comparator(SocketProtocolFamily family) {
        switch (family) {
            case INET:
                return IPv4;
            case INET6:
                return IPv6;
            default:
                throw new IllegalArgumentException();
        }
    }

    private final Class<? extends InetAddress> preferredAddressType;

    private PreferredAddressTypeComparator(Class<? extends InetAddress> preferredAddressType) {
        this.preferredAddressType = preferredAddressType;
    }

    @Override
    public int compare(InetAddress o1, InetAddress o2) {
        if (o1.getClass() == o2.getClass()) {
            return 0;
        }
        return preferredAddressType.isAssignableFrom(o1.getClass()) ? -1 : 1;
    }
}
