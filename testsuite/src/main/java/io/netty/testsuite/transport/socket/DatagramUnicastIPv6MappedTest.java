/*
 * Copyright 2019 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;

public class DatagramUnicastIPv6MappedTest extends DatagramUnicastIPv6Test {

    private static final InetAddress IPV4_ANY;
    private static final int MAX_PORT_SELECTION_ATTEMPTS = 10;

    static {
        try {
            IPV4_ANY = InetAddress.getByAddress(new byte[] {0, 0, 0, 0});
        } catch (UnknownHostException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    protected SocketAddress newSocketAddress() {
        // Explicit use IPv4 wildcard for bind while the channel is using SocketProtocolFamily.INET6.
        //
        // Don't use port 0: when an IPv6 socket binds to the wildcard address, macOS only checks the ports of other
        // IPv6 sockets when it selects the ephemeral port, so it might select a port that an IPv4 socket is
        // bound to. Datagrams that are sent to the IPv4 loopback address are then delivered to the IPv4 socket and
        // never reach the channel, no matter how often they are resent:
        //
        // - in6_pcbsetport() checks if the port is used with in6_pcblookup_local()...
        //   https://github.com/apple-oss-distributions/xnu/blob/xnu-12377.121.6/bsd/netinet6/in6_src.c#L1779-L1780
        // - ...which skips all sockets that are not IPv6 sockets.
        //   https://github.com/apple-oss-distributions/xnu/blob/xnu-12377.121.6/bsd/netinet6/in6_pcb.c#L1297-L1301
        // - The netns_reserve_in() check that follows only fails if Skywalk reserved the port, not BSD sockets.
        //   https://github.com/apple-oss-distributions/xnu/blob/xnu-12377.121.6/bsd/skywalk/namespace/netns.c#L774-L783
        // - When an IPv4 datagram is received, in_pcblookup_hash_locked() prefers IPv4 sockets bound to the
        //   destination or the wildcard address over IPv6 sockets bound to the wildcard address.
        //   https://github.com/apple-oss-distributions/xnu/blob/xnu-12377.121.6/bsd/netinet/in_pcb.c#L2634-L2666
        return new InetSocketAddress(IPV4_ANY, unusedPort());
    }

    /**
     * Returns a port that is neither used by an IPv4 nor by an IPv6 socket.
     */
    private static int unusedPort() {
        BindException cause = null;
        for (int i = 0; i < MAX_PORT_SELECTION_ATTEMPTS; i++) {
            int port;
            // The IPv4 stack selects a port that is unused by IPv4 sockets and by IPv6 sockets that accept IPv4
            // datagrams.
            try (java.nio.channels.DatagramChannel ipv4 =
                         java.nio.channels.DatagramChannel.open(StandardProtocolFamily.INET)) {
                ipv4.bind(new InetSocketAddress(IPV4_ANY, 0));
                port = ((InetSocketAddress) ipv4.getLocalAddress()).getPort();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            // Bind to the port like the channel will do, which fails if an IPv6 socket that only accepts IPv6
            // datagrams uses it. The IPv4 channel must be closed by now, as this binds to the IPv4 wildcard address
            // as well on some platforms (like Linux).
            try (java.nio.channels.DatagramChannel ipv6 =
                         java.nio.channels.DatagramChannel.open(StandardProtocolFamily.INET6)) {
                ipv6.bind(new InetSocketAddress(IPV4_ANY, port));
                return port;
            } catch (BindException e) {
                cause = e;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        throw new UncheckedIOException("Failed to find a port that is unused by IPv4 and IPv6 sockets", cause);
    }

    @Override
    protected InetSocketAddress convertAnyAddress(InetSocketAddress serverAddress) {
        InetAddress addr = serverAddress.getAddress();
        if (addr.isAnyLocalAddress()) {
            return new InetSocketAddress(NetUtil.LOCALHOST4, serverAddress.getPort());
        }
        return serverAddress;
    }

    @Override
    protected boolean disconnectMightFail(DatagramChannel channel) {
        // See https://bugs.openjdk.org/browse/JDK-8285515
        if (channel instanceof NioDatagramChannel && PlatformDependent.javaVersion() < 20) {
            return true;
        }
        return super.disconnectMightFail(channel);
    }
}
