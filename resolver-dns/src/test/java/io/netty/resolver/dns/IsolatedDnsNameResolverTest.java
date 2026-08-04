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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DnsNameResolverTest.TestRecursiveCacheDnsQueryLifecycleObserverFactory;
import io.netty.util.internal.PlatformDependent;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.RecordStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Set;

import static io.netty.resolver.dns.DnsNameResolverTest.DOMAINS_ALL;
import static io.netty.resolver.dns.DnsNameResolverTest.newResolver;
import static io.netty.resolver.dns.TestDnsServer.newARecord;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Isolated
public class IsolatedDnsNameResolverTest {
    private static final TestDnsServer dnsServer = new TestDnsServer(DOMAINS_ALL);
    private static final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private static boolean existingLocalhostSetting;

    @BeforeAll
    public static void captureSetting() throws Exception {
        existingLocalhostSetting = DnsNameResolver.resolveLocalhostWithoutDns;
        dnsServer.start();
    }

    @AfterAll
    public static void restoreSetting() {
        try {
            dnsServer.stop();
            group.shutdownGracefully();
        } finally {
            DnsNameResolver.resolveLocalhostWithoutDns = existingLocalhostSetting;
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveDotLocalhostViaDnsWhenDisabled(DnsNameResolverChannelStrategy strategy) throws Exception {
        final String hostname = "myservice.localhost";
        final String expectedIp = "10.0.0.1";
        final TestDnsServer customDnsServer = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                if (question.getRecordType() == RecordType.A) {
                    String domainName = question.getDomainName();
                    if (domainName.equalsIgnoreCase(hostname) || domainName.equalsIgnoreCase(hostname + '.')) {
                        return Collections.singleton(newARecord(hostname, expectedIp));
                    }
                }
                return Collections.emptySet();
            }
        });
        customDnsServer.start();
        DnsNameResolver.resolveLocalhostWithoutDns = false;
        try {
            DnsNameResolver resolver = newResolver(strategy, false, null, customDnsServer)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .hostsFileEntriesResolver(new HostsFileEntriesResolver() {
                        @Override
                        public InetAddress address(String inetHost, ResolvedAddressTypes resolvedAddressTypes) {
                            return null;
                        }
                    })
                    .build();
            try {
                InetAddress address = resolver.resolve(hostname).syncUninterruptibly().getNow();
                assertEquals(expectedIp, address.getHostAddress());

                TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                        (TestRecursiveCacheDnsQueryLifecycleObserverFactory)
                                resolver.dnsQueryLifecycleObserverFactory();
                assertFalse(lifecycleObserverFactory.observers.isEmpty());
            } finally {
                resolver.close();
            }
        } finally {
            customDnsServer.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveLocalhostViaDnsWhenDisabledOnNonWindows(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        assumeThat(PlatformDependent.isWindows()).isFalse();

        final String expectedIp = "10.0.0.2";
        final TestDnsServer customDnsServer = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                if (question.getRecordType() == RecordType.A) {
                    String domainName = question.getDomainName();
                    if (domainName.equalsIgnoreCase("localhost") || domainName.equalsIgnoreCase("localhost.")) {
                        return Collections.singleton(newARecord("localhost", expectedIp));
                    }
                }
                return Collections.emptySet();
            }
        });
        customDnsServer.start();
        DnsNameResolver.resolveLocalhostWithoutDns = false;
        try {
            DnsNameResolver resolver = newResolver(strategy, false, null, customDnsServer)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .hostsFileEntriesResolver(new HostsFileEntriesResolver() {
                        @Override
                        public InetAddress address(String inetHost, ResolvedAddressTypes resolvedAddressTypes) {
                            return null;
                        }
                    })
                    .build();
            try {
                InetAddress address = resolver.resolve("localhost").syncUninterruptibly().getNow();
                assertEquals(expectedIp, address.getHostAddress());

                TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                        (TestRecursiveCacheDnsQueryLifecycleObserverFactory)
                                resolver.dnsQueryLifecycleObserverFactory();
                assertFalse(lifecycleObserverFactory.observers.isEmpty());
            } finally {
                resolver.close();
            }
        } finally {
            customDnsServer.stop();
        }
    }
}
