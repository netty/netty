/*
 * Copyright 2016 The Netty Project
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
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.UnstableApi;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Default implementation of {@link DnsCache}, backed by a {@link ConcurrentMap}.
 * If any additional {@link DnsRecord} is used, no caching takes place.
 */
@UnstableApi
public class DefaultDnsCache implements DnsCache {

    private final ConcurrentMap<String, List<DnsCacheEntry>> resolveCache = PlatformDependent.newConcurrentHashMap();
    private final int minTtl;
    private final int maxTtl;
    private final int negativeTtl;

    /**
     * Create a cache that respects the TTL returned by the DNS server
     * and doesn't cache negative responses.
     */
    public DefaultDnsCache() {
        this(0, Integer.MAX_VALUE, 0);
    }

    /**
     * Create a cache.
     * @param minTtl the minimum TTL
     * @param maxTtl the maximum TTL
     * @param negativeTtl the TTL for failed queries
     */
    public DefaultDnsCache(int minTtl, int maxTtl, int negativeTtl) {
        this.minTtl = checkPositiveOrZero(minTtl, "minTtl");
        this.maxTtl = checkPositiveOrZero(maxTtl, "maxTtl");
        if (minTtl > maxTtl) {
            throw new IllegalArgumentException(
                    "minTtl: " + minTtl + ", maxTtl: " + maxTtl + " (expected: 0 <= minTtl <= maxTtl)");
        }
        this.negativeTtl = checkPositiveOrZero(negativeTtl, "negativeTtl");
    }

    /**
     * Returns the minimum TTL of the cached DNS resource records (in seconds).
     *
     * @see #maxTtl()
     */
    public int minTtl() {
        return minTtl;
    }

    /**
     * Returns the maximum TTL of the cached DNS resource records (in seconds).
     *
     * @see #minTtl()
     */
    public int maxTtl() {
        return maxTtl;
    }

    /**
     * Returns the TTL of the cache for the failed DNS queries (in seconds). The default value is {@code 0}, which
     * disables the cache for negative results.
     */
    public int negativeTtl() {
        return negativeTtl;
    }

    @Override
    public void clear() {
        for (Iterator<Map.Entry<String, List<DnsCacheEntry>>> i = resolveCache.entrySet().iterator(); i.hasNext();) {
            final Map.Entry<String, List<DnsCacheEntry>> e = i.next();
            i.remove();
            cancelExpiration(e.getValue());
        }
    }

    @Override
    public boolean clear(String hostname) {
        checkNotNull(hostname, "hostname");
        boolean removed = false;
        for (Iterator<Map.Entry<String, List<DnsCacheEntry>>> i = resolveCache.entrySet().iterator(); i.hasNext();) {
            final Map.Entry<String, List<DnsCacheEntry>> e = i.next();
            if (e.getKey().equals(hostname)) {
                i.remove();
                cancelExpiration(e.getValue());
                removed = true;
            }
        }
        return removed;
    }

    private static boolean emptyAdditionals(DnsRecord[] additionals) {
        return additionals == null || additionals.length == 0;
    }

    @Override
    public List<DnsCacheEntry> get(String hostname, DnsRecord[] additionals) {
        checkNotNull(hostname, "hostname");
        if (!emptyAdditionals(additionals)) {
            return null;
        }
        return resolveCache.get(hostname);
    }

    private List<DnsCacheEntry> cachedEntries(String hostname) {
        List<DnsCacheEntry> oldEntries = resolveCache.get(hostname);
        final List<DnsCacheEntry> entries;
        if (oldEntries == null) {
            List<DnsCacheEntry> newEntries = new ArrayList<DnsCacheEntry>(8);
            oldEntries = resolveCache.putIfAbsent(hostname, newEntries);
            entries = oldEntries != null? oldEntries : newEntries;
        } else {
            entries = oldEntries;
        }
        return entries;
    }

    @Override
    public void cache(String hostname, DnsRecord[] additionals,
                      InetAddress address, long originalTtl, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(address, "address");
        checkNotNull(loop, "loop");
        if (maxTtl == 0 || !emptyAdditionals(additionals)) {
            return;
        }
        final int ttl = Math.max(minTtl, (int) Math.min(maxTtl, originalTtl));
        final List<DnsCacheEntry> entries = cachedEntries(hostname);
        final DnsCacheEntry e = new DnsCacheEntry(hostname, address);

        synchronized (entries) {
            if (!entries.isEmpty()) {
                final DnsCacheEntry firstEntry = entries.get(0);
                if (firstEntry.cause() != null) {
                    assert entries.size() == 1;
                    firstEntry.cancelExpiration();
                    entries.clear();
                }
            }
            entries.add(e);
        }

        scheduleCacheExpiration(entries, e, ttl, loop);
    }

    @Override
    public void cache(String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(cause, "cause");
        checkNotNull(loop, "loop");

        if (negativeTtl == 0 || !emptyAdditionals(additionals)) {
            return;
        }
        final List<DnsCacheEntry> entries = cachedEntries(hostname);
        final DnsCacheEntry e = new DnsCacheEntry(hostname, cause);

        synchronized (entries) {
            final int numEntries = entries.size();
            for (int i = 0; i < numEntries; i ++) {
                entries.get(i).cancelExpiration();
            }
            entries.clear();
            entries.add(e);
        }

        scheduleCacheExpiration(entries, e, negativeTtl, loop);
    }

    private static void cancelExpiration(List<DnsCacheEntry> entries) {
        final int numEntries = entries.size();
        for (int i = 0; i < numEntries; i++) {
            entries.get(i).cancelExpiration();
        }
    }

    private void scheduleCacheExpiration(final List<DnsCacheEntry> entries,
                                         final DnsCacheEntry e,
                                         int ttl,
                                         EventLoop loop) {
        e.scheduleExpiration(loop, new Runnable() {
                    @Override
                    public void run() {
                        synchronized (entries) {
                            entries.remove(e);
                            if (entries.isEmpty()) {
                                resolveCache.remove(e.hostname());
                            }
                        }
                    }
                }, ttl, TimeUnit.SECONDS);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("DefaultDnsCache(minTtl=")
                .append(minTtl).append(", maxTtl=")
                .append(maxTtl).append(", negativeTtl=")
                .append(negativeTtl).append(", cached resolved hostname=")
                .append(resolveCache.size()).append(")")
                .toString();
    }
}
