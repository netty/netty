/*
 * Copyright 2017 The Netty Project
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

import io.netty.util.internal.UnstableApi;

import java.net.InetSocketAddress;

import static io.netty.resolver.dns.DnsServerAddresses.sequential;

/**
 * A {@link DnsServerAddressStreamProvider} which is backed by a sequential list of DNS servers.
 */
@UnstableApi
public final class SequentialDnsServerAddressStreamProvider extends UniSequentialDnsServerAddressStreamProvider {
    /**
     * Create a new instance.
     * @param addresses The addresses which will be be returned in sequential order via
     * {@link #nameServerAddressStream(String)}
     */
    public SequentialDnsServerAddressStreamProvider(InetSocketAddress... addresses) {
        super(sequential(addresses));
    }

    /**
     * Create a new instance.
     * @param addresses The addresses which will be be returned in sequential order via
     * {@link #nameServerAddressStream(String)}
     */
    public SequentialDnsServerAddressStreamProvider(Iterable<? extends InetSocketAddress> addresses) {
        super(sequential(addresses));
    }
}
