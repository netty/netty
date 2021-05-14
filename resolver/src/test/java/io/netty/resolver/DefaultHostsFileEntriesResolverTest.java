/*
 * Copyright 2016 The Netty Project
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
package io.netty.resolver;

import io.netty.util.NetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DefaultHostsFileEntriesResolverTest {

    /**
     * show issue https://github.com/netty/netty/issues/5182
     * HostsFileParser tries to resolve hostnames as case-sensitive
     */
    @Test
    public void testCaseInsensitivity() {
        DefaultHostsFileEntriesResolver resolver = new DefaultHostsFileEntriesResolver();
        //normalized somehow
        assertEquals(resolver.normalize("localhost"), resolver.normalize("LOCALHOST"));
    }

    @Test
    public void shouldntFindWhenAddressTypeDoesntMatch() {
        Map<String, List<InetAddress>> inet4Entries = new HashMap<String, List<InetAddress>>();
        Map<String, List<InetAddress>> inet6Entries = new HashMap<String, List<InetAddress>>();

        inet4Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST4));

        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(new HostsFileEntriesProvider(inet4Entries, inet6Entries));

        InetAddress address = resolver.address("localhost", ResolvedAddressTypes.IPV6_ONLY);
        assertNull("Should pick an IPv6 address", address);
    }

    @Test
    public void shouldPickIpv4WhenBothAreDefinedButIpv4IsPreferred() {
        Map<String, List<InetAddress>> inet4Entries = new HashMap<String, List<InetAddress>>();
        Map<String, List<InetAddress>> inet6Entries = new HashMap<String, List<InetAddress>>();

        inet4Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST4));
        inet6Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST6));

        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(new HostsFileEntriesProvider(inet4Entries, inet6Entries));

        InetAddress address = resolver.address("localhost", ResolvedAddressTypes.IPV4_PREFERRED);
        assertTrue("Should pick an IPv4 address", address instanceof Inet4Address);
    }

    @Test
    public void shouldPickIpv6WhenBothAreDefinedButIpv6IsPreferred() {
        Map<String, List<InetAddress>> inet4Entries = new HashMap<String, List<InetAddress>>();
        Map<String, List<InetAddress>> inet6Entries = new HashMap<String, List<InetAddress>>();

        inet4Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST4));
        inet6Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST6));

        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(new HostsFileEntriesProvider(inet4Entries, inet6Entries));

        InetAddress address = resolver.address("localhost", ResolvedAddressTypes.IPV6_PREFERRED);
        assertTrue("Should pick an IPv6 address", address instanceof Inet6Address);
    }

    @Test
    public void shouldntFindWhenAddressesTypeDoesntMatch() {
        Map<String, List<InetAddress>> inet4Entries = new HashMap<String, List<InetAddress>>();
        Map<String, List<InetAddress>> inet6Entries = new HashMap<String, List<InetAddress>>();

        inet4Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST4));

        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(new HostsFileEntriesProvider(inet4Entries, inet6Entries));

        List<InetAddress> addresses = resolver.addresses("localhost", ResolvedAddressTypes.IPV6_ONLY);
        assertNull("Should pick an IPv6 address", addresses);
    }

    @Test
    public void shouldPickIpv4FirstWhenBothAreDefinedButIpv4IsPreferred() {
        Map<String, List<InetAddress>> inet4Entries = new HashMap<String, List<InetAddress>>();
        Map<String, List<InetAddress>> inet6Entries = new HashMap<String, List<InetAddress>>();

        inet4Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST4));
        inet6Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST6));

        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(new HostsFileEntriesProvider(inet4Entries, inet6Entries));

        List<InetAddress> addresses = resolver.addresses("localhost", ResolvedAddressTypes.IPV4_PREFERRED);
        assertNotNull(addresses);
        assertEquals(2, addresses.size());
        assertTrue("Should pick an IPv4 address", addresses.get(0) instanceof Inet4Address);
        assertTrue("Should pick an IPv6 address", addresses.get(1) instanceof Inet6Address);
    }

    @Test
    public void shouldPickIpv6FirstWhenBothAreDefinedButIpv6IsPreferred() {
        Map<String, List<InetAddress>> inet4Entries = new HashMap<String, List<InetAddress>>();
        Map<String, List<InetAddress>> inet6Entries = new HashMap<String, List<InetAddress>>();

        inet4Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST4));
        inet6Entries.put("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST6));

        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(new HostsFileEntriesProvider(inet4Entries, inet6Entries));

        List<InetAddress> addresses = resolver.addresses("localhost", ResolvedAddressTypes.IPV6_PREFERRED);
        assertNotNull(addresses);
        assertEquals(2, addresses.size());
        assertTrue("Should pick an IPv6 address", addresses.get(0) instanceof Inet6Address);
        assertTrue("Should pick an IPv4 address", addresses.get(1) instanceof Inet4Address);
    }
}
