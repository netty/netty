/*
 * Copyright 2018 The Netty Project
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

import static io.netty.resolver.dns.DnsAddressDecoder.decodeAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.util.ReferenceCountUtil;

final class DnsRecordResolveContext extends DnsResolveContext<DnsRecord> {

    DnsRecordResolveContext(DnsNameResolver parent, DnsQuestion question, DnsRecord[] additionals,
                            DnsServerAddressStream nameServerAddrs) {
        this(parent, question.name(), question.dnsClass(),
             new DnsRecordType[] { question.type() },
             additionals, nameServerAddrs);
    }

    private DnsRecordResolveContext(DnsNameResolver parent, String hostname,
                                    int dnsClass, DnsRecordType[] expectedTypes,
                                    DnsRecord[] additionals,
                                    DnsServerAddressStream nameServerAddrs) {
        super(parent, hostname, dnsClass, expectedTypes, additionals, nameServerAddrs);
    }

    @Override
    DnsResolveContext<DnsRecord> newResolverContext(DnsNameResolver parent, String hostname,
                                                    int dnsClass, DnsRecordType[] expectedTypes,
                                                    DnsRecord[] additionals,
                                                    DnsServerAddressStream nameServerAddrs) {
        return new DnsRecordResolveContext(parent, hostname, dnsClass, expectedTypes, additionals, nameServerAddrs);
    }

    @Override
    DnsRecord convertRecord(DnsRecord record, String hostname, DnsRecord[] additionals, EventLoop eventLoop) {
        return ReferenceCountUtil.retain(record);
    }

    @Override
    boolean containsExpectedResult(List<DnsRecord> finalResult) {
        return true;
    }

    @Override
    List<DnsRecord> filterResults(List<DnsRecord> unfiltered) {
        return unfiltered;
    }

    @Override
    void cache(String hostname, DnsRecord[] additionals, DnsRecord result, DnsRecord convertedResult) {
        // Do not cache.
        // XXX: When we implement cache, we would need to retain the reference count of the result record.
    }

    @Override
    void cache(String hostname, DnsRecord[] additionals, UnknownHostException cause) {
        // Do not cache.
        // XXX: When we implement cache, we would need to retain the reference count of the result record.
    }
}
