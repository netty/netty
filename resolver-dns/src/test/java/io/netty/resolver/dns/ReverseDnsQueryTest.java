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

import org.junit.Test;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecordType;

public class ReverseDnsQueryTest {

    @Test
    public void rdns() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        DnsNameResolver resolver = new DnsNameResolverBuilder(group.next())
            .channelType(NioDatagramChannel.class)
            .build();

        try {
          String name = "1.0.0.127.in-addr.arpa";
          DnsQuestion question = new DefaultDnsQuestion(name, DnsRecordType.PTR);

          resolver.query(question).get();
        } finally {
            resolver.close();
            group.shutdownGracefully();
        }
    }
}
