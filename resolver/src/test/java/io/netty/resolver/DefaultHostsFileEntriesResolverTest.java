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
import java.util.HashMap;
import java.util.Map;

public class DefaultHostsFileEntriesResolverTest {

    /**
     * show issue https://github.com/netty/netty/issues/5182
     * HostsFileParser tries to resolve hostnames as case-sensitive
     */
    @Test
    public void testCaseInsensitivity() throws Exception {
        DefaultHostsFileEntriesResolver resolver = new DefaultHostsFileEntriesResolver();
        //normalized somehow
        Assert.assertEquals(resolver.normalize("localhost"), resolver.normalize("LOCALHOST"));
    }

    @Test
    public void shouldntFindWhenAddressTypeDoesntMatch() {
        Map<String, Inet4Address> inet4Entries = new HashMap<String, Inet4Address>();
        Map<String, Inet6Address> inet6Entries = new HashMap<String, Inet6Address>();

        inet4Entries.put("localhost", NetUtil.LOCALHOST4);

        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(new HostsFileEntries(inet4Entries, inet6Entries));

        InetAddress address = resolver.address("localhost", ResolvedAddressTypes.IPV6_ONLY);
        Assert.assertNull("Should pick an IPv6 address", address);
    }

    @Test
    public void shouldPickIpv4WhenBothAreDefinedButIpv4IsPreferred() {
        Map<String, Inet4Address> inet4Entries = new HashMap<String, Inet4Address>();
        Map<String, Inet6Address> inet6Entries = new HashMap<String, Inet6Address>();

        inet4Entries.put("localhost", NetUtil.LOCALHOST4);
        inet6Entries.put("localhost", NetUtil.LOCALHOST6);

        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(new HostsFileEntries(inet4Entries, inet6Entries));

        InetAddress address = resolver.address("localhost", ResolvedAddressTypes.IPV4_PREFERRED);
        Assert.assertTrue("Should pick an IPv4 address", address instanceof Inet4Address);
    }

    @Test
    public void shouldPickIpv6WhenBothAreDefinedButIpv6IsPreferred() {
        Map<String, Inet4Address> inet4Entries = new HashMap<String, Inet4Address>();
        Map<String, Inet6Address> inet6Entries = new HashMap<String, Inet6Address>();

        inet4Entries.put("localhost", NetUtil.LOCALHOST4);
        inet6Entries.put("localhost", NetUtil.LOCALHOST6);

        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(new HostsFileEntries(inet4Entries, inet6Entries));

        InetAddress address = resolver.address("localhost", ResolvedAddressTypes.IPV6_PREFERRED);
        Assert.assertTrue("Should pick an IPv6 address", address instanceof Inet6Address);
    }
}
