/*
 * Copyright 2014 The Netty Project
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
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsType;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class DnsNameResolverTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);

    private static final List<InetSocketAddress> SERVERS = Arrays.asList(
            new InetSocketAddress("8.8.8.8", 53), // Google Public DNS
            new InetSocketAddress("8.8.4.4", 53),
            new InetSocketAddress("208.67.222.222", 53), // OpenDNS
            new InetSocketAddress("208.67.220.220", 53),
            new InetSocketAddress("37.235.1.174", 53), // FreeDNS
            new InetSocketAddress("37.235.1.177", 53)
    );

    // Using the top-100 web sites ranked in Alexa.com (Oct 2014)
    // Please use the following series of shell commands to get this up-to-date:
    // $ curl -O http://s3.amazonaws.com/alexa-static/top-1m.csv.zip
    // $ unzip -o top-1m.csv.zip top-1m.csv
    // $ head -100 top-1m.csv | cut -d, -f2 | cut -d/ -f1 | while read L; do echo '"'"$L"'",'; done > topsites.txt
    private static final String[] DOMAINS = {
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
            "thepiratebay.se",
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
    };

    /**
     * The list of the domain names to exclude from {@link #testResolveAorAAAA()}.
     */
    private static final Set<String> EXCLUSIONS_RESOLVE_A = new HashSet<String>();
    static {
        Collections.addAll(
                EXCLUSIONS_RESOLVE_A,
                "akamaihd.net",
                "googleusercontent.com",
                "");
    }

    /**
     * The list of the domain names to exclude from {@link #testResolveAAAA()}.
     * Unfortunately, there are only handful of domain names with IPv6 addresses.
     */
    private static final Set<String> EXCLUSIONS_RESOLVE_AAAA = new HashSet<String>();
    static {
        EXCLUSIONS_RESOLVE_AAAA.addAll(EXCLUSIONS_RESOLVE_A);
        Collections.addAll(EXCLUSIONS_RESOLVE_AAAA, DOMAINS);
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
                "");
    }

    private static final EventLoopGroup group = new NioEventLoopGroup(1);
    private static final DnsNameResolver resolver = new DnsNameResolver(
            group.next(), NioDatagramChannel.class, DnsServerAddresses.shuffled(SERVERS));

    static {
        resolver.setMaxTriesPerQuery(SERVERS.size());
    }

    @AfterClass
    public static void destroy() {
        group.shutdownGracefully();
    }

    @After
    public void reset() throws Exception {
        resolver.clearCache();
    }

    @Test
    public void testResolveAorAAAA() throws Exception {
        testResolve0(EXCLUSIONS_RESOLVE_A, InternetProtocolFamily.IPv4, InternetProtocolFamily.IPv6);
    }

    @Test
    public void testResolveAAAAorA() throws Exception {
        testResolve0(EXCLUSIONS_RESOLVE_A, InternetProtocolFamily.IPv6, InternetProtocolFamily.IPv4);
    }

    @Test
    public void testResolveA() throws Exception {

        final int oldMinTtl = resolver.minTtl();
        final int oldMaxTtl = resolver.maxTtl();

        // Cache for eternity.
        resolver.setTtl(Integer.MAX_VALUE, Integer.MAX_VALUE);

        try {
            final Map<String, InetAddress> resultA = testResolve0(EXCLUSIONS_RESOLVE_A, InternetProtocolFamily.IPv4);

            // Now, try to resolve again to see if it's cached.
            // This test works because the DNS servers usually randomizes the order of the records in a response.
            // If cached, the resolved addresses must be always same, because we reuse the same response.

            final Map<String, InetAddress> resultB = testResolve0(EXCLUSIONS_RESOLVE_A, InternetProtocolFamily.IPv4);

            assertThat(resultA, is(resultB));
        } finally {
            // Restore the TTL configuration.
            resolver.setTtl(oldMinTtl, oldMaxTtl);
        }
    }

    @Test
    public void testResolveAAAA() throws Exception {
        testResolve0(EXCLUSIONS_RESOLVE_AAAA, InternetProtocolFamily.IPv6);
    }

    private static Map<String, InetAddress> testResolve0(
            Set<String> excludedDomains, InternetProtocolFamily... famililies) throws InterruptedException {

        final List<InternetProtocolFamily> oldResolveAddressTypes = resolver.resolveAddressTypes();

        assertThat(resolver.isRecursionDesired(), is(true));
        assertThat(oldResolveAddressTypes.size(), is(InternetProtocolFamily.values().length));

        resolver.setResolveAddressTypes(famililies);

        final Map<String, InetAddress> results = new HashMap<String, InetAddress>();
        try {
            final Map<InetSocketAddress, Future<InetSocketAddress>> futures =
                    new LinkedHashMap<InetSocketAddress, Future<InetSocketAddress>>();

            for (String name : DOMAINS) {
                if (excludedDomains.contains(name)) {
                    continue;
                }

                resolve(futures, name);
            }

            for (Entry<InetSocketAddress, Future<InetSocketAddress>> e : futures.entrySet()) {
                InetSocketAddress unresolved = e.getKey();
                InetSocketAddress resolved = e.getValue().sync().getNow();

                logger.info("{}: {}", unresolved.getHostString(), resolved.getAddress().getHostAddress());

                assertThat(resolved.isUnresolved(), is(false));
                assertThat(resolved.getHostString(), is(unresolved.getHostString()));
                assertThat(resolved.getPort(), is(unresolved.getPort()));

                boolean typeMatches = false;
                for (InternetProtocolFamily f: famililies) {
                    Class<?> resolvedType = resolved.getAddress().getClass();
                    switch (f) {
                    case IPv4:
                        if (Inet4Address.class.isAssignableFrom(resolvedType)) {
                            typeMatches = true;
                        }
                        break;
                    case IPv6:
                        if (Inet6Address.class.isAssignableFrom(resolvedType)) {
                            typeMatches = true;
                        }
                        break;
                    }
                }

                assertThat(typeMatches, is(true));

                results.put(resolved.getHostString(), resolved.getAddress());
            }
        } finally {
            resolver.setResolveAddressTypes(oldResolveAddressTypes);
        }

        return results;
    }

    @Test
    public void testQueryMx() throws Exception {
        assertThat(resolver.isRecursionDesired(), is(true));

        Map<String, Future<DnsResponse>> futures =
                new LinkedHashMap<String, Future<DnsResponse>>();
        for (String name: DOMAINS) {
            if (EXCLUSIONS_QUERY_MX.contains(name)) {
                continue;
            }

            queryMx(futures, name);
        }

        for (Entry<String, Future<DnsResponse>> e: futures.entrySet()) {
            String hostname = e.getKey();
            DnsResponse response = e.getValue().sync().getNow();

            assertThat(response.header().responseCode(), is(DnsResponseCode.NOERROR));
            List<DnsResource> mxList = new ArrayList<DnsResource>();
            for (DnsResource r: response.answers()) {
                if (r.type() == DnsType.MX) {
                    mxList.add(r);
                }
            }

            assertThat(mxList.size(), is(greaterThan(0)));
            StringBuilder buf = new StringBuilder();
            for (DnsResource r: mxList) {
                buf.append(StringUtil.NEWLINE);
                buf.append('\t');
                buf.append(r.name());
                buf.append(' ');
                buf.append(r.type());
                buf.append(' ');
                buf.append(r.content().readUnsignedShort());
                buf.append(' ');
                buf.append(DnsNameResolverContext.decodeDomainName(r.content()));
            }

            logger.info("{} has the following MX records:{}", hostname, buf);
            response.release();
        }
    }

    private static void resolve(Map<InetSocketAddress, Future<InetSocketAddress>> futures, String hostname) {
        InetSocketAddress unresolved =
                InetSocketAddress.createUnresolved(hostname, ThreadLocalRandom.current().nextInt(65536));

        futures.put(unresolved, resolver.resolve(unresolved));
    }

    private static void queryMx(Map<String, Future<DnsResponse>> futures, String hostname) throws Exception {
        futures.put(hostname, resolver.query(new DnsQuestion(hostname, DnsType.MX)));
    }
}
