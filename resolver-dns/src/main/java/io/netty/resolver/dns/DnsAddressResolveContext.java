/*
 * Copyright 2018 The Netty Project
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

import static io.netty.resolver.dns.DnsAddressDecoder.decodeAddress;
import static java.util.Collections.emptyList;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

final class DnsAddressResolveContext extends DnsResolveContext<DnsResolveResult> {

    private final DnsCache resolveCache;
    private final AuthoritativeDnsServerCache authoritativeDnsServerCache;
    private final boolean completeEarlyIfPossible;
    private final boolean trackCnames;
    private List<String> cnameChain; // Tracks CNAMEs discovered during resolution

    DnsAddressResolveContext(DnsNameResolver parent, Channel channel,
                             Promise<?> originalPromise, String hostname, DnsRecord[] additionals,
                             DnsServerAddressStream nameServerAddrs, int allowedQueries, DnsCache resolveCache,
                             AuthoritativeDnsServerCache authoritativeDnsServerCache,
                             boolean completeEarlyIfPossible, boolean trackCnames) {
        super(parent, channel, originalPromise, hostname, DnsRecord.CLASS_IN,
              parent.resolveRecordTypes(), additionals, nameServerAddrs, allowedQueries);
        this.resolveCache = resolveCache;
        this.authoritativeDnsServerCache = authoritativeDnsServerCache;
        this.completeEarlyIfPossible = completeEarlyIfPossible;
        this.trackCnames = trackCnames;
    }

    @Override
    DnsResolveContext<DnsResolveResult> newResolverContext(DnsNameResolver parent, Channel channel,
                                                          Promise<?> originalPromise,
                                                          String hostname,
                                                          int dnsClass, DnsRecordType[] expectedTypes,
                                                          DnsRecord[] additionals,
                                                          DnsServerAddressStream nameServerAddrs, int allowedQueries) {
        DnsAddressResolveContext result = new DnsAddressResolveContext(parent, channel, originalPromise, hostname,
                additionals,
                nameServerAddrs, allowedQueries, resolveCache, authoritativeDnsServerCache,
                completeEarlyIfPossible, trackCnames);
        // To be safe, when deriving a context we need to make a copy of the cname chain since the new context may
        // modify it.
        if (cnameChain != null) {
            result.cnameChain = new ArrayList<>(this.cnameChain);
        }
        return result;
    }

    @Override
    DnsResolveResult convertRecord(DnsRecord record, String hostname, DnsRecord[] additionals, EventLoop eventLoop) {
        InetAddress inetAddress = decodeAddress(record, hostname, parent.isDecodeIdn());
        if (inetAddress == null) {
            return null;
        }

        List<String> chain = cnameChain != null ? new ArrayList<>(cnameChain) : emptyList();
        return new DnsResolveResult(inetAddress, chain);
    }

    @Override
    List<DnsResolveResult> filterResults(List<DnsResolveResult> unfiltered) {
        Collections.sort(unfiltered, PreferredAddressTypeComparator.comparator(parent.preferredAddressType()));
        return unfiltered;
    }

    @Override
    boolean isCompleteEarly(DnsResolveResult resolved) {
        return completeEarlyIfPossible &&
                DnsNameResolver.addressType(parent.preferredAddressType()) == resolved.address().getClass();
    }

    @Override
    boolean shouldAddToResult(DnsResolveResult value, Collection<DnsResolveResult> entries) {
        // If we're tracking CNAME entries we use equality of whole entry, not just address.
        if (trackCnames) {
            return !entries.contains(value); // Don't add if already present (full equality check)
        } else {
            for (DnsResolveResult result : entries) {
                if (result.address().equals(value.address())) {
                    return false; // Don't add if address already present
                }
            }
            return true; // Add if address not already present
        }
    }

    @Override
    void cache(String hostname, DnsRecord[] additionals,
               DnsRecord result, DnsResolveResult convertedResult) {
        List<String> chain = convertedResult.cnameChain();
        if (!chain.isEmpty()) {
            // Use new cache method with CNAME chain
            resolveCache.cache(hostname, additionals, convertedResult.address(),
                chain, result.timeToLive(), channel().eventLoop());
        } else {
            // Fall back to old method
            resolveCache.cache(hostname, additionals, convertedResult.address(),
                result.timeToLive(), channel().eventLoop());
        }
    }

    @Override
    void cache(String hostname, DnsRecord[] additionals, UnknownHostException cause) {
        List<String> chain = cnameChain != null ?
            new ArrayList<String>(cnameChain) : Collections.<String>emptyList();
        if (!chain.isEmpty()) {
            // Use new cache method with CNAME chain
            resolveCache.cache(hostname, additionals, cause, chain, channel().eventLoop());
        } else {
            // Fall back to old method
            resolveCache.cache(hostname, additionals, cause, channel().eventLoop());
        }
    }

    @Override
    protected void addCnameToChain(String cname) {
        if (cnameChain == null) {
            cnameChain = new ArrayList<>();
        }
        cnameChain.add(cname);
    }

    /**
     * Special resolve method that returns InetAddress list instead of DnsResolveResult list.
     * Used for search domain queries and internal resolution where CNAME tracking is not needed.
     */
    void resolveForAddresses(final Promise<List<InetAddress>> promise) {
        Promise<List<DnsResolveResult>> internalPromise = parent.executor().newPromise();
        internalPromise.addListener(new FutureListener<List<DnsResolveResult>>() {
            @Override
            public void operationComplete(Future<List<DnsResolveResult>> future) throws Exception {
                if (future.isSuccess()) {
                    List<DnsResolveResult> results = future.getNow();
                    List<InetAddress> addresses = new ArrayList<InetAddress>(results.size());
                    for (DnsResolveResult result : results) {
                        addresses.add(result.address());
                    }
                    promise.setSuccess(addresses);
                } else {
                    promise.setFailure(future.cause());
                }
            }
        });
        resolve(internalPromise);
    }

    @Override
    DnsCache resolveCache() {
        return resolveCache;
    }

    @Override
    AuthoritativeDnsServerCache authoritativeDnsServerCache() {
        return authoritativeDnsServerCache;
    }
}
