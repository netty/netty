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

abstract class DefaultDnsServerAddresses extends DnsServerAddresses {

    protected final InetSocketAddress[] addresses;
    private final String strVal;

    DefaultDnsServerAddresses(String type, InetSocketAddress[] addresses) {
        this.addresses = addresses;

        final StringBuilder buf = new StringBuilder(type.length() + 2 + addresses.length * 16);
        buf.append(type).append('(');

        for (InetSocketAddress a: addresses) {
            buf.append(a).append(", ");
        }

        buf.setLength(buf.length() - 2);
        buf.append(')');

        strVal = buf.toString();
    }

    @Override
    public String toString() {
        return strVal;
    }
}
