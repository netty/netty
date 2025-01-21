/*
 * Copyright 2018 The Netty Project
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
import io.netty.util.AsciiString;

import java.util.List;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * Default implementation of a {@link DnsCnameCache}.
 */
public final class DefaultDnsCnameCache implements DnsCnameCache {
    private final int minTtl;
    private final int maxTtl;

    private final Cache<String> cache = new Cache<String>() {
        @Override
        protected boolean shouldReplaceAll(String entry) {
            // Only one 1:1 mapping is supported as specified in the RFC.
            return true;
        }

        @Override
        protected boolean equals(String entry, String otherEntry) {
            return AsciiString.contentEqualsIgnoreCase(entry, otherEntry);
        }
    };

    /**
     * Create a cache that respects the TTL returned by the DNS server.
     */
    public DefaultDnsCnameCache() {
        this(0, Cache.MAX_SUPPORTED_TTL_SECS);
    }

    /**
     * Create a cache.
     *
     * @param minTtl the minimum TTL
     * @param maxTtl the maximum TTL
     */
    public DefaultDnsCnameCache(int minTtl, int maxTtl) {
        this.minTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(minTtl, "minTtl"));
        this.maxTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositive(maxTtl, "maxTtl"));
        if (minTtl > maxTtl) {
            throw new IllegalArgumentException(
                    "minTtl: " + minTtl + ", maxTtl: " + maxTtl + " (expected: 0 <= minTtl <= maxTtl)");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public String get(String hostname) {
        List<? extends String> cached =  cache.get(checkNotNull(hostname, "hostname"));
        if (cached == null || cached.isEmpty()) {
            return null;
        }
        // We can never have more then one record.
        return cached.get(0);
    }

    @Override
    public void cache(String hostname, String cname, long originalTtl, EventLoop loop) {
        checkNotNull(hostname, "hostname");
        checkNotNull(cname, "cname");
        checkNotNull(loop, "loop");
        cache.cache(hostname, cname, Math.max(minTtl, (int) Math.min(maxTtl, originalTtl)), loop);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public boolean clear(String hostname) {
        return cache.clear(checkNotNull(hostname, "hostname"));
    }

    // Package visibility for testing purposes
    int minTtl() {
        return minTtl;
    }

    // Package visibility for testing purposes
    int maxTtl() {
        return maxTtl;
    }
}
