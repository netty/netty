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

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;

/**
 * A noop {@link AuthoritativeDnsServerCache} that actually never caches anything.
 */
@UnstableApi
public final class NoopAuthoritativeDnsServerCache implements AuthoritativeDnsServerCache {
    public static final NoopAuthoritativeDnsServerCache INSTANCE = new NoopAuthoritativeDnsServerCache();

    private NoopAuthoritativeDnsServerCache() { }

    @Override
    public DnsServerAddressStream get(String hostname) {
        return null;
    }

    @Override
    public void cache(String hostname, InetSocketAddress address, long originalTtl, EventLoop loop) {
        // NOOP
    }

    @Override
    public void clear() {
        // NOOP
    }

    @Override
    public boolean clear(String hostname) {
        return false;
    }
}
