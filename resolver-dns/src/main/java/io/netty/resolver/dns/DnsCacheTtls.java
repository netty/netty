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

import io.netty.handler.codec.dns.DnsRecord;
import io.netty.util.internal.StringUtil;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * The TTL rules shared by {@link DefaultDnsCache} and {@link DefaultDnsHttpsCache}.
 */
final class DnsCacheTtls {

    private final int minTtl;
    private final int maxTtl;
    private final int negativeTtl;

    DnsCacheTtls(int minTtl, int maxTtl, int negativeTtl) {
        this.minTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(minTtl, "minTtl"));
        this.maxTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(maxTtl, "maxTtl"));
        if (minTtl > maxTtl) {
            throw new IllegalArgumentException(
                    "minTtl: " + minTtl + ", maxTtl: " + maxTtl + " (expected: 0 <= minTtl <= maxTtl)");
        }
        this.negativeTtl = Math.min(Cache.MAX_SUPPORTED_TTL_SECS, checkPositiveOrZero(negativeTtl, "negativeTtl"));
    }

    int minTtl() {
        return minTtl;
    }

    int maxTtl() {
        return maxTtl;
    }

    int negativeTtl() {
        return negativeTtl;
    }

    /**
     * Returns {@code true} if a successful result for a query with {@code additionals} is cached.
     */
    boolean cachesResult(DnsRecord[] additionals) {
        return maxTtl != 0 && emptyAdditionals(additionals);
    }

    /**
     * Returns {@code true} if a failure for a query with {@code additionals} is cached.
     */
    boolean cachesFailure(DnsRecord[] additionals) {
        return negativeTtl != 0 && emptyAdditionals(additionals);
    }

    /**
     * Returns the TTL to cache a successful result with, {@code originalTtl} clamped to the minimum and maximum TTL.
     */
    int ttl(long originalTtl) {
        return Math.max(minTtl, (int) Math.min(maxTtl, originalTtl));
    }

    static boolean emptyAdditionals(DnsRecord[] additionals) {
        return additionals == null || additionals.length == 0;
    }

    static String appendDot(String hostname) {
        return StringUtil.endsWith(hostname, '.') ? hostname : hostname + '.';
    }
}
