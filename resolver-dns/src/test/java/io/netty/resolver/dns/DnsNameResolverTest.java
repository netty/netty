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
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class DnsNameResolverTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);

    private static final List<InetSocketAddress> SERVER_ADDRS = Arrays.asList(
            new InetSocketAddress("8.8.8.8", 53), // Google Public DNS
            new InetSocketAddress("8.8.4.4", 53),
            new InetSocketAddress("208.67.222.222", 53), // OpenDNS
            new InetSocketAddress("208.67.220.220", 53),
            new InetSocketAddress("37.235.1.174", 53), // FreeDNS
            new InetSocketAddress("37.235.1.177", 53)
    );

    // Using Alexa top 20 web sites on 10/05/2014.
    private static final List<String> DOMAINS = Arrays.asList(
            "www.google.com",
            "www.facebook.com",
            "www.youtube.com",
            "www.yahoo.com",
            "www.baidu.com",
            "www.wikipedia.org",
            "www.amazon.com",
            "www.twitter.com",
            //"www.qq.com", - QQ.com's authoritative name server does not understand wildcard queries.
            "www.linkedin.com",
            "www.taobao.com",
            "www.google.co.in",
            "www.live.com",
            "www.hao123.com",
            "www.sina.com.cn",
            "www.blogspot.com",
            "www.weibo.com",
            "www.yahoo.co.jp",
            "www.tmall.com",
            "www.yandex.ru"
    );

    private static final EventLoopGroup group = new NioEventLoopGroup(1);
    private static final DnsNameResolver resolver = new DnsNameResolver(
            group.next(), NioDatagramChannel.class, DnsServerAddresses.shuffled(SERVER_ADDRS));

    static {
        resolver.setMaxTries(SERVER_ADDRS.size());
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
    public void testResolveWithRecursionDesired() throws Exception {
        assertThat(resolver.isRecursionDesired(), is(true));
        for (String name: DOMAINS) {
            testResolve(name);
        }
    }

    @Test
    @Ignore("Fails due to a known issue")
    public void testResolveWithoutRecursionDesired() throws Exception {
        resolver.setRecursionDesired(false);
        for (String name: DOMAINS) {
            testResolve(name);
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
}
