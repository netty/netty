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

import io.netty.util.concurrent.Promise;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

final class DnsNamesResolverContext extends AbstractDnsNameResolverContext<List<InetSocketAddress>> {
    public DnsNamesResolverContext(DnsNameResolver parent, String hostname, int port,
                                   Promise<List<InetSocketAddress>> promise) {
        super(parent, hostname, port, promise);
    }

    @Override
    protected boolean finishResolveWithIPv4(List<InetAddress> resolvedAddresses, int port) {
        List<InetSocketAddress> addresses = null;
        final int size = resolvedAddresses.size();
        for (int i = 0; i < size; i ++) {
            InetAddress a = resolvedAddresses.get(i);
            if (a instanceof Inet4Address) {
                if (addresses == null) {
                    addresses = new ArrayList<InetSocketAddress>();
                }
                addresses.add(new InetSocketAddress(a, port));
            }
        }
        if (addresses != null) {
            promise.trySuccess(addresses);
            return true;
        }

        return false;
    }

    @Override
    protected boolean finishResolveWithIPv6(List<InetAddress> resolvedAddresses, int port) {
        List<InetSocketAddress> addresses = null;
        final int size = resolvedAddresses.size();

        for (int i = 0; i < size; i ++) {
            InetAddress a = resolvedAddresses.get(i);
            if (a instanceof Inet6Address) {
                if (addresses == null) {
                    addresses = new ArrayList<InetSocketAddress>();
                }
                addresses.add(new InetSocketAddress(a, port));
            }
        }
        if (addresses != null) {
            promise.trySuccess(addresses);
            return true;
        }

        return false;
    }
}
