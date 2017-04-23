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

import io.netty.util.NetUtil;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.internal.PlatformDependent;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

final class DnsQueryContextManager {

    /**
     * A map whose key is the DNS server address and value is the map of the DNS query ID and its corresponding
     * {@link DnsQueryContext}.
     */
    final Map<InetSocketAddress, IntObjectMap<DnsQueryContext>> map =
            new HashMap<InetSocketAddress, IntObjectMap<DnsQueryContext>>();

    int add(DnsQueryContext qCtx) {
        final IntObjectMap<DnsQueryContext> contexts = getOrCreateContextMap(qCtx.nameServerAddr());

        int id = PlatformDependent.threadLocalRandom().nextInt(65536 - 1) + 1;
        final int maxTries = 65535 << 1;
        int tries = 0;

        synchronized (contexts) {
            for (;;) {
                if (!contexts.containsKey(id)) {
                    contexts.put(id, qCtx);
                    return id;
                }

                id = id + 1 & 0xFFFF;

                if (++tries >= maxTries) {
                    throw new IllegalStateException("query ID space exhausted: " + qCtx.question());
                }
            }
        }
    }

    DnsQueryContext get(InetSocketAddress nameServerAddr, int id) {
        final IntObjectMap<DnsQueryContext> contexts = getContextMap(nameServerAddr);
        final DnsQueryContext qCtx;
        if (contexts != null) {
            synchronized (contexts) {
                qCtx = contexts.get(id);
            }
        } else {
            qCtx = null;
        }

        return qCtx;
    }

    DnsQueryContext remove(InetSocketAddress nameServerAddr, int id) {
        final IntObjectMap<DnsQueryContext> contexts = getContextMap(nameServerAddr);
        if (contexts == null) {
            return null;
        }

        synchronized (contexts) {
            return  contexts.remove(id);
        }
    }

    private IntObjectMap<DnsQueryContext> getContextMap(InetSocketAddress nameServerAddr) {
        synchronized (map) {
            return map.get(nameServerAddr);
        }
    }

    private IntObjectMap<DnsQueryContext> getOrCreateContextMap(InetSocketAddress nameServerAddr) {
        synchronized (map) {
            final IntObjectMap<DnsQueryContext> contexts = map.get(nameServerAddr);
            if (contexts != null) {
                return contexts;
            }

            final IntObjectMap<DnsQueryContext> newContexts = new IntObjectHashMap<DnsQueryContext>();
            final InetAddress a = nameServerAddr.getAddress();
            final int port = nameServerAddr.getPort();
            map.put(nameServerAddr, newContexts);

            if (a instanceof Inet4Address) {
                // Also add the mapping for the IPv4-compatible IPv6 address.
                final Inet4Address a4 = (Inet4Address) a;
                if (a4.isLoopbackAddress()) {
                    map.put(new InetSocketAddress(NetUtil.LOCALHOST6, port), newContexts);
                } else {
                    map.put(new InetSocketAddress(toCompactAddress(a4), port), newContexts);
                }
            } else if (a instanceof Inet6Address) {
                // Also add the mapping for the IPv4 address if this IPv6 address is compatible.
                final Inet6Address a6 = (Inet6Address) a;
                if (a6.isLoopbackAddress()) {
                    map.put(new InetSocketAddress(NetUtil.LOCALHOST4, port), newContexts);
                } else if (a6.isIPv4CompatibleAddress()) {
                    map.put(new InetSocketAddress(toIPv4Address(a6), port), newContexts);
                }
            }

            return newContexts;
        }
    }

    private static Inet6Address toCompactAddress(Inet4Address a4) {
        byte[] b4 = a4.getAddress();
        byte[] b6 = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b4[0], b4[1], b4[2], b4[3] };
        try {
            return (Inet6Address) InetAddress.getByAddress(b6);
        } catch (UnknownHostException e) {
            throw new Error(e);
        }
    }

    private static Inet4Address toIPv4Address(Inet6Address a6) {
        byte[] b6 = a6.getAddress();
        byte[] b4 = { b6[12], b6[13], b6[14], b6[15] };
        try {
            return (Inet4Address) InetAddress.getByAddress(b4);
        } catch (UnknownHostException e) {
            throw new Error(e);
        }
    }
}
