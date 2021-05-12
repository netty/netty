/*
 * Copyright 2016 The Netty Project
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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsOptEcsRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.util.internal.SocketUtils;
import io.netty.util.concurrent.Future;
import org.junit.Ignore;
import org.junit.Test;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DnsNameResolverClientSubnetTest {

    // See https://www.gsic.uva.es/~jnisigl/dig-edns-client-subnet.html
    // Ignore as this needs to query real DNS servers.
    @Ignore
    @Test
    public void testSubnetQuery() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        DnsNameResolver resolver = newResolver(group).build();
        try {
            // Same as:
            // # /.bind-9.9.3-edns/bin/dig @ns1.google.com www.google.es +client=157.88.0.0/24
            Future<List<InetAddress>> future = resolver.resolveAll("www.google.es",
                    Collections.<DnsRecord>singleton(
                            // Suggest max payload size of 1024
                            // 157.88.0.0 / 24
                            new DefaultDnsOptEcsRecord(1024, 24,
                                                       SocketUtils.addressByName("157.88.0.0").getAddress())));
            for (InetAddress address: future.syncUninterruptibly().getNow()) {
                System.out.println(address);
            }
        } finally {
            resolver.close();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }
    }

    private static DnsNameResolverBuilder newResolver(EventLoopGroup group) {
        return new DnsNameResolverBuilder(group.next())
                .channelType(NioDatagramChannel.class)
                .nameServerProvider(
                        new SingletonDnsServerAddressStreamProvider(SocketUtils.socketAddress("8.8.8.8", 53)))
                .maxQueriesPerResolve(1)
                .optResourceEnabled(false)
                .ndots(1);
    }
}
