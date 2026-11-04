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

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the "mirror" name server keys registered by {@link DnsQueryContextManager}: besides the address of the
 * name server itself, the manager also registers the equivalent address of the other IP family so that a response
 * is still matched if its sender address is reported in that other family.
 */
public class DnsQueryContextManagerTest {

    private static final int PORT = 53;

    private EmbeddedChannel channel;
    private DnsQueryContextManager manager;

    @BeforeEach
    public void setUp() {
        channel = new EmbeddedChannel();
        manager = new DnsQueryContextManager();
    }

    @AfterEach
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    /**
     * Regression test for <a href="https://github.com/netty/netty/issues/18249">#18249</a>.
     *
     * <p>Every address in {@code 127.0.0.0/8} is a loopback address, but only {@code 127.0.0.1} is the IPv4 peer of
     * {@code ::1}. Mirroring all of them to {@code ::1} made two distinct loopback name servers - a setup that is
     * standard on GitHub Actions hosted runners, where {@code /etc/resolv.conf} lists both {@code 127.0.0.100} and
     * {@code 127.0.0.53} - collide on the same mirror key. That tripped an assertion with {@code -ea}, and silently
     * clobbered the first server's mirror entry without it.
     */
    @Test
    public void distinctLoopbackNameServersDoNotCollide() throws Exception {
        InetSocketAddress first = address("127.0.0.100");
        InetSocketAddress second = address("127.0.0.53");

        DnsQueryContext firstCtx = newContext(first);
        DnsQueryContext secondCtx = newContext(second);

        // Before the fix this threw an AssertionError on the second add(), because both name servers were mirrored
        // to ::1:53.
        int firstId = manager.add(first, firstCtx);
        int secondId = manager.add(second, secondCtx);

        assertThat(firstId).isNotNegative();
        assertThat(secondId).isNotNegative();

        // Each name server must keep its own context map.
        assertThat(manager.get(first, firstId)).isSameAs(firstCtx);
        assertThat(manager.get(second, secondId)).isSameAs(secondCtx);

        // Neither of them may be aliased to the IPv6 loopback: that alias belongs to 127.0.0.1 alone. With
        // assertions disabled the pre-fix code registered the second name server under ::1:53, so this is what
        // catches the bug when the AssertionError above is not raised.
        InetSocketAddress ipv6Loopback = new InetSocketAddress(NetUtil.LOCALHOST6, PORT);
        assertThat(manager.get(ipv6Loopback, firstId)).isNull();
        assertThat(manager.get(ipv6Loopback, secondId)).isNull();

        // Instead they are mirrored to their own IPv4-compatible IPv6 address.
        assertThat(manager.get(compatAddress("127.0.0.100"), firstId)).isSameAs(firstCtx);
        assertThat(manager.get(compatAddress("127.0.0.53"), secondId)).isSameAs(secondCtx);
    }

    /**
     * The full name server list of a GitHub Actions hosted runner, plus both loopback addresses, must register
     * without any collision.
     */
    @Test
    public void manyNameServersIncludingLoopbackDoNotCollide() throws Exception {
        String[] nameServers = { "127.0.0.100", "127.0.0.53", "168.63.129.16", "127.0.0.1", "::1", "8.8.8.8" };

        for (String nameServer : nameServers) {
            InetSocketAddress nameServerAddr = address(nameServer);
            DnsQueryContext ctx = newContext(nameServerAddr);
            int id = manager.add(nameServerAddr, ctx);

            assertThat(id).as("id for %s", nameServer).isNotNegative();
            assertThat(manager.get(nameServerAddr, id)).as("context for %s", nameServer).isSameAs(ctx);
        }
    }

    @Test
    public void ipv4LoopbackIsMirroredToIpv6Loopback() throws Exception {
        InetSocketAddress nameServerAddr = new InetSocketAddress(NetUtil.LOCALHOST4, PORT);
        DnsQueryContext ctx = newContext(nameServerAddr);

        int id = manager.add(nameServerAddr, ctx);

        assertThat(manager.get(new InetSocketAddress(NetUtil.LOCALHOST6, PORT), id)).isSameAs(ctx);
    }

    @Test
    public void ipv6LoopbackIsMirroredToIpv4Loopback() throws Exception {
        InetSocketAddress nameServerAddr = new InetSocketAddress(NetUtil.LOCALHOST6, PORT);
        DnsQueryContext ctx = newContext(nameServerAddr);

        int id = manager.add(nameServerAddr, ctx);

        assertThat(manager.get(new InetSocketAddress(NetUtil.LOCALHOST4, PORT), id)).isSameAs(ctx);
    }

    @Test
    public void ipv4NameServerIsMirroredToIpv4CompatibleAddress() throws Exception {
        InetSocketAddress nameServerAddr = address("8.8.8.8");
        DnsQueryContext ctx = newContext(nameServerAddr);

        int id = manager.add(nameServerAddr, ctx);

        assertThat(manager.get(compatAddress("8.8.8.8"), id)).isSameAs(ctx);
    }

    @Test
    public void ipv4CompatibleNameServerIsMirroredToIpv4Address() throws Exception {
        InetSocketAddress nameServerAddr = compatAddress("8.8.8.8");
        DnsQueryContext ctx = newContext(nameServerAddr);

        int id = manager.add(nameServerAddr, ctx);

        assertThat(manager.get(address("8.8.8.8"), id)).isSameAs(ctx);
    }

    /**
     * A name server and its mirror address must share one context map, no matter which of the two is registered
     * first. Otherwise, the two would hand out ids from two independent id spaces for what is the same server.
     */
    @Test
    public void mirrorAddressRegisteredFirstSharesContextMap() throws Exception {
        InetSocketAddress ipv6Loopback = new InetSocketAddress(NetUtil.LOCALHOST6, PORT);
        InetSocketAddress ipv4Loopback = new InetSocketAddress(NetUtil.LOCALHOST4, PORT);

        DnsQueryContext ipv6Ctx = newContext(ipv6Loopback);
        DnsQueryContext ipv4Ctx = newContext(ipv4Loopback);

        int ipv6Id = manager.add(ipv6Loopback, ipv6Ctx);
        int ipv4Id = manager.add(ipv4Loopback, ipv4Ctx);

        // Same context map, so the second add() must not be able to hand out the id the first one is using.
        assertThat(ipv4Id).isNotEqualTo(ipv6Id);

        assertThat(manager.get(ipv4Loopback, ipv6Id)).isSameAs(ipv6Ctx);
        assertThat(manager.get(ipv6Loopback, ipv4Id)).isSameAs(ipv4Ctx);
    }

    private DnsQueryContext newContext(InetSocketAddress nameServerAddr) {
        Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise = channel.eventLoop().newPromise();
        return new DatagramDnsQueryContext(
                channel,
                nameServerAddr,
                manager,
                /* maxPayLoadSize */ 4096,
                /* recursionDesired */ true,
                /* queryTimeoutMillis */ 5000,
                new DefaultDnsQuestion("netty.io.", DnsRecordType.A),
                new DnsRecord[0],
                promise,
                /* socketBootstrap */ null,
                /* retryWithTcpOnTimeout */ false,
                /* protection */ null);
    }

    private static InetSocketAddress address(String ip) throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(ip), PORT);
    }

    /**
     * The IPv4-compatible IPv6 form ({@code ::a.b.c.d}) of the given IPv4 address, built from the raw bytes the
     * same way {@code DnsQueryContextManager} builds its mirror key.
     */
    private static InetSocketAddress compatAddress(String ipv4) throws UnknownHostException {
        byte[] b4 = ((Inet4Address) InetAddress.getByName(ipv4)).getAddress();
        byte[] b6 = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, b4[0], b4[1], b4[2], b4[3] };
        InetAddress a6 = InetAddress.getByAddress(b6);
        assertThat(a6).isInstanceOf(Inet6Address.class);
        return new InetSocketAddress(a6, PORT);
    }
}
