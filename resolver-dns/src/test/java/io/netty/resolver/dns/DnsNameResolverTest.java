/*
 * Copyright 2015 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.resolver.HostsFileEntriesProvider;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.directory.server.dns.DnsException;
import org.apache.directory.server.dns.io.encoder.DnsMessageEncoder;
import org.apache.directory.server.dns.messages.DnsMessage;
import org.apache.directory.server.dns.messages.DnsMessageModifier;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.messages.ResourceRecordModifier;
import org.apache.directory.server.dns.messages.ResponseCode;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.apache.mina.core.buffer.IoBuffer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.netty.handler.codec.dns.DnsRecordType.A;
import static io.netty.handler.codec.dns.DnsRecordType.AAAA;
import static io.netty.handler.codec.dns.DnsRecordType.CNAME;
import static io.netty.handler.codec.dns.DnsRecordType.NAPTR;
import static io.netty.handler.codec.dns.DnsRecordType.SRV;
import static io.netty.resolver.dns.DnsNameResolver.DEFAULT_RESOLVE_ADDRESS_TYPES;
import static io.netty.resolver.dns.DnsResolveContext.TRY_FINAL_CNAME_ON_ADDRESS_LOOKUPS;
import static io.netty.resolver.dns.DnsServerAddresses.sequential;
import static io.netty.resolver.dns.TestDnsServer.newARecord;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DnsNameResolverTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);
    private static final long DEFAULT_TEST_TIMEOUT_MS = 30000;

    // Using the top-100 web sites ranked in Alexa.com (Oct 2014)
    // Please use the following series of shell commands to get this up-to-date:
    // $ curl -O https://s3.amazonaws.com/alexa-static/top-1m.csv.zip
    // $ unzip -o top-1m.csv.zip top-1m.csv
    // $ head -100 top-1m.csv | cut -d, -f2 | cut -d/ -f1 | while read L; do echo '"'"$L"'",'; done > topsites.txt
    private static final Set<String> DOMAINS = Collections.unmodifiableSet(new HashSet<String>(asList(
            "google.com",
            "youtube.com",
            "facebook.com",
            "baidu.com",
            "wikipedia.org",
            "yahoo.com",
            "reddit.com",
            "google.co.in",
            "qq.com",
            "amazon.com",
            "taobao.com",
            "tmall.com",
            "twitter.com",
            "vk.com",
            "live.com",
            "sohu.com",
            "instagram.com",
            "google.co.jp",
            "sina.com.cn",
            "jd.com",
            "weibo.com",
            "360.cn",
            "google.de",
            "google.co.uk",
            "google.com.br",
            "list.tmall.com",
            "google.ru",
            "google.fr",
            "yandex.ru",
            "netflix.com",
            "google.it",
            "google.com.hk",
            "linkedin.com",
            "pornhub.com",
            "t.co",
            "google.es",
            "twitch.tv",
            "alipay.com",
            "xvideos.com",
            "ebay.com",
            "yahoo.co.jp",
            "google.ca",
            "google.com.mx",
            "bing.com",
            "ok.ru",
            "imgur.com",
            "microsoft.com",
            "mail.ru",
            "imdb.com",
            "aliexpress.com",
            "hao123.com",
            "msn.com",
            "tumblr.com",
            "csdn.net",
            "wikia.com",
            "wordpress.com",
            "office.com",
            "google.com.tr",
            "livejasmin.com",
            "amazon.co.jp",
            "deloton.com",
            "apple.com",
            "google.com.au",
            "paypal.com",
            "google.com.tw",
            "bongacams.com",
            "popads.net",
            "whatsapp.com",
            "blogspot.com",
            "detail.tmall.com",
            "google.pl",
            "microsoftonline.com",
            "xhamster.com",
            "google.co.id",
            "github.com",
            "stackoverflow.com",
            "pinterest.com",
            "amazon.de",
            "diply.com",
            "amazon.co.uk",
            "so.com",
            "google.com.ar",
            "coccoc.com",
            "soso.com",
            "espn.com",
            "adobe.com",
            "google.com.ua",
            "tianya.cn",
            "xnxx.com",
            "googleusercontent.com",
            "savefrom.net",
            "google.com.pk",
            "amazon.in",
            "nicovideo.jp",
            "google.co.th",
            "dropbox.com",
            "thepiratebay.org",
            "google.com.sa",
            "google.com.eg",
            "pixnet.net",
            "localhost")));

    private static final Map<String, String> DOMAINS_PUNYCODE = new HashMap<String, String>();

    static {
        DOMAINS_PUNYCODE.put("büchner.de", "xn--bchner-3ya.de");
        DOMAINS_PUNYCODE.put("müller.de", "xn--mller-kva.de");
    }

    private static final Set<String> DOMAINS_ALL;

    static {
        Set<String> all = new HashSet<String>(DOMAINS.size() + DOMAINS_PUNYCODE.size());
        all.addAll(DOMAINS);
        all.addAll(DOMAINS_PUNYCODE.values());
        DOMAINS_ALL = Collections.unmodifiableSet(all);
    }

    /**
     * The list of the domain names to exclude from {@link #testResolveAorAAAA(DnsNameResolverChannelStrategy)}.
     */
    private static final Set<String> EXCLUSIONS_RESOLVE_A = new HashSet<String>();

    static {
        Collections.addAll(
                EXCLUSIONS_RESOLVE_A,
                "akamaihd.net",
                "googleusercontent.com",
                StringUtil.EMPTY_STRING);
    }

    /**
     * The list of the domain names to exclude from {@link #testResolveAAAA(DnsNameResolverChannelStrategy)}.
     * Unfortunately, there are only handful of domain names with IPv6 addresses.
     */
    private static final Set<String> EXCLUSIONS_RESOLVE_AAAA = new HashSet<String>();

    static {
        EXCLUSIONS_RESOLVE_AAAA.addAll(EXCLUSIONS_RESOLVE_A);
        EXCLUSIONS_RESOLVE_AAAA.addAll(DOMAINS);
        EXCLUSIONS_RESOLVE_AAAA.removeAll(asList(
                "google.com",
                "facebook.com",
                "youtube.com",
                "wikipedia.org",
                "google.co.in",
                "blogspot.com",
                "vk.com",
                "google.de",
                "google.co.jp",
                "google.co.uk",
                "google.fr",
                "google.com.br",
                "google.ru",
                "google.it",
                "google.es",
                "google.com.mx",
                "xhamster.com",
                "google.ca",
                "google.co.id",
                "blogger.com",
                "flipkart.com",
                "google.com.tr",
                "google.com.au",
                "google.pl",
                "google.com.hk",
                "blogspot.in"
        ));
    }

    /**
     * The list of the domain names to exclude from {@link #testQueryMx(DnsNameResolverChannelStrategy)}.
     */
    private static final Set<String> EXCLUSIONS_QUERY_MX = new HashSet<String>();

    static {
        Collections.addAll(
                EXCLUSIONS_QUERY_MX,
                "hao123.com",
                "blogspot.com",
                "t.co",
                "espn.go.com",
                "people.com.cn",
                "googleusercontent.com",
                "blogspot.in",
                "localhost",
                StringUtil.EMPTY_STRING);
    }

    private static final String WINDOWS_HOST_NAME;
    private static final boolean WINDOWS_HOSTS_FILE_LOCALHOST_ENTRY_EXISTS;
    private static final boolean WINDOWS_HOSTS_FILE_HOST_NAME_ENTRY_EXISTS;

    static {
        String windowsHostName;
        boolean windowsHostsFileLocalhostEntryExists;
        boolean windowsHostsFileHostNameEntryExists;
        try {
            if (PlatformDependent.isWindows()) {
                windowsHostName = InetAddress.getLocalHost().getHostName();

                HostsFileEntriesProvider provider =
                        HostsFileEntriesProvider.parser()
                                .parseSilently(Charset.defaultCharset(), CharsetUtil.UTF_16, CharsetUtil.UTF_8);
                windowsHostsFileLocalhostEntryExists =
                        provider.ipv4Entries().get("localhost") != null ||
                                provider.ipv6Entries().get("localhost") != null;
                windowsHostsFileHostNameEntryExists =
                        provider.ipv4Entries().get(windowsHostName) != null ||
                                provider.ipv6Entries().get(windowsHostName) != null;
            } else {
                windowsHostName = null;
                windowsHostsFileLocalhostEntryExists = false;
                windowsHostsFileHostNameEntryExists = false;
            }
        } catch (Exception ignore) {
            windowsHostName = null;
            windowsHostsFileLocalhostEntryExists = false;
            windowsHostsFileHostNameEntryExists = false;
        }
        WINDOWS_HOST_NAME = windowsHostName;
        WINDOWS_HOSTS_FILE_LOCALHOST_ENTRY_EXISTS = windowsHostsFileLocalhostEntryExists;
        WINDOWS_HOSTS_FILE_HOST_NAME_ENTRY_EXISTS = windowsHostsFileHostNameEntryExists;
    }

    private static final TestDnsServer dnsServer = new TestDnsServer(DOMAINS_ALL);
    private static final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

    private static DnsNameResolverBuilder newResolver(DnsNameResolverChannelStrategy strategy,
                                                      boolean decodeToUnicode) {
        return newResolver(strategy, decodeToUnicode, null);
    }

    private static DnsNameResolverBuilder newResolver(DnsNameResolverChannelStrategy strategy, boolean decodeToUnicode,
                                                      DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        return newResolver(strategy, decodeToUnicode, dnsServerAddressStreamProvider, dnsServer);
    }

    private static DnsNameResolverBuilder newResolver(DnsNameResolverChannelStrategy strategy, boolean decodeToUnicode,
                                                      DnsServerAddressStreamProvider dnsServerAddressStreamProvider,
                                                      TestDnsServer dnsServer) {
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder(group.next())
                .dnsQueryLifecycleObserverFactory(new TestRecursiveCacheDnsQueryLifecycleObserverFactory())
                .datagramChannelType(NioDatagramChannel.class)
                .maxQueriesPerResolve(1)
                .decodeIdn(decodeToUnicode)
                .optResourceEnabled(false)
                .ndots(1)
                .datagramChannelStrategy(strategy);

        if (dnsServerAddressStreamProvider == null) {
            builder.nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()));
        } else {
            builder.nameServerProvider(new MultiDnsServerAddressStreamProvider(dnsServerAddressStreamProvider,
                    new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress())));
        }

        return builder;
    }

    private static DnsNameResolverBuilder newResolver(DnsNameResolverChannelStrategy strategy) {
        return newResolver(strategy, true);
    }

    private static DnsNameResolverBuilder newResolver(DnsNameResolverChannelStrategy strategy,
                                                      ResolvedAddressTypes resolvedAddressTypes) {
        return newResolver(strategy)
                .resolvedAddressTypes(resolvedAddressTypes);
    }

    private static DnsNameResolverBuilder newNonCachedResolver(DnsNameResolverChannelStrategy strategy,
                                                               ResolvedAddressTypes resolvedAddressTypes) {
        return newResolver(strategy)
                .resolveCache(NoopDnsCache.INSTANCE)
                .resolvedAddressTypes(resolvedAddressTypes);
    }

    @BeforeAll
    public static void init() throws Exception {
        dnsServer.start();
    }

    @AfterAll
    public static void destroy() {
        dnsServer.stop();
        group.shutdownGracefully();
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAorAAAA(DnsNameResolverChannelStrategy strategy) throws Exception {
        DnsNameResolver resolver = newResolver(strategy, ResolvedAddressTypes.IPV4_PREFERRED).build();
        try {
            testResolve0(resolver, EXCLUSIONS_RESOLVE_A, AAAA);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAAAAorA(DnsNameResolverChannelStrategy strategy) throws Exception {
        DnsNameResolver resolver = newResolver(strategy, ResolvedAddressTypes.IPV6_PREFERRED).build();
        try {
            testResolve0(resolver, EXCLUSIONS_RESOLVE_A, A);
        } finally {
            resolver.close();
        }
    }

    /**
     * This test will start an second DNS test server which returns fixed results that can be easily verified as
     * originating from the second DNS test server. The resolver will put {@link DnsServerAddressStreamProvider} under
     * test to ensure that some hostnames can be directed toward both the primary and secondary DNS test servers
     * simultaneously.
     */
    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testNameServerCache(DnsNameResolverChannelStrategy channelStrategy)
            throws IOException, InterruptedException {
        final String overriddenIP = "12.34.12.34";
        final TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                switch (question.getRecordType()) {
                    case A:
                        Map<String, Object> attr = new HashMap<String, Object>();
                        attr.put(DnsAttribute.IP_ADDRESS.toLowerCase(Locale.US), overriddenIP);
                        return Collections.<ResourceRecord>singleton(
                                new TestDnsServer.TestResourceRecord(
                                        question.getDomainName(), question.getRecordType(), attr));
                    default:
                        return null;
                }
            }
        });
        dnsServer2.start();
        try {
            final Set<String> overriddenHostnames = new HashSet<String>();
            for (String name : DOMAINS) {
                if (EXCLUSIONS_RESOLVE_A.contains(name)) {
                    continue;
                }
                if (PlatformDependent.threadLocalRandom().nextBoolean()) {
                    overriddenHostnames.add(name);
                }
            }
            DnsNameResolver resolver = newResolver(channelStrategy, false, new DnsServerAddressStreamProvider() {
                @Override
                public DnsServerAddressStream nameServerAddressStream(String hostname) {
                    return overriddenHostnames.contains(hostname) ? sequential(dnsServer2.localAddress()).stream() :
                            null;
                }
            }).build();
            try {
                final Map<String, InetAddress> resultA = testResolve0(resolver, EXCLUSIONS_RESOLVE_A, AAAA);
                for (Entry<String, InetAddress> resolvedEntry : resultA.entrySet()) {
                    if (resolvedEntry.getValue().isLoopbackAddress()) {
                        continue;
                    }
                    if (overriddenHostnames.contains(resolvedEntry.getKey())) {
                        assertEquals(overriddenIP, resolvedEntry.getValue().getHostAddress(),
                                "failed to resolve " + resolvedEntry.getKey());
                    } else {
                        assertNotEquals(overriddenIP, resolvedEntry.getValue().getHostAddress(),
                                "failed to resolve " + resolvedEntry.getKey());
                    }
                }
            } finally {
                resolver.close();
            }
        } finally {
            dnsServer2.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveA(DnsNameResolverChannelStrategy strategy) throws Exception {
        DnsNameResolver resolver = newResolver(strategy, ResolvedAddressTypes.IPV4_ONLY)
                // Cache for eternity
                .ttl(Integer.MAX_VALUE, Integer.MAX_VALUE)
                .build();
        try {
            final Map<String, InetAddress> resultA = testResolve0(resolver, EXCLUSIONS_RESOLVE_A, null);

            // Now, try to resolve again to see if it's cached.
            // This test works because the DNS servers usually randomizes the order of the records in a response.
            // If cached, the resolved addresses must be always same, because we reuse the same response.

            final Map<String, InetAddress> resultB = testResolve0(resolver, EXCLUSIONS_RESOLVE_A, null);

            // Ensure the result from the cache is identical from the uncached one.
            assertThat(resultB.size(), is(resultA.size()));
            for (Entry<String, InetAddress> e : resultA.entrySet()) {
                InetAddress expected = e.getValue();
                InetAddress actual = resultB.get(e.getKey());
                assertThat("Cache for " + e.getKey() + ": " + resolver.resolveAll(e.getKey()).getNow(),
                        actual, is(expected));
            }
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAAAA(DnsNameResolverChannelStrategy strategy) throws Exception {
        DnsNameResolver resolver = newResolver(strategy, ResolvedAddressTypes.IPV6_ONLY).build();
        try {
            testResolve0(resolver, EXCLUSIONS_RESOLVE_AAAA, null);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testNonCachedResolve(DnsNameResolverChannelStrategy strategy) throws Exception {
        DnsNameResolver resolver = newNonCachedResolver(strategy, ResolvedAddressTypes.IPV4_ONLY).build();
        try {
            testResolve0(resolver, EXCLUSIONS_RESOLVE_A, null);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = DEFAULT_TEST_TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
    public void testNonCachedResolveEmptyHostName(DnsNameResolverChannelStrategy strategy) throws Exception {
        testNonCachedResolveEmptyHostName(strategy, "");
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = DEFAULT_TEST_TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
    public void testNonCachedResolveNullHostName(DnsNameResolverChannelStrategy strategy) throws Exception {
        testNonCachedResolveEmptyHostName(strategy, null);
    }

    private static void testNonCachedResolveEmptyHostName(DnsNameResolverChannelStrategy strategy, String inetHost)
            throws Exception {
        DnsNameResolver resolver = newNonCachedResolver(strategy, ResolvedAddressTypes.IPV4_ONLY).build();
        try {
            InetAddress addr = resolver.resolve(inetHost).syncUninterruptibly().getNow();
            assertEquals(SocketUtils.addressByName(inetHost), addr);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = DEFAULT_TEST_TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
    public void testNonCachedResolveAllEmptyHostName(DnsNameResolverChannelStrategy strategy) throws Exception {
        testNonCachedResolveAllEmptyHostName(strategy, "");
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = DEFAULT_TEST_TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
    public void testNonCachedResolveAllNullHostName(DnsNameResolverChannelStrategy strategy) throws Exception {
        testNonCachedResolveAllEmptyHostName(strategy, null);
    }

    private static void testNonCachedResolveAllEmptyHostName(DnsNameResolverChannelStrategy strategy, String inetHost)
            throws UnknownHostException {
        DnsNameResolver resolver = newNonCachedResolver(strategy, ResolvedAddressTypes.IPV4_ONLY).build();
        try {
            List<InetAddress> addrs = resolver.resolveAll(inetHost).syncUninterruptibly().getNow();
            assertEquals(asList(
                    SocketUtils.allAddressesByName(inetHost)), addrs);
        } finally {
            resolver.close();
        }
    }

    private static Map<String, InetAddress> testResolve0(DnsNameResolver resolver, Set<String> excludedDomains,
                                                         DnsRecordType cancelledType)
            throws InterruptedException {

        assertThat(resolver.isRecursionDesired(), is(true));

        final Map<String, InetAddress> results = new HashMap<String, InetAddress>();
        final Map<String, Future<InetAddress>> futures =
                new LinkedHashMap<String, Future<InetAddress>>();

        for (String name : DOMAINS) {
            if (excludedDomains.contains(name)) {
                continue;
            }

            resolve(resolver, futures, name);
        }

        for (Entry<String, Future<InetAddress>> e : futures.entrySet()) {
            String unresolved = e.getKey();
            InetAddress resolved = e.getValue().sync().getNow();

            logger.info("{}: {}", unresolved, resolved.getHostAddress());

            assertThat(resolved.getHostName(), is(unresolved));

            boolean typeMatches = false;
            for (SocketProtocolFamily f : resolver.resolvedInternetProtocolFamiliesUnsafe()) {
                Class<?> resolvedType = resolved.getClass();
                Class<? extends InetAddress> addressType = DnsNameResolver.addressType(f);
                assertNotNull(addressType);
                if (addressType.isAssignableFrom(resolvedType)) {
                    typeMatches = true;
                }
            }

            assertThat(typeMatches, is(true));

            results.put(resolved.getHostName(), resolved);
        }

        assertQueryObserver(resolver, cancelledType);

        return results;
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testQueryMx(DnsNameResolverChannelStrategy strategy) {
        DnsNameResolver resolver = newResolver(strategy).build();
        try {
            assertThat(resolver.isRecursionDesired(), is(true));

            Map<String, Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> futures =
                    new LinkedHashMap<String, Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>>();
            for (String name : DOMAINS) {
                if (EXCLUSIONS_QUERY_MX.contains(name)) {
                    continue;
                }

                queryMx(resolver, futures, name);
            }

            for (Entry<String, Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> e : futures.entrySet()) {
                String hostname = e.getKey();
                Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> f = e.getValue().awaitUninterruptibly();

                DnsResponse response = f.getNow().content();
                assertThat(response.code(), is(DnsResponseCode.NOERROR));

                final int answerCount = response.count(DnsSection.ANSWER);
                final List<DnsRecord> mxList = new ArrayList<DnsRecord>(answerCount);
                for (int i = 0; i < answerCount; i++) {
                    final DnsRecord r = response.recordAt(DnsSection.ANSWER, i);
                    if (r.type() == DnsRecordType.MX) {
                        mxList.add(r);
                    }
                }

                assertThat(mxList.size(), is(greaterThan(0)));
                StringBuilder buf = new StringBuilder();
                for (DnsRecord r : mxList) {
                    ByteBuf recordContent = ((ByteBufHolder) r).content();

                    buf.append(StringUtil.NEWLINE);
                    buf.append('\t');
                    buf.append(r.name());
                    buf.append(' ');
                    buf.append(r.type().name());
                    buf.append(' ');
                    buf.append(recordContent.readUnsignedShort());
                    buf.append(' ');
                    buf.append(DnsResolveContext.decodeDomainName(recordContent));
                }

                logger.info("{} has the following MX records:{}", hostname, buf);
                response.release();

                // We only track query lifecycle if it is managed by the DnsNameResolverContext, and not direct calls
                // to query.
                assertNoQueriesMade(resolver);
            }
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testNegativeTtl(DnsNameResolverChannelStrategy strategy) throws Exception {
        final DnsNameResolver resolver = newResolver(strategy).negativeTtl(10).build();
        try {
            resolveNonExistentDomain(resolver);

            final int size = 10000;
            final List<UnknownHostException> exceptions = new ArrayList<UnknownHostException>();

            // If negative cache works, this thread should be done really quickly.
            final Thread negativeLookupThread = new Thread() {
                @Override
                public void run() {
                    for (int i = 0; i < size; i++) {
                        exceptions.add(resolveNonExistentDomain(resolver));
                        if (isInterrupted()) {
                            break;
                        }
                    }
                }
            };

            negativeLookupThread.start();
            negativeLookupThread.join(DEFAULT_TEST_TIMEOUT_MS);

            if (negativeLookupThread.isAlive()) {
                negativeLookupThread.interrupt();
                fail("Cached negative lookups did not finish quickly.");
            }

            assertThat(exceptions, hasSize(size));
        } finally {
            resolver.close();
        }
    }

    private static UnknownHostException resolveNonExistentDomain(DnsNameResolver resolver) {
        try {
            resolver.resolve("non-existent.netty.io").sync();
            fail();
            return null;
        } catch (Exception e) {
            assertThat(e, is(instanceOf(UnknownHostException.class)));

            TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                    (TestRecursiveCacheDnsQueryLifecycleObserverFactory) resolver.dnsQueryLifecycleObserverFactory();
            TestDnsQueryLifecycleObserver observer = lifecycleObserverFactory.observers.poll();
            if (observer != null) {
                Object o = observer.events.poll();
                if (o instanceof QueryCancelledEvent) {
                    assertTrue(observer.question.type() == CNAME || observer.question.type() == AAAA,
                        "unexpected type: " + observer.question);
                } else if (o instanceof QueryWrittenEvent) {
                    QueryFailedEvent failedEvent = (QueryFailedEvent) observer.events.poll();
                } else if (!(o instanceof QueryFailedEvent)) {
                    fail("unexpected event type: " + o);
                }
                assertTrue(observer.events.isEmpty());
            }
            return (UnknownHostException) e;
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveIp(DnsNameResolverChannelStrategy strategy) {
        DnsNameResolver resolver = newResolver(strategy).build();
        try {
            InetAddress address = resolver.resolve("10.0.0.1").syncUninterruptibly().getNow();

            assertEquals("10.0.0.1", address.getHostAddress());

            // This address is already resolved, and so we shouldn't have to query for anything.
            assertNoQueriesMade(resolver);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveEmptyIpv4(DnsNameResolverChannelStrategy strategy) {
        testResolve0(strategy, ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, StringUtil.EMPTY_STRING);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveEmptyIpv6(DnsNameResolverChannelStrategy strategy) {
        testResolve0(strategy, ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, StringUtil.EMPTY_STRING);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveLocalhostIpv4(DnsNameResolverChannelStrategy strategy) {
        assumeThat(PlatformDependent.isWindows()).isTrue();
        assumeThat(WINDOWS_HOSTS_FILE_LOCALHOST_ENTRY_EXISTS).isFalse();
        assumeThat(DEFAULT_RESOLVE_ADDRESS_TYPES).isNotEqualTo(ResolvedAddressTypes.IPV6_PREFERRED);
        testResolve0(strategy, ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, "localhost");
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveLocalhostIpv6(DnsNameResolverChannelStrategy strategy) {
        assumeThat(PlatformDependent.isWindows()).isTrue();
        assumeThat(WINDOWS_HOSTS_FILE_LOCALHOST_ENTRY_EXISTS).isFalse();
        assumeThat(DEFAULT_RESOLVE_ADDRESS_TYPES).isEqualTo(ResolvedAddressTypes.IPV6_PREFERRED);
        testResolve0(strategy, ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, "localhost");
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveHostNameIpv4(DnsNameResolverChannelStrategy strategy) {
        assumeThat(PlatformDependent.isWindows()).isTrue();
        assumeThat(WINDOWS_HOSTS_FILE_HOST_NAME_ENTRY_EXISTS).isFalse();
        assumeThat(DEFAULT_RESOLVE_ADDRESS_TYPES).isNotEqualTo(ResolvedAddressTypes.IPV6_PREFERRED);
        testResolve0(strategy, ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, WINDOWS_HOST_NAME);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveHostNameIpv6(DnsNameResolverChannelStrategy strategy) {
        assumeThat(PlatformDependent.isWindows()).isTrue();
        assumeThat(WINDOWS_HOSTS_FILE_HOST_NAME_ENTRY_EXISTS).isFalse();
        assumeThat(DEFAULT_RESOLVE_ADDRESS_TYPES).isEqualTo(ResolvedAddressTypes.IPV6_PREFERRED);
        testResolve0(strategy, ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, WINDOWS_HOST_NAME);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveNullIpv4(DnsNameResolverChannelStrategy strategy) {
        testResolve0(strategy, ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, null);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveNullIpv6(DnsNameResolverChannelStrategy strategy) {
        testResolve0(strategy, ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, null);
    }

    private static void testResolve0(DnsNameResolverChannelStrategy strategy, ResolvedAddressTypes addressTypes,
                                     InetAddress expectedAddr, String name) {
        DnsNameResolver resolver = newResolver(strategy, addressTypes).build();
        try {
            InetAddress address = resolver.resolve(name).syncUninterruptibly().getNow();
            assertEquals(expectedAddr, address);

            // We are resolving the local address, so we shouldn't make any queries.
            assertNoQueriesMade(resolver);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllEmptyIpv4(DnsNameResolverChannelStrategy strategy) {
        testResolveAll0(strategy, ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, StringUtil.EMPTY_STRING);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllEmptyIpv6(DnsNameResolverChannelStrategy strategy) {
        testResolveAll0(strategy, ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, StringUtil.EMPTY_STRING);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllLocalhostIpv4(DnsNameResolverChannelStrategy strategy) {
        assumeThat(PlatformDependent.isWindows()).isTrue();
        assumeThat(WINDOWS_HOSTS_FILE_LOCALHOST_ENTRY_EXISTS).isFalse();
        assumeThat(DEFAULT_RESOLVE_ADDRESS_TYPES).isNotEqualTo(ResolvedAddressTypes.IPV6_PREFERRED);
        testResolveAll0(strategy, ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, "localhost");
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllLocalhostIpv6(DnsNameResolverChannelStrategy strategy) {
        assumeThat(PlatformDependent.isWindows()).isTrue();
        assumeThat(WINDOWS_HOSTS_FILE_LOCALHOST_ENTRY_EXISTS).isFalse();
        assumeThat(DEFAULT_RESOLVE_ADDRESS_TYPES).isEqualTo(ResolvedAddressTypes.IPV6_PREFERRED);
        testResolveAll0(strategy, ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, "localhost");
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllHostNameIpv4(DnsNameResolverChannelStrategy strategy) {
        assumeThat(PlatformDependent.isWindows()).isTrue();
        assumeThat(WINDOWS_HOSTS_FILE_HOST_NAME_ENTRY_EXISTS).isFalse();
        assumeThat(DEFAULT_RESOLVE_ADDRESS_TYPES).isNotEqualTo(ResolvedAddressTypes.IPV6_PREFERRED);
        testResolveAll0(strategy, ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, WINDOWS_HOST_NAME);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllHostNameIpv6(DnsNameResolverChannelStrategy strategy) {
        assumeThat(PlatformDependent.isWindows()).isTrue();
        assumeThat(WINDOWS_HOSTS_FILE_HOST_NAME_ENTRY_EXISTS).isFalse();
        assumeThat(DEFAULT_RESOLVE_ADDRESS_TYPES).isEqualTo(ResolvedAddressTypes.IPV6_PREFERRED);
        testResolveAll0(strategy, ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, WINDOWS_HOST_NAME);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNAMEResolveAllIpv4(DnsNameResolverChannelStrategy strategy) throws IOException {
        testCNAMERecursiveResolve(strategy, true);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNAMEResolveAllIpv6(DnsNameResolverChannelStrategy strategy) throws IOException {
        testCNAMERecursiveResolve(strategy, false);
    }

    private static void testCNAMERecursiveResolve(DnsNameResolverChannelStrategy strategy, boolean ipv4Preferred)
            throws IOException {
        final String firstName = "firstname.com";
        final String secondName = "secondname.com";
        final String lastName = "lastname.com";
        final String ipv4Addr = "1.2.3.4";
        final String ipv6Addr = "::1";
        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                ResourceRecordModifier rm = new ResourceRecordModifier();
                rm.setDnsClass(RecordClass.IN);
                rm.setDnsName(question.getDomainName());
                rm.setDnsTtl(100);
                rm.setDnsType(RecordType.CNAME);

                if (question.getDomainName().equals(firstName)) {
                    rm.put(DnsAttribute.DOMAIN_NAME, secondName);
                } else if (question.getDomainName().equals(secondName)) {
                    rm.put(DnsAttribute.DOMAIN_NAME, lastName);
                } else if (question.getDomainName().equals(lastName)) {
                    rm.setDnsType(question.getRecordType());
                    switch (question.getRecordType()) {
                        case A:
                            rm.put(DnsAttribute.IP_ADDRESS, ipv4Addr);
                            break;
                        case AAAA:
                            rm.put(DnsAttribute.IP_ADDRESS, ipv6Addr);
                            break;
                        default:
                            return null;
                    }
                } else {
                    return null;
                }
                return Collections.singleton(rm.getEntry());
            }
        });
        dnsServer2.start();
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(true)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));
            if (ipv4Preferred) {
                builder.resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED);
            } else {
                builder.resolvedAddressTypes(ResolvedAddressTypes.IPV6_PREFERRED);
            }
            resolver = builder.build();
            InetAddress resolvedAddress = resolver.resolve(firstName).syncUninterruptibly().getNow();
            if (ipv4Preferred) {
                assertEquals(ipv4Addr, resolvedAddress.getHostAddress());
            } else {
                assertEquals(ipv6Addr, NetUtil.toAddressString(resolvedAddress));
            }
            assertEquals(firstName, resolvedAddress.getHostName());
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNAMERecursiveResolveMultipleNameServersIPv4(DnsNameResolverChannelStrategy strategy)
            throws IOException {
        testCNAMERecursiveResolveMultipleNameServers(strategy, true);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNAMERecursiveResolveMultipleNameServersIPv6(DnsNameResolverChannelStrategy strategy)
            throws IOException {
        testCNAMERecursiveResolveMultipleNameServers(strategy, false);
    }

    private static void testCNAMERecursiveResolveMultipleNameServers(DnsNameResolverChannelStrategy strategy,
                                                                     boolean ipv4Preferred) throws IOException {
        final String firstName = "firstname.nettyfoo.com";
        final String lastName = "lastname.nettybar.com";
        final String ipv4Addr = "1.2.3.4";
        final String ipv6Addr = "::1";
        final AtomicBoolean hitServer2 = new AtomicBoolean();
        final TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) throws DnsException {
                hitServer2.set(true);
                if (question.getDomainName().equals(firstName)) {
                    ResourceRecordModifier rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(question.getDomainName());
                    rm.setDnsTtl(100);
                    rm.setDnsType(RecordType.CNAME);
                    rm.put(DnsAttribute.DOMAIN_NAME, lastName);
                    return Collections.singleton(rm.getEntry());
                } else {
                    throw new DnsException(ResponseCode.REFUSED);
                }
            }
        });
        final TestDnsServer dnsServer3 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) throws DnsException {
                if (question.getDomainName().equals(lastName)) {
                    ResourceRecordModifier rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(question.getDomainName());
                    rm.setDnsTtl(100);
                    rm.setDnsType(question.getRecordType());
                    switch (question.getRecordType()) {
                        case A:
                            rm.put(DnsAttribute.IP_ADDRESS, ipv4Addr);
                            break;
                        case AAAA:
                            rm.put(DnsAttribute.IP_ADDRESS, ipv6Addr);
                            break;
                        default:
                            return null;
                    }

                    return Collections.singleton(rm.getEntry());
                } else {
                    throw new DnsException(ResponseCode.REFUSED);
                }
            }
        });
        dnsServer2.start();
        dnsServer3.start();
        DnsNameResolver resolver = null;
        try {
            AuthoritativeDnsServerCache nsCache = new DefaultAuthoritativeDnsServerCache();
            // What we want to test is the following:
            // 1. Do a DNS query.
            // 2. CNAME is returned, we want to lookup that CNAME on multiple DNS servers
            // 3. The first DNS server should fail
            // 4. The second DNS server should succeed
            // This verifies that we do in fact follow multiple DNS servers in the CNAME resolution.
            // The DnsCache is used for the name server cache, but doesn't provide a InetSocketAddress (only InetAddress
            // so no port), so we only specify the name server in the cache, and then specify both name servers in the
            // fallback name server provider.
            nsCache.cache("nettyfoo.com.", dnsServer2.localAddress(), 10000, group.next());
            SequentialDnsServerAddressStreamProvider provider =
                    new SequentialDnsServerAddressStreamProvider(dnsServer2.localAddress(), dnsServer3.localAddress());
            resolver = new DnsNameResolver(
                    group.next(), new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class), null,
                    false, NoopDnsCache.INSTANCE, NoopDnsCnameCache.INSTANCE, nsCache, null,
                    NoopDnsQueryLifecycleObserverFactory.INSTANCE, 3000,
                    ipv4Preferred ? ResolvedAddressTypes.IPV4_ONLY : ResolvedAddressTypes.IPV6_ONLY, true,
                    10, true, 4096, false, HostsFileEntriesResolver.DEFAULT,
                    provider, new ThreadLocalNameServerAddressStream(provider),
                    DnsNameResolver.DEFAULT_SEARCH_DOMAINS, 0, true, false, 0, strategy) {
                @Override
                InetSocketAddress newRedirectServerAddress(InetAddress server) {
                    int port = hitServer2.get() ? dnsServer3.localAddress().getPort() :
                            dnsServer2.localAddress().getPort();
                    return new InetSocketAddress(server, port);
                }
            };
            InetAddress resolvedAddress = resolver.resolve(firstName).syncUninterruptibly().getNow();
            if (ipv4Preferred) {
                assertEquals(ipv4Addr, resolvedAddress.getHostAddress());
            } else {
                assertEquals(ipv6Addr, NetUtil.toAddressString(resolvedAddress));
            }
            assertEquals(firstName, resolvedAddress.getHostName());
        } finally {
            dnsServer2.stop();
            dnsServer3.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllNullIpv4(DnsNameResolverChannelStrategy strategy) {
        testResolveAll0(strategy, ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, null);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllNullIpv6(DnsNameResolverChannelStrategy strategy) {
        testResolveAll0(strategy, ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, null);
    }

    private static void testResolveAll0(DnsNameResolverChannelStrategy strategy, ResolvedAddressTypes addressTypes,
                                        InetAddress expectedAddr, String name) {
        DnsNameResolver resolver = newResolver(strategy, addressTypes).build();
        try {
            List<InetAddress> addresses = resolver.resolveAll(name).syncUninterruptibly().getNow();
            assertEquals(1, addresses.size());
            assertEquals(expectedAddr, addresses.get(0));

            // We are resolving the local address, so we shouldn't make any queries.
            assertNoQueriesMade(resolver);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllMx(DnsNameResolverChannelStrategy strategy) {
        final DnsNameResolver resolver = newResolver(strategy).build();
        try {
            assertThat(resolver.isRecursionDesired(), is(true));

            final Map<String, Future<List<DnsRecord>>> futures = new LinkedHashMap<String, Future<List<DnsRecord>>>();
            for (String name : DOMAINS) {
                if (EXCLUSIONS_QUERY_MX.contains(name)) {
                    continue;
                }

                futures.put(name, resolver.resolveAll(new DefaultDnsQuestion(name, DnsRecordType.MX)));
            }

            for (Entry<String, Future<List<DnsRecord>>> e : futures.entrySet()) {
                String hostname = e.getKey();
                Future<List<DnsRecord>> f = e.getValue().awaitUninterruptibly();

                final List<DnsRecord> mxList = f.getNow();
                assertThat(mxList.size(), is(greaterThan(0)));
                StringBuilder buf = new StringBuilder();
                for (DnsRecord r : mxList) {
                    ByteBuf recordContent = ((ByteBufHolder) r).content();

                    buf.append(StringUtil.NEWLINE);
                    buf.append('\t');
                    buf.append(r.name());
                    buf.append(' ');
                    buf.append(r.type().name());
                    buf.append(' ');
                    buf.append(recordContent.readUnsignedShort());
                    buf.append(' ');
                    buf.append(DnsResolveContext.decodeDomainName(recordContent));

                    ReferenceCountUtil.release(r);
                }

                logger.info("{} has the following MX records:{}", hostname, buf);
            }
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllHostsFile(DnsNameResolverChannelStrategy strategy) {
        final DnsNameResolver resolver = new DnsNameResolverBuilder(group.next())
                .datagramChannelType(NioDatagramChannel.class)
                .hostsFileEntriesResolver(new HostsFileEntriesResolver() {
                    @Override
                    public InetAddress address(String inetHost, ResolvedAddressTypes resolvedAddressTypes) {
                        if ("foo.com.".equals(inetHost)) {
                            try {
                                return InetAddress.getByAddress("foo.com", new byte[] { 1, 2, 3, 4 });
                            } catch (UnknownHostException e) {
                                throw new Error(e);
                            }
                        }
                        return null;
                    }
                })
                .datagramChannelStrategy(strategy)
                .build();

        final List<DnsRecord> records = resolver.resolveAll(new DefaultDnsQuestion("foo.com.", A))
                .syncUninterruptibly().getNow();
        assertThat(records, Matchers.<DnsRecord>hasSize(1));
        assertThat(records.get(0), Matchers.<DnsRecord>instanceOf(DnsRawRecord.class));

        final DnsRawRecord record = (DnsRawRecord) records.get(0);
        final ByteBuf content = record.content();
        assertThat(record.name(), is("foo.com."));
        assertThat(record.dnsClass(), is(DnsRecord.CLASS_IN));
        assertThat(record.type(), is(A));
        assertThat(content.readableBytes(), is(4));
        assertThat(content.readInt(), is(0x01020304));
        record.release();
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveDecodeUnicode(DnsNameResolverChannelStrategy strategy) {
        testResolveUnicode(strategy, true);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveNotDecodeUnicode(DnsNameResolverChannelStrategy strategy) {
        testResolveUnicode(strategy, false);
    }

    private static void testResolveUnicode(DnsNameResolverChannelStrategy strategy, boolean decode) {
        DnsNameResolver resolver = newResolver(strategy, decode).build();
        try {
            for (Entry<String, String> entries : DOMAINS_PUNYCODE.entrySet()) {
                InetAddress address = resolver.resolve(entries.getKey()).syncUninterruptibly().getNow();
                assertEquals(decode ? entries.getKey() : entries.getValue(), address.getHostName());
            }

            assertQueryObserver(resolver, AAAA);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = DEFAULT_TEST_TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
    public void secondDnsServerShouldBeUsedBeforeCNAMEFirstServerNotStarted(DnsNameResolverChannelStrategy strategy)
            throws IOException {
        secondDnsServerShouldBeUsedBeforeCNAME(strategy, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = DEFAULT_TEST_TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
    public void secondDnsServerShouldBeUsedBeforeCNAMEFirstServerFailResolve(DnsNameResolverChannelStrategy strategy)
            throws IOException {
        secondDnsServerShouldBeUsedBeforeCNAME(strategy, true);
    }

    private static void secondDnsServerShouldBeUsedBeforeCNAME(
            DnsNameResolverChannelStrategy strategy, boolean startDnsServer1) throws IOException {
        final String knownHostName = "netty.io";
        final TestDnsServer dnsServer1 = new TestDnsServer(Collections.singleton("notnetty.com"));
        final TestDnsServer dnsServer2 = new TestDnsServer(Collections.singleton(knownHostName));
        DnsNameResolver resolver = null;
        try {
            final InetSocketAddress dnsServer1Address;
            if (startDnsServer1) {
                dnsServer1.start();
                dnsServer1Address = dnsServer1.localAddress();
            } else {
                // Some address where a DNS server will not be running.
                dnsServer1Address = new InetSocketAddress("127.0.0.1", 22);
            }
            dnsServer2.start();

            TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                    new TestRecursiveCacheDnsQueryLifecycleObserverFactory();

            DnsNameResolverBuilder builder = new DnsNameResolverBuilder(group.next())
                    .dnsQueryLifecycleObserverFactory(lifecycleObserverFactory)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .datagramChannelType(NioDatagramChannel.class)
                    .queryTimeoutMillis(1000) // We expect timeouts if startDnsServer1 is false
                    .optResourceEnabled(false)
                    .ndots(1)
                    .datagramChannelStrategy(strategy);

            builder.nameServerProvider(new SequentialDnsServerAddressStreamProvider(dnsServer1Address,
                    dnsServer2.localAddress()));
            resolver = builder.build();
            assertNotNull(resolver.resolve(knownHostName).syncUninterruptibly().getNow());

            TestDnsQueryLifecycleObserver observer = lifecycleObserverFactory.observers.poll();
            assertNotNull(observer);
            assertEquals(1, lifecycleObserverFactory.observers.size());
            assertEquals(2, observer.events.size());
            QueryWrittenEvent writtenEvent = (QueryWrittenEvent) observer.events.poll();
            assertEquals(dnsServer1Address, writtenEvent.dnsServerAddress);
            QueryFailedEvent failedEvent = (QueryFailedEvent) observer.events.poll();

            observer = lifecycleObserverFactory.observers.poll();
            assertEquals(2, observer.events.size());
            writtenEvent = (QueryWrittenEvent) observer.events.poll();
            assertEquals(dnsServer2.localAddress(), writtenEvent.dnsServerAddress);
            QuerySucceededEvent succeededEvent = (QuerySucceededEvent) observer.events.poll();
        } finally {
            if (resolver != null) {
                resolver.close();
            }
            dnsServer1.stop();
            dnsServer2.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = DEFAULT_TEST_TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
    public void aAndAAAAQueryShouldTryFirstDnsServerBeforeSecond(DnsNameResolverChannelStrategy strategy)
            throws IOException {
        final String knownHostName = "netty.io";
        final TestDnsServer dnsServer1 = new TestDnsServer(Collections.singleton("notnetty.com"));
        final TestDnsServer dnsServer2 = new TestDnsServer(Collections.singleton(knownHostName));
        DnsNameResolver resolver = null;
        try {
            dnsServer1.start();
            dnsServer2.start();

            TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                    new TestRecursiveCacheDnsQueryLifecycleObserverFactory();

            DnsNameResolverBuilder builder = new DnsNameResolverBuilder(group.next())
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .dnsQueryLifecycleObserverFactory(lifecycleObserverFactory)
                    .datagramChannelType(NioDatagramChannel.class)
                    .optResourceEnabled(false)
                    .ndots(1)
                    .datagramChannelStrategy(strategy);

            builder.nameServerProvider(new SequentialDnsServerAddressStreamProvider(dnsServer1.localAddress(),
                    dnsServer2.localAddress()));
            resolver = builder.build();
            assertNotNull(resolver.resolve(knownHostName).syncUninterruptibly().getNow());

            TestDnsQueryLifecycleObserver observer = lifecycleObserverFactory.observers.poll();
            assertNotNull(observer);
            assertEquals(1, lifecycleObserverFactory.observers.size());
            assertEquals(2, observer.events.size());
            QueryWrittenEvent writtenEvent = (QueryWrittenEvent) observer.events.poll();
            assertEquals(dnsServer1.localAddress(), writtenEvent.dnsServerAddress);
            QueryFailedEvent failedEvent = (QueryFailedEvent) observer.events.poll();

            observer = lifecycleObserverFactory.observers.poll();
            assertEquals(2, observer.events.size());
            writtenEvent = (QueryWrittenEvent) observer.events.poll();
            assertEquals(dnsServer2.localAddress(), writtenEvent.dnsServerAddress);
            QuerySucceededEvent succeededEvent = (QuerySucceededEvent) observer.events.poll();
        } finally {
            if (resolver != null) {
                resolver.close();
            }
            dnsServer1.stop();
            dnsServer2.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testRecursiveResolveNoCache(DnsNameResolverChannelStrategy strategy) throws Exception {
        testRecursiveResolveCache(strategy, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testRecursiveResolveCache(DnsNameResolverChannelStrategy strategy) throws Exception {
        testRecursiveResolveCache(strategy, true);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testIpv4PreferredWhenIpv6First(DnsNameResolverChannelStrategy strategy) throws Exception {
        testResolvesPreferredWhenNonPreferredFirst0(strategy, ResolvedAddressTypes.IPV4_PREFERRED);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testIpv6PreferredWhenIpv4First(DnsNameResolverChannelStrategy strategy) throws Exception {
        testResolvesPreferredWhenNonPreferredFirst0(strategy, ResolvedAddressTypes.IPV6_PREFERRED);
    }

    private static void testResolvesPreferredWhenNonPreferredFirst0(
            DnsNameResolverChannelStrategy strategy, ResolvedAddressTypes types) throws Exception {
        final String name = "netty.com";
        // This store is non-compliant, returning records of the wrong type for a query.
        // It works since we don't verify the type of the result when resolving to deal with
        // non-compliant servers in the wild.
        List<Set<ResourceRecord>> records = new ArrayList<Set<ResourceRecord>>();
        final String ipv6Address = "0:0:0:0:0:0:1:1";
        final String ipv4Address = "1.1.1.1";
        if (types == ResolvedAddressTypes.IPV4_PREFERRED) {
            records.add(Collections.singleton(TestDnsServer.newAddressRecord(name, RecordType.AAAA, ipv6Address)));
            records.add(Collections.singleton(TestDnsServer.newAddressRecord(name, RecordType.A, ipv4Address)));
        } else {
            records.add(Collections.singleton(TestDnsServer.newAddressRecord(name, RecordType.A, ipv4Address)));
            records.add(Collections.singleton(TestDnsServer.newAddressRecord(name, RecordType.AAAA, ipv6Address)));
        }
        final Iterator<Set<ResourceRecord>> recordsIterator = records.iterator();
        RecordStore arbitrarilyOrderedStore = new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                return recordsIterator.next();
            }
        };
        TestDnsServer nonCompliantDnsServer = new TestDnsServer(arbitrarilyOrderedStore);
        nonCompliantDnsServer.start();
        try {
            DnsNameResolver resolver = newResolver(strategy, types)
                    .maxQueriesPerResolve(2)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(
                            nonCompliantDnsServer.localAddress()))
                    .build();
            InetAddress resolved = resolver.resolve("netty.com").syncUninterruptibly().getNow();
            if (types == ResolvedAddressTypes.IPV4_PREFERRED) {
                assertEquals(ipv4Address, resolved.getHostAddress());
            } else {
                assertEquals(ipv6Address, resolved.getHostAddress());
            }
            InetAddress ipv4InetAddress = InetAddress.getByAddress("netty.com",
                    InetAddress.getByName(ipv4Address).getAddress());
            InetAddress ipv6InetAddress = InetAddress.getByAddress("netty.com",
                    InetAddress.getByName(ipv6Address).getAddress());

            List<InetAddress> resolvedAll = resolver.resolveAll("netty.com").syncUninterruptibly().getNow();
            List<InetAddress> expected = types == ResolvedAddressTypes.IPV4_PREFERRED ?
                    asList(ipv4InetAddress, ipv6InetAddress) :  asList(ipv6InetAddress, ipv4InetAddress);
            assertEquals(expected, resolvedAll);
        } finally {
            nonCompliantDnsServer.stop();
        }
    }

    private static void testRecursiveResolveCache(DnsNameResolverChannelStrategy strategy, boolean cache)
            throws Exception {
        final String hostname = "some.record.netty.io";
        final String hostname2 = "some2.record.netty.io";

        final TestDnsServer dnsServerAuthority = new TestDnsServer(new HashSet<String>(
                asList(hostname, hostname2)));
        dnsServerAuthority.start();

        TestDnsServer dnsServer = new RedirectingTestDnsServer(hostname,
                dnsServerAuthority.localAddress().getAddress().getHostAddress());
        dnsServer.start();

        TestAuthoritativeDnsServerCache nsCache = new TestAuthoritativeDnsServerCache(
                cache ? new DefaultAuthoritativeDnsServerCache() : NoopAuthoritativeDnsServerCache.INSTANCE);
        TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                new TestRecursiveCacheDnsQueryLifecycleObserverFactory();

        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        SingletonDnsServerAddressStreamProvider provider =
                new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress());
        final DnsNameResolver resolver = new DnsNameResolver(
                group.next(), new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class), null,
                false, NoopDnsCache.INSTANCE, NoopDnsCnameCache.INSTANCE, nsCache, null,
                lifecycleObserverFactory, 3000, ResolvedAddressTypes.IPV4_ONLY, true,
                10, true, 4096, false, HostsFileEntriesResolver.DEFAULT,
                provider, new ThreadLocalNameServerAddressStream(provider), DnsNameResolver.DEFAULT_SEARCH_DOMAINS,
                0, true, false, 0, strategy) {
            @Override
            InetSocketAddress newRedirectServerAddress(InetAddress server) {
                if (server.equals(dnsServerAuthority.localAddress().getAddress())) {
                    return new InetSocketAddress(server, dnsServerAuthority.localAddress().getPort());
                }
                return super.newRedirectServerAddress(server);
            }
        };

        String expectedDnsName = "dns4.some.record.netty.io.";

        try {
            resolver.resolveAll(hostname).syncUninterruptibly();

            TestDnsQueryLifecycleObserver observer = lifecycleObserverFactory.observers.poll();
            assertNotNull(observer);
            assertTrue(lifecycleObserverFactory.observers.isEmpty());
            assertEquals(4, observer.events.size());
            QueryWrittenEvent writtenEvent1 = (QueryWrittenEvent) observer.events.poll();
            assertEquals(dnsServer.localAddress(), writtenEvent1.dnsServerAddress);
            QueryRedirectedEvent redirectedEvent = (QueryRedirectedEvent) observer.events.poll();

            assertEquals(expectedDnsName, redirectedEvent.nameServers.get(0).getHostName());
            assertEquals(dnsServerAuthority.localAddress(), redirectedEvent.nameServers.get(0));
            QueryWrittenEvent writtenEvent2 = (QueryWrittenEvent) observer.events.poll();
            assertEquals(dnsServerAuthority.localAddress(), writtenEvent2.dnsServerAddress);
            QuerySucceededEvent succeededEvent = (QuerySucceededEvent) observer.events.poll();

            if (cache) {
                assertNull(nsCache.cache.get("io."));
                assertNull(nsCache.cache.get("netty.io."));
                DnsServerAddressStream entries = nsCache.cache.get("record.netty.io.");

                // First address should be resolved (as we received a matching additional record), second is unresolved.
                assertEquals(2, entries.size());
                assertFalse(entries.next().isUnresolved());
                assertTrue(entries.next().isUnresolved());

                assertNull(nsCache.cache.get(hostname));

                // Test again via cache.
                resolver.resolveAll(hostname).syncUninterruptibly();

                observer = lifecycleObserverFactory.observers.poll();
                assertNotNull(observer);
                assertTrue(lifecycleObserverFactory.observers.isEmpty());
                assertEquals(2, observer.events.size());
                writtenEvent1 = (QueryWrittenEvent) observer.events.poll();
                assertEquals(expectedDnsName, writtenEvent1.dnsServerAddress.getHostName());
                assertEquals(dnsServerAuthority.localAddress(), writtenEvent1.dnsServerAddress);
                succeededEvent = (QuerySucceededEvent) observer.events.poll();

                resolver.resolveAll(hostname2).syncUninterruptibly();

                observer = lifecycleObserverFactory.observers.poll();
                assertNotNull(observer);
                assertTrue(lifecycleObserverFactory.observers.isEmpty());
                assertEquals(2, observer.events.size());
                writtenEvent1 = (QueryWrittenEvent) observer.events.poll();
                assertEquals(expectedDnsName, writtenEvent1.dnsServerAddress.getHostName());
                assertEquals(dnsServerAuthority.localAddress(), writtenEvent1.dnsServerAddress);
                succeededEvent = (QuerySucceededEvent) observer.events.poll();

                // Check that it only queried the cache for record.netty.io.
                assertNull(nsCache.cacheHits.get("io."));
                assertNull(nsCache.cacheHits.get("netty.io."));
                assertNotNull(nsCache.cacheHits.get("record.netty.io."));
                assertNull(nsCache.cacheHits.get("some.record.netty.io."));
            }
        } finally {
            resolver.close();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
            dnsServer.stop();
            dnsServerAuthority.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testFollowNsRedirectsNoopCaches(DnsNameResolverChannelStrategy strategy) throws Exception {
        testFollowNsRedirects(strategy, NoopDnsCache.INSTANCE, NoopAuthoritativeDnsServerCache.INSTANCE, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testFollowNsRedirectsNoopDnsCache(DnsNameResolverChannelStrategy strategy) throws Exception {
        testFollowNsRedirects(strategy, NoopDnsCache.INSTANCE, new DefaultAuthoritativeDnsServerCache(), false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testFollowNsRedirectsNoopAuthoritativeDnsServerCache(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        testFollowNsRedirects(strategy, new DefaultDnsCache(), NoopAuthoritativeDnsServerCache.INSTANCE, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testFollowNsRedirectsDefaultCaches(DnsNameResolverChannelStrategy strategy) throws Exception {
        testFollowNsRedirects(strategy, new DefaultDnsCache(), new DefaultAuthoritativeDnsServerCache(), false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testFollowNsRedirectAndTrySecondNsOnTimeout(DnsNameResolverChannelStrategy strategy) throws Exception {
        testFollowNsRedirects(strategy, NoopDnsCache.INSTANCE, NoopAuthoritativeDnsServerCache.INSTANCE, true);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testFollowNsRedirectAndTrySecondNsOnTimeoutDefaultCaches(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        testFollowNsRedirects(strategy, new DefaultDnsCache(), new DefaultAuthoritativeDnsServerCache(), true);
    }

    private void testFollowNsRedirects(DnsNameResolverChannelStrategy strategy, DnsCache cache,
                                       AuthoritativeDnsServerCache authoritativeDnsServerCache,
                                       final boolean invalidNsFirst) throws Exception {
        final String domain = "netty.io";
        final String ns1Name = "ns1." + domain;
        final String ns2Name = "ns2." + domain;
        final InetAddress expected = InetAddress.getByAddress("some.record." + domain, new byte[] { 10, 10, 10, 10 });

        // This is used to simulate a query timeout...
        final DatagramSocket socket = new DatagramSocket(new InetSocketAddress(0));

        final TestDnsServer dnsServerAuthority = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                if (question.getDomainName().equals(expected.getHostName())) {
                    return Collections.singleton(newARecord(
                            expected.getHostName(), expected.getHostAddress()));
                }
                return Collections.emptySet();
            }
        });
        dnsServerAuthority.start();

        TestDnsServer redirectServer = new TestDnsServer(new HashSet<String>(
                asList(expected.getHostName(), ns1Name, ns2Name))) {
            @Override
            protected DnsMessage filterMessage(DnsMessage message) {
                for (QuestionRecord record: message.getQuestionRecords()) {
                    if (record.getDomainName().equals(expected.getHostName())) {
                        message.getAdditionalRecords().clear();
                        message.getAnswerRecords().clear();
                        if (invalidNsFirst) {
                            message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns2Name));
                            message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns1Name));
                        } else {
                            message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns1Name));
                            message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns2Name));
                        }
                        return message;
                    }
                }
                return message;
            }
        };
        redirectServer.start();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        SingletonDnsServerAddressStreamProvider provider =
                new SingletonDnsServerAddressStreamProvider(redirectServer.localAddress());
        final DnsNameResolver resolver = new DnsNameResolver(
                group.next(), new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class), null, false,
                cache, NoopDnsCnameCache.INSTANCE, authoritativeDnsServerCache, null,
                NoopDnsQueryLifecycleObserverFactory.INSTANCE, 2000, ResolvedAddressTypes.IPV4_ONLY, true,
                10, true, 4096, false, HostsFileEntriesResolver.DEFAULT,
                provider, new ThreadLocalNameServerAddressStream(provider), DnsNameResolver.DEFAULT_SEARCH_DOMAINS,
                0, true, false, 0, strategy) {

            @Override
            InetSocketAddress newRedirectServerAddress(InetAddress server) {
                try {
                    if (server.getHostName().startsWith(ns1Name)) {
                        return new InetSocketAddress(InetAddress.getByAddress(ns1Name,
                                dnsServerAuthority.localAddress().getAddress().getAddress()),
                                dnsServerAuthority.localAddress().getPort());
                    }
                    if (server.getHostName().startsWith(ns2Name)) {
                        return new InetSocketAddress(InetAddress.getByAddress(ns2Name,
                                NetUtil.LOCALHOST.getAddress()), socket.getLocalPort());
                    }
                } catch (UnknownHostException e) {
                    throw new IllegalStateException(e);
                }
                return super.newRedirectServerAddress(server);
            }
        };

        try {
            List<InetAddress> resolved = resolver.resolveAll(expected.getHostName()).syncUninterruptibly().getNow();
            assertEquals(1, resolved.size());
            assertEquals(expected, resolved.get(0));

            List<InetAddress> resolved2 = resolver.resolveAll(expected.getHostName()).syncUninterruptibly().getNow();
            assertEquals(1, resolved2.size());
            assertEquals(expected, resolved2.get(0));

            if (authoritativeDnsServerCache != NoopAuthoritativeDnsServerCache.INSTANCE) {
                DnsServerAddressStream cached = authoritativeDnsServerCache.get(domain + '.');
                assertEquals(2, cached.size());
                InetSocketAddress ns1Address = InetSocketAddress.createUnresolved(
                        ns1Name + '.', DefaultDnsServerAddressStreamProvider.DNS_PORT);
                InetSocketAddress ns2Address = InetSocketAddress.createUnresolved(
                        ns2Name + '.', DefaultDnsServerAddressStreamProvider.DNS_PORT);

                if (invalidNsFirst) {
                    assertEquals(ns2Address, cached.next());
                    assertEquals(ns1Address, cached.next());
                } else {
                    assertEquals(ns1Address, cached.next());
                    assertEquals(ns2Address, cached.next());
                }
            }
            if (cache != NoopDnsCache.INSTANCE) {
                List<? extends DnsCacheEntry> ns1Cached = cache.get(ns1Name + '.', null);
                assertEquals(1, ns1Cached.size());
                DnsCacheEntry nsEntry = ns1Cached.get(0);
                assertNotNull(nsEntry.address());
                assertNull(nsEntry.cause());

                List<? extends DnsCacheEntry> ns2Cached = cache.get(ns2Name + '.', null);
                if (invalidNsFirst) {
                    assertEquals(1, ns2Cached.size());
                    DnsCacheEntry ns2Entry = ns2Cached.get(0);
                    assertNotNull(ns2Entry.address());
                    assertNull(ns2Entry.cause());
                } else {
                    // We should not even have tried to resolve the DNS name so this should be null.
                    assertNull(ns2Cached);
                }

                List<? extends DnsCacheEntry> expectedCached = cache.get(expected.getHostName(), null);
                assertEquals(1, expectedCached.size());
                DnsCacheEntry expectedEntry = expectedCached.get(0);
                assertEquals(expected, expectedEntry.address());
                assertNull(expectedEntry.cause());
            }
        } finally {
            resolver.close();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
            redirectServer.stop();
            dnsServerAuthority.stop();
            socket.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testMultipleAdditionalRecordsForSameNSRecord(DnsNameResolverChannelStrategy strategy) throws Exception {
        testMultipleAdditionalRecordsForSameNSRecord(strategy, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testMultipleAdditionalRecordsForSameNSRecordReordered(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        testMultipleAdditionalRecordsForSameNSRecord(strategy, true);
    }

    private static void testMultipleAdditionalRecordsForSameNSRecord(
            DnsNameResolverChannelStrategy strategy, final boolean reversed) throws Exception {
        final String domain = "netty.io";
        final String hostname = "test.netty.io";
        final String ns1Name = "ns1." + domain;
        final InetSocketAddress ns1Address = new InetSocketAddress(
                InetAddress.getByAddress(ns1Name, new byte[] { 10, 0, 0, 1 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);
        final InetSocketAddress ns2Address = new InetSocketAddress(
                InetAddress.getByAddress(ns1Name, new byte[] { 10, 0, 0, 2 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);
        final InetSocketAddress ns3Address = new InetSocketAddress(
                InetAddress.getByAddress(ns1Name, new byte[] { 10, 0, 0, 3 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);
        final InetSocketAddress ns4Address = new InetSocketAddress(
                InetAddress.getByAddress(ns1Name, new byte[] { 10, 0, 0, 4 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);

        TestDnsServer redirectServer = new TestDnsServer(new HashSet<String>(asList(hostname, ns1Name))) {
            @Override
            protected DnsMessage filterMessage(DnsMessage message) {
                for (QuestionRecord record: message.getQuestionRecords()) {
                    if (record.getDomainName().equals(hostname)) {
                        message.getAdditionalRecords().clear();
                        message.getAnswerRecords().clear();
                        message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns1Name));
                        message.getAdditionalRecords().add(newARecord(ns1Address));
                        message.getAdditionalRecords().add(newARecord(ns2Address));
                        message.getAdditionalRecords().add(newARecord(ns3Address));
                        message.getAdditionalRecords().add(newARecord(ns4Address));
                        return message;
                    }
                }
                return message;
            }

            private ResourceRecord newARecord(InetSocketAddress address) {
                return newARecord(address.getHostName(), address.getAddress().getHostAddress());
            }
        };
        redirectServer.start();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

        final List<InetSocketAddress> cached = new CopyOnWriteArrayList<InetSocketAddress>();
        final AuthoritativeDnsServerCache authoritativeDnsServerCache = new AuthoritativeDnsServerCache() {
            @Override
            public DnsServerAddressStream get(String hostname) {
                return null;
            }

            @Override
            public void cache(String hostname, InetSocketAddress address, long originalTtl, EventLoop loop) {
                cached.add(address);
            }

            @Override
            public void clear() {
                // NOOP
            }

            @Override
            public boolean clear(String hostname) {
                return false;
            }
        };

        final AtomicReference<DnsServerAddressStream> redirectedRef = new AtomicReference<DnsServerAddressStream>();
        SingletonDnsServerAddressStreamProvider provider =
                new SingletonDnsServerAddressStreamProvider(redirectServer.localAddress());
        final DnsNameResolver resolver = new DnsNameResolver(
                group.next(), new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class), null, false,
                NoopDnsCache.INSTANCE, NoopDnsCnameCache.INSTANCE, authoritativeDnsServerCache, null,
                NoopDnsQueryLifecycleObserverFactory.INSTANCE, 2000, ResolvedAddressTypes.IPV4_ONLY,
                true, 10, true, 4096,
                false, HostsFileEntriesResolver.DEFAULT,
                provider, new ThreadLocalNameServerAddressStream(provider), DnsNameResolver.DEFAULT_SEARCH_DOMAINS, 0,
                true, false, 0, strategy) {

            @Override
            protected DnsServerAddressStream newRedirectDnsServerStream(
                    String hostname, List<InetSocketAddress> nameservers) {
                if (reversed) {
                    Collections.reverse(nameservers);
                }
                DnsServerAddressStream stream = new SequentialDnsServerAddressStream(nameservers, 0);
                redirectedRef.set(stream);
                return stream;
            }
        };

        try {
            Throwable cause = resolver.resolveAll(hostname).await().cause();
            assertTrue(cause instanceof UnknownHostException);
            DnsServerAddressStream redirected = redirectedRef.get();
            assertNotNull(redirected);
            assertEquals(4, redirected.size());
            assertEquals(4, cached.size());

            if (reversed) {
                assertEquals(ns4Address, redirected.next());
                assertEquals(ns3Address, redirected.next());
                assertEquals(ns2Address, redirected.next());
                assertEquals(ns1Address, redirected.next());
            } else {
                assertEquals(ns1Address, redirected.next());
                assertEquals(ns2Address, redirected.next());
                assertEquals(ns3Address, redirected.next());
                assertEquals(ns4Address, redirected.next());
            }

            // We should always have the same order in the cache.
            assertEquals(ns1Address, cached.get(0));
            assertEquals(ns2Address, cached.get(1));
            assertEquals(ns3Address, cached.get(2));
            assertEquals(ns4Address, cached.get(3));
        } finally {
            resolver.close();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
            redirectServer.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testNSRecordsFromCache(DnsNameResolverChannelStrategy strategy) throws Exception {
        final String domain = "netty.io";
        final String hostname = "test.netty.io";
        final String ns0Name = "ns0." + domain + '.';
        final String ns1Name = "ns1." + domain + '.';
        final String ns2Name = "ns2." + domain + '.';

        final InetSocketAddress ns0Address = new InetSocketAddress(
                InetAddress.getByAddress(ns0Name, new byte[] { 10, 1, 0, 1 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);
        final InetSocketAddress ns1Address = new InetSocketAddress(
                InetAddress.getByAddress(ns1Name, new byte[] { 10, 0, 0, 1 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);
        final InetSocketAddress ns2Address = new InetSocketAddress(
                InetAddress.getByAddress(ns1Name, new byte[] { 10, 0, 0, 2 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);
        final InetSocketAddress ns3Address = new InetSocketAddress(
                InetAddress.getByAddress(ns1Name, new byte[] { 10, 0, 0, 3 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);
        final InetSocketAddress ns4Address = new InetSocketAddress(
                InetAddress.getByAddress(ns1Name, new byte[] { 10, 0, 0, 4 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);
        final InetSocketAddress ns5Address = new InetSocketAddress(
                InetAddress.getByAddress(ns2Name, new byte[] { 10, 0, 0, 5 }),
                DefaultDnsServerAddressStreamProvider.DNS_PORT);
        TestDnsServer redirectServer = new TestDnsServer(new HashSet<String>(asList(hostname, ns1Name))) {
            @Override
            protected DnsMessage filterMessage(DnsMessage message) {
                for (QuestionRecord record: message.getQuestionRecords()) {
                    if (record.getDomainName().equals(hostname)) {
                        message.getAdditionalRecords().clear();
                        message.getAnswerRecords().clear();
                        message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns0Name));
                        message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns1Name));
                        message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns2Name));

                        message.getAdditionalRecords().add(newARecord(ns0Address));
                        message.getAdditionalRecords().add(newARecord(ns5Address));

                        return message;
                    }
                }
                return message;
            }

            private ResourceRecord newARecord(InetSocketAddress address) {
                return newARecord(address.getHostName(), address.getAddress().getHostAddress());
            }
        };
        redirectServer.start();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

        final List<InetSocketAddress> cached = new CopyOnWriteArrayList<InetSocketAddress>();
        final AuthoritativeDnsServerCache authoritativeDnsServerCache = new AuthoritativeDnsServerCache() {
            @Override
            public DnsServerAddressStream get(String hostname) {
                return null;
            }

            @Override
            public void cache(String hostname, InetSocketAddress address, long originalTtl, EventLoop loop) {
                cached.add(address);
            }

            @Override
            public void clear() {
                // NOOP
            }

            @Override
            public boolean clear(String hostname) {
                return false;
            }
        };

        EventLoop loop = group.next();
        DefaultDnsCache cache = new DefaultDnsCache();
        cache.cache(ns1Name, null, ns1Address.getAddress(), 10000, loop);
        cache.cache(ns1Name, null, ns2Address.getAddress(), 10000, loop);
        cache.cache(ns1Name, null, ns3Address.getAddress(), 10000, loop);
        cache.cache(ns1Name, null, ns4Address.getAddress(), 10000, loop);

        final AtomicReference<DnsServerAddressStream> redirectedRef = new AtomicReference<DnsServerAddressStream>();
        SingletonDnsServerAddressStreamProvider provider =
                new SingletonDnsServerAddressStreamProvider(redirectServer.localAddress());
        final DnsNameResolver resolver = new DnsNameResolver(
                loop, new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class), null, false,
                cache, NoopDnsCnameCache.INSTANCE, authoritativeDnsServerCache, null,
                NoopDnsQueryLifecycleObserverFactory.INSTANCE, 2000, ResolvedAddressTypes.IPV4_ONLY,
                true, 10, true, 4096,
                false, HostsFileEntriesResolver.DEFAULT,
                provider, new ThreadLocalNameServerAddressStream(provider),
                DnsNameResolver.DEFAULT_SEARCH_DOMAINS, 0, true, false, 0, strategy) {

            @Override
            protected DnsServerAddressStream newRedirectDnsServerStream(
                    String hostname, List<InetSocketAddress> nameservers) {
                DnsServerAddressStream stream = new SequentialDnsServerAddressStream(nameservers, 0);
                redirectedRef.set(stream);
                return stream;
            }
        };

        try {
            Throwable cause = resolver.resolveAll(hostname).await().cause();
            assertTrue(cause instanceof UnknownHostException);
            DnsServerAddressStream redirected = redirectedRef.get();
            assertNotNull(redirected);
            assertEquals(6, redirected.size());
            assertEquals(3, cached.size());

            // The redirected addresses should have been retrieven from the DnsCache if not resolved, so these are
            // fully resolved.
            assertEquals(ns0Address, redirected.next());
            assertEquals(ns1Address, redirected.next());
            assertEquals(ns2Address, redirected.next());
            assertEquals(ns3Address, redirected.next());
            assertEquals(ns4Address, redirected.next());
            assertEquals(ns5Address, redirected.next());

            // As this address was supplied as ADDITIONAL we should put it resolved into the cache.
            assertEquals(ns0Address, cached.get(0));
            assertEquals(ns5Address, cached.get(1));

            // We should have put the unresolved address in the AuthoritativeDnsServerCache (but only 1 time)
            assertEquals(unresolved(ns1Address), cached.get(2));
        } finally {
            resolver.close();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
            redirectServer.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testNsLoopFailsResolveWithAuthoritativeDnsServerCache(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        testNsLoopFailsResolve(strategy, new DefaultAuthoritativeDnsServerCache());
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testNsLoopFailsResolveWithoutAuthoritativeDnsServerCache(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        testNsLoopFailsResolve(strategy, NoopAuthoritativeDnsServerCache.INSTANCE);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testRRNameContainsDifferentSearchDomainNoDomains(final DnsNameResolverChannelStrategy strategy) {
        assertThrows(UnknownHostException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                testRRNameContainsDifferentSearchDomain(strategy, Collections.<String>emptyList(), "netty");
            }
        });
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testRRNameContainsDifferentSearchDomainEmptyExtraDomain(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        testRRNameContainsDifferentSearchDomain(strategy, asList("io", ""), "netty");
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testRRNameContainsDifferentSearchDomainSingleExtraDomain(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        testRRNameContainsDifferentSearchDomain(strategy, asList("io", "foo.dom"), "netty");
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testRRNameContainsDifferentSearchDomainMultiExtraDomains(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        testRRNameContainsDifferentSearchDomain(strategy, asList("com", "foo.dom", "bar.dom"), "google");
    }

    private static void testRRNameContainsDifferentSearchDomain(DnsNameResolverChannelStrategy strategy,
                                                                final List<String> searchDomains, String unresolved)
            throws Exception {
        final String ipAddrPrefix = "1.2.3.";
        TestDnsServer searchDomainServer = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                Set<ResourceRecord> records = new HashSet<ResourceRecord>(searchDomains.size());
                final String qName = questionRecord.getDomainName();
                for (String searchDomain : searchDomains) {
                    if (qName.endsWith(searchDomain)) {
                        continue;
                    }
                    final ResourceRecord rr = newARecord(qName + '.' + searchDomain,
                            ipAddrPrefix + ThreadLocalRandom.current().nextInt(1, 10));
                    logger.info("Adding A record: " + rr);
                    records.add(rr);
                }
                return records;
            }
        });
        searchDomainServer.start();

        final DnsNameResolver resolver = newResolver(strategy, false, null, searchDomainServer)
                .searchDomains(searchDomains)
                .build();

        try {
            final List<InetAddress> addresses = resolver.resolveAll(unresolved).sync().get();
            assertThat(addresses, Matchers.<InetAddress>hasSize(greaterThan(0)));
            for (InetAddress address : addresses) {
                assertThat(address.getHostName(), startsWith(unresolved));
                assertThat(address.getHostAddress(), startsWith(ipAddrPrefix));
            }
        } finally {
            resolver.close();
            searchDomainServer.stop();
        }
    }

    private void testNsLoopFailsResolve(DnsNameResolverChannelStrategy strategy,
                                        AuthoritativeDnsServerCache authoritativeDnsServerCache) throws Exception {
        final String domain = "netty.io";
        final String ns1Name = "ns1." + domain;
        final String ns2Name = "ns2." + domain;

        TestDnsServer testDnsServer = new TestDnsServer(new HashSet<String>(
                Collections.singletonList(domain))) {

            @Override
            protected DnsMessage filterMessage(DnsMessage message) {
                // Just always return NS records only without any additional records (glue records).
                // Because of this the resolver will never be able to resolve and so fail eventually at some
                // point.
                for (QuestionRecord record: message.getQuestionRecords()) {
                    if (record.getDomainName().equals(domain)) {
                        message.getAdditionalRecords().clear();
                        message.getAnswerRecords().clear();
                        message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns1Name));
                        message.getAuthorityRecords().add(TestDnsServer.newNsRecord(domain, ns2Name));
                    }
                }
                return message;
            }
        };
        testDnsServer.start();
        DnsNameResolverBuilder builder = newResolver(strategy);

        final DnsNameResolver resolver = builder.resolveCache(NoopDnsCache.INSTANCE)
                .authoritativeDnsServerCache(authoritativeDnsServerCache)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(testDnsServer.localAddress())).build();

        try {
            assertThat(resolver.resolve(domain).await().cause(),
                    Matchers.<Throwable>instanceOf(UnknownHostException.class));
            assertThat(resolver.resolveAll(domain).await().cause(),
                    Matchers.<Throwable>instanceOf(UnknownHostException.class));
        } finally {
            resolver.close();
            testDnsServer.stop();
        }
    }

    private static InetSocketAddress unresolved(InetSocketAddress address) {
        return InetSocketAddress.createUnresolved(address.getHostString(), address.getPort());
    }

    private static void resolve(DnsNameResolver resolver, Map<String, Future<InetAddress>> futures, String hostname) {
        futures.put(hostname, resolver.resolve(hostname));
    }

    private static void queryMx(
            DnsNameResolver resolver,
            Map<String, Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> futures,
            String hostname) {
        futures.put(hostname, resolver.query(new DefaultDnsQuestion(hostname, DnsRecordType.MX)));
    }

    private static void assertNoQueriesMade(DnsNameResolver resolver) {
        TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                (TestRecursiveCacheDnsQueryLifecycleObserverFactory) resolver.dnsQueryLifecycleObserverFactory();
        assertTrue(lifecycleObserverFactory.observers.isEmpty());
    }

    private static void assertQueryObserver(DnsNameResolver resolver, DnsRecordType cancelledType) {
        TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                (TestRecursiveCacheDnsQueryLifecycleObserverFactory) resolver.dnsQueryLifecycleObserverFactory();
        TestDnsQueryLifecycleObserver observer;
        while ((observer = lifecycleObserverFactory.observers.poll()) != null) {
            Object o = observer.events.poll();
            if (o instanceof QueryCancelledEvent) {
                assertEquals(cancelledType, observer.question.type());
            } else if (o instanceof QueryWrittenEvent) {
                QuerySucceededEvent succeededEvent = (QuerySucceededEvent) observer.events.poll();
            } else {
                fail("unexpected event type: " + o);
            }
            assertTrue(observer.events.isEmpty());
        }
    }

    private static final class TestRecursiveCacheDnsQueryLifecycleObserverFactory
            implements DnsQueryLifecycleObserverFactory {
        final Queue<TestDnsQueryLifecycleObserver> observers =
                new ConcurrentLinkedQueue<TestDnsQueryLifecycleObserver>();
        @Override
        public DnsQueryLifecycleObserver newDnsQueryLifecycleObserver(DnsQuestion question) {
            TestDnsQueryLifecycleObserver observer = new TestDnsQueryLifecycleObserver(question);
            observers.add(observer);
            return observer;
        }
    }

    private static final class QueryWrittenEvent {
        final InetSocketAddress dnsServerAddress;

        QueryWrittenEvent(InetSocketAddress dnsServerAddress) {
            this.dnsServerAddress = dnsServerAddress;
        }
    }

    private static final class QueryCancelledEvent {
        final int queriesRemaining;

        QueryCancelledEvent(int queriesRemaining) {
            this.queriesRemaining = queriesRemaining;
        }
    }

    private static final class QueryRedirectedEvent {
        final List<InetSocketAddress> nameServers;

        QueryRedirectedEvent(List<InetSocketAddress> nameServers) {
            this.nameServers = nameServers;
        }
    }

    private static final class QueryCnamedEvent {
        final DnsQuestion question;

        QueryCnamedEvent(DnsQuestion question) {
            this.question = question;
        }
    }

    private static final class QueryNoAnswerEvent {
        final DnsResponseCode code;

        QueryNoAnswerEvent(DnsResponseCode code) {
            this.code = code;
        }
    }

    private static final class QueryFailedEvent {
        final Throwable cause;

        QueryFailedEvent(Throwable cause) {
            this.cause = cause;
        }
    }

    private static final class QuerySucceededEvent {
    }

    private static final class TestDnsQueryLifecycleObserver implements DnsQueryLifecycleObserver {
        final Queue<Object> events = new ArrayDeque<Object>();
        final DnsQuestion question;

        TestDnsQueryLifecycleObserver(DnsQuestion question) {
            this.question = question;
        }

        @Override
        public void queryWritten(InetSocketAddress dnsServerAddress, ChannelFuture future) {
            events.add(new QueryWrittenEvent(dnsServerAddress));
        }

        @Override
        public void queryCancelled(int queriesRemaining) {
            events.add(new QueryCancelledEvent(queriesRemaining));
        }

        @Override
        public DnsQueryLifecycleObserver queryRedirected(List<InetSocketAddress> nameServers) {
            events.add(new QueryRedirectedEvent(nameServers));
            return this;
        }

        @Override
        public DnsQueryLifecycleObserver queryCNAMEd(DnsQuestion cnameQuestion) {
            events.add(new QueryCnamedEvent(cnameQuestion));
            return this;
        }

        @Override
        public DnsQueryLifecycleObserver queryNoAnswer(DnsResponseCode code) {
            events.add(new QueryNoAnswerEvent(code));
            return this;
        }

        @Override
        public void queryFailed(Throwable cause) {
            events.add(new QueryFailedEvent(cause));
        }

        @Override
        public void querySucceed() {
            events.add(new QuerySucceededEvent());
        }
    }

    private static final class TestAuthoritativeDnsServerCache implements AuthoritativeDnsServerCache {
        final AuthoritativeDnsServerCache cache;
        final Map<String, DnsServerAddressStream> cacheHits = new HashMap<String, DnsServerAddressStream>();

        TestAuthoritativeDnsServerCache(AuthoritativeDnsServerCache cache) {
            this.cache = cache;
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
        public DnsServerAddressStream get(String hostname) {
            DnsServerAddressStream cached = cache.get(hostname);
            if (cached != null) {
                cacheHits.put(hostname, cached.duplicate());
            }
            return cached;
        }

        @Override
        public void cache(String hostname, InetSocketAddress address, long originalTtl, EventLoop loop) {
            cache.cache(hostname, address, originalTtl, loop);
        }
    }

    private static final class TestDnsCache implements DnsCache {
        final DnsCache cache;
        final Map<String, List<? extends DnsCacheEntry>> cacheHits =
                new HashMap<String, List<? extends DnsCacheEntry>>();

        TestDnsCache(DnsCache cache) {
            this.cache = cache;
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
            List<? extends DnsCacheEntry> cached = cache.get(hostname, additionals);
            cacheHits.put(hostname, cached);
            return cached;
        }

        @Override
        public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, InetAddress address,
                                   long originalTtl, EventLoop loop) {
            return cache.cache(hostname, additionals, address, originalTtl, loop);
        }

        @Override
        public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
            return cache.cache(hostname, additionals, cause, loop);
        }
    }

    private static class RedirectingTestDnsServer extends TestDnsServer {

        private final String dnsAddress;
        private final String domain;

        RedirectingTestDnsServer(String domain, String dnsAddress) {
            super(Collections.singleton(domain));
            this.domain = domain;
            this.dnsAddress = dnsAddress;
        }

        @Override
        protected DnsMessage filterMessage(DnsMessage message) {
            // Clear the answers as we want to add our own stuff to test dns redirects.
            message.getAnswerRecords().clear();
            message.getAuthorityRecords().clear();
            message.getAdditionalRecords().clear();

            String name = domain;
            for (int i = 0 ;; i++) {
                int idx = name.indexOf('.');
                if (idx <= 0) {
                    break;
                }
                name = name.substring(idx + 1); // skip the '.' as well.
                String dnsName = "dns" + idx + '.' + domain;
                message.getAuthorityRecords().add(newNsRecord(name, dnsName));
                message.getAdditionalRecords().add(newARecord(dnsName, i == 0 ? dnsAddress : "1.2.3." + idx));

                // Add an unresolved NS record (with no additionals as well)
                message.getAuthorityRecords().add(newNsRecord(name, "unresolved." + dnsName));
            }

            return message;
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testTimeoutNotCached(DnsNameResolverChannelStrategy strategy) {
        DnsCache cache = new DnsCache() {
            @Override
            public void clear() {
                // NOOP
            }

            @Override
            public boolean clear(String hostname) {
                return false;
            }

            @Override
            public List<? extends DnsCacheEntry> get(String hostname, DnsRecord[] additionals) {
                return Collections.emptyList();
            }

            @Override
            public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, InetAddress address,
                                       long originalTtl, EventLoop loop) {
                fail("Should not be cached");
                return null;
            }

            @Override
            public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
                fail("Should not be cached");
                return null;
            }
        };
        DnsNameResolverBuilder builder = newResolver(strategy);
        builder.queryTimeoutMillis(100)
                .authoritativeDnsServerCache(cache)
                .resolveCache(cache)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(
                        new InetSocketAddress(NetUtil.LOCALHOST, 12345)));
        DnsNameResolver resolver = builder.build();
        Future<InetAddress> result = resolver.resolve("doesnotexist.netty.io").awaitUninterruptibly();
        Throwable cause = result.cause();
        assertThat(cause, Matchers.instanceOf(UnknownHostException.class));
        cause.getCause().printStackTrace();
        assertThat(cause.getCause(), Matchers.instanceOf(DnsNameResolverTimeoutException.class));
        assertTrue(DnsNameResolver.isTimeoutError(cause));
        assertTrue(DnsNameResolver.isTransportOrTimeoutError(cause));
        resolver.close();
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testTimeoutIpv4PreferredA(DnsNameResolverChannelStrategy strategy) throws IOException {
        testTimeoutOneQuery(strategy, ResolvedAddressTypes.IPV4_PREFERRED, RecordType.A, RecordType.AAAA);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testTimeoutIpv4PreferredAAAA(DnsNameResolverChannelStrategy strategy) throws IOException {
        testTimeoutOneQuery(strategy, ResolvedAddressTypes.IPV4_PREFERRED, RecordType.AAAA, RecordType.A);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testTimeoutIpv6PreferredA(DnsNameResolverChannelStrategy strategy) throws IOException {
        testTimeoutOneQuery(strategy, ResolvedAddressTypes.IPV6_PREFERRED, RecordType.A, RecordType.AAAA);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testTimeoutIpv6PreferredAAAA(DnsNameResolverChannelStrategy strategy) throws IOException {
        testTimeoutOneQuery(strategy, ResolvedAddressTypes.IPV6_PREFERRED, RecordType.AAAA, RecordType.A);
    }

    private static void testTimeoutOneQuery(DnsNameResolverChannelStrategy strategy, ResolvedAddressTypes type,
                                            final RecordType recordType, RecordType dropType)
            throws IOException {

        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {

            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                Set<ResourceRecord> records = new LinkedHashSet<ResourceRecord>(2);
                Map<String, Object> map1 = new HashMap<String, Object>();
                if (question.getRecordType() == RecordType.A) {
                    map1.put(DnsAttribute.IP_ADDRESS.toLowerCase(), "10.0.0.2");
                } else {
                    map1.put(DnsAttribute.IP_ADDRESS.toLowerCase(), "::1");
                }
                records.add(new TestDnsServer.TestResourceRecord(
                        question.getDomainName(), recordType, map1));
                return records;
            }
        });
        dnsServer2.start(dropType);
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(true)
                    .queryTimeoutMillis(500)
                    .resolvedAddressTypes(type)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));

            resolver = builder.build();
            List<InetAddress> resolvedAddresses =
                    resolver.resolveAll("somehost.netty.io").syncUninterruptibly().getNow();
            assertEquals(1, resolvedAddresses.size());
            if (recordType == RecordType.A) {
                assertTrue(resolvedAddresses.contains(InetAddress.getByAddress(new byte[] { 10, 0, 0, 2 })));
            } else {
                assertTrue(resolvedAddresses.contains(InetAddress.getByAddress(
                        new byte[]{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 })));
            }
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @Test
    public void testDnsNameResolverBuilderCopy() {
        ChannelFactory<DatagramChannel> channelFactory =
                new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class);
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder(group.next())
                .datagramChannelFactory(channelFactory);
        DnsNameResolverBuilder copiedBuilder = builder.copy();

        // change channel factory does not propagate to previously made copy
        ChannelFactory<DatagramChannel> newChannelFactory =
                new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class);
        builder.datagramChannelFactory(newChannelFactory);
        assertEquals(channelFactory, copiedBuilder.datagramChannelFactory());
        assertEquals(newChannelFactory, builder.datagramChannelFactory());
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testFollowCNAMEEvenIfARecordIsPresent(DnsNameResolverChannelStrategy strategy) throws IOException {
        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {

            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                if (question.getDomainName().equals("cname.netty.io")) {
                    Map<String, Object> map1 = new HashMap<String, Object>();
                    map1.put(DnsAttribute.IP_ADDRESS.toLowerCase(), "10.0.0.99");
                    return Collections.<ResourceRecord>singleton(
                            new TestDnsServer.TestResourceRecord(question.getDomainName(), RecordType.A, map1));
                } else {
                    Set<ResourceRecord> records = new LinkedHashSet<ResourceRecord>(2);
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put(DnsAttribute.DOMAIN_NAME.toLowerCase(), "cname.netty.io");
                    records.add(new TestDnsServer.TestResourceRecord(
                            question.getDomainName(), RecordType.CNAME, map));

                    Map<String, Object> map1 = new HashMap<String, Object>();
                    map1.put(DnsAttribute.IP_ADDRESS.toLowerCase(), "10.0.0.2");
                    records.add(new TestDnsServer.TestResourceRecord(
                            question.getDomainName(), RecordType.A, map1));
                    return records;
                }
            }
        });
        dnsServer2.start();
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(true)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));

            resolver = builder.build();
            List<InetAddress> resolvedAddresses =
                    resolver.resolveAll("somehost.netty.io").syncUninterruptibly().getNow();
            assertEquals(2, resolvedAddresses.size());
            assertTrue(resolvedAddresses.contains(InetAddress.getByAddress(new byte[] { 10, 0, 0, 99 })));
            assertTrue(resolvedAddresses.contains(InetAddress.getByAddress(new byte[] { 10, 0, 0, 2 })));
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    //
    // This should only result in one query.
    // ;; ANSWER SECTION:
    // somehost.netty.io.     594    IN    CNAME    cname.netty.io.
    // cname.netty.io.        9042   IN    CNAME    cname2.netty.io.
    // cname2.netty.io.       1312   IN    CNAME    cname3.netty.io.io.
    // cname3.netty.io.       20     IN    A        10.0.0.2
    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNAMEFollowInResponseWithoutExtraQuery(DnsNameResolverChannelStrategy strategy) throws IOException {
        final AtomicInteger queryCount = new AtomicInteger();
        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {

            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                queryCount.incrementAndGet();
                if (question.getDomainName().equals("somehost.netty.io")) {
                    Set<ResourceRecord> records = new LinkedHashSet<ResourceRecord>(2);
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put(DnsAttribute.DOMAIN_NAME.toLowerCase(), "cname.netty.io");
                    records.add(new TestDnsServer.TestResourceRecord(
                            question.getDomainName(), RecordType.CNAME, map));

                    map = new HashMap<String, Object>();
                    map.put(DnsAttribute.DOMAIN_NAME.toLowerCase(), "cname2.netty.io");
                    records.add(new TestDnsServer.TestResourceRecord(
                            "cname.netty.io", RecordType.CNAME, map));

                    map = new HashMap<String, Object>();
                    map.put(DnsAttribute.DOMAIN_NAME.toLowerCase(), "cname3.netty.io");
                    records.add(new TestDnsServer.TestResourceRecord(
                            "cname2.netty.io", RecordType.CNAME, map));

                    Map<String, Object> map1 = new HashMap<String, Object>();
                    map1.put(DnsAttribute.IP_ADDRESS.toLowerCase(), "10.0.0.2");
                    records.add(new TestDnsServer.TestResourceRecord(
                           "cname3.netty.io", RecordType.A, map1));
                    return records;
                }
                return null;
            }
        });
        dnsServer2.start();
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(true)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));

            resolver = builder.build();
            List<InetAddress> resolvedAddresses =
                    resolver.resolveAll("somehost.netty.io").syncUninterruptibly().getNow();
            assertEquals(1, resolvedAddresses.size());
            assertTrue(resolvedAddresses.contains(InetAddress.getByAddress(new byte[] { 10, 0, 0, 2 })));
            assertEquals(1, queryCount.get());
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testFollowCNAMELoop(DnsNameResolverChannelStrategy strategy) throws IOException {
        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {

            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                Set<ResourceRecord> records = new LinkedHashSet<ResourceRecord>(4);

                records.add(new TestDnsServer.TestResourceRecord("x." + question.getDomainName(),
                        RecordType.A, Collections.<String, Object>singletonMap(
                                DnsAttribute.IP_ADDRESS.toLowerCase(), "10.0.0.99")));
                records.add(new TestDnsServer.TestResourceRecord(
                        "cname2.netty.io", RecordType.CNAME,
                        Collections.<String, Object>singletonMap(
                                DnsAttribute.DOMAIN_NAME.toLowerCase(), "cname.netty.io")));
                records.add(new TestDnsServer.TestResourceRecord(
                        "cname.netty.io", RecordType.CNAME,
                        Collections.<String, Object>singletonMap(
                                DnsAttribute.DOMAIN_NAME.toLowerCase(), "cname2.netty.io")));
                records.add(new TestDnsServer.TestResourceRecord(
                        question.getDomainName(), RecordType.CNAME,
                        Collections.<String, Object>singletonMap(
                                DnsAttribute.DOMAIN_NAME.toLowerCase(), "cname.netty.io")));
                return records;
            }
        });
        dnsServer2.start();
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(false)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));

            resolver = builder.build();
            final DnsNameResolver finalResolver = resolver;
            assertThrows(UnknownHostException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    finalResolver.resolveAll("somehost.netty.io").syncUninterruptibly().getNow();
                }
            });
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNAMELoopInCache(DnsNameResolverChannelStrategy strategy) throws Throwable {
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(false)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()));

            resolver = builder.build();
            // Add a CNAME loop into the cache
            final String name = "somehost.netty.io.";
            String name2 = "cname.netty.io.";

            resolver.cnameCache().cache(name, name2, Long.MAX_VALUE, resolver.executor());
            resolver.cnameCache().cache(name2, name, Long.MAX_VALUE, resolver.executor());
            final DnsNameResolver finalResolver = resolver;
            assertThrows(UnknownHostException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    finalResolver.resolve(name).syncUninterruptibly().getNow();
                }
            });
        } finally {
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testSearchDomainQueryFailureForSingleAddressTypeCompletes(
            final DnsNameResolverChannelStrategy strategy) {
        assertThrows(UnknownHostException.class, new Executable() {
            @Override
            public void execute() {
                testSearchDomainQueryFailureCompletes(strategy, ResolvedAddressTypes.IPV4_ONLY);
            }
        });
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testSearchDomainQueryFailureForMultipleAddressTypeCompletes(
            final DnsNameResolverChannelStrategy strategy) {
        assertThrows(UnknownHostException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                testSearchDomainQueryFailureCompletes(strategy, ResolvedAddressTypes.IPV4_PREFERRED);
            }
        });
    }

    private void testSearchDomainQueryFailureCompletes(
            DnsNameResolverChannelStrategy strategy, ResolvedAddressTypes types) {
        DnsNameResolver resolver = newResolver(strategy)
                .resolvedAddressTypes(types)
                .ndots(1)
                .searchDomains(singletonList(".")).build();
        try {
            resolver.resolve("invalid.com").syncUninterruptibly();
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testCachesClearedOnClose(DnsNameResolverChannelStrategy strategy) throws Exception {
        final CountDownLatch resolveLatch = new CountDownLatch(1);
        final CountDownLatch authoritativeLatch = new CountDownLatch(1);

        DnsNameResolver resolver = newResolver(strategy).resolveCache(new DnsCache() {
            @Override
            public void clear() {
                resolveLatch.countDown();
            }

            @Override
            public boolean clear(String hostname) {
                return false;
            }

            @Override
            public List<? extends DnsCacheEntry> get(String hostname, DnsRecord[] additionals) {
                return null;
            }

            @Override
            public DnsCacheEntry cache(
                    String hostname, DnsRecord[] additionals, InetAddress address, long originalTtl, EventLoop loop) {
                return null;
            }

            @Override
            public DnsCacheEntry cache(
                    String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
                return null;
            }
        }).authoritativeDnsServerCache(new DnsCache() {
            @Override
            public void clear() {
                authoritativeLatch.countDown();
            }

            @Override
            public boolean clear(String hostname) {
                return false;
            }

            @Override
            public List<? extends DnsCacheEntry> get(String hostname, DnsRecord[] additionals) {
                return null;
            }

            @Override
            public DnsCacheEntry cache(
                    String hostname, DnsRecord[] additionals, InetAddress address, long originalTtl, EventLoop loop) {
                return null;
            }

            @Override
            public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
                return null;
            }
        }).build();

        resolver.close();
        resolveLatch.await();
        authoritativeLatch.await();
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveACachedWithDot(DnsNameResolverChannelStrategy strategy) {
        final DnsCache cache = new DefaultDnsCache();
        DnsNameResolver resolver = newResolver(strategy, ResolvedAddressTypes.IPV4_ONLY)
                .resolveCache(cache).build();

        try {
            String domain = DOMAINS.iterator().next();
            String domainWithDot = domain + '.';

            resolver.resolve(domain).syncUninterruptibly();
            List<? extends DnsCacheEntry> cached = cache.get(domain, null);
            List<? extends DnsCacheEntry> cached2 = cache.get(domainWithDot, null);

            assertEquals(1, cached.size());
            assertEquals(cached, cached2);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveACachedWithDotSearchDomain(DnsNameResolverChannelStrategy strategy) throws Exception {
        final TestDnsCache cache = new TestDnsCache(new DefaultDnsCache());
        TestDnsServer server = new TestDnsServer(Collections.singleton("test.netty.io"));
        server.start();
        DnsNameResolver resolver = newResolver(strategy, ResolvedAddressTypes.IPV4_ONLY)
                .searchDomains(Collections.singletonList("netty.io"))
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(server.localAddress()))
                .resolveCache(cache).build();
        try {
            resolver.resolve("test").syncUninterruptibly();

            assertNull(cache.cacheHits.get("test.netty.io"));

            List<? extends DnsCacheEntry> cached = cache.cache.get("test.netty.io", null);
            List<? extends DnsCacheEntry> cached2 = cache.cache.get("test.netty.io.", null);
            assertEquals(1, cached.size());
            assertEquals(cached, cached2);

            Promise<List<InetAddress>> promise = ImmediateEventExecutor.INSTANCE.newPromise();
            boolean isCached = DnsNameResolver.doResolveAllCached("test", null, promise, cache,
                    resolver.searchDomains(), resolver.ndots(), resolver.resolvedInternetProtocolFamiliesUnsafe());
            assertTrue(isCached);
            promise.sync();

            List<? extends DnsCacheEntry> entries = cache.cacheHits.get("test.netty.io");
            assertFalse(entries.isEmpty());
        } finally {
            resolver.close();
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNameCached(DnsNameResolverChannelStrategy strategy) throws Exception {
        final Map<String, String> cache = new ConcurrentHashMap<String, String>();
        final AtomicInteger cnameQueries = new AtomicInteger();
        final AtomicInteger aQueries = new AtomicInteger();

        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {

            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                if ("cname.netty.io".equals(question.getDomainName())) {
                    aQueries.incrementAndGet();

                    return Collections.<ResourceRecord>singleton(new TestDnsServer.TestResourceRecord(
                            question.getDomainName(), RecordType.A,
                            Collections.<String, Object>singletonMap(
                                    DnsAttribute.IP_ADDRESS.toLowerCase(), "10.0.0.99")));
                }
                if ("x.netty.io".equals(question.getDomainName())) {
                    cnameQueries.incrementAndGet();

                    return Collections.<ResourceRecord>singleton(new TestDnsServer.TestResourceRecord(
                            question.getDomainName(), RecordType.CNAME,
                            Collections.<String, Object>singletonMap(
                                    DnsAttribute.DOMAIN_NAME.toLowerCase(), "cname.netty.io")));
                }
                if ("y.netty.io".equals(question.getDomainName())) {
                    cnameQueries.incrementAndGet();

                    return Collections.<ResourceRecord>singleton(new TestDnsServer.TestResourceRecord(
                            question.getDomainName(), RecordType.CNAME,
                            Collections.<String, Object>singletonMap(
                                    DnsAttribute.DOMAIN_NAME.toLowerCase(), "x.netty.io")));
                }
                return Collections.emptySet();
            }
        });
        dnsServer2.start();
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(true)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()))
                    .resolveCache(NoopDnsCache.INSTANCE)
                    .cnameCache(new DnsCnameCache() {
                        @Override
                        public String get(String hostname) {
                            assertTrue(hostname.endsWith("."), hostname);
                            return cache.get(hostname);
                        }

                        @Override
                        public void cache(String hostname, String cname, long originalTtl, EventLoop loop) {
                            assertTrue(hostname.endsWith("."), hostname);
                            cache.put(hostname, cname);
                        }

                        @Override
                        public void clear() {
                            // NOOP
                        }

                        @Override
                        public boolean clear(String hostname) {
                            return false;
                        }
                    });
            resolver = builder.build();
            List<InetAddress> resolvedAddresses =
                    resolver.resolveAll("x.netty.io").syncUninterruptibly().getNow();
            assertEquals(1, resolvedAddresses.size());
            assertTrue(resolvedAddresses.contains(InetAddress.getByAddress(new byte[] { 10, 0, 0, 99 })));

            assertEquals("cname.netty.io.", cache.get("x.netty.io."));
            assertEquals(1, cnameQueries.get());
            assertEquals(1, aQueries.get());

            resolvedAddresses =
                    resolver.resolveAll("x.netty.io").syncUninterruptibly().getNow();
            assertEquals(1, resolvedAddresses.size());
            assertTrue(resolvedAddresses.contains(InetAddress.getByAddress(new byte[] { 10, 0, 0, 99 })));

            // Should not have queried for the CNAME again.
            assertEquals(1, cnameQueries.get());
            assertEquals(2, aQueries.get());

            resolvedAddresses =
                    resolver.resolveAll("y.netty.io").syncUninterruptibly().getNow();
            assertEquals(1, resolvedAddresses.size());
            assertTrue(resolvedAddresses.contains(InetAddress.getByAddress(new byte[] { 10, 0, 0, 99 })));

            assertEquals("x.netty.io.", cache.get("y.netty.io."));

            // Will only query for one CNAME
            assertEquals(2, cnameQueries.get());
            assertEquals(3, aQueries.get());

            resolvedAddresses =
                    resolver.resolveAll("y.netty.io").syncUninterruptibly().getNow();
            assertEquals(1, resolvedAddresses.size());
            assertTrue(resolvedAddresses.contains(InetAddress.getByAddress(new byte[] { 10, 0, 0, 99 })));

            // Should not have queried for the CNAME again.
            assertEquals(2, cnameQueries.get());
            assertEquals(4, aQueries.get());
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @Test
    public void testInstanceWithNullPreferredAddressType() {
        new DnsNameResolver(
                group.next(), // eventLoop
                new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class), // channelFactory
                NoopDnsCache.INSTANCE, // resolveCache
                NoopAuthoritativeDnsServerCache.INSTANCE, // authoritativeDnsServerCache
                NoopDnsQueryLifecycleObserverFactory.INSTANCE, // dnsQueryLifecycleObserverFactory
                100, // queryTimeoutMillis
                null, // resolvedAddressTypes, see https://github.com/netty/netty/pull/8445
                true, // recursionDesired
                1, // maxQueriesPerResolve
                false, // traceEnabled
                4096, // maxPayloadSize
                true, // optResourceEnabled
                HostsFileEntriesResolver.DEFAULT, // hostsFileEntriesResolver
                DnsServerAddressStreamProviders.platformDefault(), // dnsServerAddressStreamProvider
                null, // searchDomains
                1, // ndots
                true // decodeIdn
        ).close();
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testQueryTxt(DnsNameResolverChannelStrategy strategy) throws Exception {
        final String hostname = "txt.netty.io";
        final String txt1 = "some text";
        final String txt2 = "some more text";

        TestDnsServer server = new TestDnsServer(new RecordStore() {

            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                if (question.getDomainName().equals(hostname)) {
                    Map<String, Object> map1 = new HashMap<String, Object>();
                    map1.put(DnsAttribute.CHARACTER_STRING.toLowerCase(), txt1);

                    Map<String, Object> map2 = new HashMap<String, Object>();
                    map2.put(DnsAttribute.CHARACTER_STRING.toLowerCase(), txt2);

                    Set<ResourceRecord> records = new HashSet<ResourceRecord>();
                    records.add(new TestDnsServer.TestResourceRecord(question.getDomainName(), RecordType.TXT, map1));
                    records.add(new TestDnsServer.TestResourceRecord(question.getDomainName(), RecordType.TXT, map2));
                    return records;
                }
                return Collections.emptySet();
            }
        });
        server.start();
        DnsNameResolver resolver = newResolver(strategy, ResolvedAddressTypes.IPV4_ONLY)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(server.localAddress()))
                .build();
        try {
            AddressedEnvelope<DnsResponse, InetSocketAddress> envelope = resolver.query(
                    new DefaultDnsQuestion(hostname, DnsRecordType.TXT)).syncUninterruptibly().getNow();
            assertNotNull(envelope.sender());

            DnsResponse response = envelope.content();
            assertNotNull(response);

            assertEquals(DnsResponseCode.NOERROR, response.code());
            int count = response.count(DnsSection.ANSWER);

            assertEquals(2, count);
            List<String> txts = new ArrayList<String>();

            for (int i = 0; i < 2; i++) {
                txts.addAll(decodeTxt(response.recordAt(DnsSection.ANSWER, i)));
            }
            assertTrue(txts.contains(txt1));
            assertTrue(txts.contains(txt2));
            envelope.release();
        } finally {
            resolver.close();
            server.stop();
        }
    }

    private static List<String> decodeTxt(DnsRecord record) {
        if (!(record instanceof DnsRawRecord)) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<String>();
        ByteBuf data = ((DnsRawRecord) record).content();
        int idx = data.readerIndex();
        int wIdx = data.writerIndex();
        while (idx < wIdx) {
            int len = data.getUnsignedByte(idx++);
            list.add(data.toString(idx, len, CharsetUtil.UTF_8));
            idx += len;
        }
        return list;
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testNotIncludeDuplicates(DnsNameResolverChannelStrategy strategy) throws IOException {
        final String name = "netty.io";
        final String ipv4Addr = "1.2.3.4";
        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                Set<ResourceRecord> records = new LinkedHashSet<ResourceRecord>(4);
                String qName = question.getDomainName().toLowerCase();
                if (qName.equals(name)) {
                    records.add(new TestDnsServer.TestResourceRecord(
                            qName, RecordType.CNAME,
                            Collections.<String, Object>singletonMap(
                                    DnsAttribute.DOMAIN_NAME.toLowerCase(), "cname.netty.io")));
                    records.add(new TestDnsServer.TestResourceRecord(qName,
                            RecordType.A, Collections.<String, Object>singletonMap(
                            DnsAttribute.IP_ADDRESS.toLowerCase(), ipv4Addr)));
                } else {
                    records.add(new TestDnsServer.TestResourceRecord(qName,
                            RecordType.A, Collections.<String, Object>singletonMap(
                            DnsAttribute.IP_ADDRESS.toLowerCase(), ipv4Addr)));
                }
                return records;
            }
        });
        dnsServer2.start();
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(true)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));
            builder.resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY);

            resolver = builder.build();
            List<InetAddress> resolvedAddresses = resolver.resolveAll(name).syncUninterruptibly().getNow();
            assertEquals(Collections.singletonList(InetAddress.getByAddress(name, new byte[] { 1, 2, 3, 4 })),
                    resolvedAddresses);
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testIncludeDuplicates(DnsNameResolverChannelStrategy strategy) throws IOException {
        final String name = "netty.io";
        final String ipv4Addr = "1.2.3.4";
        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                Set<ResourceRecord> records = new LinkedHashSet<ResourceRecord>(2);
                String qName = question.getDomainName().toLowerCase();
                records.add(new TestDnsServer.TestResourceRecord(qName,
                        RecordType.A, Collections.<String, Object>singletonMap(
                        DnsAttribute.IP_ADDRESS.toLowerCase(), ipv4Addr)));
                records.add(new TestDnsServer.TestResourceRecord(qName,
                        RecordType.A, Collections.<String, Object>singletonMap(
                        DnsAttribute.IP_ADDRESS.toLowerCase(), ipv4Addr)));
                return records;
            }
        });
        dnsServer2.start();
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(true)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));
            builder.resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY);

            resolver = builder.build();
            List<DnsRecord> resolvedAddresses = resolver.resolveAll(new DefaultDnsQuestion(name, A))
                    .syncUninterruptibly().getNow();
            assertEquals(2, resolvedAddresses.size());
            for (DnsRecord record: resolvedAddresses) {
                ReferenceCountUtil.release(record);
            }
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testDropAAAA(DnsNameResolverChannelStrategy strategy) throws IOException {
        String host = "somehost.netty.io";
        TestDnsServer dnsServer2 = new TestDnsServer(Collections.singleton(host));
        dnsServer2.start(RecordType.AAAA);
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(false)
                    .queryTimeoutMillis(500)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));

            resolver = builder.build();
            List<InetAddress> addressList = resolver.resolveAll(host).syncUninterruptibly().getNow();
            assertEquals(1, addressList.size());
            assertEquals(host, addressList.get(0).getHostName());
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testDropAAAAResolveFast(DnsNameResolverChannelStrategy strategy) throws IOException {
        String host = "somehost.netty.io";
        TestDnsServer dnsServer2 = new TestDnsServer(Collections.singleton(host));
        dnsServer2.start(RecordType.AAAA);
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(false)
                    .queryTimeoutMillis(10000)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                    .completeOncePreferredResolved(true)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));

            resolver = builder.build();
            InetAddress address = resolver.resolve(host).syncUninterruptibly().getNow();
            assertEquals(host, address.getHostName());
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testDropAAAAResolveAllFast(DnsNameResolverChannelStrategy strategy) throws IOException {
        final String host = "somehost.netty.io";
        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) throws DnsException {
                String name = question.getDomainName();
                if (name.equals(host)) {
                    Set<ResourceRecord> records = new HashSet<ResourceRecord>(2);
                    records.add(new TestDnsServer.TestResourceRecord(name, RecordType.A,
                            Collections.<String, Object>singletonMap(DnsAttribute.IP_ADDRESS.toLowerCase(),
                                    "10.0.0.1")));
                    records.add(new TestDnsServer.TestResourceRecord(name, RecordType.A,
                            Collections.<String, Object>singletonMap(DnsAttribute.IP_ADDRESS.toLowerCase(),
                                    "10.0.0.2")));
                    return records;
                }
                return null;
            }
        });
        dnsServer2.start(RecordType.AAAA);
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(false)
                    .queryTimeoutMillis(10000)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                    .completeOncePreferredResolved(true)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));

            resolver = builder.build();
            List<InetAddress> addresses = resolver.resolveAll(host).syncUninterruptibly().getNow();
            assertEquals(2, addresses.size());
            for (InetAddress address: addresses) {
                assertThat(address, instanceOf(Inet4Address.class));
                assertEquals(host, address.getHostName());
            }
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testTruncatedWithoutTcpFallback(DnsNameResolverChannelStrategy strategy) throws IOException {
        testTruncated0(strategy, false, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testTruncatedWithTcpFallback(DnsNameResolverChannelStrategy strategy) throws IOException {
        testTruncated0(strategy, true, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testTruncatedWithTcpFallbackBecauseOfMtu(DnsNameResolverChannelStrategy strategy) throws IOException {
        testTruncated0(strategy, true, true);
    }

    private static DnsMessageModifier modifierFrom(DnsMessage message) {
        DnsMessageModifier modifier = new DnsMessageModifier();
        modifier.setAcceptNonAuthenticatedData(message.isAcceptNonAuthenticatedData());
        modifier.setAdditionalRecords(message.getAdditionalRecords());
        modifier.setAnswerRecords(message.getAnswerRecords());
        modifier.setAuthoritativeAnswer(message.isAuthoritativeAnswer());
        modifier.setAuthorityRecords(message.getAuthorityRecords());
        modifier.setMessageType(message.getMessageType());
        modifier.setOpCode(message.getOpCode());
        modifier.setQuestionRecords(message.getQuestionRecords());
        modifier.setRecursionAvailable(message.isRecursionAvailable());
        modifier.setRecursionDesired(message.isRecursionDesired());
        modifier.setReserved(message.isReserved());
        modifier.setResponseCode(message.getResponseCode());
        modifier.setTransactionId(message.getTransactionId());
        modifier.setTruncated(message.isTruncated());
        return modifier;
    }

    private static void testTruncated0(DnsNameResolverChannelStrategy strategy,
                                       boolean tcpFallback, final boolean truncatedBecauseOfMtu) throws IOException {
        ServerSocket serverSocket = null;
        final String host = "somehost.netty.io";
        final String txt = "this is a txt record";
        final AtomicReference<DnsMessage> messageRef = new AtomicReference<DnsMessage>();

        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                String name = question.getDomainName();
                if (name.equals(host)) {
                    return Collections.<ResourceRecord>singleton(
                            new TestDnsServer.TestResourceRecord(name, RecordType.TXT,
                                    Collections.<String, Object>singletonMap(
                                            DnsAttribute.CHARACTER_STRING.toLowerCase(), txt)));
                }
                return null;
            }
        }) {
            @Override
            protected DnsMessage filterMessage(DnsMessage message) {
                // Store a original message so we can replay it later on.
                messageRef.set(message);

                if (!truncatedBecauseOfMtu) {
                    // Create a copy of the message but set the truncated flag.
                    DnsMessageModifier modifier = modifierFrom(message);
                    modifier.setTruncated(true);
                    return modifier.getDnsMessage();
                }
                return message;
            }
        };
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy);
            final DatagramChannel datagramChannel = new NioDatagramChannel();
            ChannelFactory<DatagramChannel> channelFactory = new ChannelFactory<DatagramChannel>() {
                @Override
                public DatagramChannel newChannel() {
                    return datagramChannel;
                }
            };
            builder.datagramChannelFactory(channelFactory);
            if (tcpFallback) {
                // If we are configured to use TCP as a fallback also bind a TCP socket
                serverSocket = startDnsServerAndCreateServerSocket(dnsServer2);
                // If we are configured to use TCP as a fallback also bind a TCP socket
                builder.socketChannelType(NioSocketChannel.class);
            } else {
                dnsServer2.start();
            }
            builder.queryTimeoutMillis(10000)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));
            resolver = builder.build();
            if (truncatedBecauseOfMtu) {
                datagramChannel.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof DatagramPacket) {
                            // Truncate the packet by 1 byte.
                            DatagramPacket packet = (DatagramPacket) msg;
                            packet.content().writerIndex(packet.content().writerIndex() - 1);
                        }
                        ctx.fireChannelRead(msg);
                    }
                });
            }
            Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> envelopeFuture = resolver.query(
                    new DefaultDnsQuestion(host, DnsRecordType.TXT));

            if (tcpFallback) {
                // If we are configured to use TCP as a fallback lets replay the dns message over TCP
                Socket socket = serverSocket.accept();
                responseViaSocket(socket, messageRef.get());

                // Let's wait until we received the envelope before closing the socket.
                envelopeFuture.syncUninterruptibly();

                socket.close();
                serverSocket.close();
            }

            AddressedEnvelope<DnsResponse, InetSocketAddress> envelope = envelopeFuture.syncUninterruptibly().getNow();
            assertNotNull(envelope.sender());

            DnsResponse response = envelope.content();
            assertNotNull(response);

            assertEquals(DnsResponseCode.NOERROR, response.code());
            int count = response.count(DnsSection.ANSWER);

            assertEquals(1, count);
            List<String> texts = decodeTxt(response.recordAt(DnsSection.ANSWER, 0));
            assertEquals(1, texts.size());
            assertEquals(txt, texts.get(0));

            if (tcpFallback) {
                assertFalse(envelope.content().isTruncated());
            } else {
                assertTrue(envelope.content().isTruncated());
            }
            assertTrue(envelope.release());
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    private static void responseViaSocket(Socket socket, DnsMessage message) throws IOException {
        InputStream in = socket.getInputStream();
        assertTrue((in.read() << 8 | (in.read() & 0xff)) > 2); // skip length field
        int txnId = in.read() << 8 | (in.read() & 0xff);

        IoBuffer ioBuffer = IoBuffer.allocate(1024);
        // Must replace the transactionId with the one from the TCP request
        DnsMessageModifier modifier = modifierFrom(message);
        modifier.setTransactionId(txnId);
        new DnsMessageEncoder().encode(ioBuffer, modifier.getDnsMessage());
        ioBuffer.flip();

        ByteBuffer lenBuffer = ByteBuffer.allocate(2);
        lenBuffer.putShort((short) ioBuffer.remaining());
        lenBuffer.flip();

        while (lenBuffer.hasRemaining()) {
            socket.getOutputStream().write(lenBuffer.get());
        }

        while (ioBuffer.hasRemaining()) {
            socket.getOutputStream().write(ioBuffer.get());
        }
        socket.getOutputStream().flush();
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testTcpFallbackWhenTimeout(DnsNameResolverChannelStrategy strategy) throws IOException {
        testTcpFallbackWhenTimeout(strategy, true);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testTcpFallbackFailedWhenTimeout(DnsNameResolverChannelStrategy strategy) throws IOException {
        testTcpFallbackWhenTimeout(strategy, false);
    }

    private static ServerSocket startDnsServerAndCreateServerSocket(TestDnsServer dns) throws IOException {
        for (int i = 0;; i++) {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0));
            try {
                dns.start(null, (InetSocketAddress) serverSocket.getLocalSocketAddress());
                return serverSocket;
            } catch (IOException e) {
                serverSocket.close();
                if (i == 10) {
                    // We tried 10 times without success
                    throw new IllegalStateException(
                            "Unable to bind TestDnsServer and ServerSocket to the same address", e);
                }
                // We could not start the DnsServer which is most likely because the localAddress was already used,
                // let's retry
            }
        }
    }

    private void testTcpFallbackWhenTimeout(DnsNameResolverChannelStrategy strategy, boolean tcpSuccess)
            throws IOException {
        final String host = "somehost.netty.io";
        final String txt = "this is a txt record";
        final AtomicReference<DnsMessage> messageRef = new AtomicReference<DnsMessage>();

        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                String name = question.getDomainName();
                if (name.equals(host)) {
                    return Collections.<ResourceRecord>singleton(
                            new TestDnsServer.TestResourceRecord(name, RecordType.TXT,
                                    Collections.<String, Object>singletonMap(
                                            DnsAttribute.CHARACTER_STRING.toLowerCase(), txt)));
                }
                return null;
            }
        }) {
            @Override
            protected DnsMessage filterMessage(DnsMessage message) {
                // Store a original message so we can replay it later on.
                messageRef.set(message);
                return null;
            }
        };
        DnsNameResolver resolver = null;
        ServerSocket serverSocket = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy);
            final DatagramChannel datagramChannel = new NioDatagramChannel();
            ChannelFactory<DatagramChannel> channelFactory = new ChannelFactory<DatagramChannel>() {
                @Override
                public DatagramChannel newChannel() {
                    return datagramChannel;
                }
            };
            builder.datagramChannelFactory(channelFactory);
            serverSocket = startDnsServerAndCreateServerSocket(dnsServer2);
            // If we are configured to use TCP as a fallback also bind a TCP socket
            builder.socketChannelType(NioSocketChannel.class, true);

            builder.queryTimeoutMillis(1000)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()))
                    .datagramChannelStrategy(strategy);
            resolver = builder.build();
            Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> envelopeFuture = resolver.query(
                    new DefaultDnsQuestion(host, DnsRecordType.TXT));

            // If we are configured to use TCP as a fallback lets replay the dns message over TCP
            Socket socket = serverSocket.accept();

            if (tcpSuccess) {
                responseViaSocket(socket, messageRef.get());

                // Let's wait until we received the envelope before closing the socket.
                envelopeFuture.syncUninterruptibly();
                socket.close();

                AddressedEnvelope<DnsResponse, InetSocketAddress> envelope =
                        envelopeFuture.syncUninterruptibly().getNow();
                assertNotNull(envelope.sender());

                DnsResponse response = envelope.content();
                assertNotNull(response);

                assertEquals(DnsResponseCode.NOERROR, response.code());
                int count = response.count(DnsSection.ANSWER);

                assertEquals(1, count);
                List<String> texts = decodeTxt(response.recordAt(DnsSection.ANSWER, 0));
                assertEquals(1, texts.size());
                assertEquals(txt, texts.get(0));

                assertFalse(envelope.content().isTruncated());
                assertTrue(envelope.release());
            } else {
                // Just close the socket. This should cause the original exception to be used.
                socket.close();
                Throwable error = envelopeFuture.awaitUninterruptibly().cause();
                assertThat(error, instanceOf(DnsNameResolverTimeoutException.class));
                assertThat(error.getSuppressed().length, greaterThanOrEqualTo(1));
            }
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCancelPromise(DnsNameResolverChannelStrategy strategy) throws Exception {
        final EventLoop eventLoop = group.next();
        final Promise<InetAddress> promise = eventLoop.newPromise();
        final TestDnsServer dnsServer1 = new TestDnsServer(Collections.<String>emptySet()) {
            @Override
            protected DnsMessage filterMessage(DnsMessage message) {
                promise.cancel(true);
                return message;
            }
        };
        dnsServer1.start();
        final AtomicBoolean isQuerySentToSecondServer = new AtomicBoolean();
        final TestDnsServer dnsServer2 = new TestDnsServer(Collections.<String>emptySet()) {
            @Override
            protected DnsMessage filterMessage(DnsMessage message) {
                isQuerySentToSecondServer.set(true);
                return message;
            }
        };
        dnsServer2.start();
        DnsServerAddressStreamProvider nameServerProvider =
                new SequentialDnsServerAddressStreamProvider(dnsServer1.localAddress(),
                                                             dnsServer2.localAddress());
        final DnsNameResolver resolver = new DnsNameResolverBuilder(group.next())
                .dnsQueryLifecycleObserverFactory(new TestRecursiveCacheDnsQueryLifecycleObserverFactory())
                .datagramChannelType(NioDatagramChannel.class)
                .optResourceEnabled(false)
                .nameServerProvider(nameServerProvider)
                .datagramChannelStrategy(strategy)
                .build();

        try {
            resolver.resolve("non-existent.netty.io", promise).sync();
            fail();
        } catch (Exception e) {
            assertThat(e, is(instanceOf(CancellationException.class)));
        }
        assertThat(isQuerySentToSecondServer.get(), is(false));
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNAMERecursiveResolveDifferentNameServersForDomains(DnsNameResolverChannelStrategy strategy)
            throws IOException {
        final String firstName = "firstname.com";
        final String secondName = "secondname.com";
        final String lastName = "lastname.com";
        final String ipv4Addr = "1.2.3.4";
        final TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                ResourceRecordModifier rm = new ResourceRecordModifier();
                rm.setDnsClass(RecordClass.IN);
                rm.setDnsName(question.getDomainName());
                rm.setDnsTtl(100);

                if (question.getDomainName().equals(firstName)) {
                    rm.setDnsType(RecordType.CNAME);
                    rm.put(DnsAttribute.DOMAIN_NAME, secondName);
                } else if (question.getDomainName().equals(lastName)) {
                    rm.setDnsType(question.getRecordType());
                    rm.put(DnsAttribute.IP_ADDRESS, ipv4Addr);
                } else {
                    return null;
                }
                return Collections.singleton(rm.getEntry());
            }
        });
        dnsServer2.start();
        final TestDnsServer dnsServer3 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                if (question.getDomainName().equals(secondName)) {
                    ResourceRecordModifier rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(question.getDomainName());
                    rm.setDnsTtl(100);
                    rm.setDnsType(RecordType.CNAME);
                    rm.put(DnsAttribute.DOMAIN_NAME, lastName);
                    return Collections.singleton(rm.getEntry());
                }
                return null;
            }
        });
        dnsServer3.start();
        DnsNameResolver resolver = null;
        try {
            resolver = newResolver(strategy)
                    .resolveCache(NoopDnsCache.INSTANCE)
                    .cnameCache(NoopDnsCnameCache.INSTANCE)
                    .recursionDesired(true)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new DnsServerAddressStreamProvider() {
                        @Override
                        public DnsServerAddressStream nameServerAddressStream(String hostname) {
                            if (hostname.equals(secondName + '.')) {
                                return DnsServerAddresses.singleton(dnsServer3.localAddress()).stream();
                            }
                            return DnsServerAddresses.singleton(dnsServer2.localAddress()).stream();
                        }
                    })
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED).build();

            assertResolvedAddress(resolver.resolve(firstName).syncUninterruptibly().getNow(), ipv4Addr, firstName);
        } finally {
            dnsServer2.stop();
            dnsServer3.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    private static void assertResolvedAddress(InetAddress resolvedAddress, String ipAddr, String hostname) {
        assertEquals(ipAddr, resolvedAddress.getHostAddress());
        assertEquals(hostname, resolvedAddress.getHostName());
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testAllNameServers(DnsNameResolverChannelStrategy strategy) throws IOException {
        final String domain = "netty.io";
        final String ipv4Addr = "1.2.3.4";
        final AtomicInteger server2Counter = new AtomicInteger();
        final TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                server2Counter.incrementAndGet();
                ResourceRecordModifier rm = new ResourceRecordModifier();
                rm.setDnsClass(RecordClass.IN);
                rm.setDnsName(question.getDomainName());
                rm.setDnsTtl(100);

                rm.setDnsType(question.getRecordType());
                rm.put(DnsAttribute.IP_ADDRESS, ipv4Addr);
                return Collections.singleton(rm.getEntry());
            }
        });
        dnsServer2.start();

        final AtomicInteger server3Counter = new AtomicInteger();
        final TestDnsServer dnsServer3 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                server3Counter.incrementAndGet();
                ResourceRecordModifier rm = new ResourceRecordModifier();
                rm.setDnsClass(RecordClass.IN);
                rm.setDnsName(question.getDomainName());
                rm.setDnsTtl(100);

                rm.setDnsType(question.getRecordType());
                rm.put(DnsAttribute.IP_ADDRESS, ipv4Addr);
                return Collections.singleton(rm.getEntry());
            }
        });
        dnsServer3.start();
        DnsNameResolver resolver = null;
        try {
            resolver = newResolver(strategy)
                    .resolveCache(NoopDnsCache.INSTANCE)
                    .cnameCache(NoopDnsCnameCache.INSTANCE)
                    .recursionDesired(true)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new DnsServerAddressStreamProvider() {
                        private final DnsServerAddresses addresses =
                                DnsServerAddresses.rotational(dnsServer2.localAddress(), dnsServer3.localAddress());
                        @Override
                        public DnsServerAddressStream nameServerAddressStream(String hostname) {
                            return addresses.stream();
                        }
                    })
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY).build();

            assertResolvedAddress(resolver.resolve(domain).syncUninterruptibly().getNow(), ipv4Addr, domain);
            assertEquals(1, server2Counter.get());
            assertEquals(0, server3Counter.get());
            assertResolvedAddress(resolver.resolve(domain).syncUninterruptibly().getNow(), ipv4Addr, domain);
            assertEquals(1, server2Counter.get());
            assertEquals(1, server3Counter.get());
            assertResolvedAddress(resolver.resolve(domain).syncUninterruptibly().getNow(), ipv4Addr, domain);
            assertEquals(2, server2Counter.get());
            assertEquals(1, server3Counter.get());
            assertResolvedAddress(resolver.resolve(domain).syncUninterruptibly().getNow(), ipv4Addr, domain);
            assertEquals(2, server2Counter.get());
            assertEquals(2, server3Counter.get());
        } finally {
            dnsServer2.stop();
            dnsServer3.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testSrvWithCnameNotCached(DnsNameResolverChannelStrategy strategy) throws Exception {
        final AtomicBoolean alias = new AtomicBoolean();
        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                String name = question.getDomainName();
                if (name.equals("service.netty.io")) {
                    Set<ResourceRecord> records = new HashSet<ResourceRecord>(2);

                    ResourceRecordModifier rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(name);
                    rm.setDnsTtl(10);
                    rm.setDnsType(RecordType.CNAME);
                    rm.put(DnsAttribute.DOMAIN_NAME, "alias.service.netty.io");
                    records.add(rm.getEntry());

                    rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(name);
                    rm.setDnsTtl(10);
                    rm.setDnsType(RecordType.SRV);
                    rm.put(DnsAttribute.DOMAIN_NAME, "foo.service.netty.io");
                    rm.put(DnsAttribute.SERVICE_PORT, "8080");
                    rm.put(DnsAttribute.SERVICE_PRIORITY, "10");
                    rm.put(DnsAttribute.SERVICE_WEIGHT, "1");
                    records.add(rm.getEntry());
                    return records;
                }
                if (name.equals("foo.service.netty.io")) {
                    ResourceRecordModifier rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(name);
                    rm.setDnsTtl(10);
                    rm.setDnsType(RecordType.A);
                    rm.put(DnsAttribute.IP_ADDRESS, "10.0.0.1");
                    return Collections.singleton(rm.getEntry());
                }
                if (alias.get()) {
                    ResourceRecordModifier rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(name);
                    rm.setDnsTtl(10);
                    rm.setDnsType(RecordType.SRV);
                    rm.put(DnsAttribute.DOMAIN_NAME, "foo.service.netty.io");
                    rm.put(DnsAttribute.SERVICE_PORT, "8080");
                    rm.put(DnsAttribute.SERVICE_PRIORITY, "10");
                    rm.put(DnsAttribute.SERVICE_WEIGHT, "1");
                    return Collections.singleton(rm.getEntry());
                }
                return null;
            }
        });
        dnsServer2.start();
        DnsNameResolver resolver = null;
        try {
            DnsNameResolverBuilder builder = newResolver(strategy)
                    .recursionDesired(false)
                    .queryTimeoutMillis(10000)
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                    .completeOncePreferredResolved(true)
                    .maxQueriesPerResolve(16)
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()));

            resolver = builder.build();
            assertNotEmptyAndRelease(resolver.resolveAll(new DefaultDnsQuestion("service.netty.io", SRV)));
            alias.set(true);
            assertNotEmptyAndRelease(resolver.resolveAll(new DefaultDnsQuestion("service.netty.io", SRV)));
            alias.set(false);
            assertNotEmptyAndRelease(resolver.resolveAll(new DefaultDnsQuestion("service.netty.io", SRV)));
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNAMENotTriedOnAddressLookupsWhenDisabled(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        TRY_FINAL_CNAME_ON_ADDRESS_LOOKUPS = false;
        testFollowUpCNAME(strategy, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCNAMEOnlyTriedOnAddressLookups(DnsNameResolverChannelStrategy strategy) throws Exception {
        TRY_FINAL_CNAME_ON_ADDRESS_LOOKUPS = true;
        try {
            testFollowUpCNAME(strategy, true);
        } finally {
            TRY_FINAL_CNAME_ON_ADDRESS_LOOKUPS = false;
        }
    }

    private void testFollowUpCNAME(DnsNameResolverChannelStrategy strategy, final boolean enabled) throws Exception {
        final AtomicInteger cnameQueries = new AtomicInteger();

        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                if (questionRecord.getRecordType() == RecordType.CNAME) {
                    cnameQueries.incrementAndGet();
                }

                return Collections.emptySet();
            }
        });

        DnsNameResolver resolver = null;
        try {
            dnsServer2.start();
            resolver = newNonCachedResolver(strategy, ResolvedAddressTypes.IPV4_PREFERRED)
                    .maxQueriesPerResolve(4)
                    .searchDomains(Collections.<String>emptyList())
                    .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()))
                    .build();

            // We expect these resolves to fail with UnknownHostException,
            // and then check that no unexpected CNAME queries were performed.
            assertThat(resolver.resolveAll(new DefaultDnsQuestion("lookup-srv.netty.io", SRV)).await().cause(),
                    instanceOf(UnknownHostException.class));
            assertEquals(0, cnameQueries.get());

            assertThat(resolver.resolveAll(new DefaultDnsQuestion("lookup-naptr.netty.io", NAPTR)).await().cause(),
                    instanceOf(UnknownHostException.class));
            assertEquals(0, cnameQueries.get());

            assertThat(resolver.resolveAll(new DefaultDnsQuestion("lookup-cname.netty.io", CNAME)).await().cause(),
                    instanceOf(UnknownHostException.class));
            assertEquals(1, cnameQueries.getAndSet(0));

            assertThat(resolver.resolveAll(new DefaultDnsQuestion("lookup-a.netty.io", A)).await().cause(),
                    instanceOf(UnknownHostException.class));
            assertEquals(enabled ? 1 : 0, cnameQueries.getAndSet(0));

            assertThat(resolver.resolveAll(new DefaultDnsQuestion("lookup-aaaa.netty.io", AAAA)).await().cause(),
                    instanceOf(UnknownHostException.class));
            assertEquals(enabled ? 1 : 0, cnameQueries.getAndSet(0));

            assertThat(resolver.resolveAll("lookup-address.netty.io").await().cause(),
                    instanceOf(UnknownHostException.class));
            assertEquals(enabled ? 1 : 0, cnameQueries.getAndSet(0));
        } finally {
            dnsServer2.stop();
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    private static void assertNotEmptyAndRelease(Future<List<DnsRecord>> recordsFuture) throws Exception {
        List<DnsRecord> records = recordsFuture.get();
        assertFalse(records.isEmpty());
        for (DnsRecord record : records) {
            ReferenceCountUtil.release(record);
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveIpv6WithScopeId(DnsNameResolverChannelStrategy strategy) throws Exception {
        testResolveIpv6WithScopeId0(strategy, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllIpv6WithScopeId(DnsNameResolverChannelStrategy strategy) throws Exception {
        testResolveIpv6WithScopeId0(strategy, true);
    }

    private void testResolveIpv6WithScopeId0(DnsNameResolverChannelStrategy strategy, boolean resolveAll)
            throws Exception {
        DnsNameResolver resolver = newResolver(strategy).build();
        String address = "fe80:0:0:0:1c31:d1d1:4824:72a9";
        int scopeId = 15;
        String addressString = address + '%' + scopeId;
        byte[] bytes =  NetUtil.createByteArrayFromIpAddressString(address);
        Inet6Address inet6Address = Inet6Address.getByAddress(null, bytes, scopeId);
        try {
            final InetAddress addr;
            if (resolveAll) {
                List<InetAddress> addressList = resolver.resolveAll(addressString).getNow();
                assertEquals(1, addressList.size());
                addr = addressList.get(0);
            } else {
                addr = resolver.resolve(addressString).getNow();
            }
            assertEquals(inet6Address, addr);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveIpv6WithoutScopeId(DnsNameResolverChannelStrategy strategy) throws Exception {
        testResolveIpv6WithoutScopeId0(strategy, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllIpv6WithoutScopeId(DnsNameResolverChannelStrategy strategy) throws Exception {
        testResolveIpv6WithoutScopeId0(strategy, true);
    }

    private void testResolveIpv6WithoutScopeId0(DnsNameResolverChannelStrategy strategy, boolean resolveAll)
            throws Exception {
        DnsNameResolver resolver = newResolver(strategy).build();
        String addressString = "fe80:0:0:0:1c31:d1d1:4824:72a9";
        byte[] bytes =  NetUtil.createByteArrayFromIpAddressString(addressString);
        Inet6Address inet6Address = (Inet6Address) InetAddress.getByAddress(bytes);
        try {
            final InetAddress addr;
            if (resolveAll) {
                List<InetAddress> addressList = resolver.resolveAll(addressString).getNow();
                assertEquals(1, addressList.size());
                addr = addressList.get(0);
            } else {
                addr = resolver.resolve(addressString).getNow();
            }
            assertEquals(inet6Address, addr);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveIp4(DnsNameResolverChannelStrategy strategy) throws Exception {
        testResolveIp4(strategy, false);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveAllIp4(DnsNameResolverChannelStrategy strategy) throws Exception {
        testResolveIp4(strategy, true);
    }

    private void testResolveIp4(DnsNameResolverChannelStrategy strategy, boolean resolveAll) throws Exception {
        DnsNameResolver resolver = newResolver(strategy).build();
        String addressString = "10.0.0.1";
        byte[] bytes =  NetUtil.createByteArrayFromIpAddressString(addressString);
        InetAddress inetAddress = InetAddress.getByAddress(bytes);
        try {
            final InetAddress addr;
            if (resolveAll) {
                List<InetAddress> addressList = resolver.resolveAll(addressString).getNow();
                assertEquals(1, addressList.size());
                addr = addressList.get(0);
            } else {
                addr = resolver.resolve(addressString).getNow();
            }
            assertEquals(inetAddress, addr);
        } finally {
            resolver.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveSearchDomainStopOnFirstSuccess(DnsNameResolverChannelStrategy strategy) throws Exception {
        final String addressString = "10.0.0.1";
        final Queue<String> names = new ConcurrentLinkedQueue<String>();
        final TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            private int called;
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                names.offer(question.getDomainName());
                if (++called == 2) {
                    ResourceRecordModifier rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(question.getDomainName());
                    rm.setDnsTtl(100);

                    rm.setDnsType(question.getRecordType());
                    rm.put(DnsAttribute.IP_ADDRESS, addressString);
                    return Collections.singleton(rm.getEntry());
                }
                return null;
            }
        });
        dnsServer2.start();

        DnsNameResolver resolver = newResolver(strategy).searchDomains(
                Arrays.asList("search1.netty.io", "search2.netty.io", "search3.netty.io"))
                .ndots(2).nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()))
                .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .build();

        byte[] bytes =  NetUtil.createByteArrayFromIpAddressString(addressString);
        InetAddress inetAddress = InetAddress.getByAddress(bytes);
        try {
            final InetAddress addr = resolver.resolve("netty.io").sync().getNow();
            assertEquals(inetAddress, addr);
        } finally {
            resolver.close();
            dnsServer2.stop();
            assertEquals("netty.io.search1.netty.io", names.poll());
            assertEquals("netty.io.search2.netty.io", names.poll());
            assertTrue(names.isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveTryWithoutSearchDomainFirst(DnsNameResolverChannelStrategy strategy) throws Exception {
        testResolveTryWithoutSearchDomainFirst(strategy, true);
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResolveTryWithoutSearchDomainFirstButContinue(DnsNameResolverChannelStrategy strategy)
            throws Exception {
        testResolveTryWithoutSearchDomainFirst(strategy, false);
    }

    private static void testResolveTryWithoutSearchDomainFirst(
            DnsNameResolverChannelStrategy strategy, final boolean absoluteSuccess) throws Exception {
        final String addressString = "10.0.0.1";
        final Queue<String> names = new ConcurrentLinkedQueue<String>();
        final TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            private int called;
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                names.offer(question.getDomainName());
                ++called;
                if ((absoluteSuccess && called == 1) || called == 3) {
                    ResourceRecordModifier rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(question.getDomainName());
                    rm.setDnsTtl(100);

                    rm.setDnsType(question.getRecordType());
                    rm.put(DnsAttribute.IP_ADDRESS, addressString);
                    return Collections.singleton(rm.getEntry());
                }
                return null;
            }
        });
        dnsServer2.start();

        DnsNameResolver resolver = newResolver(strategy).searchDomains(
                        Arrays.asList("search1.netty.io", "search2.netty.io", "search3.netty.io"))
                .ndots(1).nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()))
                .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .build();

        byte[] bytes =  NetUtil.createByteArrayFromIpAddressString(addressString);
        InetAddress inetAddress = InetAddress.getByAddress(bytes);
        try {
            final InetAddress addr = resolver.resolve("netty.io").sync().getNow();
            assertEquals(inetAddress, addr);
        } finally {
            resolver.close();
            dnsServer2.stop();
            assertEquals("netty.io", names.poll());
            if (!absoluteSuccess) {
                assertEquals("netty.io.search1.netty.io", names.poll());
                assertEquals("netty.io.search2.netty.io", names.poll());
            }
            assertTrue(names.isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testInflightQueries(DnsNameResolverChannelStrategy strategy) throws Exception {
        final String addressString = "10.0.0.1";
        final AtomicInteger called = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        final TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                called.incrementAndGet();
                try {
                    latch.await();
                    ResourceRecordModifier rm = new ResourceRecordModifier();
                    rm.setDnsClass(RecordClass.IN);
                    rm.setDnsName(question.getDomainName());
                    rm.setDnsTtl(100);

                    rm.setDnsType(question.getRecordType());
                    rm.put(DnsAttribute.IP_ADDRESS, addressString);
                    return Collections.singleton(rm.getEntry());
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        dnsServer2.start();

        DnsNameResolver resolver = newResolver(strategy)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer2.localAddress()))
                .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .consolidateCacheSize(2)
                .build();

        byte[] bytes =  NetUtil.createByteArrayFromIpAddressString(addressString);
        InetAddress inetAddress = InetAddress.getByAddress(bytes);
        try {
            Future<InetAddress> f = resolver.resolve("netty.io");
            Future<InetAddress> f2 = resolver.resolve("netty.io");
            assertFalse(f.isDone());
            assertFalse(f2.isDone());

            // Now unblock so we receive the response back for our query.
            latch.countDown();

            assertEquals(inetAddress, f.sync().getNow());
            assertEquals(inetAddress, f2.sync().getNow());
        } finally {
            resolver.close();
            dnsServer2.stop();
            assertEquals(1, called.get());
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testAddressAlreadyInUse(DnsNameResolverChannelStrategy strategy) throws Exception {
        DatagramSocket datagramSocket = new DatagramSocket();
        try {
            assertTrue(datagramSocket.isBound());
            try {
                final DnsNameResolver resolver = newResolver(strategy)
                        .localAddress(datagramSocket.getLocalSocketAddress()).build();
                try {
                    Throwable cause = assertThrows(UnknownHostException.class, new Executable() {
                        @Override
                        public void execute() throws Throwable {
                            resolver.resolve("netty.io").sync();
                        }
                    });
                    assertThat(cause.getCause(), Matchers.<Throwable>instanceOf(BindException.class));
                } finally {
                    resolver.close();
                }
            } catch (IllegalStateException cause) {
                // We might also throw directly here... in this case let's verify that we use the correct exception.
                assertThat(cause.getCause(), Matchers.<Throwable>instanceOf(BindException.class));
            }
        } finally {
            datagramSocket.close();
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testResponseFeedbackStream(DnsNameResolverChannelStrategy strategy) {
        final AtomicBoolean successCalled = new AtomicBoolean();
        final AtomicBoolean failureCalled = new AtomicBoolean();
        final AtomicBoolean returnSuccess = new AtomicBoolean(false);
        final DnsNameResolver resolver = newResolver(strategy, true, new DnsServerAddressStreamProvider() {
            @Override
            public DnsServerAddressStream nameServerAddressStream(String hostname) {
                return new DnsServerResponseFeedbackAddressStream() {
                    @Override
                    public void feedbackSuccess(InetSocketAddress address, long queryResponseTimeNanos) {
                        assertThat(queryResponseTimeNanos, greaterThanOrEqualTo(0L));
                        successCalled.set(true);
                    }

                    @Override
                    public void feedbackFailure(InetSocketAddress address,
                                                Throwable failureCause,
                                                long queryResponseTimeNanos) {
                        assertThat(queryResponseTimeNanos, greaterThanOrEqualTo(0L));
                        assertNotNull(failureCause);
                        failureCalled.set(true);
                    }

                    @Override
                    public InetSocketAddress next() {
                        if (returnSuccess.get()) {
                            return dnsServer.localAddress();
                        }
                        try {
                            return new InetSocketAddress(InetAddress.getByAddress("foo.com",
                                    new byte[] {(byte) 169, (byte) 254, 12, 34 }), 53);
                        } catch (UnknownHostException e) {
                            throw new Error(e);
                        }
                    }

                    @Override
                    public int size() {
                        return 1;
                    }

                    @Override
                    public DnsServerAddressStream duplicate() {
                        return this;
                    }
                };
            }
        }).build();
        try {
            // setup call to be successful and verify
            returnSuccess.set(true);
            resolver.resolve("google.com").syncUninterruptibly().getNow();
            assertTrue(successCalled.get());
            assertFalse(failureCalled.get());

            // reset state for next query
            successCalled.set(false);
            failureCalled.set(false);

            // setup call to fail and verify
            returnSuccess.set(false);
            try {
                resolver.resolve("yahoo.com").syncUninterruptibly().getNow();
                fail();
            } catch (Exception e) {
                // expected
                assertThat(e, is(instanceOf(UnknownHostException.class)));
            } finally {
                assertFalse(successCalled.get());
                assertTrue(failureCalled.get());
            }
        } finally {
            if (resolver != null) {
                resolver.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DnsNameResolverChannelStrategy.class)
    public void testCnameWithAAndAdditionalsAndAuthorities(DnsNameResolverChannelStrategy strategy) throws Exception {
        final String hostname = "test.netty.io";
        final String cname = "cname.netty.io";

        final List<String> nameServers = new ArrayList<String>();

        for (int i = 0; i < 13; i++) {
            nameServers.add("ns" + i + ".foo.bar");
        }

        TestDnsServer server = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
                ResourceRecordModifier rm = new ResourceRecordModifier();
                rm.setDnsClass(RecordClass.IN);
                rm.setDnsName(hostname);
                rm.setDnsTtl(10000);
                rm.setDnsType(RecordType.CNAME);
                rm.put(DnsAttribute.DOMAIN_NAME, cname);

                Set<ResourceRecord> records = new LinkedHashSet<ResourceRecord>();
                records.add(rm.getEntry());
                records.add(newARecord(cname, "10.0.0.2"));
                return records;
            }
        }) {
            @Override
            protected DnsMessage filterMessage(DnsMessage message) {
                for (QuestionRecord record: message.getQuestionRecords()) {
                    if (record.getDomainName().equals(hostname)) {
                        // Let's add some extra records.
                        message.getAuthorityRecords().clear();
                        message.getAdditionalRecords().clear();

                        for (String nameserver: nameServers) {
                            message.getAuthorityRecords().add(TestDnsServer.newNsRecord(".", nameserver));
                            message.getAdditionalRecords().add(newAddressRecord(nameserver, RecordType.A, "10.0.0.1"));
                            message.getAdditionalRecords().add(newAddressRecord(nameserver, RecordType.AAAA, "::1"));
                        }

                        return message;
                    }
                }
                return message;
            }
        };
        server.start();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

        SingletonDnsServerAddressStreamProvider provider =
                new SingletonDnsServerAddressStreamProvider(server.localAddress());
        final DnsNameResolver resolver = new DnsNameResolver(
                group.next(), new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class), null, false,
                NoopDnsCache.INSTANCE, NoopDnsCnameCache.INSTANCE, NoopAuthoritativeDnsServerCache.INSTANCE, null,
                NoopDnsQueryLifecycleObserverFactory.INSTANCE, 2000, ResolvedAddressTypes.IPV4_ONLY,
                true, 8, true, 4096,
                false, HostsFileEntriesResolver.DEFAULT,
                provider, new ThreadLocalNameServerAddressStream(provider),
                new String [] { "k8se-apps.svc.cluster.local, svc.cluster.local, cluster.local" }, 1,
                true, false, 0, strategy);
        try {
            InetAddress address = resolver.resolve(hostname).sync().getNow();
            assertArrayEquals(new byte[] { 10, 0, 0, 2 }, address.getAddress());
        } finally {
            resolver.close();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
            server.stop();
        }
    }
}
