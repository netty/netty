/*
 * Copyright 2015 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.directory.server.dns.DnsException;
import org.apache.directory.server.dns.messages.DnsMessage;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.messages.ResourceRecordModifier;
import org.apache.directory.server.dns.messages.ResponseCode;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.dns.DnsRecordType.A;
import static io.netty.handler.codec.dns.DnsRecordType.AAAA;
import static io.netty.handler.codec.dns.DnsRecordType.CNAME;
import static io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider.DNS_PORT;
import static io.netty.resolver.dns.DnsServerAddresses.sequential;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DnsNameResolverTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);
    private static final long DEFAULT_TEST_TIMEOUT_MS = 30000;

    // Using the top-100 web sites ranked in Alexa.com (Oct 2014)
    // Please use the following series of shell commands to get this up-to-date:
    // $ curl -O http://s3.amazonaws.com/alexa-static/top-1m.csv.zip
    // $ unzip -o top-1m.csv.zip top-1m.csv
    // $ head -100 top-1m.csv | cut -d, -f2 | cut -d/ -f1 | while read L; do echo '"'"$L"'",'; done > topsites.txt
    private static final Set<String> DOMAINS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
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
     * The list of the domain names to exclude from {@link #testResolveAorAAAA()}.
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
     * The list of the domain names to exclude from {@link #testResolveAAAA()}.
     * Unfortunately, there are only handful of domain names with IPv6 addresses.
     */
    private static final Set<String> EXCLUSIONS_RESOLVE_AAAA = new HashSet<String>();
    static {
        EXCLUSIONS_RESOLVE_AAAA.addAll(EXCLUSIONS_RESOLVE_A);
        EXCLUSIONS_RESOLVE_AAAA.addAll(DOMAINS);
        EXCLUSIONS_RESOLVE_AAAA.removeAll(Arrays.asList(
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
     * The list of the domain names to exclude from {@link #testQueryMx()}.
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

    private static final TestDnsServer dnsServer = new TestDnsServer(DOMAINS_ALL);
    private static final EventLoopGroup group = new NioEventLoopGroup(1);

    private static DnsNameResolverBuilder newResolver(boolean decodeToUnicode) {
        return newResolver(decodeToUnicode, null);
    }

    private static DnsNameResolverBuilder newResolver(boolean decodeToUnicode,
                                                      DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder(group.next())
                .dnsQueryLifecycleObserverFactory(new TestRecursiveCacheDnsQueryLifecycleObserverFactory())
                .channelType(NioDatagramChannel.class)
                .maxQueriesPerResolve(1)
                .decodeIdn(decodeToUnicode)
                .optResourceEnabled(false)
                .ndots(1);

        if (dnsServerAddressStreamProvider == null) {
            builder.nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()));
        } else {
            builder.nameServerProvider(new MultiDnsServerAddressStreamProvider(dnsServerAddressStreamProvider,
                    new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress())));
        }

        return builder;
    }

    private static DnsNameResolverBuilder newResolver() {
        return newResolver(true);
    }

    private static DnsNameResolverBuilder newResolver(ResolvedAddressTypes resolvedAddressTypes) {
        return newResolver()
                .resolvedAddressTypes(resolvedAddressTypes);
    }

    private static DnsNameResolverBuilder newNonCachedResolver(ResolvedAddressTypes resolvedAddressTypes) {
        return newResolver()
                .resolveCache(NoopDnsCache.INSTANCE)
                .resolvedAddressTypes(resolvedAddressTypes);
    }

    @BeforeClass
    public static void init() throws Exception {
        dnsServer.start();
    }
    @AfterClass
    public static void destroy() {
        dnsServer.stop();
        group.shutdownGracefully();
    }

    @Test
    public void testResolveAorAAAA() throws Exception {
        DnsNameResolver resolver = newResolver(ResolvedAddressTypes.IPV4_PREFERRED).build();
        try {
            testResolve0(resolver, EXCLUSIONS_RESOLVE_A, AAAA);
        } finally {
            resolver.close();
        }
    }

    @Test
    public void testResolveAAAAorA() throws Exception {
        DnsNameResolver resolver = newResolver(ResolvedAddressTypes.IPV6_PREFERRED).build();
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
    @Test
    public void testNameServerCache() throws IOException, InterruptedException {
        final String overriddenIP = "12.34.12.34";
        final TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) throws DnsException {
                ResourceRecordModifier rm = new ResourceRecordModifier();
                rm.setDnsClass(RecordClass.IN);
                rm.setDnsName(question.getDomainName());
                rm.setDnsTtl(100);
                rm.setDnsType(question.getRecordType());
                switch (question.getRecordType()) {
                    case A:
                        rm.put(DnsAttribute.IP_ADDRESS, overriddenIP);
                        break;
                    default:
                        return null;
                }
                return Collections.singleton(rm.getEntry());
            }
        });
        dnsServer2.start();
        try {
            final Set<String> overridenHostnames = new HashSet<String>();
            for (String name : DOMAINS) {
                if (EXCLUSIONS_RESOLVE_A.contains(name)) {
                    continue;
                }
                if (PlatformDependent.threadLocalRandom().nextBoolean()) {
                    overridenHostnames.add(name);
                }
            }
            DnsNameResolver resolver = newResolver(false, new DnsServerAddressStreamProvider() {
                @Override
                public DnsServerAddressStream nameServerAddressStream(String hostname) {
                    return overridenHostnames.contains(hostname) ? sequential(dnsServer2.localAddress()).stream() :
                                                                   null;
                }
            }).build();
            try {
                final Map<String, InetAddress> resultA = testResolve0(resolver, EXCLUSIONS_RESOLVE_A, AAAA);
                for (Entry<String, InetAddress> resolvedEntry : resultA.entrySet()) {
                    if (resolvedEntry.getValue().isLoopbackAddress()) {
                        continue;
                    }
                    if (overridenHostnames.contains(resolvedEntry.getKey())) {
                        assertEquals("failed to resolve " + resolvedEntry.getKey(),
                                overriddenIP, resolvedEntry.getValue().getHostAddress());
                    } else {
                        assertNotEquals("failed to resolve " + resolvedEntry.getKey(),
                                overriddenIP, resolvedEntry.getValue().getHostAddress());
                    }
                }
            } finally {
                resolver.close();
            }
        } finally {
            dnsServer2.stop();
        }
    }

    @Test
    public void testResolveA() throws Exception {
        DnsNameResolver resolver = newResolver(ResolvedAddressTypes.IPV4_ONLY)
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
            for (Entry<String, InetAddress> e: resultA.entrySet()) {
                InetAddress expected = e.getValue();
                InetAddress actual = resultB.get(e.getKey());
                if (!actual.equals(expected)) {
                    // Print the content of the cache when test failure is expected.
                    System.err.println("Cache for " + e.getKey() + ": " + resolver.resolveAll(e.getKey()).getNow());
                }
                assertThat(actual, is(expected));
            }
        } finally {
            resolver.close();
        }
    }

    @Test
    public void testResolveAAAA() throws Exception {
        DnsNameResolver resolver = newResolver(ResolvedAddressTypes.IPV6_ONLY).build();
        try {
            testResolve0(resolver, EXCLUSIONS_RESOLVE_AAAA, null);
        } finally {
            resolver.close();
        }
    }

    @Test
    public void testNonCachedResolve() throws Exception {
        DnsNameResolver resolver = newNonCachedResolver(ResolvedAddressTypes.IPV4_ONLY).build();
        try {
            testResolve0(resolver, EXCLUSIONS_RESOLVE_A, null);
        } finally {
            resolver.close();
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT_MS)
    public void testNonCachedResolveEmptyHostName() throws Exception {
        testNonCachedResolveEmptyHostName("");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT_MS)
    public void testNonCachedResolveNullHostName() throws Exception {
        testNonCachedResolveEmptyHostName(null);
    }

    public void testNonCachedResolveEmptyHostName(String inetHost) throws Exception {
        DnsNameResolver resolver = newNonCachedResolver(ResolvedAddressTypes.IPV4_ONLY).build();
        try {
            InetAddress addr = resolver.resolve(inetHost).syncUninterruptibly().getNow();
            assertEquals(SocketUtils.addressByName(inetHost), addr);
        } finally {
            resolver.close();
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT_MS)
    public void testNonCachedResolveAllEmptyHostName() throws Exception {
        testNonCachedResolveAllEmptyHostName("");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT_MS)
    public void testNonCachedResolveAllNullHostName() throws Exception {
        testNonCachedResolveAllEmptyHostName(null);
    }

    private static void testNonCachedResolveAllEmptyHostName(String inetHost) throws UnknownHostException {
        DnsNameResolver resolver = newNonCachedResolver(ResolvedAddressTypes.IPV4_ONLY).build();
        try {
            List<InetAddress> addrs = resolver.resolveAll(inetHost).syncUninterruptibly().getNow();
            assertEquals(Arrays.asList(
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
            for (InternetProtocolFamily f: resolver.resolvedInternetProtocolFamiliesUnsafe()) {
                Class<?> resolvedType = resolved.getClass();
                if (f.addressType().isAssignableFrom(resolvedType)) {
                    typeMatches = true;
                }
            }

            assertThat(typeMatches, is(true));

            results.put(resolved.getHostName(), resolved);
        }

        assertQueryObserver(resolver, cancelledType);

        return results;
    }

    @Test
    public void testQueryMx() throws Exception {
        DnsNameResolver resolver = newResolver().build();
        try {
            assertThat(resolver.isRecursionDesired(), is(true));

            Map<String, Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> futures =
                    new LinkedHashMap<String, Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>>();
            for (String name: DOMAINS) {
                if (EXCLUSIONS_QUERY_MX.contains(name)) {
                    continue;
                }

                queryMx(resolver, futures, name);
            }

            for (Entry<String, Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> e: futures.entrySet()) {
                String hostname = e.getKey();
                Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> f = e.getValue().awaitUninterruptibly();

                DnsResponse response = f.getNow().content();
                assertThat(response.code(), is(DnsResponseCode.NOERROR));

                final int answerCount = response.count(DnsSection.ANSWER);
                final List<DnsRecord> mxList = new ArrayList<DnsRecord>(answerCount);
                for (int i = 0; i < answerCount; i ++) {
                    final DnsRecord r = response.recordAt(DnsSection.ANSWER, i);
                    if (r.type() == DnsRecordType.MX) {
                        mxList.add(r);
                    }
                }

                assertThat(mxList.size(), is(greaterThan(0)));
                StringBuilder buf = new StringBuilder();
                for (DnsRecord r: mxList) {
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

    @Test
    public void testNegativeTtl() throws Exception {
        final DnsNameResolver resolver = newResolver().negativeTtl(10).build();
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
                    assertTrue("unexpected type: " + observer.question,
                            observer.question.type() == CNAME || observer.question.type() == AAAA);
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

    @Test
    public void testResolveIp() {
        DnsNameResolver resolver = newResolver().build();
        try {
            InetAddress address = resolver.resolve("10.0.0.1").syncUninterruptibly().getNow();

            assertEquals("10.0.0.1", address.getHostAddress());

            // This address is already resolved, and so we shouldn't have to query for anything.
            assertNoQueriesMade(resolver);
        } finally {
            resolver.close();
        }
    }

    @Test
    public void testResolveEmptyIpv4() {
        testResolve0(ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, StringUtil.EMPTY_STRING);
    }

    @Test
    public void testResolveEmptyIpv6() {
        testResolve0(ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, StringUtil.EMPTY_STRING);
    }

    @Test
    public void testResolveNullIpv4() {
        testResolve0(ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, null);
    }

    @Test
    public void testResolveNullIpv6() {
        testResolve0(ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, null);
    }

    private static void testResolve0(ResolvedAddressTypes addressTypes, InetAddress expectedAddr, String name) {
        DnsNameResolver resolver = newResolver(addressTypes).build();
        try {
            InetAddress address = resolver.resolve(name).syncUninterruptibly().getNow();
            assertEquals(expectedAddr, address);

            // We are resolving the local address, so we shouldn't make any queries.
            assertNoQueriesMade(resolver);
        } finally {
            resolver.close();
        }
    }

    @Test
    public void testResolveAllEmptyIpv4() {
        testResolveAll0(ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, StringUtil.EMPTY_STRING);
    }

    @Test
    public void testResolveAllEmptyIpv6() {
        testResolveAll0(ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, StringUtil.EMPTY_STRING);
    }

    @Test
    public void testCNAMEResolveAllIpv4() throws IOException, InterruptedException {
        testCNAMERecursiveResolve(true);
    }

    @Test
    public void testCNAMEResolveAllIpv6() throws IOException, InterruptedException {
        testCNAMERecursiveResolve(false);
    }

    private static void testCNAMERecursiveResolve(boolean ipv4Preferred) throws IOException, InterruptedException {
        final String firstName = "firstname.com";
        final String secondName = "secondname.com";
        final String lastName = "lastname.com";
        final String ipv4Addr = "1.2.3.4";
        final String ipv6Addr = "::1";
        TestDnsServer dnsServer2 = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) throws DnsException {
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
            DnsNameResolverBuilder builder = newResolver()
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

    @Test
    public void testCNAMERecursiveResolveMultipleNameServersIPv4() throws IOException {
        testCNAMERecursiveResolveMultipleNameServers(true);
    }

    @Test
    public void testCNAMERecursiveResolveMultipleNameServersIPv6() throws IOException {
        testCNAMERecursiveResolveMultipleNameServers(false);
    }

    private static void testCNAMERecursiveResolveMultipleNameServers(boolean ipv4Preferred) throws IOException {
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
            DnsCache nsCache = new DefaultDnsCache();
            // What we want to test is the following:
            // 1. Do a DNS query.
            // 2. CNAME is returned, we want to lookup that CNAME on multiple DNS servers
            // 3. The first DNS server should fail
            // 4. The second DNS server should succeed
            // This verifies that we do in fact follow multiple DNS servers in the CNAME resolution.
            // The DnsCache is used for the name server cache, but doesn't provide a InetSocketAddress (only InetAddress
            // so no port), so we only specify the name server in the cache, and then specify both name servers in the
            // fallback name server provider.
            nsCache.cache("nettyfoo.com.", null, dnsServer2.localAddress().getAddress(), 10000, group.next());
            resolver = new DnsNameResolver(
                    group.next(), new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class),
                    NoopDnsCache.INSTANCE, nsCache, NoopDnsQueryLifecycleObserverFactory.INSTANCE, 3000,
                    ipv4Preferred ? ResolvedAddressTypes.IPV4_ONLY : ResolvedAddressTypes.IPV6_ONLY, true,
                    10, true, 4096, false, HostsFileEntriesResolver.DEFAULT,
                    new SequentialDnsServerAddressStreamProvider(dnsServer2.localAddress(), dnsServer3.localAddress()),
                    DnsNameResolver.DEFAULT_SEARCH_DOMAINS, 0, true) {
                @Override
                int dnsRedirectPort(InetAddress server) {
                    return hitServer2.get() ? dnsServer3.localAddress().getPort() : dnsServer2.localAddress().getPort();
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

    @Test
    public void testResolveAllNullIpv4() {
        testResolveAll0(ResolvedAddressTypes.IPV4_ONLY, NetUtil.LOCALHOST4, null);
    }

    @Test
    public void testResolveAllNullIpv6() {
        testResolveAll0(ResolvedAddressTypes.IPV6_ONLY, NetUtil.LOCALHOST6, null);
    }

    private static void testResolveAll0(ResolvedAddressTypes addressTypes, InetAddress expectedAddr, String name) {
        DnsNameResolver resolver = newResolver(addressTypes).build();
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

    @Test
    public void testResolveAllMx() {
        final DnsNameResolver resolver = newResolver().build();
        try {
            assertThat(resolver.isRecursionDesired(), is(true));

            final Map<String, Future<List<DnsRecord>>> futures = new LinkedHashMap<String, Future<List<DnsRecord>>>();
            for (String name: DOMAINS) {
                if (EXCLUSIONS_QUERY_MX.contains(name)) {
                    continue;
                }

                futures.put(name, resolver.resolveAll(new DefaultDnsQuestion(name, DnsRecordType.MX)));
            }

            for (Entry<String, Future<List<DnsRecord>>> e: futures.entrySet()) {
                String hostname = e.getKey();
                Future<List<DnsRecord>> f = e.getValue().awaitUninterruptibly();

                final List<DnsRecord> mxList = f.getNow();
                assertThat(mxList.size(), is(greaterThan(0)));
                StringBuilder buf = new StringBuilder();
                for (DnsRecord r: mxList) {
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

    @Test
    public void testResolveAllHostsFile() {
        final DnsNameResolver resolver = new DnsNameResolverBuilder(group.next())
                .channelType(NioDatagramChannel.class)
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
                }).build();

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

    @Test
    public void testResolveDecodeUnicode() {
        testResolveUnicode(true);
    }

    @Test
    public void testResolveNotDecodeUnicode() {
        testResolveUnicode(false);
    }

    private static void testResolveUnicode(boolean decode) {
        DnsNameResolver resolver = newResolver(decode).build();
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

    @Test(timeout = DEFAULT_TEST_TIMEOUT_MS)
    public void secondDnsServerShouldBeUsedBeforeCNAMEFirstServerNotStarted() throws IOException {
        secondDnsServerShouldBeUsedBeforeCNAME(false);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT_MS)
    public void secondDnsServerShouldBeUsedBeforeCNAMEFirstServerFailResolve() throws IOException {
        secondDnsServerShouldBeUsedBeforeCNAME(true);
    }

    private static void secondDnsServerShouldBeUsedBeforeCNAME(boolean startDnsServer1) throws IOException {
        final String knownHostName = "netty.io";
        final TestDnsServer dnsServer1 = new TestDnsServer(new HashSet<String>(Arrays.asList("notnetty.com")));
        final TestDnsServer dnsServer2 = new TestDnsServer(new HashSet<String>(Arrays.asList(knownHostName)));
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
                    .channelType(NioDatagramChannel.class)
                    .queryTimeoutMillis(1000) // We expect timeouts if startDnsServer1 is false
                    .optResourceEnabled(false)
                    .ndots(1);

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

    @Test(timeout = DEFAULT_TEST_TIMEOUT_MS)
    public void aAndAAAAQueryShouldTryFirstDnsServerBeforeSecond() throws IOException {
        final String knownHostName = "netty.io";
        final TestDnsServer dnsServer1 = new TestDnsServer(new HashSet<String>(Arrays.asList("notnetty.com")));
        final TestDnsServer dnsServer2 = new TestDnsServer(new HashSet<String>(Arrays.asList(knownHostName)));
        DnsNameResolver resolver = null;
        try {
            dnsServer1.start();
            dnsServer2.start();

            TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                    new TestRecursiveCacheDnsQueryLifecycleObserverFactory();

            DnsNameResolverBuilder builder = new DnsNameResolverBuilder(group.next())
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV6_PREFERRED)
                    .dnsQueryLifecycleObserverFactory(lifecycleObserverFactory)
                    .channelType(NioDatagramChannel.class)
                    .optResourceEnabled(false)
                    .ndots(1);

            builder.nameServerProvider(new SequentialDnsServerAddressStreamProvider(dnsServer1.localAddress(),
                    dnsServer2.localAddress()));
            resolver = builder.build();
            assertNotNull(resolver.resolve(knownHostName).syncUninterruptibly().getNow());

            TestDnsQueryLifecycleObserver observer = lifecycleObserverFactory.observers.poll();
            assertNotNull(observer);
            assertEquals(2, lifecycleObserverFactory.observers.size());
            assertEquals(2, observer.events.size());
            QueryWrittenEvent writtenEvent = (QueryWrittenEvent) observer.events.poll();
            assertEquals(dnsServer1.localAddress(), writtenEvent.dnsServerAddress);
            QueryFailedEvent failedEvent = (QueryFailedEvent) observer.events.poll();

            observer = lifecycleObserverFactory.observers.poll();
            assertEquals(2, observer.events.size());
            writtenEvent = (QueryWrittenEvent) observer.events.poll();
            assertEquals(dnsServer1.localAddress(), writtenEvent.dnsServerAddress);
            failedEvent = (QueryFailedEvent) observer.events.poll();

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

    @Test
    public void testRecursiveResolveNoCache() throws Exception {
        testRecursiveResolveCache(false);
    }

    @Test
    public void testRecursiveResolveCache() throws Exception {
        testRecursiveResolveCache(true);
    }

    @Test
    public void testIpv4PreferredWhenIpv6First() throws Exception {
        testResolvesPreferredWhenNonPreferredFirst0(ResolvedAddressTypes.IPV4_PREFERRED);
    }

    @Test
    public void testIpv6PreferredWhenIpv4First() throws Exception {
        testResolvesPreferredWhenNonPreferredFirst0(ResolvedAddressTypes.IPV6_PREFERRED);
    }

    private static void testResolvesPreferredWhenNonPreferredFirst0(ResolvedAddressTypes types) throws Exception {
        final String name = "netty.com";
        // This store is non-compliant, returning records of the wrong type for a query.
        // It works since we don't verify the type of the result when resolving to deal with
        // non-compliant servers in the wild.
        List<Set<ResourceRecord>> records = new ArrayList<Set<ResourceRecord>>();
        final String ipv6Address = "0:0:0:0:0:0:1:1";
        final String ipv4Address = "1.1.1.1";
        if (types == ResolvedAddressTypes.IPV4_PREFERRED) {
            records.add(newAddressRecord(name, RecordType.AAAA, ipv6Address));
            records.add(newAddressRecord(name, RecordType.A, ipv4Address));
        } else {
            records.add(newAddressRecord(name, RecordType.A, ipv4Address));
            records.add(newAddressRecord(name, RecordType.AAAA, ipv6Address));
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
            DnsNameResolver resolver = newResolver(types)
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
        } finally {
            nonCompliantDnsServer.stop();
        }
    }

    private static Set<ResourceRecord> newAddressRecord(String name, RecordType type, String address) {
        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName(name);
        rm.setDnsTtl(100);
        rm.setDnsType(type);
        rm.put(DnsAttribute.IP_ADDRESS, address);
        return Collections.singleton(rm.getEntry());
    }

    private static void testRecursiveResolveCache(boolean cache)
            throws Exception {
        final String hostname = "some.record.netty.io";
        final String hostname2 = "some2.record.netty.io";

        final TestDnsServer dnsServerAuthority = new TestDnsServer(new HashSet<String>(
                Arrays.asList(hostname, hostname2)));
        dnsServerAuthority.start();

        TestDnsServer dnsServer = new RedirectingTestDnsServer(hostname,
                dnsServerAuthority.localAddress().getAddress().getHostAddress());
        dnsServer.start();

        TestDnsCache nsCache = new TestDnsCache(cache ? new DefaultDnsCache() : NoopDnsCache.INSTANCE);
        TestRecursiveCacheDnsQueryLifecycleObserverFactory lifecycleObserverFactory =
                new TestRecursiveCacheDnsQueryLifecycleObserverFactory();
        EventLoopGroup group = new NioEventLoopGroup(1);
        DnsNameResolver resolver = new DnsNameResolver(
                group.next(), new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class),
                NoopDnsCache.INSTANCE, nsCache, lifecycleObserverFactory, 3000, ResolvedAddressTypes.IPV4_ONLY, true,
                10, true, 4096, false, HostsFileEntriesResolver.DEFAULT,
                new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()),
                DnsNameResolver.DEFAULT_SEARCH_DOMAINS, 0, true) {
            @Override
            int dnsRedirectPort(InetAddress server) {
                return server.equals(dnsServerAuthority.localAddress().getAddress()) ?
                        dnsServerAuthority.localAddress().getPort() : DNS_PORT;
            }
        };

        // Java7 will strip of the "." so we need to adjust the expected dnsname. Both are valid in terms of the RFC
        // so its ok.
        String expectedDnsName = PlatformDependent.javaVersion() == 7 ?
                "dns4.some.record.netty.io" : "dns4.some.record.netty.io.";

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
                assertNull(nsCache.cache.get("io.", null));
                assertNull(nsCache.cache.get("netty.io.", null));
                List<? extends DnsCacheEntry> entries = nsCache.cache.get("record.netty.io.", null);
                assertEquals(1, entries.size());

                assertNull(nsCache.cache.get(hostname, null));

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

    private static void resolve(DnsNameResolver resolver, Map<String, Future<InetAddress>> futures, String hostname) {
        futures.put(hostname, resolver.resolve(hostname));
    }

    private static void queryMx(
            DnsNameResolver resolver,
            Map<String, Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> futures,
            String hostname) throws Exception {
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

    private static final class TestDnsCache implements DnsCache {
        private final DnsCache cache;
        final Map<String, List<? extends DnsCacheEntry>> cacheHits = new HashMap<String,
                                                                                  List<? extends DnsCacheEntry>>();

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
            List<? extends DnsCacheEntry> cacheEntries = cache.get(hostname, additionals);
            cacheHits.put(hostname, cacheEntries);
            return cacheEntries;
        }

        @Override
        public DnsCacheEntry cache(
                String hostname, DnsRecord[] additionals, InetAddress address, long originalTtl, EventLoop loop) {
            return cache.cache(hostname, additionals, address, originalTtl, loop);
        }

        @Override
        public DnsCacheEntry cache(
                String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
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
            }

            return message;
        }

        private static ResourceRecord newARecord(String dnsname, String ipAddress) {
            ResourceRecordModifier rm = new ResourceRecordModifier();
            rm.setDnsClass(RecordClass.IN);
            rm.setDnsName(dnsname);
            rm.setDnsTtl(100);
            rm.setDnsType(RecordType.A);
            rm.put(DnsAttribute.IP_ADDRESS, ipAddress);
            return rm.getEntry();
        }

        private static ResourceRecord newNsRecord(String dnsname, String domainName) {
            ResourceRecordModifier rm = new ResourceRecordModifier();
            rm.setDnsClass(RecordClass.IN);
            rm.setDnsName(dnsname);
            rm.setDnsTtl(100);
            rm.setDnsType(RecordType.NS);
            rm.put(DnsAttribute.DOMAIN_NAME, domainName);
            return rm.getEntry();
        }
    }

    @Test(timeout = 3000)
    public void testTimeoutNotCached() {
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
        DnsNameResolverBuilder builder = newResolver();
        builder.queryTimeoutMillis(100)
                .authoritativeDnsServerCache(cache)
                .resolveCache(cache)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(
                        new InetSocketAddress(NetUtil.LOCALHOST, 12345)));
        DnsNameResolver resolver = builder.build();
        Future<InetAddress> result = resolver.resolve("doesnotexist.netty.io").awaitUninterruptibly();
        Throwable cause = result.cause();
        assertTrue(cause instanceof UnknownHostException);
        assertTrue(cause.getCause() instanceof DnsNameResolverTimeoutException);
        assertTrue(DnsNameResolver.isTimeoutError(cause));
        assertTrue(DnsNameResolver.isTransportOrTimeoutError(cause));
        resolver.close();
    }

    @Test
    public void testDnsNameResolverBuilderCopy() {
        ChannelFactory<DatagramChannel> channelFactory =
                new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class);
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder(group.next())
                .channelFactory(channelFactory);
        DnsNameResolverBuilder copiedBuilder = builder.copy();

        // change channel factory does not propagate to previously made copy
        ChannelFactory<DatagramChannel> newChannelFactory =
                new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class);
        builder.channelFactory(newChannelFactory);
        assertEquals(channelFactory, copiedBuilder.channelFactory());
        assertEquals(newChannelFactory, builder.channelFactory());
    }
}
