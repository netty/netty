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
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.NetUtil;
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
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import static org.junit.Assert.fail;

public class DnsNameResolverTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);

    // Using the top-100 web sites ranked in Alexa.com (Oct 2014)
    // Please use the following series of shell commands to get this up-to-date:
    // $ curl -O http://s3.amazonaws.com/alexa-static/top-1m.csv.zip
    // $ unzip -o top-1m.csv.zip top-1m.csv
    // $ head -100 top-1m.csv | cut -d, -f2 | cut -d/ -f1 | while read L; do echo '"'"$L"'",'; done > topsites.txt
    private static final Set<String> DOMAINS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "google.com",
            "facebook.com",
            "youtube.com",
            "yahoo.com",
            "baidu.com",
            "wikipedia.org",
            "amazon.com",
            "twitter.com",
            "qq.com",
            "taobao.com",
            "linkedin.com",
            "google.co.in",
            "live.com",
            "hao123.com",
            "sina.com.cn",
            "blogspot.com",
            "weibo.com",
            "yahoo.co.jp",
            "tmall.com",
            "yandex.ru",
            "sohu.com",
            "bing.com",
            "ebay.com",
            "pinterest.com",
            "vk.com",
            "google.de",
            "wordpress.com",
            "apple.com",
            "google.co.jp",
            "google.co.uk",
            "360.cn",
            "instagram.com",
            "google.fr",
            "msn.com",
            "ask.com",
            "soso.com",
            "google.com.br",
            "tumblr.com",
            "paypal.com",
            "mail.ru",
            "xvideos.com",
            "microsoft.com",
            "google.ru",
            "reddit.com",
            "google.it",
            "imgur.com",
            "163.com",
            "google.es",
            "imdb.com",
            "aliexpress.com",
            "t.co",
            "go.com",
            "adcash.com",
            "craigslist.org",
            "amazon.co.jp",
            "alibaba.com",
            "google.com.mx",
            "stackoverflow.com",
            "xhamster.com",
            "fc2.com",
            "google.ca",
            "bbc.co.uk",
            "espn.go.com",
            "cnn.com",
            "google.co.id",
            "people.com.cn",
            "gmw.cn",
            "pornhub.com",
            "blogger.com",
            "huffingtonpost.com",
            "flipkart.com",
            "akamaihd.net",
            "google.com.tr",
            "amazon.de",
            "netflix.com",
            "onclickads.net",
            "googleusercontent.com",
            "kickass.to",
            "google.com.au",
            "google.pl",
            "xinhuanet.com",
            "ebay.de",
            "wordpress.org",
            "odnoklassniki.ru",
            "google.com.hk",
            "adobe.com",
            "dailymotion.com",
            "dailymail.co.uk",
            "indiatimes.com",
            "amazon.co.uk",
            "xnxx.com",
            "rakuten.co.jp",
            "dropbox.com",
            "tudou.com",
            "about.com",
            "cnet.com",
            "vimeo.com",
            "redtube.com",
            "blogspot.in",
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
                .channelType(NioDatagramChannel.class)
                .maxQueriesPerResolve(1)
                .decodeIdn(decodeToUnicode)
                .optResourceEnabled(false);

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
            testResolve0(resolver, EXCLUSIONS_RESOLVE_A);
        } finally {
            resolver.close();
        }
    }

    @Test
    public void testResolveAAAAorA() throws Exception {
        DnsNameResolver resolver = newResolver(ResolvedAddressTypes.IPV6_PREFERRED).build();
        try {
            testResolve0(resolver, EXCLUSIONS_RESOLVE_A);
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
                final Map<String, InetAddress> resultA = testResolve0(resolver, EXCLUSIONS_RESOLVE_A);
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
    public void  testResolveA() throws Exception {
        DnsNameResolver resolver = newResolver(ResolvedAddressTypes.IPV4_ONLY)
                // Cache for eternity
                .ttl(Integer.MAX_VALUE, Integer.MAX_VALUE)
                .build();
        try {
            final Map<String, InetAddress> resultA = testResolve0(resolver, EXCLUSIONS_RESOLVE_A);

            // Now, try to resolve again to see if it's cached.
            // This test works because the DNS servers usually randomizes the order of the records in a response.
            // If cached, the resolved addresses must be always same, because we reuse the same response.

            final Map<String, InetAddress> resultB = testResolve0(resolver, EXCLUSIONS_RESOLVE_A);

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
            testResolve0(resolver, EXCLUSIONS_RESOLVE_AAAA);
        } finally {
            resolver.close();
        }
    }

    @Test
    public void testNonCachedResolve() throws Exception {
        DnsNameResolver resolver = newNonCachedResolver(ResolvedAddressTypes.IPV4_ONLY).build();
        try {
            testResolve0(resolver, EXCLUSIONS_RESOLVE_A);
        } finally {
            resolver.close();
        }
    }

    @Test(timeout = 5000)
    public void testNonCachedResolveEmptyHostName() throws Exception {
        testNonCachedResolveEmptyHostName("");
    }

    @Test(timeout = 5000)
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

    @Test(timeout = 5000)
    public void testNonCachedResolveAllEmptyHostName() throws Exception {
        testNonCachedResolveAllEmptyHostName("");
    }

    @Test(timeout = 5000)
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

    private static Map<String, InetAddress> testResolve0(DnsNameResolver resolver, Set<String> excludedDomains)
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
                    buf.append(DnsNameResolverContext.decodeDomainName(recordContent));
                }

                logger.info("{} has the following MX records:{}", hostname, buf);
                response.release();
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
            negativeLookupThread.join(5000);

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
            return (UnknownHostException) e;
        }
    }

    @Test
    public void testResolveIp() {
        DnsNameResolver resolver = newResolver().build();
        try {
            InetAddress address = resolver.resolve("10.0.0.1").syncUninterruptibly().getNow();

            assertEquals("10.0.0.1", address.getHostAddress());
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
        } finally {
            resolver.close();
        }
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
        } finally {
            resolver.close();
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

    private static void testRecursiveResolveCache(boolean cache) throws Exception {
        final String hostname = "some.record.netty.io";
        final String hostname2 = "some2.record.netty.io";

        final TestDnsServer dnsServerAuthority = new TestDnsServer(new HashSet<String>(
                Arrays.asList(hostname, hostname2)));
        dnsServerAuthority.start();

        TestDnsServer dnsServer = new RedirectingTestDnsServer(hostname,
                dnsServerAuthority.localAddress().getAddress().getHostAddress());
        dnsServer.start();

        TestDnsCache nsCache = new TestDnsCache(cache ? new DefaultDnsCache() : NoopDnsCache.INSTANCE);
        EventLoopGroup group = new NioEventLoopGroup(1);
        DnsNameResolver resolver = new DnsNameResolver(
                group.next(), new ReflectiveChannelFactory<DatagramChannel>(NioDatagramChannel.class),
                NoopDnsCache.INSTANCE, nsCache, 3000, ResolvedAddressTypes.IPV4_ONLY, true, 10, true, 4096, false,
                HostsFileEntriesResolver.DEFAULT, new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()),
                DnsNameResolver.DEFAULT_SEARCH_DOMAINS, 0, true) {
            @Override
            int dnsRedirectPort(InetAddress server) {
                return server.equals(dnsServerAuthority.localAddress().getAddress()) ?
                        dnsServerAuthority.localAddress().getPort() : DnsServerAddresses.DNS_PORT;
            }
        };

        try {
            resolver.resolveAll(hostname).syncUninterruptibly();

            if (cache) {
                assertNull(nsCache.cache.get("io.", null));
                assertNull(nsCache.cache.get("netty.io.", null));
                List<DnsCacheEntry> entries = nsCache.cache.get("record.netty.io.", null);
                assertEquals(1, entries.size());

                assertNull(nsCache.cache.get(hostname, null));

                // Test again via cache.
                resolver.resolveAll(hostname).syncUninterruptibly();
                resolver.resolveAll(hostname2).syncUninterruptibly();

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

    private static final class TestDnsCache implements DnsCache {
        private final DnsCache cache;
        final Map<String, List<DnsCacheEntry>> cacheHits = new HashMap<String, List<DnsCacheEntry>>();

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
        public List<DnsCacheEntry> get(String hostname, DnsRecord[] additionals) {
            List<DnsCacheEntry> cacheEntries = cache.get(hostname, additionals);
            cacheHits.put(hostname, cacheEntries);
            return cacheEntries;
        }

        @Override
        public void cache(
                String hostname, DnsRecord[] additionals, InetAddress address, long originalTtl, EventLoop loop) {
            cache.cache(hostname, additionals, address, originalTtl, loop);
        }

        @Override
        public void cache(
                String hostname, DnsRecord[] additionals, Throwable cause, EventLoop loop) {
            cache.cache(hostname, additionals, cause, loop);
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
}
