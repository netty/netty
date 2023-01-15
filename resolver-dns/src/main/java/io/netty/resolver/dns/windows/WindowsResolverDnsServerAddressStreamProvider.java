/*
 * Copyright 2022 The Netty Project
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
package io.netty.resolver.dns.windows;

import io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider;
import io.netty.resolver.dns.DnsServerAddressStream;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.DnsServerAddresses;
import io.netty.util.internal.StringUtil;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WindowsResolverDnsServerAddressStreamProvider implements DnsServerAddressStreamProvider {

    private final Map<String, DnsServerAddresses> resolverMap;


    private WindowsResolverDnsServerAddressStreamProvider(List<NetworkAdapter> adapters) {
        this.resolverMap = buildMappings(adapters);
    }

    public static WindowsResolverDnsServerAddressStreamProvider loadConfig() {
        WindowsAdapterInfo.ensureAvailability();
        return new WindowsResolverDnsServerAddressStreamProvider(WindowsAdapterInfo.adapters());
    }

    public static List<String> getSearchDomains() {
        WindowsAdapterInfo.ensureAvailability();

        List<NetworkAdapter> adapters = WindowsAdapterInfo.adapters();

        List<String> searchDomains = new ArrayList<>(adapters.size());

        for (NetworkAdapter adapter : adapters) {
            searchDomains.addAll(adapter.getSearchDomains());
        }

        return searchDomains;
    }

    @Override
    public DnsServerAddressStream nameServerAddressStream(String hostname) {
        final String originalHostname = hostname;
        for (;;) {
            int i = hostname.indexOf('.', 1);
            if (i < 0 || i == hostname.length() - 1) {
                // Try access default mapping.
                DnsServerAddresses addresses = resolverMap.get(StringUtil.EMPTY_STRING);
                if (addresses != null) {
                    return addresses.stream();
                }
                return DefaultDnsServerAddressStreamProvider.INSTANCE.nameServerAddressStream(originalHostname);
            }

            DnsServerAddresses addresses = resolverMap.get(hostname);
            if (addresses != null) {
                return addresses.stream();
            }

            hostname = hostname.substring(i + 1);
        }
    }

    private static Map<String, DnsServerAddresses> buildMappings(List<NetworkAdapter> adapters) {
        if (adapters == null || adapters.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, DnsServerAddresses> resolverMap = new HashMap<>(adapters.size());
        for (NetworkAdapter adapter: adapters) {

            List<InetSocketAddress> nameservers = adapter.getNameservers();
            if (nameservers == null || nameservers.isEmpty()) {
                continue;
            }

            for (String domain : adapter.getSearchDomains()) {
                if (domain == null) {
                    // Default mapping.
                    domain = StringUtil.EMPTY_STRING;
                }

                List<InetSocketAddress> servers = adapter.getNameservers();
                for (int a = 0; a < servers.size(); a++) {
                    InetSocketAddress address = servers.get(a);
                    // Check if the default port should be used
                    if (address.getPort() == 0) {
                        servers.set(a, new InetSocketAddress(address.getAddress(), 53));
                    }
                }

                resolverMap.put(domain, DnsServerAddresses.sequential(servers));
            }
        }
        return resolverMap;
    }
}
