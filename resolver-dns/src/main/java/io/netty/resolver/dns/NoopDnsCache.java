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
import io.netty.util.internal.UnstableApi;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

/**
 * A noop DNS cache that actually never caches anything.
 */
@UnstableApi
public final class NoopDnsCache implements DnsCache {

    public static final NoopDnsCache INSTANCE = new NoopDnsCache();

    /**
     * Private singleton constructor.
     */
    private NoopDnsCache() {
    }

    @Override
    public void clear() {
    }

    @Override
    public boolean clear(String hostname) {
        return false;
    }

    @Override
    public List<DnsCacheEntry> get(String hostname, DnsRecord[] additionals) {
        return Collections.emptyList();
    }

    @Override
    public void cache(String hostname, DnsRecord[] additional,
                      InetAddress address, long originalTtl, EventLoop loop) {
    }

    @Override
    public void cache(String hostname, DnsRecord[] additional, Throwable cause, EventLoop loop) {
    }

    @Override
    public String toString() {
        return NoopDnsCache.class.getSimpleName();
    }
}
