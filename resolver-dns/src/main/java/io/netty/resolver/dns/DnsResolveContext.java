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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThrowableUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.netty.resolver.dns.DnsAddressDecoder.decodeAddress;
import static io.netty.resolver.dns.DnsNameResolver.trySuccess;
import static java.lang.Math.min;
import static java.util.Collections.unmodifiableList;

abstract class DnsResolveContext<T> {

    private static final FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>> RELEASE_RESPONSE =
            new FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>() {
                @Override
                public void operationComplete(Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future) {
                    if (future.isSuccess()) {
                        future.getNow().release();
                    }
                }
            };
    private static final RuntimeException NXDOMAIN_QUERY_FAILED_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new RuntimeException("No answer found and NXDOMAIN response code returned"),
            DnsResolveContext.class,
            "onResponse(..)");
    private static final RuntimeException CNAME_NOT_FOUND_QUERY_FAILED_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new RuntimeException("No matching CNAME record found"),
            DnsResolveContext.class,
            "onResponseCNAME(..)");
    private static final RuntimeException NO_MATCHING_RECORD_QUERY_FAILED_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new RuntimeException("No matching record type found"),
            DnsResolveContext.class,
            "onResponseAorAAAA(..)");
    private static final RuntimeException UNRECOGNIZED_TYPE_QUERY_FAILED_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new RuntimeException("Response type was unrecognized"),
            DnsResolveContext.class,
            "onResponse(..)");
    private static final RuntimeException NAME_SERVERS_EXHAUSTED_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new RuntimeException("No name servers returned an answer"),
            DnsResolveContext.class,
            "tryToFinishResolve(..)");

    final DnsNameResolver parent;
    private final DnsServerAddressStream nameServerAddrs;
    private final String hostname;
    private final int dnsClass;
    private final DnsRecordType[] expectedTypes;
    private final int maxAllowedQueries;
    private final DnsRecord[] additionals;

    private final Set<Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> queriesInProgress =
            Collections.newSetFromMap(
                    new IdentityHashMap<Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>, Boolean>());

    private List<T> finalResult;
    private int allowedQueries;
    private boolean triedCNAME;

    DnsResolveContext(DnsNameResolver parent,
                      String hostname, int dnsClass, DnsRecordType[] expectedTypes,
                      DnsRecord[] additionals, DnsServerAddressStream nameServerAddrs) {

        assert expectedTypes.length > 0;

        this.parent = parent;
        this.hostname = hostname;
        this.dnsClass = dnsClass;
        this.expectedTypes = expectedTypes;
        this.additionals = additionals;

        this.nameServerAddrs = ObjectUtil.checkNotNull(nameServerAddrs, "nameServerAddrs");
        maxAllowedQueries = parent.maxQueriesPerResolve();
        allowedQueries = maxAllowedQueries;
    }

    /**
     * Creates a new context with the given parameters.
     */
    abstract DnsResolveContext<T> newResolverContext(DnsNameResolver parent, String hostname,
                                                     int dnsClass, DnsRecordType[] expectedTypes,
                                                     DnsRecord[] additionals,
                                                     DnsServerAddressStream nameServerAddrs);

    /**
     * Converts the given {@link DnsRecord} into {@code T}.
     */
    abstract T convertRecord(DnsRecord record, String hostname, DnsRecord[] additionals, EventLoop eventLoop);

    /**
     * Returns {@code true} if the given list contains any expected records. {@code finalResult} always contains
     * at least one element.
     */
    abstract boolean containsExpectedResult(List<T> finalResult);

    /**
     * Returns a filtered list of results which should be the final result of DNS resolution. This must take into
     * account JDK semantics such as {@link NetUtil#isIpV6AddressesPreferred()}.
     */
    abstract List<T> filterResults(List<T> unfiltered);

    /**
     * Caches a successful resolution.
     */
    abstract void cache(String hostname, DnsRecord[] additionals,
                        DnsRecord result, T convertedResult);

    /**
     * Caches a failed resolution.
     */
    abstract void cache(String hostname, DnsRecord[] additionals,
                        UnknownHostException cause);

    void resolve(final Promise<List<T>> promise) {
        final String[] searchDomains = parent.searchDomains();
        if (searchDomains.length == 0 || parent.ndots() == 0 || StringUtil.endsWith(hostname, '.')) {
            internalResolve(promise);
        } else {
            final boolean startWithoutSearchDomain = hasNDots();
            final String initialHostname = startWithoutSearchDomain ? hostname : hostname + '.' + searchDomains[0];
            final int initialSearchDomainIdx = startWithoutSearchDomain ? 0 : 1;

            doSearchDomainQuery(initialHostname, new FutureListener<List<T>>() {
                private int searchDomainIdx = initialSearchDomainIdx;
                @Override
                public void operationComplete(Future<List<T>> future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause == null) {
                        promise.trySuccess(future.getNow());
                    } else {
                        if (DnsNameResolver.isTransportOrTimeoutError(cause)) {
                            promise.tryFailure(new SearchDomainUnknownHostException(cause, hostname));
                        } else if (searchDomainIdx < searchDomains.length) {
                            doSearchDomainQuery(hostname + '.' + searchDomains[searchDomainIdx++], this);
                        } else if (!startWithoutSearchDomain) {
                            internalResolve(promise);
                        } else {
                            promise.tryFailure(new SearchDomainUnknownHostException(cause, hostname));
                        }
                    }
                }
            });
        }
    }

    private boolean hasNDots() {
        for (int idx = hostname.length() - 1, dots = 0; idx >= 0; idx--) {
            if (hostname.charAt(idx) == '.' && ++dots >= parent.ndots()) {
                return true;
            }
        }
        return false;
    }

    private static final class SearchDomainUnknownHostException extends UnknownHostException {
        private static final long serialVersionUID = -8573510133644997085L;

        SearchDomainUnknownHostException(Throwable cause, String originalHostname) {
            super("Search domain query failed. Original hostname: '" + originalHostname + "' " + cause.getMessage());
            setStackTrace(cause.getStackTrace());

            // Preserve the cause
            initCause(cause.getCause());
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private void doSearchDomainQuery(String hostname, FutureListener<List<T>> listener) {
        DnsResolveContext<T> nextContext = newResolverContext(parent, hostname, dnsClass, expectedTypes,
                                                              additionals, nameServerAddrs);
        Promise<List<T>> nextPromise = parent.executor().newPromise();
        nextContext.internalResolve(nextPromise);
        nextPromise.addListener(listener);
    }

    private void internalResolve(Promise<List<T>> promise) {
        DnsServerAddressStream nameServerAddressStream = getNameServers(hostname);

        final int end = expectedTypes.length - 1;
        for (int i = 0; i < end; ++i) {
            if (!query(hostname, expectedTypes[i], nameServerAddressStream.duplicate(), promise, null)) {
                return;
            }
        }
        query(hostname, expectedTypes[end], nameServerAddressStream, promise, null);
    }

    /**
     * Add an authoritative nameserver to the cache if its not a root server.
     */
    private void addNameServerToCache(
            AuthoritativeNameServer name, InetAddress resolved, long ttl) {
        if (!name.isRootServer()) {
            // Cache NS record if not for a root server as we should never cache for root servers.
            parent.authoritativeDnsServerCache().cache(name.domainName(),
                    additionals, resolved, ttl, parent.ch.eventLoop());
        }
    }

    /**
     * Returns the {@link DnsServerAddressStream} that was cached for the given hostname or {@code null} if non
     *  could be found.
     */
    private DnsServerAddressStream getNameServersFromCache(String hostname) {
        int len = hostname.length();

        if (len == 0) {
            // We never cache for root servers.
            return null;
        }

        // We always store in the cache with a trailing '.'.
        if (hostname.charAt(len - 1) != '.') {
            hostname += ".";
        }

        int idx = hostname.indexOf('.');
        if (idx == hostname.length() - 1) {
            // We are not interested in handling '.' as we should never serve the root servers from cache.
            return null;
        }

        // We start from the closed match and then move down.
        for (;;) {
            // Skip '.' as well.
            hostname = hostname.substring(idx + 1);

            int idx2 = hostname.indexOf('.');
            if (idx2 <= 0 || idx2 == hostname.length() - 1) {
                // We are not interested in handling '.TLD.' as we should never serve the root servers from cache.
                return null;
            }
            idx = idx2;

            List<? extends DnsCacheEntry> entries = parent.authoritativeDnsServerCache().get(hostname, additionals);
            if (entries != null && !entries.isEmpty()) {
                return DnsServerAddresses.sequential(new DnsCacheIterable(entries)).stream();
            }
        }
    }

    private final class DnsCacheIterable implements Iterable<InetSocketAddress> {
        private final List<? extends DnsCacheEntry> entries;

        DnsCacheIterable(List<? extends DnsCacheEntry> entries) {
            this.entries = entries;
        }

        @Override
        public Iterator<InetSocketAddress> iterator() {
            return new Iterator<InetSocketAddress>() {
                Iterator<? extends DnsCacheEntry> entryIterator = entries.iterator();

                @Override
                public boolean hasNext() {
                    return entryIterator.hasNext();
                }

                @Override
                public InetSocketAddress next() {
                    InetAddress address = entryIterator.next().address();
                    return new InetSocketAddress(address, parent.dnsRedirectPort(address));
                }

                @Override
                public void remove() {
                    entryIterator.remove();
                }
            };
        }
    }

    private void query(final DnsServerAddressStream nameServerAddrStream, final int nameServerAddrStreamIndex,
                       final DnsQuestion question,
                       final Promise<List<T>> promise, Throwable cause) {
        query(nameServerAddrStream, nameServerAddrStreamIndex, question,
                parent.dnsQueryLifecycleObserverFactory().newDnsQueryLifecycleObserver(question), promise, cause);
    }

    private void query(final DnsServerAddressStream nameServerAddrStream,
                       final int nameServerAddrStreamIndex,
                       final DnsQuestion question,
                       final DnsQueryLifecycleObserver queryLifecycleObserver,
                       final Promise<List<T>> promise,
                       final Throwable cause) {
        if (nameServerAddrStreamIndex >= nameServerAddrStream.size() || allowedQueries == 0 || promise.isCancelled()) {
            tryToFinishResolve(nameServerAddrStream, nameServerAddrStreamIndex, question, queryLifecycleObserver,
                               promise, cause);
            return;
        }

        --allowedQueries;
        final InetSocketAddress nameServerAddr = nameServerAddrStream.next();
        final ChannelPromise writePromise = parent.ch.newPromise();
        final Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> f = parent.query0(
                nameServerAddr, question, additionals, writePromise,
                parent.ch.eventLoop().<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>>newPromise());
        queriesInProgress.add(f);

        queryLifecycleObserver.queryWritten(nameServerAddr, writePromise);

        f.addListener(new FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>() {
            @Override
            public void operationComplete(Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future) {
                queriesInProgress.remove(future);

                if (promise.isDone() || future.isCancelled()) {
                    queryLifecycleObserver.queryCancelled(allowedQueries);

                    // Check if we need to release the envelope itself. If the query was cancelled the getNow() will
                    // return null as well as the Future will be failed with a CancellationException.
                    AddressedEnvelope<DnsResponse, InetSocketAddress> result = future.getNow();
                    if (result != null) {
                        result.release();
                    }
                    return;
                }

                final Throwable queryCause = future.cause();
                try {
                    if (queryCause == null) {
                        onResponse(nameServerAddrStream, nameServerAddrStreamIndex, question, future.getNow(),
                                   queryLifecycleObserver, promise);
                    } else {
                        // Server did not respond or I/O error occurred; try again.
                        queryLifecycleObserver.queryFailed(queryCause);
                        query(nameServerAddrStream, nameServerAddrStreamIndex + 1, question, promise, queryCause);
                    }
                } finally {
                    tryToFinishResolve(nameServerAddrStream, nameServerAddrStreamIndex, question,
                                       // queryLifecycleObserver has already been terminated at this point so we must
                                       // not allow it to be terminated again by tryToFinishResolve.
                                       NoopDnsQueryLifecycleObserver.INSTANCE,
                                       promise, queryCause);
                }
            }
        });
    }

    void onResponse(final DnsServerAddressStream nameServerAddrStream, final int nameServerAddrStreamIndex,
                    final DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope,
                    final DnsQueryLifecycleObserver queryLifecycleObserver,
                    Promise<List<T>> promise) {
        try {
            final DnsResponse res = envelope.content();
            final DnsResponseCode code = res.code();
            if (code == DnsResponseCode.NOERROR) {
                if (handleRedirect(question, envelope, queryLifecycleObserver, promise)) {
                    // Was a redirect so return here as everything else is handled in handleRedirect(...)
                    return;
                }
                final DnsRecordType type = question.type();

                if (type == DnsRecordType.CNAME) {
                    onResponseCNAME(question, envelope, queryLifecycleObserver, promise);
                    return;
                }

                for (DnsRecordType expectedType : expectedTypes) {
                    if (type == expectedType) {
                        onExpectedResponse(question, envelope, queryLifecycleObserver, promise);
                        return;
                    }
                }

                queryLifecycleObserver.queryFailed(UNRECOGNIZED_TYPE_QUERY_FAILED_EXCEPTION);
                return;
            }

            // Retry with the next server if the server did not tell us that the domain does not exist.
            if (code != DnsResponseCode.NXDOMAIN) {
                query(nameServerAddrStream, nameServerAddrStreamIndex + 1, question,
                      queryLifecycleObserver.queryNoAnswer(code), promise, null);
            } else {
                queryLifecycleObserver.queryFailed(NXDOMAIN_QUERY_FAILED_EXCEPTION);
            }
        } finally {
            ReferenceCountUtil.safeRelease(envelope);
        }
    }

    /**
     * Handles a redirect answer if needed and returns {@code true} if a redirect query has been made.
     */
    private boolean handleRedirect(
            DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope,
            final DnsQueryLifecycleObserver queryLifecycleObserver, Promise<List<T>> promise) {
        final DnsResponse res = envelope.content();

        // Check if we have answers, if not this may be an non authority NS and so redirects must be handled.
        if (res.count(DnsSection.ANSWER) == 0) {
            AuthoritativeNameServerList serverNames = extractAuthoritativeNameServers(question.name(), res);

            if (serverNames != null) {
                List<InetSocketAddress> nameServers = new ArrayList<InetSocketAddress>(serverNames.size());
                int additionalCount = res.count(DnsSection.ADDITIONAL);

                for (int i = 0; i < additionalCount; i++) {
                    final DnsRecord r = res.recordAt(DnsSection.ADDITIONAL, i);

                    if (r.type() == DnsRecordType.A && !parent.supportsARecords() ||
                        r.type() == DnsRecordType.AAAA && !parent.supportsAAAARecords()) {
                        continue;
                    }

                    final String recordName = r.name();
                    final AuthoritativeNameServer authoritativeNameServer = serverNames.remove(recordName);

                    if (authoritativeNameServer == null) {
                        // Not a server we are interested in.
                        continue;
                    }

                    InetAddress resolved = decodeAddress(r, recordName, parent.isDecodeIdn());
                    if (resolved == null) {
                        // Could not parse it, move to the next.
                        continue;
                    }

                    nameServers.add(new InetSocketAddress(resolved, parent.dnsRedirectPort(resolved)));
                    addNameServerToCache(authoritativeNameServer, resolved, r.timeToLive());
                }

                if (!nameServers.isEmpty()) {
                    query(parent.uncachedRedirectDnsServerStream(nameServers), 0, question,
                          queryLifecycleObserver.queryRedirected(unmodifiableList(nameServers)), promise, null);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the {@code {@link AuthoritativeNameServerList} which were included in {@link DnsSection#AUTHORITY}
     * or {@code null} if non are found.
     */
    private static AuthoritativeNameServerList extractAuthoritativeNameServers(String questionName, DnsResponse res) {
        int authorityCount = res.count(DnsSection.AUTHORITY);
        if (authorityCount == 0) {
            return null;
        }

        AuthoritativeNameServerList serverNames = new AuthoritativeNameServerList(questionName);
        for (int i = 0; i < authorityCount; i++) {
            serverNames.add(res.recordAt(DnsSection.AUTHORITY, i));
        }
        return serverNames;
    }

    private void onExpectedResponse(
            DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope,
            final DnsQueryLifecycleObserver queryLifecycleObserver, Promise<List<T>> promise) {

        // We often get a bunch of CNAMES as well when we asked for A/AAAA.
        final DnsResponse response = envelope.content();
        final Map<String, String> cnames = buildAliasMap(response);
        final int answerCount = response.count(DnsSection.ANSWER);

        boolean found = false;
        for (int i = 0; i < answerCount; i ++) {
            final DnsRecord r = response.recordAt(DnsSection.ANSWER, i);
            final DnsRecordType type = r.type();
            boolean matches = false;
            for (DnsRecordType expectedType : expectedTypes) {
                if (type == expectedType) {
                    matches = true;
                    break;
                }
            }

            if (!matches) {
                continue;
            }

            final String questionName = question.name().toLowerCase(Locale.US);
            final String recordName = r.name().toLowerCase(Locale.US);

            // Make sure the record is for the questioned domain.
            if (!recordName.equals(questionName)) {
                // Even if the record's name is not exactly same, it might be an alias defined in the CNAME records.
                String resolved = questionName;
                do {
                    resolved = cnames.get(resolved);
                    if (recordName.equals(resolved)) {
                        break;
                    }
                } while (resolved != null);

                if (resolved == null) {
                    continue;
                }
            }

            final T converted = convertRecord(r, hostname, additionals, parent.ch.eventLoop());
            if (converted == null) {
                continue;
            }

            if (finalResult == null) {
                finalResult = new ArrayList<T>(8);
            }
            finalResult.add(converted);

            cache(hostname, additionals, r, converted);
            found = true;

            // Note that we do not break from the loop here, so we decode/cache all A/AAAA records.
        }

        if (found) {
            queryLifecycleObserver.querySucceed();
            return;
        }

        if (cnames.isEmpty()) {
            queryLifecycleObserver.queryFailed(NO_MATCHING_RECORD_QUERY_FAILED_EXCEPTION);
        } else {
            // We got only CNAME, not one of expectedTypes.
            onResponseCNAME(question, cnames, queryLifecycleObserver, promise);
        }
    }

    private void onResponseCNAME(DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope,
                                 final DnsQueryLifecycleObserver queryLifecycleObserver, Promise<List<T>> promise) {
        onResponseCNAME(question, buildAliasMap(envelope.content()), queryLifecycleObserver, promise);
    }

    private void onResponseCNAME(
            DnsQuestion question, Map<String, String> cnames,
            final DnsQueryLifecycleObserver queryLifecycleObserver,
            Promise<List<T>> promise) {

        // Resolve the host name in the question into the real host name.
        String resolved = question.name().toLowerCase(Locale.US);
        boolean found = false;
        while (!cnames.isEmpty()) { // Do not attempt to call Map.remove() when the Map is empty
                                    // because it can be Collections.emptyMap()
                                    // whose remove() throws a UnsupportedOperationException.
            final String next = cnames.remove(resolved);
            if (next != null) {
                found = true;
                resolved = next;
            } else {
                break;
            }
        }

        if (found) {
            followCname(question, resolved, queryLifecycleObserver, promise);
        } else {
            queryLifecycleObserver.queryFailed(CNAME_NOT_FOUND_QUERY_FAILED_EXCEPTION);
        }
    }

    private static Map<String, String> buildAliasMap(DnsResponse response) {
        final int answerCount = response.count(DnsSection.ANSWER);
        Map<String, String> cnames = null;
        for (int i = 0; i < answerCount; i ++) {
            final DnsRecord r = response.recordAt(DnsSection.ANSWER, i);
            final DnsRecordType type = r.type();
            if (type != DnsRecordType.CNAME) {
                continue;
            }

            if (!(r instanceof DnsRawRecord)) {
                continue;
            }

            final ByteBuf recordContent = ((ByteBufHolder) r).content();
            final String domainName = decodeDomainName(recordContent);
            if (domainName == null) {
                continue;
            }

            if (cnames == null) {
                cnames = new HashMap<String, String>(min(8, answerCount));
            }

            cnames.put(r.name().toLowerCase(Locale.US), domainName.toLowerCase(Locale.US));
        }

        return cnames != null? cnames : Collections.<String, String>emptyMap();
    }

    void tryToFinishResolve(final DnsServerAddressStream nameServerAddrStream,
                            final int nameServerAddrStreamIndex,
                            final DnsQuestion question,
                            final DnsQueryLifecycleObserver queryLifecycleObserver,
                            final Promise<List<T>> promise,
                            final Throwable cause) {
        // There are no queries left to try.
        if (!queriesInProgress.isEmpty()) {
            queryLifecycleObserver.queryCancelled(allowedQueries);

            // There are still some queries we did not receive responses for.
            if (finalResult != null && containsExpectedResult(finalResult)) {
                // But it's OK to finish the resolution process if we got something expected.
                finishResolve(promise, cause);
            }

            // We did not get an expected result yet, so we can't finish the resolution process.
            return;
        }

        // There are no queries left to try.
        if (finalResult == null) {
            if (nameServerAddrStreamIndex < nameServerAddrStream.size()) {
                if (queryLifecycleObserver == NoopDnsQueryLifecycleObserver.INSTANCE) {
                    // If the queryLifecycleObserver has already been terminated we should create a new one for this
                    // fresh query.
                    query(nameServerAddrStream, nameServerAddrStreamIndex + 1, question, promise, cause);
                } else {
                    query(nameServerAddrStream, nameServerAddrStreamIndex + 1, question, queryLifecycleObserver,
                          promise, cause);
                }
                return;
            }

            queryLifecycleObserver.queryFailed(NAME_SERVERS_EXHAUSTED_EXCEPTION);

            // .. and we could not find any expected records.

            // If cause != null we know this was caused by a timeout / cancel / transport exception. In this case we
            // won't try to resolve the CNAME as we only should do this if we could not get the expected records
            // because they do not exist and the DNS server did probably signal it.
            if (cause == null && !triedCNAME) {
                // As the last resort, try to query CNAME, just in case the name server has it.
                triedCNAME = true;

                query(hostname, DnsRecordType.CNAME, getNameServers(hostname), promise, null);
                return;
            }
        } else {
            queryLifecycleObserver.queryCancelled(allowedQueries);
        }

        // We have at least one resolved record or tried CNAME as the last resort..
        finishResolve(promise, cause);
    }

    private void finishResolve(Promise<List<T>> promise, Throwable cause) {
        if (!queriesInProgress.isEmpty()) {
            // If there are queries in progress, we should cancel it because we already finished the resolution.
            for (Iterator<Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> i = queriesInProgress.iterator();
                 i.hasNext();) {
                Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> f = i.next();
                i.remove();

                if (!f.cancel(false)) {
                    f.addListener(RELEASE_RESPONSE);
                }
            }
        }

        if (finalResult != null) {
            // Found at least one resolved record.
            trySuccess(promise, filterResults(finalResult));
            return;
        }

        // No resolved address found.
        final int tries = maxAllowedQueries - allowedQueries;
        final StringBuilder buf = new StringBuilder(64);

        buf.append("failed to resolve '").append(hostname).append('\'');
        if (tries > 1) {
            if (tries < maxAllowedQueries) {
                buf.append(" after ")
                   .append(tries)
                   .append(" queries ");
            } else {
                buf.append(". Exceeded max queries per resolve ")
                .append(maxAllowedQueries)
                .append(' ');
            }
        }
        final UnknownHostException unknownHostException = new UnknownHostException(buf.toString());
        if (cause == null) {
            // Only cache if the failure was not because of an IO error / timeout that was caused by the query
            // itself.
            cache(hostname, additionals, unknownHostException);
        } else {
            unknownHostException.initCause(cause);
        }
        promise.tryFailure(unknownHostException);
    }

    static String decodeDomainName(ByteBuf in) {
        in.markReaderIndex();
        try {
            return DefaultDnsRecordDecoder.decodeName(in);
        } catch (CorruptedFrameException e) {
            // In this case we just return null.
            return null;
        } finally {
            in.resetReaderIndex();
        }
    }

    private DnsServerAddressStream getNameServers(String hostname) {
        DnsServerAddressStream stream = getNameServersFromCache(hostname);
        return stream == null ? nameServerAddrs.duplicate() : stream;
    }

    private void followCname(DnsQuestion question, String cname, DnsQueryLifecycleObserver queryLifecycleObserver,
                             Promise<List<T>> promise) {
        DnsServerAddressStream stream = getNameServers(cname);

        final DnsQuestion cnameQuestion;
        try {
            cnameQuestion = newQuestion(cname, question.type());
        } catch (Throwable cause) {
            queryLifecycleObserver.queryFailed(cause);
            PlatformDependent.throwException(cause);
            return;
        }
        query(stream, 0, cnameQuestion, queryLifecycleObserver.queryCNAMEd(cnameQuestion), promise, null);
    }

    private boolean query(String hostname, DnsRecordType type, DnsServerAddressStream dnsServerAddressStream,
                          Promise<List<T>> promise, Throwable cause) {
        final DnsQuestion question = newQuestion(hostname, type);
        if (question == null) {
            return false;
        }
        query(dnsServerAddressStream, 0, question, promise, cause);
        return true;
    }

    private DnsQuestion newQuestion(String hostname, DnsRecordType type) {
        try {
            return new DefaultDnsQuestion(hostname, type, dnsClass);
        } catch (IllegalArgumentException e) {
            // java.net.IDN.toASCII(...) may throw an IllegalArgumentException if it fails to parse the hostname
            return null;
        }
    }

    /**
     * Holds the closed DNS Servers for a domain.
     */
    private static final class AuthoritativeNameServerList {

        private final String questionName;

        // We not expect the linked-list to be very long so a double-linked-list is overkill.
        private AuthoritativeNameServer head;
        private int count;

        AuthoritativeNameServerList(String questionName) {
            this.questionName = questionName.toLowerCase(Locale.US);
        }

        void add(DnsRecord r) {
            if (r.type() != DnsRecordType.NS || !(r instanceof DnsRawRecord)) {
                return;
            }

            // Only include servers that serve the correct domain.
            if (questionName.length() <  r.name().length()) {
                return;
            }

            String recordName = r.name().toLowerCase(Locale.US);

            int dots = 0;
            for (int a = recordName.length() - 1, b = questionName.length() - 1; a >= 0; a--, b--) {
                char c = recordName.charAt(a);
                if (questionName.charAt(b) != c) {
                    return;
                }
                if (c == '.') {
                    dots++;
                }
            }

            if (head != null && head.dots > dots) {
                // We already have a closer match so ignore this one, no need to parse the domainName etc.
                return;
            }

            final ByteBuf recordContent = ((ByteBufHolder) r).content();
            final String domainName = decodeDomainName(recordContent);
            if (domainName == null) {
                // Could not be parsed, ignore.
                return;
            }

            // We are only interested in preserving the nameservers which are the closest to our qName, so ensure
            // we drop servers that have a smaller dots count.
            if (head == null || head.dots < dots) {
                count = 1;
                head = new AuthoritativeNameServer(dots, recordName, domainName);
            } else if (head.dots == dots) {
                AuthoritativeNameServer serverName = head;
                while (serverName.next != null) {
                    serverName = serverName.next;
                }
                serverName.next = new AuthoritativeNameServer(dots, recordName, domainName);
                count++;
            }
        }

        // Just walk the linked-list and mark the entry as removed when matched, so next lookup will need to process
        // one node less.
        AuthoritativeNameServer remove(String nsName) {
            AuthoritativeNameServer serverName = head;

            while (serverName != null) {
                if (!serverName.removed && serverName.nsName.equalsIgnoreCase(nsName)) {
                    serverName.removed = true;
                    return serverName;
                }
                serverName = serverName.next;
            }
            return null;
        }

        int size() {
            return count;
        }
    }

    static final class AuthoritativeNameServer {
        final int dots;
        final String nsName;
        final String domainName;

        AuthoritativeNameServer next;
        boolean removed;

        AuthoritativeNameServer(int dots, String domainName, String nsName) {
            this.dots = dots;
            this.nsName = nsName;
            this.domainName = domainName;
        }

        /**
         * Returns {@code true} if its a root server.
         */
        boolean isRootServer() {
            return dots == 1;
        }

        /**
         * The domain for which the {@link AuthoritativeNameServer} is responsible.
         */
        String domainName() {
            return domainName;
        }
    }
}
