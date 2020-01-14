/*
 * Copyright 2018 The Netty Project
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

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsRecord;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * {@link AuthoritativeDnsServerCache} implementation which delegates all operations to a wrapped {@link DnsCache}.
 * This implementation is only present to preserve a upgrade story.
 */
final class AuthoritativeDnsServerCacheAdapter implements AuthoritativeDnsServerCache {

    private static final DnsRecord[] EMPTY = new DnsRecord[0];
    private final DnsCache cache;

    AuthoritativeDnsServerCacheAdapter(DnsCache cache) {
        this.cache = checkNotNull(cache, "cache");
    }

    @Override
    public DnsServerAddressStream get(String hostname) {
        List<? extends DnsCacheEntry> entries = cache.get(hostname, EMPTY);
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        if (entries.get(0).cause() != null) {
            return null;
        }

        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(entries.size());

        int i = 0;
        do {
            InetAddress addr = entries.get(i).address();
            addresses.add(new InetSocketAddress(addr, DefaultDnsServerAddressStreamProvider.DNS_PORT));
        } while (++i < entries.size());
        return new SequentialDnsServerAddressStream(addresses, 0);
    }

    @Override
    public void cache(String hostname, InetSocketAddress address, long originalTtl, EventLoop loop) {
        // We only cache resolved addresses.
        if (!address.isUnresolved()) {
            cache.cache(hostname, EMPTY, address.getAddress(), originalTtl, loop);
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public boolean clear(String hostname) {
        return cache.clear(hostname);
    }
}
