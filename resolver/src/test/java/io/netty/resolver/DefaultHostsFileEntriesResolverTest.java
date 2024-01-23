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

import com.google.common.collect.Maps;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;

public class DefaultHostsFileEntriesResolverTest {
    private static final Map<String, List<InetAddress>> LOCALHOST_V4_ADDRESSES =
            Collections.singletonMap("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST4));
    private static final Map<String, List<InetAddress>> LOCALHOST_V6_ADDRESSES =
            Collections.singletonMap("localhost", Collections.<InetAddress>singletonList(NetUtil.LOCALHOST6));
    private static final long ENTRIES_TTL = TimeUnit.MINUTES.toNanos(1);

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
        HostsFileEntriesProvider.Parser parser = givenHostsParserWith(
                LOCALHOST_V4_ADDRESSES,
                Collections.<String, List<InetAddress>>emptyMap()
        );

        DefaultHostsFileEntriesResolver resolver = new DefaultHostsFileEntriesResolver(parser, ENTRIES_TTL);

        InetAddress address = resolver.address("localhost", ResolvedAddressTypes.IPV6_ONLY);
        assertNull(address, "Should pick an IPv6 address");
    }

    @Test
    public void shouldPickIpv4WhenBothAreDefinedButIpv4IsPreferred() {
        HostsFileEntriesProvider.Parser parser = givenHostsParserWith(
                LOCALHOST_V4_ADDRESSES,
                LOCALHOST_V6_ADDRESSES
        );

        DefaultHostsFileEntriesResolver resolver = new DefaultHostsFileEntriesResolver(parser, ENTRIES_TTL);

        InetAddress address = resolver.address("localhost", ResolvedAddressTypes.IPV4_PREFERRED);
        assertThat("Should pick an IPv4 address", address, instanceOf(Inet4Address.class));
    }

    @Test
    public void shouldPickIpv6WhenBothAreDefinedButIpv6IsPreferred() {
        HostsFileEntriesProvider.Parser parser = givenHostsParserWith(
                LOCALHOST_V4_ADDRESSES,
                LOCALHOST_V6_ADDRESSES
        );

        DefaultHostsFileEntriesResolver resolver = new DefaultHostsFileEntriesResolver(parser, ENTRIES_TTL);

        InetAddress address = resolver.address("localhost", ResolvedAddressTypes.IPV6_PREFERRED);
        assertThat("Should pick an IPv6 address", address, instanceOf(Inet6Address.class));
    }

    @Test
    public void shouldntFindWhenAddressesTypeDoesntMatch() {
        HostsFileEntriesProvider.Parser parser = givenHostsParserWith(
                LOCALHOST_V4_ADDRESSES,
                Collections.<String, List<InetAddress>>emptyMap()
        );

        DefaultHostsFileEntriesResolver resolver = new DefaultHostsFileEntriesResolver(parser, ENTRIES_TTL);

        List<InetAddress> addresses = resolver.addresses("localhost", ResolvedAddressTypes.IPV6_ONLY);
        assertNull(addresses, "Should pick an IPv6 address");
    }

    @Test
    public void shouldPickIpv4FirstWhenBothAreDefinedButIpv4IsPreferred() {
        HostsFileEntriesProvider.Parser parser = givenHostsParserWith(
                LOCALHOST_V4_ADDRESSES,
                LOCALHOST_V6_ADDRESSES
        );

        DefaultHostsFileEntriesResolver resolver = new DefaultHostsFileEntriesResolver(parser, ENTRIES_TTL);

        List<InetAddress> addresses = resolver.addresses("localhost", ResolvedAddressTypes.IPV4_PREFERRED);
        assertNotNull(addresses);
        assertEquals(2, addresses.size());
        assertThat("Should pick an IPv4 address", addresses.get(0), instanceOf(Inet4Address.class));
        assertThat("Should pick an IPv6 address", addresses.get(1), instanceOf(Inet6Address.class));
    }

    @Test
    public void shouldPickIpv6FirstWhenBothAreDefinedButIpv6IsPreferred() {
        HostsFileEntriesProvider.Parser parser = givenHostsParserWith(
                LOCALHOST_V4_ADDRESSES,
                LOCALHOST_V6_ADDRESSES
        );

        DefaultHostsFileEntriesResolver resolver = new DefaultHostsFileEntriesResolver(parser, ENTRIES_TTL);

        List<InetAddress> addresses = resolver.addresses("localhost", ResolvedAddressTypes.IPV6_PREFERRED);
        assertNotNull(addresses);
        assertEquals(2, addresses.size());
        assertThat("Should pick an IPv6 address", addresses.get(0), instanceOf(Inet6Address.class));
        assertThat("Should pick an IPv4 address", addresses.get(1), instanceOf(Inet4Address.class));
    }

    @Test
    public void shouldNotRefreshHostsFileContentBeforeRefreshIntervalElapsed() {
        Map<String, List<InetAddress>> v4Addresses = Maps.newHashMap(LOCALHOST_V4_ADDRESSES);
        Map<String, List<InetAddress>> v6Addresses = Maps.newHashMap(LOCALHOST_V6_ADDRESSES);
        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(givenHostsParserWith(v4Addresses, v6Addresses), ENTRIES_TTL);
        String newHost = UUID.randomUUID().toString();

        v4Addresses.put(newHost, Collections.<InetAddress>singletonList(NetUtil.LOCALHOST4));
        v6Addresses.put(newHost, Collections.<InetAddress>singletonList(NetUtil.LOCALHOST6));

        assertNull(resolver.address(newHost, ResolvedAddressTypes.IPV4_ONLY));
        assertNull(resolver.address(newHost, ResolvedAddressTypes.IPV6_ONLY));
    }

    @Test
    public void shouldRefreshHostsFileContentAfterRefreshInterval() throws Exception {
        Map<String, List<InetAddress>> v4Addresses = Maps.newHashMap(LOCALHOST_V4_ADDRESSES);
        Map<String, List<InetAddress>> v6Addresses = Maps.newHashMap(LOCALHOST_V6_ADDRESSES);
        DefaultHostsFileEntriesResolver resolver =
                new DefaultHostsFileEntriesResolver(givenHostsParserWith(v4Addresses, v6Addresses), /*nanos*/1);
        String newHost = UUID.randomUUID().toString();

        InetAddress address = resolver.address(newHost, ResolvedAddressTypes.IPV6_ONLY);
        assertNull(address);
        /*let refreshIntervalNanos = 1 elapse*/
        Thread.sleep(1);
        v4Addresses.put(newHost, Collections.<InetAddress>singletonList(NetUtil.LOCALHOST4));
        v6Addresses.put(newHost, Collections.<InetAddress>singletonList(NetUtil.LOCALHOST6));

        assertEquals(NetUtil.LOCALHOST4, resolver.address(newHost, ResolvedAddressTypes.IPV4_ONLY));
        assertEquals(NetUtil.LOCALHOST6, resolver.address(newHost, ResolvedAddressTypes.IPV6_ONLY));
    }

    private HostsFileEntriesProvider.Parser givenHostsParserWith(final Map<String, List<InetAddress>> inet4Entries,
                                                                 final Map<String, List<InetAddress>> inet6Entries) {
        HostsFileEntriesProvider.Parser mockParser = mock(HostsFileEntriesProvider.Parser.class);

        Answer<HostsFileEntriesProvider> mockedAnswer = new Answer<HostsFileEntriesProvider>() {
            @Override
            public HostsFileEntriesProvider answer(InvocationOnMock invocation) {
                return new HostsFileEntriesProvider(inet4Entries, inet6Entries);
            }
        };

        when(mockParser.parseSilently()).thenAnswer(mockedAnswer);
        when(mockParser.parseSilently(any(Charset.class))).thenAnswer(mockedAnswer);

        return mockParser;
    }
}
