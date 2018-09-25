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

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Default implementation of a {@link RedirectDnsServerAddressStreamProvider} which just uses a {@link Comparator}
 * to sorty the {@link List} of servers and returns a sequential {@link DnsServerAddressStream} out of it.
 */
@UnstableApi
public final class DefaultRedirectDnsServerAddressStreamProvider implements RedirectDnsServerAddressStreamProvider {

    private final Comparator<InetSocketAddress> nameServerComparator;

    public DefaultRedirectDnsServerAddressStreamProvider(Comparator<InetSocketAddress> nameServerComparator) {
        this.nameServerComparator = ObjectUtil.checkNotNull(nameServerComparator, "nameServerComparator");
    }

    @Override
    public DnsServerAddressStream nameServerAddressStream(String hostname, List<InetSocketAddress> nameservers) {
        Collections.sort(nameservers, nameServerComparator);
        return new SequentialDnsServerAddressStream(nameservers, 0);
    }
}
