/*
 * Copyright 2015 The Netty Project
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

package io.netty.resolver.dns;

import java.net.InetSocketAddress;

final class SequentialDnsServerAddressStream implements DnsServerAddressStream {

    private final InetSocketAddress[] addresses;
    private int i;

    SequentialDnsServerAddressStream(InetSocketAddress[] addresses, int startIdx) {
        this.addresses = addresses;
        i = startIdx;
    }

    @Override
    public InetSocketAddress next() {
        int i = this.i;
        InetSocketAddress next = addresses[i];
        if (++ i < addresses.length) {
            this.i = i;
        } else {
            this.i = 0;
        }
        return next;
    }

    @Override
    public int size() {
        return addresses.length;
    }

    @Override
    public SequentialDnsServerAddressStream duplicate() {
        return new SequentialDnsServerAddressStream(addresses, i);
    }

    @Override
    public String toString() {
        return toString("sequential", i, addresses);
    }

    static String toString(String type, int index, InetSocketAddress[] addresses) {
        final StringBuilder buf = new StringBuilder(type.length() + 2 + addresses.length * 16);
        buf.append(type).append("(index: ").append(index);
        buf.append(", addrs: (");
        for (InetSocketAddress a: addresses) {
            buf.append(a).append(", ");
        }

        buf.setLength(buf.length() - 2);
        buf.append("))");

        return buf.toString();
    }
}
