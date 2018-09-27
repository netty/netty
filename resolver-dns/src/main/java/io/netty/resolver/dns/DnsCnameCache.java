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
import io.netty.util.internal.UnstableApi;

/**
 * A cache for {@code CNAME}s.
 */
@UnstableApi
public interface DnsCnameCache {

    /**
     * Returns the cached cname for the given hostname.
     *
     * @param hostname the hostname
     * @return the cached entries or an {@code null} if none.
     */
    String get(String hostname);

    /**
     * Caches a cname entry that should be used for the given hostname.
     *
     * @param hostname the hostname
     * @param cname the cname mapping.
     * @param originalTtl the TTL as returned by the DNS server
     * @param loop the {@link EventLoop} used to register the TTL timeout
     */
    void cache(String hostname, String cname, long originalTtl, EventLoop loop);

    /**
     * Clears all cached nameservers.
     *
     * @see #clear(String)
     */
    void clear();

    /**
     * Clears the cached nameservers for the specified hostname.
     *
     * @return {@code true} if and only if there was an entry for the specified host name in the cache and
     *         it has been removed by this method
     */
    boolean clear(String hostname);
}
