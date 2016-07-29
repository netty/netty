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
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;

import java.net.Inet4Address;
import java.net.Inet6Address;
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

abstract class DnsNameResolverContext<T> {

    private static final int INADDRSZ4 = 4;
    private static final int INADDRSZ6 = 16;

    private static final FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>> RELEASE_RESPONSE =
            new FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>() {
                @Override
                public void operationComplete(Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future) {
                    if (future.isSuccess()) {
                        future.getNow().release();
                    }
                }
            };

    private final DnsNameResolver parent;
    private final DnsServerAddressStream nameServerAddrs;
    private final String hostname;
    protected String pristineHostname;
    private final DnsCache resolveCache;
    private final boolean traceEnabled;
    private final int maxAllowedQueries;
    private final InternetProtocolFamily[] resolveAddressTypes;
    private final DnsRecord[] additionals;

    private final Set<Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> queriesInProgress =
            Collections.newSetFromMap(
                    new IdentityHashMap<Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>, Boolean>());

    private List<DnsCacheEntry> resolvedEntries;
    private StringBuilder trace;
    private int allowedQueries;
    private boolean triedCNAME;

    protected DnsNameResolverContext(DnsNameResolver parent,
                                     String hostname,
                                     DnsRecord[] additionals,
                                     DnsCache resolveCache) {
        this.parent = parent;
        this.hostname = hostname;
        this.additionals = additionals;
        this.resolveCache = resolveCache;

        nameServerAddrs = parent.nameServerAddresses.stream();
        maxAllowedQueries = parent.maxQueriesPerResolve();
        resolveAddressTypes = parent.resolveAddressTypesUnsafe();
        traceEnabled = parent.isTraceEnabled();
        allowedQueries = maxAllowedQueries;
    }

    void resolve(Promise<T> promise) {
        boolean directSearch = parent.searchDomains().length == 0 || StringUtil.endsWith(hostname, '.');
        if (directSearch) {
            internalResolve(promise);
        } else {
            final Promise<T> original = promise;
            promise = parent.executor().newPromise();
            promise.addListener(new FutureListener<T>() {
                int count;
                @Override
                public void operationComplete(Future<T> future) throws Exception {
                    if (future.isSuccess()) {
                        original.trySuccess(future.getNow());
                    } else if (count < parent.searchDomains().length) {
                        String searchDomain = parent.searchDomains()[count++];
                        Promise<T> nextPromise = parent.executor().newPromise();
                        String nextHostname = hostname + '.' + searchDomain;
                        DnsNameResolverContext<T> nextContext = newResolverContext(parent,
                            nextHostname, additionals, resolveCache);
                        nextContext.pristineHostname = hostname;
                        nextContext.internalResolve(nextPromise);
                        nextPromise.addListener(this);
                    } else {
                        original.tryFailure(future.cause());
                    }
                }
            });
            if (parent.ndots() == 0) {
                internalResolve(promise);
            } else {
                int dots = 0;
                for (int idx = hostname.length() - 1; idx >= 0; idx--) {
                    if (hostname.charAt(idx) == '.' && ++dots >= parent.ndots()) {
                        internalResolve(promise);
                        return;
                    }
                }
                promise.tryFailure(new UnknownHostException(hostname));
            }
        }
    }

    private void internalResolve(Promise<T> promise) {
        InetSocketAddress nameServerAddrToTry = nameServerAddrs.next();
        for (InternetProtocolFamily f: resolveAddressTypes) {
            final DnsRecordType type;
            switch (f) {
            case IPv4:
                type = DnsRecordType.A;
                break;
            case IPv6:
                type = DnsRecordType.AAAA;
                break;
            default:
                throw new Error();
            }

            query(nameServerAddrToTry, new DefaultDnsQuestion(hostname, type), promise);
        }
    }

    private void query(InetSocketAddress nameServerAddr, final DnsQuestion question, final Promise<T> promise) {
        if (allowedQueries == 0 || promise.isCancelled()) {
            tryToFinishResolve(promise);
            return;
        }

        allowedQueries --;

        final Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> f = parent.query0(
                nameServerAddr, question, additionals,
                parent.ch.eventLoop().<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>>newPromise());
        queriesInProgress.add(f);

        f.addListener(new FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>() {
            @Override
            public void operationComplete(Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future) {
                queriesInProgress.remove(future);

                if (promise.isDone() || future.isCancelled()) {
                    return;
                }

                try {
                    if (future.isSuccess()) {
                        onResponse(question, future.getNow(), promise);
                    } else {
                        // Server did not respond or I/O error occurred; try again.
                        if (traceEnabled) {
                            addTrace(future.cause());
                        }
                        query(nameServerAddrs.next(), question, promise);
                    }
                } finally {
                    tryToFinishResolve(promise);
                }
            }
        });
    }

    void onResponse(final DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope,
                    Promise<T> promise) {
        try {
            final DnsResponse res = envelope.content();
            final DnsResponseCode code = res.code();
            if (code == DnsResponseCode.NOERROR) {
                final DnsRecordType type = question.type();
                if (type == DnsRecordType.A || type == DnsRecordType.AAAA) {
                    onResponseAorAAAA(type, question, envelope, promise);
                } else if (type == DnsRecordType.CNAME) {
                    onResponseCNAME(question, envelope, promise);
                }
                return;
            }

            if (traceEnabled) {
                addTrace(envelope.sender(),
                         "response code: " + code + " with " + res.count(DnsSection.ANSWER) + " answer(s) and " +
                         res.count(DnsSection.AUTHORITY) + " authority resource(s)");
            }

            // Retry with the next server if the server did not tell us that the domain does not exist.
            if (code != DnsResponseCode.NXDOMAIN) {
                query(nameServerAddrs.next(), question, promise);
            }
        } finally {
            ReferenceCountUtil.safeRelease(envelope);
        }
    }

    private void onResponseAorAAAA(
            DnsRecordType qType, DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope,
            Promise<T> promise) {

        // We often get a bunch of CNAMES as well when we asked for A/AAAA.
        final DnsResponse response = envelope.content();
        final Map<String, String> cnames = buildAliasMap(response);
        final int answerCount = response.count(DnsSection.ANSWER);

        boolean found = false;
        for (int i = 0; i < answerCount; i ++) {
            final DnsRecord r = response.recordAt(DnsSection.ANSWER, i);
            final DnsRecordType type = r.type();
            if (type != DnsRecordType.A && type != DnsRecordType.AAAA) {
                continue;
            }

            final String qName = question.name().toLowerCase(Locale.US);
            final String rName = r.name().toLowerCase(Locale.US);

            // Make sure the record is for the questioned domain.
            if (!rName.equals(qName)) {
                // Even if the record's name is not exactly same, it might be an alias defined in the CNAME records.
                String resolved = qName;
                do {
                    resolved = cnames.get(resolved);
                    if (rName.equals(resolved)) {
                        break;
                    }
                } while (resolved != null);

                if (resolved == null) {
                    continue;
                }
            }

            if (!(r instanceof DnsRawRecord)) {
                continue;
            }

            final ByteBuf content = ((ByteBufHolder) r).content();
            final int contentLen = content.readableBytes();
            if (contentLen != INADDRSZ4 && contentLen != INADDRSZ6) {
                continue;
            }

            final byte[] addrBytes = new byte[contentLen];
            content.getBytes(content.readerIndex(), addrBytes);

            final InetAddress resolved;
            try {
                resolved = InetAddress.getByAddress(hostname, addrBytes);
            } catch (UnknownHostException e) {
                // Should never reach here.
                throw new Error(e);
            }

            if (resolvedEntries == null) {
                resolvedEntries = new ArrayList<DnsCacheEntry>(8);
            }

            final DnsCacheEntry e = new DnsCacheEntry(hostname, resolved);
            resolveCache.cache(hostname, additionals, resolved, r.timeToLive(), parent.ch.eventLoop());
            resolvedEntries.add(e);
            found = true;

            // Note that we do not break from the loop here, so we decode/cache all A/AAAA records.
        }

        if (found) {
            return;
        }

        if (traceEnabled) {
            addTrace(envelope.sender(), "no matching " + qType + " record found");
        }

        // We aked for A/AAAA but we got only CNAME.
        if (!cnames.isEmpty()) {
            onResponseCNAME(question, envelope, cnames, false, promise);
        }
    }

    private void onResponseCNAME(DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope,
                                 Promise<T> promise) {
        onResponseCNAME(question, envelope, buildAliasMap(envelope.content()), true, promise);
    }

    private void onResponseCNAME(
            DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> response,
            Map<String, String> cnames, boolean trace, Promise<T> promise) {

        // Resolve the host name in the question into the real host name.
        final String name = question.name().toLowerCase(Locale.US);
        String resolved = name;
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
            followCname(response.sender(), name, resolved, promise);
        } else if (trace && traceEnabled) {
            addTrace(response.sender(), "no matching CNAME record found");
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
                cnames = new HashMap<String, String>();
            }

            cnames.put(r.name().toLowerCase(Locale.US), domainName.toLowerCase(Locale.US));
        }

        return cnames != null? cnames : Collections.<String, String>emptyMap();
    }

    void tryToFinishResolve(Promise<T> promise) {
        if (!queriesInProgress.isEmpty()) {
            // There are still some queries we did not receive responses for.
            if (gotPreferredAddress()) {
                // But it's OK to finish the resolution process if we got a resolved address of the preferred type.
                finishResolve(promise);
            }

            // We did not get any resolved address of the preferred type, so we can't finish the resolution process.
            return;
        }

        // There are no queries left to try.
        if (resolvedEntries == null) {
            // .. and we could not find any A/AAAA records.
            if (!triedCNAME) {
                // As the last resort, try to query CNAME, just in case the name server has it.
                triedCNAME = true;
                query(nameServerAddrs.next(), new DefaultDnsQuestion(hostname, DnsRecordType.CNAME), promise);
                return;
            }
        }

        // We have at least one resolved address or tried CNAME as the last resort..
        finishResolve(promise);
    }

    private boolean gotPreferredAddress() {
        if (resolvedEntries == null) {
            return false;
        }

        final int size = resolvedEntries.size();
        switch (resolveAddressTypes[0]) {
        case IPv4:
            for (int i = 0; i < size; i ++) {
                if (resolvedEntries.get(i).address() instanceof Inet4Address) {
                    return true;
                }
            }
            break;
        case IPv6:
            for (int i = 0; i < size; i ++) {
                if (resolvedEntries.get(i).address() instanceof Inet6Address) {
                    return true;
                }
            }
            break;
        }

        return false;
    }

    private void finishResolve(Promise<T> promise) {
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

        if (resolvedEntries != null) {
            // Found at least one resolved address.
            for (InternetProtocolFamily f: resolveAddressTypes) {
                if (finishResolve(f.addressType(), resolvedEntries, promise)) {
                    return;
                }
            }
        }

        // No resolved address found.
        final int tries = maxAllowedQueries - allowedQueries;
        final StringBuilder buf = new StringBuilder(64);

        buf.append("failed to resolve '");
        if (pristineHostname != null) {
          buf.append(pristineHostname);
        } else {
          buf.append(hostname);
        }
        buf.append('\'');
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
        if (trace != null) {
            buf.append(':')
               .append(trace);
        }
        final UnknownHostException cause = new UnknownHostException(buf.toString());

        resolveCache.cache(hostname, additionals, cause, parent.ch.eventLoop());
        promise.tryFailure(cause);
    }

    abstract boolean finishResolve(Class<? extends InetAddress> addressType, List<DnsCacheEntry> resolvedEntries,
                                   Promise<T> promise);

    abstract DnsNameResolverContext<T> newResolverContext(DnsNameResolver parent, String hostname,
                                                          DnsRecord[] additionals, DnsCache resolveCache);

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

    private void followCname(InetSocketAddress nameServerAddr, String name, String cname, Promise<T> promise) {

        if (traceEnabled) {
            if (trace == null) {
                trace = new StringBuilder(128);
            }

            trace.append(StringUtil.NEWLINE);
            trace.append("\tfrom ");
            trace.append(nameServerAddr);
            trace.append(": ");
            trace.append(name);
            trace.append(" CNAME ");
            trace.append(cname);
        }

        final InetSocketAddress nextAddr = nameServerAddrs.next();
        query(nextAddr, new DefaultDnsQuestion(cname, DnsRecordType.A), promise);
        query(nextAddr, new DefaultDnsQuestion(cname, DnsRecordType.AAAA), promise);
    }

    private void addTrace(InetSocketAddress nameServerAddr, String msg) {
        assert traceEnabled;

        if (trace == null) {
            trace = new StringBuilder(128);
        }

        trace.append(StringUtil.NEWLINE);
        trace.append("\tfrom ");
        trace.append(nameServerAddr);
        trace.append(": ");
        trace.append(msg);
    }

    private void addTrace(Throwable cause) {
        assert traceEnabled;

        if (trace == null) {
            trace = new StringBuilder(128);
        }

        trace.append(StringUtil.NEWLINE);
        trace.append("Caused by: ");
        trace.append(cause);
    }
}
