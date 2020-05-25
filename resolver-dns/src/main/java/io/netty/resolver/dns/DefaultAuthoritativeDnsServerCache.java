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
import io.netty.util.internal.PlatformDependent;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * Default implementation of {@link AuthoritativeDnsServerCache}, backed by a {@link ConcurrentMap}.
 */
public class DefaultAuthoritativeDnsServerCache implements AuthoritativeDnsServerCache {

    private final int minTtl;
    private final int maxTtl;
    private final Comparator<InetSocketAddress> comparator;
    private final Cache<InetSocketAddress> resolveCache = new Cache<InetSocketAddress>() {
        @Override
        protected boolean shouldReplaceAll(InetSocketAddress entry) {
            return false;
        }

        @Override
        protected boolean equals(InetSocketAddress entry, InetSocketAddress otherEntry) {
            if (PlatformDependent.javaVersion() >= 7) {
                return entry.getHostString().equalsIgnoreCase(otherEntry.getHostString());
            }
            return entry.getHostName().equalsIgnoreCase(otherEntry.getHostName());
        }

        @Override
        protected void sortEntries(String hostname, List<InetSocketAddress> entries) {
            if (comparator != null) {
                Collections.sort(entries, comparator);
            }
        }
    };

    /**
     * Create a cache that respects the TTL returned by the DNS server.
     */
    public DefaultAuthoritativeDnsServerCache() {
        this(0, Cache.MAX_SUPPORTED_TTL_SECS, null);
    }

    /**
     * Create a cache.
     *
     * @param minTtl the minimum TTL
     * @param maxTtl the maximum TTL
     * @param comparator the {@link Comparator} to order the {@link InetSocketAddress} for a hostname or {@code null}
     *                   if insertion order should be used.
     */
    public DefaultAuthoritativeDnsServerCache(int minTtl, int maxTtl, Comparator<InetSocketAddress> comparator) {
        this.minTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(minTtl, "minTtl"));
        this.maxTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositive(maxTtl, "maxTtl"));
        if (minTtl > maxTtl) {
            throw new IllegalArgumentException(
                    "minTtl: " + minTtl + ", maxTtl: " + maxTtl + " (expected: 0 <= minTtl <= maxTtl)");
        }
        this.comparator = comparator;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DnsServerAddressStream get(String hostname) {
        checkNotNull(hostname, "hostname");

        List<? extends InetSocketAddress> addresses = resolveCache.get(hostname);
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        return new SequentialDnsServerAddressStream(addresses, 0);
    }

    @Override
    public void cache(String hostname, InetSocketAddress address, long originalTtl, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(address, "address");
        checkNotNull(loop, "loop");

        if (PlatformDependent.javaVersion() >= 7 && address.getHostString() == null) {
            // We only cache addresses that have also a host string as we will need it later when trying to replace
            // unresolved entries in the cache.
            return;
        }

        resolveCache.cache(hostname, address, Math.max(minTtl, (int) Math.min(maxTtl, originalTtl)), loop);
    }

    @Override
    public void clear() {
        resolveCache.clear();
    }

    @Override
    public boolean clear(String hostname) {
        return resolveCache.clear(checkNotNull(hostname, "hostname"));
    }

    @Override
    public String toString() {
        return "DefaultAuthoritativeDnsServerCache(minTtl=" + minTtl + ", maxTtl=" + maxTtl + ", cached nameservers=" +
                resolveCache.size() + ')';
    }
}
