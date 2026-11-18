/*
 * Copyright 2026 The Netty Project
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

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsSvcbRecord;
import io.netty.util.concurrent.Ticker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link DnsHttpsCache}, with the same TTL rules as {@link DefaultDnsCache}.
 */
final class DefaultDnsHttpsCache implements DnsHttpsCache {

    private final Cache<DefaultDnsHttpsCacheEntry> cache = new Cache<DefaultDnsHttpsCacheEntry>() {
        @Override
        protected boolean shouldReplaceAll(DefaultDnsHttpsCacheEntry entry) {
            return true;
        }

        @Override
        protected boolean equals(DefaultDnsHttpsCacheEntry entry, DefaultDnsHttpsCacheEntry otherEntry) {
            return entry == otherEntry;
        }
    };

    private final DnsCacheTtls ttls;

    /**
     * Creates a cache with the same defaults as {@link DefaultDnsCache#DefaultDnsCache()}: records are cached for
     * their TTL, and failures are not cached.
     */
    DefaultDnsHttpsCache() {
        this(0, Cache.MAX_SUPPORTED_TTL_SECS, 0);
    }

    /**
     * Creates a cache.
     *
     * @param minTtl the minimum TTL
     * @param maxTtl the maximum TTL
     * @param negativeTtl the TTL for failed resolutions
     */
    DefaultDnsHttpsCache(int minTtl, int maxTtl, int negativeTtl) {
        ttls = new DnsCacheTtls(minTtl, maxTtl, negativeTtl);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public boolean clear(String hostname) {
        checkNotNull(hostname, "hostname");
        return cache.clear(DnsCacheTtls.appendDot(hostname));
    }

    @Override
    public DnsHttpsCacheEntry get(String hostname, DnsRecord[] additionals) {
        checkNotNull(hostname, "hostname");
        if (!DnsCacheTtls.emptyAdditionals(additionals)) {
            return null;
        }
        List<? extends DefaultDnsHttpsCacheEntry> entries = cache.get(DnsCacheTtls.appendDot(hostname));
        return entries == null || entries.isEmpty() ? null : entries.get(0);
    }

    @Override
    public DnsHttpsCacheEntry cache(String hostname, DnsRecord[] additionals, List<DnsSvcbRecord> records,
                                    long originalTtl, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(records, "records");
        checkNotNull(loop, "loop");
        final int ttl = ttls.ttl(originalTtl);
        DefaultDnsHttpsCacheEntry e = new DefaultDnsHttpsCacheEntry(
                Collections.unmodifiableList(new ArrayList<DnsSvcbRecord>(records)), null, loop.ticker(), ttl);
        if (!ttls.cachesResult(additionals)) {
            return e;
        }
        cache.cache(DnsCacheTtls.appendDot(hostname), e, ttl, loop);
        return e;
    }

    @Override
    public DnsHttpsCacheEntry cache(String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(cause, "cause");
        checkNotNull(loop, "loop");
        DefaultDnsHttpsCacheEntry e = new DefaultDnsHttpsCacheEntry(null, cause, loop.ticker(), ttls.negativeTtl());
        if (!ttls.cachesFailure(additionals)) {
            return e;
        }
        cache.cache(DnsCacheTtls.appendDot(hostname), e, ttls.negativeTtl(), loop);
        return e;
    }

    private static final class DefaultDnsHttpsCacheEntry implements DnsHttpsCacheEntry {
        private final List<DnsSvcbRecord> records;
        private final Throwable cause;
        private final long deadlineNanos;

        DefaultDnsHttpsCacheEntry(List<DnsSvcbRecord> records, Throwable cause, Ticker ticker, int ttl) {
            this.records = records;
            this.cause = cause;
            deadlineNanos = ticker.nanoTime() + TimeUnit.SECONDS.toNanos(ttl);
        }

        @Override
        public List<DnsSvcbRecord> records() {
            return records;
        }

        @Override
        public Throwable cause() {
            return cause;
        }

        @Override
        public long deadlineNanos() {
            return deadlineNanos;
        }
    }
}
