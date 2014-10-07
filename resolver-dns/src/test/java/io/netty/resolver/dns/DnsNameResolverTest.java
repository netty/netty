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
import io.netty.resolver.NameResolver;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
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

    private static final List<InetSocketAddress> ROOT_SERVERS = Arrays.asList(
            new InetSocketAddress("198.41.0.4", 53),
            new InetSocketAddress("192.228.79.201", 53),
            new InetSocketAddress("192.33.4.12", 53),
            new InetSocketAddress("199.7.91.13", 53),
            new InetSocketAddress("192.203.230.10", 53),
            new InetSocketAddress("192.5.5.241", 53),
            new InetSocketAddress("192.112.36.4", 53),
            new InetSocketAddress("128.63.2.53", 53),
            new InetSocketAddress("192.36.148.17", 53),
            new InetSocketAddress("192.58.128.30", 53),
            new InetSocketAddress("193.0.14.129", 53),
            new InetSocketAddress("199.7.83.42", 53),
            new InetSocketAddress("202.12.27.33", 53)
    );

    // Using the top US web sites ranked in Alexa.com (Oct 2014)
    private static final List<String> DOMAINS = Arrays.asList(
            "google.com",
            "facebook.com",
            "yahoo.com",
            "youtube.com",
            "amazon.com",
            "wikipedia.org",
            "linkedin.com",
            "ebay.com",
            "twitter.com",
            "craigslist.org",
            "bing.com",
            "pinterest.com",
            "go.com",
            "espn.go.com",
            "blogspot.com",
            "reddit.com",
            "instagram.com",
            "live.com",
            "paypal.com",
            "imgur.com"
    );

    private static final EventLoopGroup group = new NioEventLoopGroup(1);
    private static final DnsNameResolver basicResolver = new DnsNameResolver(
            group.next(), NioDatagramChannel.class, DnsServerAddresses.shuffled(SERVERS));
    private static final DnsNameResolver rootResolver = new DnsNameResolver(
            group.next(), NioDatagramChannel.class, DnsServerAddresses.rotational(ROOT_SERVERS));

    static {
        basicResolver.setMaxTries(SERVERS.size());

        rootResolver.setRecursionDesired(false);
        rootResolver.setMaxTries(ROOT_SERVERS.size());
        rootResolver.setMaxRecursionLevel(16);
    }

    @AfterClass
    public static void destroy() {
        group.shutdownGracefully();
    }

    @Before
    public void reset() {
        basicResolver.clearCache();
        rootResolver.clearCache();
    }

    @Test
    public void testResolveWithRecursionDesired() throws Exception {
        assertThat(basicResolver.isRecursionDesired(), is(true));
        for (String name: DOMAINS) {
            testResolve(basicResolver, name);
        }
    }

    @Test
    public void testResolveWithoutRecursionDesired() throws Exception {
        for (String name: DOMAINS) {
            testResolve(rootResolver, name);
        }
    }

    private static void testResolve(NameResolver resolver, String hostname) throws Exception {
        int port = ThreadLocalRandom.current().nextInt(65536);
        InetSocketAddress resolved = (InetSocketAddress) resolver.resolve(hostname, port).sync().getNow();

        logger.info("{}:{} has been resolved into {}.", hostname, port, resolved);

        assertThat(resolved.isUnresolved(), is(false));
        assertThat(resolved.getHostString(), is(hostname));
        assertThat(resolved.getPort(), is(port));
    }
}
