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
package io.netty.resolver.dns;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchDomainTest {

    private DnsNameResolverBuilder newResolver() {
        return new DnsNameResolverBuilder(group.next())
            .datagramChannelType(NioDatagramChannel.class)
            .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()))
            .maxQueriesPerResolve(1)
            .optResourceEnabled(false)
            .ndots(1);
    }

    private TestDnsServer dnsServer;
    private EventLoopGroup group;
    private DnsNameResolver resolver;

    @BeforeEach
    public void before() {
        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    }

    @AfterEach
    public void destroy() {
        if (dnsServer != null) {
            dnsServer.stop();
            dnsServer = null;
        }
        if (resolver != null) {
            resolver.close();
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

        resolver = newResolver().searchDomains(Collections.singletonList("foo.com")).ndots(2).build();

        String a = "host1.foo.com";
        String resolved = assertResolve(resolver, a);
        assertEquals(store.getAddress("host1.foo.com"), resolved);

        // host1 resolves host1.foo.com with foo.com search domain
        resolved = assertResolve(resolver, "host1");
        assertEquals(store.getAddress("host1.foo.com"), resolved);

        // "host1." absolute query
        resolved = assertResolve(resolver, "host1.");
        assertEquals(store.getAddress("host1"), resolved);

        // "host2" not resolved
        assertNotResolve(resolver, "host2");

        // "host3" does not contain a dot nor it's absolute but it should still be resolved after search list have
        // been checked
        resolved = assertResolve(resolver, "host3");
        assertEquals(store.getAddress("host3"), resolved);

        // "host3." does not contain a dot but is absolute
        resolved = assertResolve(resolver, "host3.");
        assertEquals(store.getAddress("host3"), resolved);

        // "host4.sub" contains a dot but not resolved then resolved to "host4.sub.foo.com" with "foo.com" search domain
        resolved = assertResolve(resolver, "host4.sub");
        assertEquals(store.getAddress("host4.sub.foo.com"), resolved);

        // "host5.sub" would have been directly resolved but since it has less than ndots the "foo.com" search domain
        // is used.
        resolved = assertResolve(resolver, "host5.sub");
        assertEquals(store.getAddress("host5.sub.foo.com"), resolved);
    }

    @Test
    public void testResolveAll() throws Exception {
        Set<String> domains = new HashSet<String>();
        domains.add("host1.foo.com");
        domains.add("host1");
        domains.add("host3");
        domains.add("host4.sub.foo.com");
        domains.add("host5.sub.foo.com");
        domains.add("host5.sub");

        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(domains, 2);
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        resolver = newResolver().searchDomains(Collections.singletonList("foo.com")).ndots(2).build();

        String a = "host1.foo.com";
        List<String> resolved = assertResolveAll(resolver, a);
        assertEquals(store.getAddresses("host1.foo.com"), resolved);

        // host1 resolves host1.foo.com with foo.com search domain
        resolved = assertResolveAll(resolver, "host1");
        assertEquals(store.getAddresses("host1.foo.com"), resolved);

        // "host1." absolute query
        resolved = assertResolveAll(resolver, "host1.");
        assertEquals(store.getAddresses("host1"), resolved);

        // "host2" not resolved
        assertNotResolveAll(resolver, "host2");

        // "host3" does not contain a dot nor it's absolute but it should still be resolved after search list have
        // been checked
        resolved = assertResolveAll(resolver, "host3");
        assertEquals(store.getAddresses("host3"), resolved);

        // "host3." does not contain a dot but is absolute
        resolved = assertResolveAll(resolver, "host3.");
        assertEquals(store.getAddresses("host3"), resolved);

        // "host4.sub" contains a dot but not resolved then resolved to "host4.sub.foo.com" with "foo.com" search domain
        resolved = assertResolveAll(resolver, "host4.sub");
        assertEquals(store.getAddresses("host4.sub.foo.com"), resolved);

        // "host5.sub" would have been directly resolved but since it has less than ndots the "foo.com" search domain
        // is used.
        resolved = assertResolveAll(resolver, "host5.sub");
        assertEquals(store.getAddresses("host5.sub.foo.com"), resolved);
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

        resolver = newResolver().searchDomains(Arrays.asList("foo.com", "bar.com")).build();

        // "host1" resolves via the "foo.com" search path
        String resolved = assertResolve(resolver, "host1");
        assertEquals(store.getAddress("host1.foo.com"), resolved);

        // "host2" resolves via the "bar.com" search path
        resolved = assertResolve(resolver, "host2");
        assertEquals(store.getAddress("host2.bar.com"), resolved);

        // "host3" resolves via the "foo.com" search path as it is the first one
        resolved = assertResolve(resolver, "host3");
        assertEquals(store.getAddress("host3.foo.com"), resolved);

        // "host4" does not resolve
        assertNotResolve(resolver, "host4");
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

        resolver = newResolver().searchDomains(Collections.singleton("foo.com")).ndots(2).build();

        String resolved = assertResolve(resolver, "host1.sub");
        assertEquals(store.getAddress("host1.sub.foo.com"), resolved);

        // "host2.sub" is resolved with the foo.com search domain as ndots = 2
        resolved = assertResolve(resolver, "host2.sub");
        assertEquals(store.getAddress("host2.sub.foo.com"), resolved);
    }

    @Test
    public void testSearchDomainWithNdots0() throws Exception {
        Set<String> domains = new HashSet<String>();
        domains.add("host1");
        domains.add("host1.foo.com");
        domains.add("host2.foo.com");

        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(domains);
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        resolver = newResolver().searchDomains(Collections.singleton("foo.com")).ndots(0).build();

        // "host1" resolves directly as ndots = 0
        String resolved = assertResolve(resolver, "host1");
        assertEquals(store.getAddress("host1"), resolved);

        // "host1.foo.com" resolves to host1.foo
        resolved = assertResolve(resolver, "host1.foo.com");
        assertEquals(store.getAddress("host1.foo.com"), resolved);

        // "host2" shouldn't resolve because it is not in the known domain names, and "host2" has 0 dots which is not
        // less ndots (which is also 0).
        assertNotResolve(resolver, "host2");
    }

    private static void assertNotResolve(DnsNameResolver resolver, String inetHost) throws InterruptedException {
        Future<InetAddress> fut = resolver.resolve(inetHost);
        assertTrue(fut.await(10, TimeUnit.SECONDS));
        assertFalse(fut.isSuccess());
    }

    private static void assertNotResolveAll(DnsNameResolver resolver, String inetHost) throws InterruptedException {
        Future<List<InetAddress>> fut = resolver.resolveAll(inetHost);
        assertTrue(fut.await(10, TimeUnit.SECONDS));
        assertFalse(fut.isSuccess());
    }

    private static String assertResolve(DnsNameResolver resolver, String inetHost) throws InterruptedException {
        Future<InetAddress> fut = resolver.resolve(inetHost);
        assertTrue(fut.await(10, TimeUnit.SECONDS));
        return fut.getNow().getHostAddress();
    }

    private static List<String> assertResolveAll(DnsNameResolver resolver,
                                                 String inetHost) throws InterruptedException {
        Future<List<InetAddress>> fut = resolver.resolveAll(inetHost);
        assertTrue(fut.await(10, TimeUnit.SECONDS));
        List<String> list = new ArrayList<String>();
        for (InetAddress addr : fut.getNow()) {
            list.add(addr.getHostAddress());
        }
        return list;
    }

    @Test
    public void testExceptionMsgContainsSearchDomain() throws Exception {
        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(Collections.<String>emptySet());
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        resolver = newResolver().searchDomains(Collections.singletonList("foo.com")).ndots(1).build();

        Future<InetAddress> fut = resolver.resolve("unknown.hostname");
        assertTrue(fut.await(10, TimeUnit.SECONDS));
        assertFalse(fut.isSuccess());
        final Throwable cause = fut.cause();
        assertInstanceOf(UnknownHostException.class, cause);
        assertThat(cause.getMessage()).contains("foo.com");
    }

    @Test
    public void testExceptionMsgDoesNotContainSearchDomainIfNdotsIsNotReached() throws Exception {
        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(Collections.<String>emptySet());
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        resolver = newResolver().searchDomains(Collections.singletonList("foo.com")).ndots(2).build();

        Future<InetAddress> fut = resolver.resolve("unknown.hostname");
        assertTrue(fut.await(10, TimeUnit.SECONDS));
        assertFalse(fut.isSuccess());
        final Throwable cause = fut.cause();
        assertInstanceOf(UnknownHostException.class, cause);
        assertThat(cause.getMessage()).doesNotContain("foo.com");
    }

    @Test
    public void testResolveWithRootSearchDomain() throws Exception {
        Set<String> domains = new HashSet<String>();
        domains.add("host1");
        domains.add("host2.foo.com");
        domains.add("host3.sub");

        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(domains);
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        // systemd-resolved writes "search ." to resolv.conf when it knows no search domains. "." is the root domain.
        resolver = newResolver().searchDomains(Collections.singletonList(".")).build();
        assertThat(assertResolve(resolver, "host1")).isEqualTo(store.getAddress("host1"));
        assertThat(assertResolve(resolver, "host3.sub")).isEqualTo(store.getAddress("host3.sub"));
        resolver.close();

        resolver = newResolver().searchDomains(Arrays.asList(".", "foo.com")).build();
        assertThat(assertResolve(resolver, "host1")).isEqualTo(store.getAddress("host1"));
        assertThat(assertResolve(resolver, "host2")).isEqualTo(store.getAddress("host2.foo.com"));
        assertThat(assertResolve(resolver, "host3.sub")).isEqualTo(store.getAddress("host3.sub"));
    }

    @Test
    public void testResolveWithLeadingDotSearchDomain() throws Exception {
        Set<String> domains = new HashSet<String>();
        domains.add("host1");
        domains.add("host1.foo.com");
        domains.add("host2.sub.foo.com");

        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(domains);
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        // ".foo.com" is used as "foo.com", like glibc does.
        resolver = newResolver().searchDomains(Collections.singletonList(".foo.com")).build();
        assertThat(assertResolve(resolver, "host1")).isEqualTo(store.getAddress("host1.foo.com"));
        assertThat(assertResolve(resolver, "host2.sub")).isEqualTo(store.getAddress("host2.sub.foo.com"));
    }

    @Test
    public void testResolveWithEmptySearchDomainFirst() throws Exception {
        Set<String> domains = new HashSet<String>();
        domains.add("host1");
        domains.add("host1.foo.com");

        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(domains);
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        // An empty search domain queries the absolute name at its position, so "host1" is tried before
        // "host1.foo.com".
        resolver = newResolver().searchDomains(Arrays.asList("", "foo.com")).ndots(1).build();
        assertThat(assertResolve(resolver, "host1")).isEqualTo(store.getAddress("host1"));
    }

    @Test
    public void testResolveAllWithEmptySearchDomainFirst() throws Exception {
        Set<String> domains = new HashSet<String>();
        domains.add("host1");
        domains.add("host1.foo.com");

        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(domains, 2);
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        // An empty search domain queries the absolute name at its position, so "host1" is tried before
        // "host1.foo.com".
        resolver = newResolver().searchDomains(Arrays.asList("", "foo.com")).ndots(1).build();
        assertThat(assertResolveAll(resolver, "host1"))
                .containsExactlyInAnyOrderElementsOf(store.getAddresses("host1"));
    }

    @Test
    public void testExceptionCauseWithRootSearchDomain() throws Exception {
        testExceptionCauseIsDnsError(Collections.singletonList("."));
    }

    @Test
    public void testExceptionCauseWithRootSearchDomainLast() throws Exception {
        testExceptionCauseIsDnsError(Arrays.asList("foo.com", "."));
    }

    @Test
    public void testExceptionCauseWithLeadingDotSearchDomain() throws Exception {
        testExceptionCauseIsDnsError(Collections.singletonList(".foo.com"));
    }

    private void testExceptionCauseIsDnsError(List<String> searchDomains) throws Exception {
        TestDnsServer.MapRecordStoreA store = new TestDnsServer.MapRecordStoreA(Collections.<String>emptySet());
        dnsServer = new TestDnsServer(store);
        dnsServer.start();

        resolver = newResolver().searchDomains(searchDomains).ndots(1).build();

        // "unknown.hostname" has at least ndots dots, so it is queried as is first and then with each search domain.
        Future<InetAddress> fut = resolver.resolve("unknown.hostname");
        assertThat(fut.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(fut.isSuccess()).isFalse();
        final Throwable cause = fut.cause();
        // The cause must be the NXDOMAIN of the server, not the IllegalArgumentException for an invalid name like
        // "unknown.hostname..".
        assertThat(cause).isInstanceOf(UnknownHostException.class).hasCauseInstanceOf(DnsErrorCauseException.class);
        assertThat(((DnsErrorCauseException) cause.getCause()).getCode()).isEqualTo(DnsResponseCode.NXDOMAIN);
    }
}
