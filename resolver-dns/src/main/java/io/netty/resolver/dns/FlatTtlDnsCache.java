/*
 * Copyright 2020 The Netty Project
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

/**
 * A {@link DnsCache} implementation that uses cache TTL instead of DNS record TTL.
 * If any additional {@link DnsRecord} is used, no caching takes place.
 */
public class FlatTtlDnsCache extends DefaultDnsCache {

    private final int ttl;

    /**
     * Creates a FlatTtlDnsCache with negative caching disabled.
     * @param ttl TTL in seconds to use for caching all DNS records.
     */
    public FlatTtlDnsCache(int ttl) {
        this(ttl, 0);
    }

    /**
     * Creates a FlatTtlDnsCache.
     * @param ttl TTL in seconds to use for caching all DNS records.
     * @param negativeTtl TTL in seconds to retain bad DNS answers. 0 to not no cache.
     */
    public FlatTtlDnsCache(int ttl, int negativeTtl) {
        super(ttl, ttl, negativeTtl);
        this.ttl = ttl;
    }

    @Override
    public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, InetAddress address, long originalTtl,
                               EventLoop loop) {
        return super.cache(hostname, additionals, address, ttl, loop);
    }
}
