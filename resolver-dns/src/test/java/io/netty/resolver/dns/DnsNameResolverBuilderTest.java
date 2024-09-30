/*
 * Copyright 2023 The Netty Project
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
import io.netty.handler.codec.dns.DnsRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static io.netty.resolver.dns.Cache.MAX_SUPPORTED_TTL_SECS;
import static org.assertj.core.api.Assertions.assertThat;

class DnsNameResolverBuilderTest {
    private static final EventLoopGroup GROUP = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

    private DnsNameResolverBuilder builder;
    private DnsNameResolver resolver;

    @BeforeEach
    void setUp() {
        builder = new DnsNameResolverBuilder(GROUP.next()).datagramChannelType(NioDatagramChannel.class);
    }

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
    }

    @AfterAll
    static void shutdownEventLoopGroup() {
        GROUP.shutdownGracefully();
    }

    @Test
    void testDefaults() {
        resolver = builder.build();

        checkDefaultDnsCache((DefaultDnsCache) resolver.resolveCache(), MAX_SUPPORTED_TTL_SECS, 0, 0);

        checkDefaultDnsCnameCache((DefaultDnsCnameCache) resolver.cnameCache(), MAX_SUPPORTED_TTL_SECS, 0);

        checkDefaultAuthoritativeDnsServerCache(
                (DefaultAuthoritativeDnsServerCache) resolver.authoritativeDnsServerCache(), MAX_SUPPORTED_TTL_SECS, 0);
assertThat(resolver.queryDnsServerAddressStream()).isInstanceOf(ThreadLocalNameServerAddressStream.class);
    }

    @Test
    void testCustomDnsCacheDefaultTtl() {
        DnsCache testDnsCache = new TestDnsCache();
        resolver = builder.resolveCache(testDnsCache).build();

        assertThat(resolver.resolveCache()).isSameAs(testDnsCache);

        checkDefaultDnsCnameCache((DefaultDnsCnameCache) resolver.cnameCache(), MAX_SUPPORTED_TTL_SECS, 0);

        checkDefaultAuthoritativeDnsServerCache(
                (DefaultAuthoritativeDnsServerCache) resolver.authoritativeDnsServerCache(), MAX_SUPPORTED_TTL_SECS, 0);
    }

    @Test
    void testCustomDnsCacheCustomTtl() {
        DnsCache testDnsCache = new TestDnsCache();
        resolver = builder.resolveCache(testDnsCache).ttl(1, 2).negativeTtl(3).build();

        assertThat(resolver.resolveCache()).isSameAs(testDnsCache);

        checkDefaultDnsCnameCache((DefaultDnsCnameCache) resolver.cnameCache(), 2, 1);

        checkDefaultAuthoritativeDnsServerCache(
                (DefaultAuthoritativeDnsServerCache) resolver.authoritativeDnsServerCache(), 2, 1);
    }

    @Test
    void testCustomDnsCnameCacheDefaultTtl() {
        DnsCnameCache testDnsCnameCache = new TestDnsCnameCache();
        resolver = builder.cnameCache(testDnsCnameCache).build();

        checkDefaultDnsCache((DefaultDnsCache) resolver.resolveCache(), MAX_SUPPORTED_TTL_SECS, 0, 0);

        assertThat(resolver.cnameCache()).isSameAs(testDnsCnameCache);

        checkDefaultAuthoritativeDnsServerCache(
                (DefaultAuthoritativeDnsServerCache) resolver.authoritativeDnsServerCache(), MAX_SUPPORTED_TTL_SECS, 0);
    }

    @Test
    void testCustomDnsCnameCacheCustomTtl() {
        DnsCnameCache testDnsCnameCache = new TestDnsCnameCache();
        resolver = builder.cnameCache(testDnsCnameCache).ttl(1, 2).negativeTtl(3).build();

        checkDefaultDnsCache((DefaultDnsCache) resolver.resolveCache(), 2, 1, 3);

        assertThat(resolver.cnameCache()).isSameAs(testDnsCnameCache);

        checkDefaultAuthoritativeDnsServerCache(
                (DefaultAuthoritativeDnsServerCache) resolver.authoritativeDnsServerCache(), 2, 1);
    }

    @Test
    void testCustomAuthoritativeDnsServerCacheDefaultTtl() {
        AuthoritativeDnsServerCache testAuthoritativeDnsServerCache = new TestAuthoritativeDnsServerCache();
        resolver = builder.authoritativeDnsServerCache(testAuthoritativeDnsServerCache).build();

        checkDefaultDnsCache((DefaultDnsCache) resolver.resolveCache(), MAX_SUPPORTED_TTL_SECS, 0, 0);

        checkDefaultDnsCnameCache((DefaultDnsCnameCache) resolver.cnameCache(), MAX_SUPPORTED_TTL_SECS, 0);

        assertThat(resolver.authoritativeDnsServerCache()).isSameAs(testAuthoritativeDnsServerCache);
    }

    @Test
    void testCustomAuthoritativeDnsServerCacheCustomTtl() {
        AuthoritativeDnsServerCache testAuthoritativeDnsServerCache = new TestAuthoritativeDnsServerCache();
        resolver = builder.authoritativeDnsServerCache(testAuthoritativeDnsServerCache)
                .ttl(1, 2).negativeTtl(3).build();

        checkDefaultDnsCache((DefaultDnsCache) resolver.resolveCache(), 2, 1, 3);

        checkDefaultDnsCnameCache((DefaultDnsCnameCache) resolver.cnameCache(), 2, 1);

        assertThat(resolver.authoritativeDnsServerCache()).isSameAs(testAuthoritativeDnsServerCache);
    }

    @Test
    void disableQueryTimeoutWithZero() {
        resolver = builder.queryTimeoutMillis(0).build();
        assertThat(resolver.queryTimeoutMillis()).isEqualTo(0);
    }

    private static void checkDefaultDnsCache(DefaultDnsCache dnsCache,
            int expectedMaxTtl, int expectedMinTtl, int expectedNegativeTtl) {
        assertThat(dnsCache.maxTtl()).isEqualTo(expectedMaxTtl);
        assertThat(dnsCache.minTtl()).isEqualTo(expectedMinTtl);
        assertThat(dnsCache.negativeTtl()).isEqualTo(expectedNegativeTtl);
    }

    private static void checkDefaultDnsCnameCache(DefaultDnsCnameCache dnsCnameCache,
            int expectedMaxTtl, int expectedMinTtl) {
        assertThat(dnsCnameCache.maxTtl()).isEqualTo(expectedMaxTtl);
        assertThat(dnsCnameCache.minTtl()).isEqualTo(expectedMinTtl);
    }

    private static void checkDefaultAuthoritativeDnsServerCache(
            DefaultAuthoritativeDnsServerCache authoritativeDnsServerCache,
            int expectedMaxTtl, int expectedMinTtl) {
        assertThat(authoritativeDnsServerCache.maxTtl()).isEqualTo(expectedMaxTtl);
        assertThat(authoritativeDnsServerCache.minTtl()).isEqualTo(expectedMinTtl);
    }

    private static final class TestDnsCache implements DnsCache {

        @Override
        public void clear() {
            //no-op
        }

        @Override
        public boolean clear(String hostname) {
            return false;
        }

        @Override
        public List<? extends DnsCacheEntry> get(String hostname, DnsRecord[] additional) {
            return null;
        }

        @Override
        public DnsCacheEntry cache(String hostname, DnsRecord[] additional, InetAddress address,
                long originalTtl, EventLoop loop) {
            return null;
        }

        @Override
        public DnsCacheEntry cache(String hostname, DnsRecord[] additional, Throwable cause, EventLoop loop) {
            return null;
        }
    }

    private static final class TestDnsCnameCache implements DnsCnameCache {

        @Override
        public String get(String hostname) {
            return null;
        }

        @Override
        public void cache(String hostname, String cname, long originalTtl, EventLoop loop) {
            //no-op
        }

        @Override
        public void clear() {
            //no-op
        }

        @Override
        public boolean clear(String hostname) {
            return false;
        }
    }

    private static final class TestAuthoritativeDnsServerCache implements AuthoritativeDnsServerCache {

        @Override
        public DnsServerAddressStream get(String hostname) {
            return null;
        }

        @Override
        public void cache(String hostname, InetSocketAddress address, long originalTtl, EventLoop loop) {
            //no-op
        }

        @Override
        public void clear() {
            //no-op
        }

        @Override
        public boolean clear(String hostname) {
            return false;
        }
    }

    @Test
    void testCustomQueryDnsServerAddressStream() {
        DnsServerAddressStream queryAddressStream = new TestQueryServerAddressStream();
        resolver = builder.queryServerAddressStream(queryAddressStream).build();

        assertThat(resolver.queryDnsServerAddressStream()).isSameAs(queryAddressStream);

        resolver = builder.copy().build();
        assertThat(resolver.queryDnsServerAddressStream()).isSameAs(queryAddressStream);
    }

    private static final class TestQueryServerAddressStream implements DnsServerAddressStream {

        @Override
        public InetSocketAddress next() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DnsServerAddressStream duplicate() {
            throw new UnsupportedOperationException();
        }
    }
}
