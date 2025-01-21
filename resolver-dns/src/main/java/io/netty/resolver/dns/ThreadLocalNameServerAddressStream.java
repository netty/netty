/*
 * Copyright 2024 The Netty Project
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

import io.netty.util.concurrent.FastThreadLocal;

import java.net.InetSocketAddress;

/**
 * A thread local based address stream for a specific hostname.
 */
final class ThreadLocalNameServerAddressStream implements DnsServerAddressStream {

    private final String hostname;
    private final DnsServerAddressStreamProvider dnsServerAddressStreamProvider;
    private final FastThreadLocal<DnsServerAddressStream> threadLocal = new FastThreadLocal<DnsServerAddressStream>() {
        @Override
        protected DnsServerAddressStream initialValue() {
            return dnsServerAddressStreamProvider.nameServerAddressStream(hostname);
        }
    };

    ThreadLocalNameServerAddressStream(DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        this(dnsServerAddressStreamProvider, "");
    }

    ThreadLocalNameServerAddressStream(DnsServerAddressStreamProvider dnsServerAddressStreamProvider, String hostname) {
        this.dnsServerAddressStreamProvider = dnsServerAddressStreamProvider;
        this.hostname = hostname;
    }

    @Override
    public InetSocketAddress next() {
        return threadLocal.get().next();
    }

    @Override
    public DnsServerAddressStream duplicate() {
        return new ThreadLocalNameServerAddressStream(dnsServerAddressStreamProvider, hostname);
    }

    @Override
    public int size() {
        return threadLocal.get().size();
    }
}
