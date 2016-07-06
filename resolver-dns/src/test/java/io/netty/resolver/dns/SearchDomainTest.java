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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.After;
import org.junit.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SearchDomainTest {

    private DnsNameResolverBuilder newResolver() {
        return new DnsNameResolverBuilder(group.next())
            .channelType(NioDatagramChannel.class)
            .nameServerAddresses(DnsServerAddresses.singleton(dnsServer.localAddress()))
            .maxQueriesPerResolve(1)
            .optResourceEnabled(false);
    }

    private TestDnsServer dnsServer;
    private final EventLoopGroup group = new NioEventLoopGroup(1);

    @After
    public void destroy() {
        if (dnsServer != null) {
            dnsServer.stop();
            dnsServer = null;
        }
        group.shutdownGracefully();
    }

    @Test
    public void testResolve() throws Exception {
        Set<String> domains = new HashSet<String>();
        domains.add("host1.foo.com");
        domains.add("host1");
        domains.add("host3");
        domains.add("host4.sub.foo.com");
        domains.add("host5.sub.foo.com");
        domains.add("host5.sub");

        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(domains);
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        DnsNameResolver resolver = newResolver().searchDomains(Collections.singletonList("foo.com")).build();

        InetAddress resolved = resolver.resolve("host1.foo.com").sync().getNow();
        assertEquals(store.getAddress("host1.foo.com"), resolved.getHostAddress());

        // host1 resolves host1.foo.com with foo.com search domain
        resolved = resolver.resolve("host1").sync().getNow();
        assertEquals(store.getAddress("host1.foo.com"), resolved.getHostAddress());

        // "host1." absolute query
        resolved = resolver.resolve("host1.").sync().getNow();
        assertEquals(store.getAddress("host1"), resolved.getHostAddress());

        // "host2" not resolved
        assertFalse(resolver.resolve("host2").await().isSuccess());

        // "host3" does not contain a dot or is not absolute
        assertFalse(resolver.resolve("host3").await().isSuccess());

        // "host3." does not contain a dot but is absolute
        resolved = resolver.resolve("host3.").sync().getNow();
        assertEquals(store.getAddress("host3"), resolved.getHostAddress());

        // "host4.sub" contains a dot but not resolved then resolved to "host4.sub.foo.com" with "foo.com" search domain
        resolved = resolver.resolve("host4.sub").sync().getNow();
        assertEquals(store.getAddress("host4.sub.foo.com"), resolved.getHostAddress());

        // "host5.sub" contains a dot and is resolved
        resolved = resolver.resolve("host5.sub").sync().getNow();
        assertEquals(store.getAddress("host5.sub"), resolved.getHostAddress());
    }


    @Test
    public void testMultipleSearchDomain() throws Exception {
        Set<String> domains = new HashSet<String>();
        domains.add("host1.foo.com");
        domains.add("host2.bar.com");
        domains.add("host3.bar.com");
        domains.add("host3.foo.com");

        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(domains);
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        DnsNameResolver resolver = newResolver().searchDomains(Arrays.asList("foo.com", "bar.com")).build();

        // "host1" resolves via the "foo.com" search path
        InetAddress resolved = resolver.resolve("host1").sync().getNow();
        assertEquals(store.getAddress("host1.foo.com"), resolved.getHostAddress());

        // "host2" resolves via the "bar.com" search path
        resolved = resolver.resolve("host2").sync().getNow();
        assertEquals(store.getAddress("host2.bar.com"), resolved.getHostAddress());

        // "host3" resolves via the the "foo.com" search path as it is the first one
        resolved = resolver.resolve("host3").sync().getNow();
        assertEquals(store.getAddress("host3.foo.com"), resolved.getHostAddress());

        // "host4" does not resolve
        assertFalse(resolver.resolve("host4").await().isSuccess());
    }

    @Test
    public void testSearchDomainWithNdots2() throws Exception {
        Set<String> domains = new HashSet<String>();
        domains.add("host1.sub.foo.com");
        domains.add("host2.sub.foo.com");
        domains.add("host2.sub");

        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(domains);
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        DnsNameResolver resolver = newResolver().searchDomains(Collections.singleton("foo.com")).ndots(2).build();

        InetAddress resolved = resolver.resolve("host1.sub").sync().getNow();
        assertEquals(store.getAddress("host1.sub.foo.com"), resolved.getHostAddress());

        // "host2.sub" is resolved with the foo.com search domain as ndots = 2
        resolved = resolver.resolve("host2.sub").sync().getNow();
        assertEquals(store.getAddress("host2.sub.foo.com"), resolved.getHostAddress());
    }

    private static List<String> asStrings(List<InetAddress> list) {
        if (list != null) {
            List<String> ret = new ArrayList<String>();
            for (InetAddress addr : list) {
              ret.add(addr.getHostAddress());
            }
            return ret;
        }
        return null;
    }
}
