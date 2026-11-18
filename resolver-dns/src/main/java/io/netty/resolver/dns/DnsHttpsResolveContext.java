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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.DefaultDnsSvcbRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.DnsSvcbRecord;
import io.netty.handler.codec.dns.SvcParam;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DnsResolveResult.HttpsAnswer;
import io.netty.resolver.dns.DnsResolveResult.HttpsRecord;
import io.netty.resolver.dns.DnsResolveResult.IpAddress;
import io.netty.resolver.dns.DnsResolveResult.ServiceUnavailable;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.netty.resolver.dns.DnsAddressDecoder.decodeAddress;
import static io.netty.util.internal.ObjectUtil.checkInRange;
import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * Resolves one part of a name for HTTPS (<a href="https://www.rfc-editor.org/rfc/rfc9460.html">RFC 9460</a>), for a
 * {@link DnsHttpsResolution}: its HTTPS records if the address types are {@code null}, otherwise its A/AAAA records of
 * the address types, queried in parallel. The HTTPS records, following CNAMEs and AliasMode records, are the usable
 * {@link HttpsRecord}s sorted by ascending priority, followed by the {@link HttpsAnswer}; a service
 * declared as not available gives a {@link ServiceUnavailable}. The A/AAAA records are
 * {@link IpAddress}s; with early completion, the resolution completes once it has an address of the family
 * preferred by the address types, without waiting for the other family, whose late answer is still cached.
 */
final class DnsHttpsResolveContext extends DnsResolveContext<DnsResolveResult> {

    /**
     * The default maximum number of AliasMode records followed for one resolution.
     */
    static final int DEFAULT_MAX_ALIASES = 8;

    /**
     * The default maximum number of HTTPS records of an RRset: above it, the RRset is rejected like a malformed one.
     */
    static final int DEFAULT_MAX_HTTPS_RECORDS = 32;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsHttpsResolveContext.class);

    /**
     * The SvcParamKeys needed to use HTTPS records for HTTP/1.1, HTTP/2 and HTTP/3 endpoints.
     */
    static final Set<Integer> DEFAULT_SUPPORTED_KEYS = Collections.unmodifiableSet(new HashSet<Integer>(
            Arrays.asList(SvcParam.MANDATORY, SvcParam.ALPN, SvcParam.NO_DEFAULT_ALPN, SvcParam.PORT,
                    SvcParam.IPV4HINT, SvcParam.IPV6HINT)));

    /**
     * The SvcPriority of the alias endpoint, the lowest one.
     */
    static final int ALIAS_ENDPOINT_PRIORITY = 65535;

    private final String hostname;
    private final int port;
    private final String httpsName;
    private final DnsCache resolveCache;
    private final DnsHttpsResolution resolution;
    private final int maxAliases;
    private final int maxHttpsRecords;
    private final Map<String, Integer> httpsRecordCounts = new HashMap<String, Integer>();
    private final DnsHttpsCache cache;
    private final ResolvedAddressTypes addressTypes;
    private final List<DnsRecordType> addressRecordTypes;
    private final boolean completeEarlyIfPossible;
    private final Set<DnsSvcbRecord> httpsRecords = new LinkedHashSet<DnsSvcbRecord>();
    private final DnsRecordType preferredAddressType;
    private boolean preferredAddressReceived;
    private final Set<String> aliasOwners = new HashSet<String>();
    private final Set<String> visitedNames = new HashSet<String>();
    private int aliases;
    private String finalAliasTarget;
    private boolean aliasLimitReached;
    private boolean serviceUnavailable;
    private CorruptedFrameException malformed;
    private long minTtl = Long.MAX_VALUE;
    private long noDataTtl = -1;

    DnsHttpsResolveContext(DnsNameResolver parent, Channel channel, Promise<?> originalPromise, String hostname,
                           int port, DnsRecord[] additionals, DnsServerAddressStream nameServerAddrs,
                           DnsHttpsResolution resolution, DnsCache resolveCache, int maxAliases,
                           int maxHttpsRecords, DnsHttpsCache cache, ResolvedAddressTypes addressTypes,
                           boolean completeEarlyIfPossible) {
        super(parent, channel, originalPromise, hostname, DnsRecord.CLASS_IN, expectedTypes(addressTypes),
                additionals, nameServerAddrs, resolution.allowedQueries());
        this.resolution = resolution;
        this.hostname = hostname;
        this.port = checkInRange(port, 1, 65535, "port");
        this.resolveCache = resolveCache;
        this.maxAliases = checkPositive(maxAliases, "maxAliases");
        this.maxHttpsRecords = checkPositive(maxHttpsRecords, "maxHttpsRecords");
        this.cache = cache;
        this.addressTypes = addressTypes;
        addressRecordTypes = addressRecordTypes(addressTypes);
        this.completeEarlyIfPossible = completeEarlyIfPossible;
        preferredAddressType = addressRecordTypes.isEmpty() ? null : addressRecordTypes.get(0);
        httpsName = httpsName(hostname, port);
        visitedNames.add(normalize(httpsName));
    }

    /**
     * Returns the name of the HTTPS records of the service on {@code port} of {@code hostname}: {@code hostname} for
     * port 443, otherwise its port prefixed name
     * (<a href="https://www.rfc-editor.org/rfc/rfc9460.html#section-9.1">RFC 9460, Section 9.1</a>).
     */
    static String httpsName(String hostname, int port) {
        return port == 443 ? hostname : "_" + port + "._https." + hostname;
    }

    @Override
    String formatQuestionName(String name, DnsRecordType type) {
        return type == DnsRecordType.HTTPS ? httpsName(name, port) : name;
    }

    /**
     * Returns the record types to query in parallel: HTTPS if {@code addressTypes} is {@code null}, otherwise the
     * address types.
     */
    private static DnsRecordType[] expectedTypes(ResolvedAddressTypes addressTypes) {
        return addressTypes == null ? new DnsRecordType[] { DnsRecordType.HTTPS } :
                addressRecordTypes(addressTypes).toArray(new DnsRecordType[0]);
    }

    /**
     * Returns the A/AAAA record types of {@code addressTypes} in their order of preference, none if {@code null}.
     */
    static List<DnsRecordType> addressRecordTypes(ResolvedAddressTypes addressTypes) {
        if (addressTypes == null) {
            return Collections.emptyList();
        }
        switch (addressTypes) {
            case IPV4_ONLY:
                return Collections.singletonList(DnsRecordType.A);
            case IPV6_ONLY:
                return Collections.singletonList(DnsRecordType.AAAA);
            case IPV4_PREFERRED:
                return Arrays.asList(DnsRecordType.A, DnsRecordType.AAAA);
            default:
                return Arrays.asList(DnsRecordType.AAAA, DnsRecordType.A);
        }
    }

    static DnsRecordType recordType(InetAddress address) {
        return address instanceof Inet4Address ? DnsRecordType.A : DnsRecordType.AAAA;
    }

    /**
     * Returns {@code true} if the {@code addressRecordTypes} cover all the address types {@code parent} queries.
     */
    static boolean coversResolverAddressTypes(DnsNameResolver parent, List<DnsRecordType> addressRecordTypes) {
        for (DnsRecordType type : parent.resolveRecordTypes()) {
            if (!addressRecordTypes.contains(type)) {
                return false;
            }
        }
        return true;
    }

    @Override
    DnsResolveContext<DnsResolveResult> newResolverContext(DnsNameResolver parent, Channel channel,
                                                           Promise<?> originalPromise, String hostname,
                                                           int dnsClass, DnsRecordType[] expectedTypes,
                                                           DnsRecord[] additionals,
                                                           DnsServerAddressStream nameServerAddrs,
                                                           int allowedQueries) {
        return new DnsHttpsResolveContext(parent, channel, originalPromise, hostname, port, additionals,
                nameServerAddrs, resolution, resolveCache, maxAliases, maxHttpsRecords, cache, addressTypes,
                completeEarlyIfPossible);
    }

    /**
     * Returns the queries left to the whole resolution, shared with the other contexts of its
     * {@link DnsHttpsResolution}.
     */
    @Override
    int allowedQueries() {
        return resolution.allowedQueries();
    }

    @Override
    void consumeQuery() {
        resolution.consumeQuery();
    }

    /**
     * Completes {@code promise} from the caches and returns {@code true} if they have the part to resolve: the HTTPS
     * records of the service on {@code port} of {@code hostname} from {@code cache} if {@code addressTypes} is
     * {@code null}, otherwise its addresses of the address types from {@code resolveCache}.
     */
    static boolean resolveCached(DnsCache resolveCache, DnsHttpsCache cache, String hostname, int port,
                                 DnsRecord[] additionals, ResolvedAddressTypes addressTypes,
                                 Promise<List<DnsResolveResult>> promise) {
        final List<DnsResolveResult> result = new ArrayList<DnsResolveResult>();
        if (addressTypes == null) {
            final DnsHttpsCacheEntry entry = cache.get(httpsName(hostname, port), additionals);
            if (entry == null) {
                return false;
            }
            if (entry.cause() != null) {
                promise.tryFailure(entry.cause());
                return true;
            }
            for (DnsSvcbRecord record : entry.records()) {
                result.add(new HttpsRecord(record));
            }
            result.add(new HttpsAnswer(entry.deadlineNanos()));
            promise.trySuccess(result);
            return true;
        }
        final List<? extends DnsCacheEntry> entries = resolveCache.get(hostname, additionals);
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        if (entries.get(0).cause() != null) {
            promise.tryFailure(entries.get(0).cause());
            return true;
        }
        final List<DnsRecordType> addressRecordTypes = addressRecordTypes(addressTypes);
        for (DnsCacheEntry entry : entries) {
            if (addressRecordTypes.contains(recordType(entry.address()))) {
                result.add(new IpAddress(hostname, entry.address(), entry.deadlineNanos()));
            }
        }
        if (result.isEmpty()) {
            return false;
        }
        promise.trySuccess(result);
        return true;
    }

    @Override
    DnsResolveResult convertRecord(DnsRecord record, String hostname, DnsRecord[] additionals,
                                   EventLoop eventLoop) {
        final DnsRecordType type = record.type();
        if (type == DnsRecordType.A || type == DnsRecordType.AAAA) {
            final InetAddress address = decodeAddress(record, hostname, parent.isDecodeIdn());
            if (address == null) {
                return null;
            }
            final DnsCacheEntry entry = resolveCache.cache(hostname, additionals, address, record.timeToLive(),
                    parent.executor());
            return new IpAddress(hostname, address, entry != null ? entry.deadlineNanos() : -1);
        }
        if (type != DnsRecordType.HTTPS || !(record instanceof DnsRawRecord)) {
            return null;
        }
        if (!withinMaxHttpsRecords(record.name())) {
            return null;
        }
        final DefaultDnsSvcbRecord svcbRecord;
        try {
            svcbRecord = new DefaultDnsSvcbRecord((DnsRawRecord) record);
        } catch (CorruptedFrameException e) {
            if (malformed == null) {
                malformed = e;
            }
            return null;
        }
        if (svcbRecord.priority() != 0 && !svcbRecord.isSelfConsistent()) {
            return null;
        }
        minTtl = Math.min(minTtl, record.timeToLive());
        return new HttpsRecord(svcbRecord);
    }

    /**
     * Counts an HTTPS record of the RRset of {@code owner} and returns {@code true} if the RRset has at most
     * {@code maxHttpsRecords}, otherwise rejects it like a malformed one, without decoding its other records.
     */
    private boolean withinMaxHttpsRecords(String owner) {
        final String name = normalize(owner);
        final Integer count = httpsRecordCounts.get(name);
        final int records = count == null ? 1 : count + 1;
        httpsRecordCounts.put(name, records);
        if (records <= maxHttpsRecords) {
            return true;
        }
        if (malformed == null) {
            logger.debug("{} Rejecting the HTTPS records of {}: more than {}", channel(), owner, maxHttpsRecords);
            malformed = new CorruptedFrameException("more than " + maxHttpsRecords + " HTTPS records for " + owner);
        }
        return false;
    }

    @Override
    String followUpName(DnsQuestion question, DnsResolveResult converted) {
        if (!(converted instanceof HttpsRecord)) {
            return null;
        }
        final DnsSvcbRecord record = ((HttpsRecord) converted).record();
        if (record.priority() != 0 || !aliasOwners.add(normalize(record.name()))) {
            return null;
        }
        final String target = record.targetName();
        if (".".equals(target)) {
            serviceUnavailable = true;
            return null;
        }
        if (++aliases > maxAliases || !visitedNames.add(normalize(target))) {
            aliasLimitReached = true;
            return null;
        }
        finalAliasTarget = target;
        return target;
    }

    /**
     * Completes the resolution of the HTTPS records: the HTTPS RRset is checked as a whole, a malformed one being
     * rejected (<a href="https://www.rfc-editor.org/rfc/rfc9460.html#section-2.2">RFC 9460, Section 2.2</a>), and the
      * usable records are cached. The resolution of the addresses completes with its results, without duplicates.
     */
    @Override
    boolean beforeFinishResolve(Promise<List<DnsResolveResult>> promise, List<DnsResolveResult> results) {
        if (addressTypes != null) {
            if (results.isEmpty()) {
                return false;
            }
            promise.trySuccess(new ArrayList<DnsResolveResult>(new LinkedHashSet<DnsResolveResult>(results)));
            return true;
        }
        if (malformed != null) {
            promise.tryFailure(new MalformedHttpsRecordsException(hostname, malformed));
            return true;
        }
        if (serviceUnavailable) {
            promise.trySuccess(Collections.<DnsResolveResult>singletonList(new ServiceUnavailable(hostname)));
            return true;
        }
        final List<DnsSvcbRecord> records = usableHttpsRecords(httpsRecords);
        final long expiration = cacheHttpsRecords(records);
        final List<DnsResolveResult> result = new ArrayList<DnsResolveResult>(records.size() + 1);
        for (DnsSvcbRecord record : records) {
            result.add(new HttpsRecord(record));
        }
        if (expiration != -1) {
            result.add(new HttpsAnswer(expiration));
        }
        if (!result.isEmpty()) {
            promise.trySuccess(result);
        } else if (aliasLimitReached) {
            promise.tryFailure(aliasLimitException());
        } else {
            return false;
        }
        return true;
    }

    private UnknownHostException aliasLimitException() {
        return new UnknownHostException("Failed to resolve '" + hostname + "' [HTTPS]: AliasMode limit of " +
                maxAliases + " reached or loop");
    }

    @Override
    List<DnsResolveResult> filterResults(List<DnsResolveResult> unfiltered) {
        return unfiltered;
    }

    /**
     * Returns {@code true} to complete early once an address of the preferred family is received.
     */
    @Override
    boolean isCompleteEarly(DnsResolveResult resolved) {
        if (resolved instanceof IpAddress &&
                preferredAddressType == recordType(((IpAddress) resolved).address())) {
            preferredAddressReceived = true;
        }
        return completeEarlyIfPossible && preferredAddressReceived;
    }

    @Override
    DnsCache resolveCache() {
        return resolveCache;
    }

    /**
     * Allows duplicates in the results, which this context removes itself in linear time.
     */
    @Override
    boolean isDuplicateAllowed() {
        return true;
    }

    /**
     * Collects the HTTPS records. The addresses are cached like {@link DnsAddressResolveContext} does, but by
     * {@link #convertRecord}, so that their result has the expiration time of their cache entry.
     */
    @Override
    void cache(String hostname, DnsRecord[] additionals, DnsRecord result, DnsResolveResult convertedResult) {
        if (convertedResult instanceof HttpsRecord) {
            httpsRecords.add(((HttpsRecord) convertedResult).record());
        }
    }

    /**
     * Remembers the negative TTL of an HTTPS answer without HTTPS records.
     */
    @Override
    boolean afterResponseRecords(DnsQuestion question, DnsResponse response) {
        if (question.type() == DnsRecordType.HTTPS && !hasAnswer(response, DnsRecordType.HTTPS)) {
            final long ttl = negativeTtl(response);
            if (ttl >= 0) {
                noDataTtl = noDataTtl < 0 ? ttl : Math.min(noDataTtl, ttl);
            }
        }
        return false;
    }

    /**
     * Caches the usable HTTPS {@code records}, or the absence of HTTPS records with the negative TTL. Returns the
     * deadline of the cached answer, or {@code -1} if none or unknown.
     */
    private long cacheHttpsRecords(List<DnsSvcbRecord> records) {
        if (!records.isEmpty()) {
            return cache.cache(httpsName, additionals, records, minTtl, parent.executor()).deadlineNanos();
        }
        if (httpsRecords.isEmpty() && noDataTtl >= 0) {
            return cache.cache(httpsName, additionals, Collections.<DnsSvcbRecord>emptyList(), noDataTtl,
                    parent.executor()).deadlineNanos();
        }
        return -1;
    }

    private static boolean hasAnswer(DnsResponse response, DnsRecordType type) {
        final int count = response.count(DnsSection.ANSWER);
        for (int i = 0; i < count; i++) {
            if (response.recordAt(DnsSection.ANSWER, i).type() == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the minimum of the TTL and the MINIMUM field of the SOA of {@code response}, or {@code -1} without SOA
     * (<a href="https://www.rfc-editor.org/rfc/rfc2308.html#section-5">RFC 2308, Section 5</a>).
     */
    private static long negativeTtl(DnsResponse response) {
        final int count = response.count(DnsSection.AUTHORITY);
        for (int i = 0; i < count; i++) {
            final DnsRecord record = response.recordAt(DnsSection.AUTHORITY, i);
            if (record.type() == DnsRecordType.SOA && record instanceof DnsRawRecord) {
                final ByteBuf content = ((DnsRawRecord) record).content();
                // MNAME and RNAME, at least one byte each, then SERIAL, REFRESH, RETRY, EXPIRE and MINIMUM.
                if (content.readableBytes() < 22) {
                    return -1;
                }
                return Math.min(record.timeToLive(), content.getUnsignedInt(content.writerIndex() - 4));
            }
        }
        return -1;
    }

    /**
     * Caches the failure for the HTTPS records, or for the addresses if the resolver's address types were all
     * queried.
     */
    @Override
    void cache(String hostname, DnsRecord[] additionals, UnknownHostException cause) {
        if (addressTypes == null) {
            cache.cache(httpsName, additionals, cause, parent.executor());
        } else if (coversResolverAddressTypes(parent, addressRecordTypes)) {
            resolveCache().cache(hostname, additionals, cause, parent.executor());
        }
    }

    /**
     * Returns the HTTPS records to use sorted by priority, before the client compatibility filter, followed by the
     * alias endpoint if an AliasMode record was followed
     * (<a href="https://www.rfc-editor.org/rfc/rfc9460.html#section-3">RFC 9460, Section 3</a>), or none if the
     * AliasMode limit was reached.
     */
    private List<DnsSvcbRecord> usableHttpsRecords(Collection<DnsSvcbRecord> records) {
        if (aliasLimitReached) {
            return Collections.emptyList();
        }
        final List<DnsSvcbRecord> selected = select(records);
        if (finalAliasTarget != null) {
            selected.add(new DefaultDnsSvcbRecord(finalAliasTarget, DnsRecordType.HTTPS, minTtl,
                    ALIAS_ENDPOINT_PRIORITY, ".", Collections.<SvcParam>emptyList()));
        }
        return selected;
    }

    private static List<DnsSvcbRecord> select(Collection<DnsSvcbRecord> records) {
        final Set<String> aliased = new HashSet<String>();
        for (DnsSvcbRecord record : records) {
            if (record.priority() == 0) {
                aliased.add(normalize(record.name()));
            }
        }
        final List<DnsSvcbRecord> selected = new ArrayList<DnsSvcbRecord>(records.size());
        for (DnsSvcbRecord record : records) {
            if (!aliased.contains(normalize(record.name()))) {
                selected.add(record);
            }
        }
        selected.sort(Comparator.comparingInt(DnsSvcbRecord::priority));
        return selected;
    }

    static String normalize(String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return StringUtil.endsWith(lower, '.') ? lower : lower + '.';
    }

    /**
     * The resolution received a malformed HTTPS record, so the whole RRset is rejected
     * (<a href="https://www.rfc-editor.org/rfc/rfc9460.html#section-2.2">RFC 9460, Section 2.2</a>).
     */
    static final class MalformedHttpsRecordsException extends UnknownHostException {
        private static final long serialVersionUID = 3437870727453049520L;

        MalformedHttpsRecordsException(String hostname, CorruptedFrameException cause) {
            super("Failed to resolve '" + hostname + "' [HTTPS]: malformed record");
            initCause(cause);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
