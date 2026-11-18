/*
 * Copyright 2026 The Netty Project
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

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsOptEcsRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.Ticker;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the API of {@link DnsNameResolver#resolveAll(DnsHttpsQuery, Iterable, Promise)}: how a {@link DnsHttpsQuery}
 * is resolved (scheme and port, hosts resolved without DNS), the properties of the returned {@link DnsHttpsAddress}es
 * (TTL, ECH, equality) and how the resolver options and lifecycle apply. The resolution of the HTTPS records is tested
 * by {@link DnsHttpsResolutionTest}.
 */
public class DnsNameResolverHttpsTest extends AbstractDnsHttpsTest {

    @Test
    public void testResolveWithAdditionals() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        List<DnsRecord> additionals = Collections.<DnsRecord>singletonList(
                new DefaultDnsOptEcsRecord(1024, 24, new byte[] { (byte) 192, 0, 2, 0 }));
        Promise<List<DnsHttpsAddress>> promise = group.next().newPromise();

        List<DnsHttpsAddress> addresses = resolver.resolveAll(query("example.com"), additionals)
                .get(10, TimeUnit.SECONDS);
        Future<List<DnsHttpsAddress>> future = resolver.resolveAll(query("example.com"), additionals, promise);

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"), host("192.0.2.1"));
        assertThat(future).isSameAs(promise);
        assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(addresses);
    }

    @Test
    public void testHttpQueryOnOtherPortIsResolvedAsHttpsOnThatPort() throws Exception {
        // RFC 9460, Section 9.5: only port 80 becomes 443, http://example.com:8080 is resolved as
        // https://example.com:8080.
        server.add(https("_8080._https.example.com.", 1, "svc.example.net."));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.add(a("example.com.", "192.0.2.1"));

        List<DnsHttpsAddress> addresses = resolve(new DnsHttpsQuery("http", "example.com", 8080));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 8080, "http/1.1"),
                host("192.0.2.1", 8080));
    }

    @Test
    public void testHostResolvedWithoutDns() throws Exception {
        List<DnsHttpsAddress> addresses = resolve(query("192.0.2.5"));

        assertThat(addresses).containsExactly(host("192.0.2.5"));
        // Without DNS, nothing tells how long the address can be used.
        assertThat(addresses.get(0).ttl()).isNull();
        assertThat(server.receivedTypes()).isEmpty();
    }

    @Test
    public void testEmptyHostResolvedWithoutDns() throws Exception {
        // Like resolveAll, an empty host is the loopback address of the preferred family.
        List<DnsHttpsAddress> addresses = resolve(query(""));

        assertThat(addresses).containsExactly(host("127.0.0.1"));
        assertThat(server.receivedTypes()).isEmpty();
    }

    @Test
    public void testHostsFileEntryResolvedWithoutDns() throws Exception {
        server.add(https("local.example.", 1, "."));
        final InetAddress entry = InetAddress.getByName("192.0.2.8");
        DnsNameResolver hostsFileResolver = build(newResolver()
                .hostsFileEntriesResolver((host, types) -> "local.example".equals(host) ? entry : null));

        List<DnsHttpsAddress> addresses = resolve(hostsFileResolver, query("local.example"));

        // The hosts file overrides DNS, HTTPS records included.
        assertThat(addresses).containsExactly(host("192.0.2.8"));
        assertThat(server.receivedTypes()).isEmpty();
    }

    @Test
    public void testEchOfAnAddressIsACopy() throws Exception {
        server.add(https("example.com.", 1, ".", ech(1, 2, 3)));
        server.add(a("example.com.", "192.0.2.1"));

        DnsHttpsAddress address = resolve(query("example.com")).get(0);
        address.ech()[0] = 9;

        assertThat(address.ech()).containsExactly(1, 2, 3);
    }

    @Test
    public void testAddressesResolvedWithDifferentTtlsAreEqual() throws Exception {
        // The same address resolved at different times, so with different TTLs, is the same address.
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        DnsNameResolver shortTtl = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .ttl(10, 10));
        DnsNameResolver longTtl = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .ttl(100, 100));

        List<DnsHttpsAddress> addresses = resolve(shortTtl, query("example.com"));
        List<DnsHttpsAddress> others = resolve(longTtl, query("example.com"));

        assertThat(addresses.get(0).ttl()).isNotEqualTo(others.get(0).ttl());
        assertThat(addresses).isEqualTo(others);
        assertThat(addresses.get(0).hashCode()).isEqualTo(others.get(0).hashCode());
    }

    // TTL

    @Test
    public void testTtlIsTheEarliestExpirationOfTheRecordsOfTheAddress() throws Exception {
        server.add(https("example.com.", 300, 1, "a.example.net."));
        server.add(https("example.com.", 300, 2, "b.example.net."));
        server.add(a("a.example.net.", 60, "198.51.100.1"));
        server.add(a("b.example.net.", 600, "198.51.100.2"));
        server.add(a("example.com.", 120, "192.0.2.1"));

        long before = System.nanoTime();
        List<DnsHttpsAddress> addresses = resolve(query("example.com"));
        long after = System.nanoTime();

        // The A record of the target, then the HTTPS record, then the A record of the host.
        assertThat(ips(addresses)).containsExactly(InetAddress.getByName("198.51.100.1"),
                InetAddress.getByName("198.51.100.2"), InetAddress.getByName("192.0.2.1"));
        assertTtl(addresses.get(0), 60, before, after);
        assertTtl(addresses.get(1), 300, before, after);
        assertTtl(addresses.get(2), 120, before, after);
    }

    @Test
    public void testTtlIncludesTheAliasModeRecords() throws Exception {
        server.add(https("example.com.", 60, 0, "svc.example.net."));
        server.add(https("svc.example.net.", 300, 1, "."));
        server.add(a("svc.example.net.", 300, "198.51.100.7"));

        long before = System.nanoTime();
        List<DnsHttpsAddress> addresses = resolve(query("example.com"));
        long after = System.nanoTime();

        // The ServiceMode endpoint and the alias endpoint both depend on the AliasMode record.
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"),
                endpoint(65535, "198.51.100.7", 443, "http/1.1"));
        assertTtl(addresses.get(0), 60, before, after);
        assertTtl(addresses.get(1), 60, before, after);
    }

    @Test
    public void testTtlIsClampedByTheTtlsOfTheResolver() throws Exception {
        server.add(https("example.com.", 300, 1, "svc.example.net."));
        server.add(a("svc.example.net.", 1, "198.51.100.7"));
        server.add(a("example.com.", 300, "192.0.2.1"));
        DnsNameResolver clamped = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .ttl(10, 100));

        long before = System.nanoTime();
        List<DnsHttpsAddress> addresses = resolve(clamped, query("example.com"));
        long after = System.nanoTime();

        assertThat(addresses).hasSize(2);
        assertTtl(addresses.get(0), 10, before, after);
        assertTtl(addresses.get(1), 100, before, after);
    }

    @Test
    public void testTtlIsZeroWhenTheCachesKeepNothing() throws Exception {
        // A maximum TTL of 0 or the no-op cache: nothing is kept, so the result has to be resolved again for the
        // next use.
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        DnsNameResolver maxTtlZero = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .cnameCache(NoopDnsCnameCache.INSTANCE)
                .authoritativeDnsServerCache(NoopAuthoritativeDnsServerCache.INSTANCE)
                .ttl(0, 0));
        DnsNameResolver noopCache = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .resolveCache(NoopDnsCache.INSTANCE)
                .cnameCache(NoopDnsCnameCache.INSTANCE)
                .authoritativeDnsServerCache(NoopAuthoritativeDnsServerCache.INSTANCE)
                .ttl(0, 0));

        for (DnsNameResolver noCaching : new DnsNameResolver[] { maxTtlZero, noopCache }) {
            List<DnsHttpsAddress> addresses = resolve(noCaching, query("example.com"));

            assertThat(addresses).hasSize(2);
            assertThat(addresses.get(0).ttl()).isEqualTo(Duration.ZERO);
            assertThat(addresses.get(1).ttl()).isEqualTo(Duration.ZERO);
        }
    }

    @Test
    public void testTtlIsTheTimeLeftBeforeTheCacheEntriesExpire() throws Exception {
        server.add(https("example.com.", 300, 1, "svc.example.net."));
        server.add(a("svc.example.net.", 300, "198.51.100.7"));
        server.add(a("example.com.", 300, "192.0.2.1"));
        // The cached addresses expire in 10 seconds, whatever the TTL of their records.
        final long deadline = Ticker.systemTicker().nanoTime() + TimeUnit.SECONDS.toNanos(10);
        DnsNameResolver fixedDeadline = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .resolveCache(new FixedDeadlineDnsCache(deadline)));

        List<DnsHttpsAddress> resolved = resolve(fixedDeadline, query("example.com"));
        int queries = server.receivedTypes().size();
        List<DnsHttpsAddress> cached = resolve(fixedDeadline, query("example.com"));
        long after = Ticker.systemTicker().nanoTime();

        assertThat(server.receivedTypes()).hasSize(queries);
        assertThat(resolved).hasSize(2);
        assertThat(cached).hasSize(2);
        for (DnsHttpsAddress address : resolved) {
            assertThat(address.ttl()).isBetween(Duration.ofNanos(deadline - after), Duration.ofSeconds(10));
        }
        for (DnsHttpsAddress address : cached) {
            assertThat(address.ttl()).isBetween(Duration.ofNanos(deadline - after), Duration.ofSeconds(10));
        }
    }

    @Test
    public void testTtlCountsFromTheAnswerNotFromTheEndOfTheResolution() throws Exception {
        // The A record is answered first, the HTTPS answer arrives later: the time in between is not left to the
        // address resolved from the A record.
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", 300, "192.0.2.1"));
        server.hold("example.com.", Type.HTTPS);
        final CountDownLatch addressCached = new CountDownLatch(1);
        DnsCache resolveCache = new DefaultDnsCache() {
            @Override
            public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, InetAddress address,
                                       long originalTtl, EventLoop loop) {
                DnsCacheEntry entry = super.cache(hostname, additionals, address, originalTtl, loop);
                addressCached.countDown();
                return entry;
            }
        };
        DnsNameResolver lateHttps = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .resolveCache(resolveCache).queryTimeoutMillis(60000));

        Future<List<DnsHttpsAddress>> future = lateHttps.resolveAll(query("example.com"));
        assertThat(addressCached.await(10, TimeUnit.SECONDS)).isTrue();
        long cached = System.nanoTime();
        // Wait long enough to tell the TTL counted from the A answer from one counted from the HTTPS answer.
        Thread.sleep(100);
        long released = System.nanoTime();
        server.release();
        List<DnsHttpsAddress> addresses = future.get(10, TimeUnit.SECONDS);

        assertThat(addresses).hasSize(2);
        for (DnsHttpsAddress address : addresses) {
            assertThat(address.ttl()).isLessThanOrEqualTo(Duration.ofSeconds(300).minusNanos(released - cached));
        }
    }

    @Test
    public void testTtlIgnoresTheRecordsOfACacheNotReportingExpirationTimes() throws Exception {
        // The host addresses have no known expiration: the entries keep the expiration of the HTTPS record.
        server.add(https("example.com.", 300, 1, "svc.example.net."));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.add(a("example.com.", "192.0.2.1"));
        DnsNameResolver noExpiration = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .resolveCache(new FixedDeadlineDnsCache(-1)));

        long before = System.nanoTime();
        List<DnsHttpsAddress> addresses = resolve(noExpiration, query("example.com"));
        long after = System.nanoTime();

        assertThat(addresses).hasSize(2);
        assertTtl(addresses.get(0), 300, before, after);
        assertTtl(addresses.get(1), 300, before, after);
    }

    @Test
    public void testTtlOfAnEndpointWithAHostsFileTargetIsTheTtlOfItsHttpsRecord() throws Exception {
        // The hosts file entry has no expiration, the HTTPS record does.
        server.add(https("example.com.", 300, 1, "svc.example.net."));
        server.add(a("example.com.", "192.0.2.1"));
        final InetAddress entry = InetAddress.getByName("198.51.100.7");
        DnsNameResolver hostsFileResolver = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .hostsFileEntriesResolver((host, types) -> host.startsWith("svc.example.net") ? entry : null));

        long before = System.nanoTime();
        List<DnsHttpsAddress> addresses = resolve(hostsFileResolver, query("example.com"));
        long after = System.nanoTime();

        assertThat(addresses.get(0)).isEqualTo(endpoint(1, "198.51.100.7", 443, "http/1.1"));
        assertTtl(addresses.get(0), 300, before, after);
    }

    // Resolver lifecycle

    @Test
    public void testGroupSharesTheHttpsCache() throws Exception {
        EventLoopGroup loops = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        DnsAddressResolverGroup resolverGroup = newResolverGroup();
        try {
            server.add(https("example.com.", 1, "."));
            server.add(a("example.com.", "192.0.2.1"));
            DnsNameResolver resolver1 = resolverGroup.getNameResolver(loops.next());
            DnsNameResolver resolver2 = resolverGroup.getNameResolver(loops.next());
            List<DnsHttpsAddress> resolved = resolve(resolver1, query("example.com"));
            int queries = server.receivedTypes().size();

            List<DnsHttpsAddress> cached = resolve(resolver2, query("example.com"));

            // The second resolver of the group answers from the caches the first one filled.
            assertThat(resolver1).isNotSameAs(resolver2);
            assertThat(cached).isEqualTo(resolved);
            assertThat(server.receivedTypes()).hasSize(queries);
        } finally {
            resolverGroup.close();
            loops.shutdownGracefully();
        }
    }

    @Test
    public void testCloseClearsTheHttpsCache() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        DnsAddressResolverGroup resolverGroup = newResolverGroup();
        try {
            resolve(resolverGroup.getNameResolver(group.next()), query("example.com"));
            int queries = server.receivedTypes().size();
            resolve(resolverGroup.getNameResolver(group.next()), query("example.com"));
            assertThat(server.receivedTypes()).hasSize(queries);

            // Closing the group closes its resolvers; the next one shares the caches they used.
            resolverGroup.close();
            resolve(resolverGroup.getNameResolver(group.next()), query("example.com"));

            assertThat(server.receivedTypesSince(queries)).contains(Type.HTTPS);
        } finally {
            resolverGroup.close();
        }
    }

    private DnsAddressResolverGroup newResolverGroup() {
        return new DnsAddressResolverGroup(new DnsNameResolverBuilder()
                .datagramChannelType(NioDatagramChannel.class)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(server.localAddress())));
    }

    /**
     * A {@link DnsCache} whose entries expire at a fixed deadline, or report an unknown one with {@code -1}.
     */
    private static final class FixedDeadlineDnsCache implements DnsCache {
        private final DnsCache cache = new DefaultDnsCache();
        private final long deadlineNanos;

        FixedDeadlineDnsCache(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        public void clear() {
            cache.clear();
        }

        @Override
        public boolean clear(String hostname) {
            return cache.clear(hostname);
        }

        @Override
        public List<? extends DnsCacheEntry> get(String hostname, DnsRecord[] additionals) {
            List<? extends DnsCacheEntry> entries = cache.get(hostname, additionals);
            if (entries == null) {
                return null;
            }
            List<DnsCacheEntry> fixed = new ArrayList<DnsCacheEntry>(entries.size());
            for (DnsCacheEntry entry : entries) {
                fixed.add(fixed(entry));
            }
            return fixed;
        }

        @Override
        public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, InetAddress address, long originalTtl,
                                   EventLoop loop) {
            return fixed(cache.cache(hostname, additionals, address, originalTtl, loop));
        }

        @Override
        public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
            return fixed(cache.cache(hostname, additionals, cause, loop));
        }

        private DnsCacheEntry fixed(final DnsCacheEntry entry) {
            return new DnsCacheEntry() {
                @Override
                public InetAddress address() {
                    return entry.address();
                }

                @Override
                public Throwable cause() {
                    return entry.cause();
                }

                @Override
                public long deadlineNanos() {
                    return deadlineNanos;
                }
            };
        }
    }
}
