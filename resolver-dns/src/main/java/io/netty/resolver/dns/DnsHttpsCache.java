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

import java.util.List;

/**
 * Caches the HTTPS records of resolved names, like {@link DnsCache} does for addresses.
 */
interface DnsHttpsCache {

    /**
     * Clears all the resolved records and failures in this cache.
     */
    void clear();

    /**
     * Clears the resolved records and failure of the specified host name from this cache.
     *
     * @param hostname the hostname
     * @return {@code true} if and only if there was an entry for the specified host name in the cache and
     *         it has been removed by this method
     */
    boolean clear(String hostname);

    /**
     * Returns the cached entry for the specified host name, or {@code null} if none.
     *
     * @param hostname the hostname
     * @param additionals the additional records
     * @return the cached entry
     */
    DnsHttpsCacheEntry get(String hostname, DnsRecord[] additionals);

    /**
     * Caches the HTTPS records of a host name.
     *
     * @param hostname the hostname
     * @param additionals the additional records
     * @param records the HTTPS records
     * @param originalTtl the TTL as returned by the DNS server
     * @param loop the {@link EventLoop} used to register the TTL timeout
     * @return the cached entry
     */
    DnsHttpsCacheEntry cache(String hostname, DnsRecord[] additionals, List<DnsSvcbRecord> records,
                             long originalTtl, EventLoop loop);

    /**
     * Caches a failed HTTPS resolution.
     *
     * @param hostname the hostname
     * @param additionals the additional records
     * @param cause the resolution failure
     * @param loop the {@link EventLoop} used to register the TTL timeout
     * @return the cached entry
     */
    DnsHttpsCacheEntry cache(String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop);
}
