/*
 * Copyright 2015 The Netty Project
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
    private final Map<InetSocketAddress, DnsQueryContextMap> map =
            new HashMap<InetSocketAddress, DnsQueryContextMap>();

    int add(InetSocketAddress nameServerAddr, DnsQueryContext qCtx) {
        final DnsQueryContextMap contexts = getOrCreateContextMap(nameServerAddr);
        return contexts.add(qCtx);
    }

    DnsQueryContext get(InetSocketAddress nameServerAddr, int id) {
        final DnsQueryContextMap contexts = getContextMap(nameServerAddr);
        if (contexts == null) {
            return null;
        }
        return contexts.get(id);
    }

    DnsQueryContext remove(InetSocketAddress nameServerAddr, int id) {
        final DnsQueryContextMap contexts = getContextMap(nameServerAddr);
        if (contexts == null) {
            return null;
        }
        return contexts.remove(id);
    }

    private DnsQueryContextMap getContextMap(InetSocketAddress nameServerAddr) {
        synchronized (map) {
            return map.get(nameServerAddr);
        }
    }

    private DnsQueryContextMap getOrCreateContextMap(InetSocketAddress nameServerAddr) {
        synchronized (map) {
            final DnsQueryContextMap contexts = map.get(nameServerAddr);
            if (contexts != null) {
                return contexts;
            }

            final DnsQueryContextMap newContexts = new DnsQueryContextMap();
            final InetAddress a = nameServerAddr.getAddress();
            final int port = nameServerAddr.getPort();
            DnsQueryContextMap old = map.put(nameServerAddr, newContexts);
            // Assert that we didn't replace an existing mapping.
            assert old == null : "DnsQueryContextMap already exists for " + nameServerAddr;

            InetSocketAddress extraAddress = null;
            if (a instanceof Inet4Address) {
                // Also add the mapping for the IPv4-compatible IPv6 address.
                final Inet4Address a4 = (Inet4Address) a;
                if (a4.isLoopbackAddress()) {
                    extraAddress = new InetSocketAddress(NetUtil.LOCALHOST6, port);
                } else {
                    extraAddress = new InetSocketAddress(toCompactAddress(a4), port);
                }
            } else if (a instanceof Inet6Address) {
                // Also add the mapping for the IPv4 address if this IPv6 address is compatible.
                final Inet6Address a6 = (Inet6Address) a;
                if (a6.isLoopbackAddress()) {
                    extraAddress = new InetSocketAddress(NetUtil.LOCALHOST4, port);
                } else if (a6.isIPv4CompatibleAddress()) {
                    extraAddress = new InetSocketAddress(toIPv4Address(a6), port);
                }
            }
            if (extraAddress != null) {
                old = map.put(extraAddress, newContexts);
                // Assert that we didn't replace an existing mapping.
                assert old == null : "DnsQueryContextMap already exists for " + extraAddress;
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
        assert a6.isIPv4CompatibleAddress();

        byte[] b6 = a6.getAddress();
        byte[] b4 = { b6[12], b6[13], b6[14], b6[15] };
        try {
            return (Inet4Address) InetAddress.getByAddress(b4);
        } catch (UnknownHostException e) {
            throw new Error(e);
        }
    }

    private static final class DnsQueryContextMap {
        private static final int MAX_ID = 65535;
        private static final int MAX_TRIES = MAX_ID << 1;

        // We increment on every usage so start with -1, this will ensure we start with 0 as first id.
        private final IntObjectMap<DnsQueryContext> map = new IntObjectHashMap<DnsQueryContext>();

        synchronized int add(DnsQueryContext ctx) {
            int tries = 0;
            int id = PlatformDependent.threadLocalRandom().nextInt(MAX_ID - 1) + 1;
            for (;;) {
                // Let's directly use put as its very unlikely that we still have the id in use.
                DnsQueryContext oldCtx = map.put(id, ctx);
                if (oldCtx == null) {
                    return id;
                }
                // Restore the mapping to the old context.
                map.put(id, oldCtx);

                id = id + 1 & 0xFFFF;
                if (++tries >= MAX_TRIES) {
                    throw new IllegalStateException(
                            "query ID space exhausted after " + MAX_TRIES + ": " + ctx.question());
                }
            }
        }

        synchronized DnsQueryContext get(int id) {
            return map.get(id);
        }

        synchronized DnsQueryContext remove(int id) {
            return map.remove(id);
        }
    }
}
