/*
 * Copyright 2019 The Netty Project
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

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

final class DatagramDnsQueryContext extends DnsQueryContext {

    DatagramDnsQueryContext(DnsNameResolver parent, InetSocketAddress nameServerAddr, DnsQuestion question,
                            DnsRecord[] additionals,
                            Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise) {
        super(parent, nameServerAddr, question, additionals, promise);
    }

    @Override
    protected DnsQuery newQuery(int id) {
        return new DatagramDnsQuery(null, nameServerAddr(), id);
    }

    @Override
    protected Channel channel() {
        return parent().ch;
    }

    @Override
    protected String protocol() {
        return "UDP";
    }
}
