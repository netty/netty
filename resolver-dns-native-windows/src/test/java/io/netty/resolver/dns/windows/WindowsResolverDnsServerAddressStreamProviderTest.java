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

import io.netty.resolver.dns.DnsServerAddressStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.WINDOWS)
public class WindowsResolverDnsServerAddressStreamProviderTest {
    @Test
    void testNativeCall() {
        WindowsResolverDnsServerAddressStreamProvider.ensureAvailability();

        NetworkAdapter[] adapters = WindowsResolverDnsServerAddressStreamProvider.adapters();
        System.out.println("Adapter count: " + adapters.length);

        for (int i = 0; i < adapters.length; ++i) {
            System.out.println("Adapter " + i);
            System.out.println("v4Index " + adapters[i].getIpv4Index());
            System.out.println("v6Index " + adapters[i].getIpv6Index());
            for (InetSocketAddress addr : adapters[i].nameservers()) {
                System.out.println("Nameserver: " + addr.getAddress().getHostAddress());
            }

            for (String searchDomain : adapters[i].searchDomains()) {
                System.out.println("Search Domain: " + searchDomain);
            }
        }
    }

    @Test
    void resolverForGoogle() {
        WindowsResolverDnsServerAddressStreamProvider provider = new WindowsResolverDnsServerAddressStreamProvider();
        DnsServerAddressStream addressStream = provider.nameServerAddressStream("google.com");
        for(int i = 0; i < addressStream.size(); ++i) {
            System.out.println(addressStream.next().getHostString());
        }

    }

    @Test
    void resolverForSearchDomain() {
        WindowsResolverDnsServerAddressStreamProvider provider = new WindowsResolverDnsServerAddressStreamProvider();
        DnsServerAddressStream addressStream = provider.nameServerAddressStream("test.fritz.box");
        for(int i = 0; i < addressStream.size(); ++i) {
            System.out.println(addressStream.next().getHostString());
        }
    }

    @Test
    void resolverForLocalhost() {
        WindowsResolverDnsServerAddressStreamProvider provider = new WindowsResolverDnsServerAddressStreamProvider();
        DnsServerAddressStream addressStream = provider.nameServerAddressStream("localhost");
        for(int i = 0; i < addressStream.size(); ++i) {
            System.out.println(addressStream.next().getHostString());
        }
    }
}
