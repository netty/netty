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
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

final class DnsAddressResolveContext extends DnsResolveContext<InetAddress> {

    private final DnsCache resolveCache;

    DnsAddressResolveContext(DnsNameResolver parent, String hostname, DnsRecord[] additionals,
                             DnsServerAddressStream nameServerAddrs, DnsCache resolveCache) {
        super(parent, hostname, DnsRecord.CLASS_IN, parent.resolveRecordTypes(), additionals, nameServerAddrs);
        this.resolveCache = resolveCache;
    }

    @Override
    DnsResolveContext<InetAddress> newResolverContext(DnsNameResolver parent, String hostname,
                                                      int dnsClass, DnsRecordType[] expectedTypes,
                                                      DnsRecord[] additionals,
                                                      DnsServerAddressStream nameServerAddrs) {
        return new DnsAddressResolveContext(parent, hostname, additionals, nameServerAddrs, resolveCache);
    }

    @Override
    InetAddress convertRecord(DnsRecord record, String hostname, DnsRecord[] additionals, EventLoop eventLoop) {
        return decodeAddress(record, hostname, parent.isDecodeIdn());
    }

    @Override
    boolean containsExpectedResult(List<InetAddress> finalResult) {
        final int size = finalResult.size();
        final Class<? extends InetAddress> inetAddressType = parent.preferredAddressType().addressType();
        for (int i = 0; i < size; i++) {
            InetAddress address = finalResult.get(i);
            if (inetAddressType.isInstance(address)) {
                return true;
            }
        }
        return false;
    }

    @Override
    void cache(String hostname, DnsRecord[] additionals,
               DnsRecord result, InetAddress convertedResult) {
        resolveCache.cache(hostname, additionals, convertedResult, result.timeToLive(), parent.ch.eventLoop());
    }

    @Override
    void cache(String hostname, DnsRecord[] additionals, UnknownHostException cause) {
        resolveCache.cache(hostname, additionals, cause, parent.ch.eventLoop());
    }
}
