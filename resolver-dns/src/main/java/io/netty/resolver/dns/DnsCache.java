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

import java.net.InetAddress;
import java.util.List;

/**
 * A cache for DNS resolution entries.
 */
public interface DnsCache {

    /**
     * Clears all the resolved addresses cached by this resolver.
     *
     * @see #clear(String)
     */
    void clear();

    /**
     * Clears the resolved addresses of the specified host name from the cache of this resolver.
     *
     * @return {@code true} if and only if there was an entry for the specified host name in the cache and
     *         it has been removed by this method
     */
    boolean clear(String hostname);

    /**
     * Return the cached entries for the given hostname.
     * @param hostname the hostname
     * @return the cached entries
     */
    List<DnsCacheEntry> get(String hostname);

    /**
     * Cache a resolved address for a given hostname.
     * @param hostname the hostname
     * @param address the resolved adresse
     * @param originalTtl the TLL as returned by the DNS server
     * @param loop the {@link EventLoop} used to register the TTL timeout
     */
    void cache(String hostname, InetAddress address, long originalTtl, EventLoop loop);

    /**
     * Cache the resolution failure for a given hostname.
     * @param hostname the hostname
     * @param cause the resolution failure
     * @param loop the {@link EventLoop} used to register the TTL timeout
     */
    void cache(String hostname, Throwable cause, EventLoop loop);
}
