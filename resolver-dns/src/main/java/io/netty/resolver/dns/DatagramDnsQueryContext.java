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
package io.netty.resolver.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

final class DatagramDnsQueryContext extends DnsQueryContext {

    DatagramDnsQueryContext(Channel channel,
                            InetSocketAddress nameServerAddr,
                            DnsQueryContextManager queryContextManager,
                            int maxPayLoadSize, boolean recursionDesired,
                            long queryTimeoutMillis,
                            DnsQuestion question, DnsRecord[] additionals,
                            Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise,
                            Bootstrap socketBootstrap, boolean retryWithTcpOnTimeout) {
        super(channel, nameServerAddr, queryContextManager, maxPayLoadSize, recursionDesired,
                queryTimeoutMillis, question, additionals, promise, socketBootstrap, retryWithTcpOnTimeout);
    }

    @Override
    protected DnsQuery newQuery(int id, InetSocketAddress nameServerAddr) {
        return new DatagramDnsQuery(null, nameServerAddr, id);
    }

    @Override
    protected String protocol() {
        return "UDP";
    }
}
