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

import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSvcbRecord;
import io.netty.handler.codec.dns.SvcParam;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DnsResolveResult.Endpoint;
import io.netty.resolver.dns.DnsResolveResult.HttpsAnswer;
import io.netty.resolver.dns.DnsResolveResult.HttpsRecord;
import io.netty.resolver.dns.DnsResolveResult.IpAddress;
import io.netty.resolver.dns.DnsResolveResult.ServiceUnavailable;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.netty.resolver.dns.DnsHttpsResolveContext.addressRecordTypes;
import static io.netty.resolver.dns.DnsHttpsResolveContext.coversResolverAddressTypes;
import static io.netty.resolver.dns.DnsHttpsResolveContext.httpsName;
import static io.netty.resolver.dns.DnsHttpsResolveContext.normalize;
import static io.netty.resolver.dns.DnsHttpsResolveContext.recordType;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * Resolves a service for HTTPS (<a href="https://www.rfc-editor.org/rfc/rfc9460.html">RFC 9460</a>) as the join of
 * its HTTPS records, if endpoints are resolved, and its A/AAAA records of the address types. Each part is taken from
 * its cache, or else resolved by a {@link DnsHttpsResolveContext}, both in parallel, so that a part still cached is
 * not queried again. With search domains, the parts of each name are joined in turn, until one has a result. The
 * HTTPS records then become {@link Endpoint}s sorted by ascending priority, with the
 * addresses of their target, followed by the A/AAAA records as {@link IpAddress}s in the order of preference
 * of the address types, and the {@link HttpsAnswer} of the HTTPS records. With early completion, the address
 * part completes once it has an address of the preferred family, without waiting for the other family.
 */
final class DnsHttpsResolution {

    private static final DnsRecord[] EMPTY_ADDITIONALS = new DnsRecord[0];

    private final DnsNameResolver parent;
    private final DnsCache resolveCache;
    private final DnsHttpsCache cache;
    private final Set<Integer> supportedKeys;
    private final int maxAliases;
    private final ResolvedAddressTypes addressTypes;
    private final List<DnsRecordType> addressRecordTypes;
    private final boolean completeEarlyIfPossible;

    DnsHttpsResolution(DnsNameResolver parent, DnsCache resolveCache, DnsHttpsCache cache, Set<Integer> supportedKeys,
                       int maxAliases, ResolvedAddressTypes addressTypes, boolean completeEarlyIfPossible) {
        this.parent = parent;
        this.resolveCache = resolveCache;
        this.cache = cache;
        this.supportedKeys = supportedKeys;
        this.maxAliases = checkPositive(maxAliases, "maxAliases");
        this.addressTypes = checkNotNull(addressTypes, "addressTypes");
        addressRecordTypes = addressRecordTypes(addressTypes);
        this.completeEarlyIfPossible = completeEarlyIfPossible;
    }

    /**
     * Resolves the service on {@code port} of {@code hostname}: its endpoints if {@code resolveEndpoints}, and its
     * addresses of the address types.
     */
    void resolve(String hostname, int port, DnsRecord[] additionals, boolean resolveEndpoints,
                 Promise<List<DnsResolveResult>> promise) {
        final String[] searchDomains = parent.searchDomains();
        if (searchDomains.length == 0 || parent.ndots() == 0 || StringUtil.endsWith(hostname, '.')) {
            join(hostname, port, additionals, resolveEndpoints, promise);
            return;
        }
        // Like DnsResolveContext, the name is tried with each search domain, after or before the name itself
        // depending on its number of dots. Each name joins both parts, so that the endpoints and the addresses come
        // from the same name: the first one with a result.
        final List<String> names = new ArrayList<String>(searchDomains.length + 1);
        for (String searchDomain : searchDomains) {
            names.add(absolute(hostname + '.' + searchDomain));
        }
        names.add(hasNDots(hostname) ? 0 : names.size(), absolute(hostname));
        // Like resolveAll, a name whose parts are all cached answers without queries: the name itself, or else a
        // search domain permutation with records.
        if (isCached(absolute(hostname), port, additionals, resolveEndpoints, false)) {
            join(absolute(hostname), port, additionals, resolveEndpoints, promise);
            return;
        }
        for (String name : names) {
            if (isCached(name, port, additionals, resolveEndpoints, true)) {
                join(name, port, additionals, resolveEndpoints, promise);
                return;
            }
        }
        joinFirst(names, 0, port, additionals, resolveEndpoints, promise);
    }

    private boolean isCached(String name, int port, DnsRecord[] additionals, boolean resolveEndpoints,
                             boolean withRecords) {
        if (resolveEndpoints) {
            final DnsHttpsCacheEntry entry = cache.get(httpsName(name, port), additionals);
            if (entry == null || withRecords && entry.cause() != null) {
                return false;
            }
        }
        final List<? extends DnsCacheEntry> entries = resolveCache.get(name, additionals);
        return entries != null && !entries.isEmpty() && !(withRecords && entries.get(0).cause() != null);
    }

    private void joinFirst(final List<String> names, final int index, final int port, final DnsRecord[] additionals,
                           final boolean resolveEndpoints, final Promise<List<DnsResolveResult>> promise) {
        final Promise<List<DnsResolveResult>> attempt = parent.executor().newPromise();
        attempt.addListener((FutureListener<List<DnsResolveResult>>) f -> {
            final Throwable cause = f.cause();
            if (cause == null) {
                promise.trySuccess(f.getNow());
            } else if (index + 1 < names.size() && !DnsNameResolver.isTransportOrTimeoutError(cause) &&
                    !(cause instanceof DnsHttpsResolveContext.MalformedHttpsRecordsException)) {
                joinFirst(names, index + 1, port, additionals, resolveEndpoints, promise);
            } else {
                promise.tryFailure(cause);
            }
        });
        join(names.get(index), port, additionals, resolveEndpoints, attempt);
    }

    private boolean hasNDots(String hostname) {
        for (int idx = hostname.length() - 1, dots = 0; idx >= 0; idx--) {
            if (hostname.charAt(idx) == '.' && ++dots >= parent.ndots()) {
                return true;
            }
        }
        return false;
    }

    private static String absolute(String name) {
        return StringUtil.endsWith(name, '.') ? name : name + '.';
    }

    private void join(final String hostname, int port, DnsRecord[] additionals, boolean resolveEndpoints,
                      final Promise<List<DnsResolveResult>> promise) {
        final String httpsName = httpsName(hostname, port);
        final Future<List<DnsResolveResult>> https = resolveEndpoints ?
                resolvePart(hostname, port, additionals, null) : succeeded();
        final Future<List<DnsResolveResult>> addresses = resolvePart(hostname, port, additionals, addressTypes);
        https.addListener(f -> addresses.addListener(f2 -> complete(hostname, httpsName, https, addresses, promise)));
    }

    private Future<List<DnsResolveResult>> succeeded() {
        return parent.executor().newSucceededFuture(Collections.<DnsResolveResult>emptyList());
    }

    /**
     * Resolves the HTTPS records if {@code types} is {@code null}, otherwise the addresses of {@code types}, from the
     * caches if possible.
     */
    private Future<List<DnsResolveResult>> resolvePart(final String hostname, final int port,
                                                      final DnsRecord[] additionals, final ResolvedAddressTypes types) {
        final Promise<List<DnsResolveResult>> promise = parent.executor().newPromise();
        if (DnsHttpsResolveContext.resolveCached(resolveCache, cache, hostname, port, additionals, types, promise)) {
            return promise;
        }
        parent.nextResolveChannel(promise).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                promise.tryFailure(f.cause());
                return;
            }
            new DnsHttpsResolveContext(parent, f.channel(), promise, hostname, port, additionals,
                    parent.newNameServerAddressStream(hostname), parent.maxQueriesPerResolve(), resolveCache,
                    maxAliases, cache, types, completeEarlyIfPossible).resolve(promise);
        });
        return promise;
    }

    /**
     * Joins the HTTPS records and the addresses. A part that failed is missing from the result, so that a malformed
     * or missing HTTPS RRset falls back to the addresses
     * (<a href="https://www.rfc-editor.org/rfc/rfc9460.html#section-2.2">RFC 9460, Section 2.2</a>); the resolution
     * fails if nothing is left. A service declared as not available gives a {@link ServiceUnavailable}
     * followed by the addresses.
     */
    private void complete(String hostname, String httpsName, Future<List<DnsResolveResult>> https,
                          Future<List<DnsResolveResult>> addressesPart, Promise<List<DnsResolveResult>> promise) {
        final List<DnsSvcbRecord> records = new ArrayList<DnsSvcbRecord>();
        long httpsExpiration = -1;
        final List<DnsResolveResult> result = new ArrayList<DnsResolveResult>();
        if (https.isSuccess()) {
            for (DnsResolveResult r : https.getNow()) {
                if (r instanceof HttpsRecord) {
                    records.add(((HttpsRecord) r).record());
                } else if (r instanceof HttpsAnswer) {
                    httpsExpiration = ((HttpsAnswer) r).deadlineNanos();
                } else if (r instanceof ServiceUnavailable) {
                    result.add(r);
                }
            }
        }
        final List<IpAddress> addresses = addressesPart.isSuccess() ?
                addresses(addressesPart.getNow()) : Collections.<IpAddress>emptyList();
        result.addAll(result(records, addresses));
        if (result.isEmpty()) {
            promise.tryFailure(failure(hostname, records, https.cause(), addressesPart.cause()));
            return;
        }
        if (httpsExpiration != -1) {
            result.add(new HttpsAnswer(httpsExpiration));
        }
        resolveTargetAddresses(hostname, httpsName, result, httpsExpiration, reusableOriginAddresses(addresses),
                completeEarlyIfPossible && hasPreferredAddress(addresses) ? addressRecordTypes.get(0) : null,
                promise);
    }

    /**
     * Returns the failure of a resolution without result: HTTPS records none of which is usable, or else the failure
     * of the HTTPS records, or of the addresses.
     */
    private static Throwable failure(String hostname, List<DnsSvcbRecord> records, Throwable httpsCause,
                                     Throwable addressesCause) {
        if (records.isEmpty() && (httpsCause != null || addressesCause != null)) {
            return httpsCause != null ? httpsCause : addressesCause;
        }
        return new UnknownHostException("Failed to resolve '" + hostname + "' [HTTPS]: no usable record");
    }

    private boolean hasPreferredAddress(List<IpAddress> addresses) {
        for (IpAddress address : addresses) {
            if (recordType(address.address()) == addressRecordTypes.get(0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the origin addresses as the resolver would, or {@code null} if its address types were not all queried.
     */
    private List<IpAddress> reusableOriginAddresses(List<IpAddress> addresses) {
        if (!coversResolverAddressTypes(parent, addressRecordTypes)) {
            return null;
        }
        final List<DnsRecordType> types = Arrays.asList(parent.resolveRecordTypes());
        final List<IpAddress> reusable = new ArrayList<IpAddress>();
        for (IpAddress address : addresses) {
            if (types.contains(recordType(address.address()))) {
                reusable.add(address);
            }
        }
        reusable.sort(Comparator.comparing(IpAddress::address,
                PreferredAddressTypeComparator.comparator(parent.preferredAddressType())));
        return reusable;
    }

    /**
     * Completes {@code promise} with {@code result}, each HTTPS record becoming a {@link Endpoint} with the
     * addresses of its target: {@code originAddresses} for {@code hostname} if not {@code null}, otherwise resolved
     * once per distinct target, in parallel. Any other target whose records all have a hint of the
     * {@code hintedFamily}, if not {@code null}, is resolved without being waited for, so that its addresses are
     * cached, and its endpoints only have their hints
     * (<a href="https://www.rfc-editor.org/rfc/rfc9460.html#section-7.3">RFC 9460, Section 7.3</a>). An endpoint
     * expires when its HTTPS records, expiring at {@code httpsExpiration}, or the addresses of its target do.
     */
    private void resolveTargetAddresses(String hostname, String httpsName, final List<DnsResolveResult> result,
                                        final long httpsExpiration, List<IpAddress> originAddresses,
                                        DnsRecordType hintedFamily, final Promise<List<DnsResolveResult>> promise) {
        final Map<String, List<IpAddress>> addresses = new HashMap<String, List<IpAddress>>();
        final Set<String> targets = new LinkedHashSet<String>();
        final Set<String> hintedTargets = new LinkedHashSet<String>();
        for (DnsResolveResult r : result) {
            if (r instanceof HttpsRecord) {
                final DnsSvcbRecord record = ((HttpsRecord) r).record();
                final String target = normalize(Endpoint.targetName(record));
                if (originAddresses != null && target.equals(normalize(hostname))) {
                    addresses.put(target, originAddresses);
                } else if (!httpsName.equals(hostname) && target.equals(normalize(httpsName))) {
                    continue;
                } else if (hasHint(record, hintedFamily)) {
                    hintedTargets.add(target);
                } else {
                    targets.add(target);
                }
            }
        }
        hintedTargets.removeAll(targets);
        for (String target : hintedTargets) {
            resolveTarget(target);
        }
        if (targets.isEmpty()) {
            promise.trySuccess(withTargetAddresses(result, addresses, httpsExpiration));
            return;
        }
        final int[] remaining = { targets.size() };
        for (final String target : targets) {
            resolveTarget(target).addListener((FutureListener<List<DnsResolveResult>>) future -> {
                if (future.isSuccess()) {
                    addresses.put(target, addresses(future.getNow()));
                }
                if (--remaining[0] == 0) {
                    promise.trySuccess(withTargetAddresses(result, addresses, httpsExpiration));
                }
            });
        }
    }

    /**
     * Resolves the addresses of {@code target} like {@link DnsNameResolver#resolveAll(String)} does, with the
     * expiration time of their cache entry.
     */
    private Promise<List<DnsResolveResult>> resolveTarget(String target) {
        final Promise<List<DnsResolveResult>> promise = parent.executor().newPromise();
        final List<InetAddress> addresses = parent.resolveAllWithoutDns(target);
        if (addresses == null) {
            resolve(DnsNameResolver.hostname(target), 443, EMPTY_ADDITIONALS, false, promise);
            return promise;
        }
        final List<DnsResolveResult> results = new ArrayList<DnsResolveResult>(addresses.size());
        for (InetAddress address : addresses) {
            // Resolved without DNS, so without expiration.
            results.add(new IpAddress(target, address, Long.MAX_VALUE));
        }
        return promise.setSuccess(results);
    }

    private static boolean hasHint(DnsSvcbRecord record, DnsRecordType family) {
        for (SvcParam param : record.params()) {
            if (family == DnsRecordType.A ? param instanceof SvcParam.Ipv4Hint :
                    family == DnsRecordType.AAAA && param instanceof SvcParam.Ipv6Hint) {
                return true;
            }
        }
        return false;
    }

    private static List<DnsResolveResult> withTargetAddresses(List<DnsResolveResult> result,
                                                              Map<String, List<IpAddress>> addresses,
                                                              long httpsExpiration) {
        final List<DnsResolveResult> filled = new ArrayList<DnsResolveResult>(result.size());
        for (DnsResolveResult r : result) {
            if (r instanceof HttpsRecord) {
                final DnsSvcbRecord record = ((HttpsRecord) r).record();
                final List<IpAddress> targetAddresses =
                        addresses.get(normalize(Endpoint.targetName(record)));
                final List<InetAddress> resolved = new ArrayList<InetAddress>();
                long expiration = httpsExpiration;
                if (targetAddresses != null) {
                    for (IpAddress address : targetAddresses) {
                        resolved.add(address.address());
                        expiration = minDeadline(expiration, address.deadlineNanos());
                    }
                }
                filled.add(Endpoint.of(record, resolved, expiration));
            } else {
                filled.add(r);
            }
        }
        return filled;
    }

    private static List<IpAddress> addresses(List<DnsResolveResult> results) {
        final List<IpAddress> addresses = new ArrayList<IpAddress>();
        for (DnsResolveResult result : results) {
            if (result instanceof IpAddress) {
                addresses.add((IpAddress) result);
            }
        }
        return addresses;
    }

    /**
     * Returns the earliest of two deadlines, ignoring an unknown one ({@code -1}).
     */
    static long minDeadline(long expiration, long otherExpiration) {
        return expiration == -1 ? otherExpiration : otherExpiration == -1 ? expiration :
                Math.min(expiration, otherExpiration);
    }

    /**
     * Returns the result: the HTTPS {@code records} compatible with the supported keys, then the {@code addresses}
     * per family, in the order of the address types.
     */
    private List<DnsResolveResult> result(List<DnsSvcbRecord> records, List<IpAddress> addresses) {
        final List<DnsResolveResult> result = new ArrayList<DnsResolveResult>(records.size() + addresses.size());
        for (DnsSvcbRecord record : records) {
            if (isCompatible(record, supportedKeys)) {
                result.add(new HttpsRecord(record));
            }
        }
        for (DnsRecordType type : addressRecordTypes) {
            for (IpAddress address : addresses) {
                if (recordType(address.address()) == type) {
                    result.add(address);
                }
            }
        }
        return result;
    }

    private static boolean isCompatible(DnsSvcbRecord record, Set<Integer> supportedKeys) {
        for (SvcParam param : record.params()) {
            if (param instanceof SvcParam.Mandatory) {
                for (int key : ((SvcParam.Mandatory) param).keys()) {
                    if (!supportedKeys.contains(key)) {
                        return false;
                    }
                }
            } else if ((param.key() == SvcParam.PORT || param.key() == SvcParam.NO_DEFAULT_ALPN) &&
                    !supportedKeys.contains(param.key())) {
                return false;
            }
        }
        return true;
    }
}
