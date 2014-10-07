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
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsType;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    // Using the top web sites ranked in Alexa.com (Oct 2014)
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
    };

    private static final String[] DOMAINS_WITHOUT_MX = {
            "hao123.com",
            "blogspot.com",
    };

    private static final EventLoopGroup group = new NioEventLoopGroup(1);
    private static final DnsNameResolver resolver = new DnsNameResolver(
            group.next(), NioDatagramChannel.class, DnsServerAddresses.shuffled(SERVERS));

    static {
        resolver.setMaxTries(SERVERS.size());
    }

    @AfterClass
    public static void destroy() {
        group.shutdownGracefully();
    }

    @Before
    public void reset() {
        resolver.clearCache();
    }

    @Test
    public void testResolve() throws Exception {
        assertThat(resolver.isRecursionDesired(), is(true));
        for (String name: DOMAINS) {
            testResolve(name);
        }
    }

    @Test
    public void testQueryMx() throws Exception {
        assertThat(resolver.isRecursionDesired(), is(true));
        for (String name: DOMAINS) {
            testQueryMx(name);
        }
    }

    private static void testResolve(String hostname) throws Exception {
        int port = ThreadLocalRandom.current().nextInt(65536);
        InetSocketAddress resolved = (InetSocketAddress) resolver.resolve(hostname, port).sync().getNow();

        logger.info("{}:{} has been resolved into {}.", hostname, port, resolved);

        assertThat(resolved.isUnresolved(), is(false));
        assertThat(resolved.getHostString(), is(hostname));
        assertThat(resolved.getPort(), is(port));
    }

    private static void testQueryMx(String hostname) throws Exception {
        DnsResponse response = resolver.query(new DnsQuestion(hostname, DnsType.MX)).sync().getNow();

        assertThat(response.header().responseCode(), is(DnsResponseCode.NOERROR));
        List<DnsResource> mxList = new ArrayList<DnsResource>();
        for (DnsResource r: response.answers()) {
            if (r.type() == DnsType.MX) {
                mxList.add(r);
            }
        }

        boolean mxExpected = true;
        for (String v: DOMAINS_WITHOUT_MX) {
            if (hostname.equals(v)) {
                mxExpected = false;
                break;
            }
        }

        if (mxExpected) {
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
        } else {
            logger.info("{} is known to have no MX records.");
        }

        response.release();
    }
}
