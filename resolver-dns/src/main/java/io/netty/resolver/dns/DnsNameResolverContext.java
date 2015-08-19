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
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.CharsetUtil;
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
    private final Promise<T> promise;
    private final String hostname;
    private final boolean traceEnabled;
    private final int maxAllowedQueries;
    private final InternetProtocolFamily[] resolveAddressTypes;

    private final Set<Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>> queriesInProgress =
            Collections.newSetFromMap(
                    new IdentityHashMap<Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>, Boolean>());

    private List<DnsCacheEntry> resolvedEntries;
    private StringBuilder trace;
    private int allowedQueries;
    private boolean triedCNAME;

    protected DnsNameResolverContext(DnsNameResolver parent, String hostname, Promise<T> promise) {
        this.parent = parent;
        this.promise = promise;
        this.hostname = hostname;

        nameServerAddrs = parent.nameServerAddresses.stream();
        maxAllowedQueries = parent.maxQueriesPerResolve();
        resolveAddressTypes = parent.resolveAddressTypesUnsafe();
        traceEnabled = parent.isTraceEnabled();
        allowedQueries = maxAllowedQueries;
    }

    protected Promise<T> promise() {
        return promise;
    }

    void resolve() {
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

            query(nameServerAddrs.next(), new DefaultDnsQuestion(hostname, type));
        }
    }

    private void query(InetSocketAddress nameServerAddr, final DnsQuestion question) {
        if (allowedQueries == 0 || promise.isCancelled()) {
            return;
        }

        allowedQueries --;

        final Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> f = parent.query(nameServerAddr, question);
        queriesInProgress.add(f);

        f.addListener(new FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>() {
            @Override
            public void operationComplete(Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future) {
                queriesInProgress.remove(future);

                if (promise.isDone()) {
                    return;
                }

                try {
                    if (future.isSuccess()) {
                        onResponse(question, future.getNow());
                    } else {
                        // Server did not respond or I/O error occurred; try again.
                        if (traceEnabled) {
                            addTrace(future.cause());
                        }
                        query(nameServerAddrs.next(), question);
                    }
                } finally {
                    tryToFinishResolve();
                }
            }
        });
    }

    void onResponse(final DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope) {
        try {
            final DnsResponse res = envelope.content();
            final DnsResponseCode code = res.code();
            if (code == DnsResponseCode.NOERROR) {
                final DnsRecordType type = question.type();
                if (type == DnsRecordType.A || type == DnsRecordType.AAAA) {
                    onResponseAorAAAA(type, question, envelope);
                } else if (type == DnsRecordType.CNAME) {
                    onResponseCNAME(question, envelope);
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
                query(nameServerAddrs.next(), question);
            }
        } finally {
            ReferenceCountUtil.safeRelease(envelope);
        }
    }

    private void onResponseAorAAAA(
            DnsRecordType qType, DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope) {

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
            parent.cache(hostname, resolved, r.timeToLive());
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
            onResponseCNAME(question, envelope, cnames, false);
        }
    }

    private void onResponseCNAME(DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> envelope) {
        onResponseCNAME(question, envelope, buildAliasMap(envelope.content()), true);
    }

    private void onResponseCNAME(
            DnsQuestion question, AddressedEnvelope<DnsResponse, InetSocketAddress> response,
            Map<String, String> cnames, boolean trace) {

        // Resolve the host name in the question into the real host name.
        final String name = question.name().toLowerCase(Locale.US);
        String resolved = name;
        boolean found = false;
        for (;;) {
            String next = cnames.get(resolved);
            if (next != null) {
                found = true;
                resolved = next;
            } else {
                break;
            }
        }

        if (found) {
            followCname(response.sender(), name, resolved);
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

    void tryToFinishResolve() {
        if (!queriesInProgress.isEmpty()) {
            // There are still some queries we did not receive responses for.
            if (gotPreferredAddress()) {
                // But it's OK to finish the resolution process if we got a resolved address of the preferred type.
                finishResolve();
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
                query(nameServerAddrs.next(), new DefaultDnsQuestion(hostname, DnsRecordType.CNAME));
                return;
            }
        }

        // We have at least one resolved address or tried CNAME as the last resort..
        finishResolve();
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

    private void finishResolve() {
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
                if (finishResolve(f.addressType(), resolvedEntries)) {
                    return;
                }
            }
        }

        // No resolved address found.
        final int tries = maxAllowedQueries - allowedQueries;
        final StringBuilder buf = new StringBuilder(64);

        buf.append("failed to resolve ");
        buf.append(hostname);

        if (tries > 1) {
            buf.append(" after ");
            buf.append(tries);
            if (trace != null) {
                buf.append(" queries:");
                buf.append(trace);
            } else {
                buf.append(" queries");
            }
        } else {
            if (trace != null) {
                buf.append(':');
                buf.append(trace);
            }
        }

        final UnknownHostException cause = new UnknownHostException(buf.toString());

        parent.cache(hostname, cause);
        promise.tryFailure(cause);
    }

    protected abstract boolean finishResolve(
            Class<? extends InetAddress> addressType, List<DnsCacheEntry> resolvedEntries);

    /**
     * Adapted from {@link DefaultDnsRecordDecoder#decodeName(ByteBuf)}.
     */
    static String decodeDomainName(ByteBuf buf) {
        buf.markReaderIndex();
        try {
            int position = -1;
            int checked = 0;
            final int end = buf.writerIndex();
            final StringBuilder name = new StringBuilder(buf.readableBytes() << 1);
            for (int len = buf.readUnsignedByte(); buf.isReadable() && len != 0; len = buf.readUnsignedByte()) {
                boolean pointer = (len & 0xc0) == 0xc0;
                if (pointer) {
                    if (position == -1) {
                        position = buf.readerIndex() + 1;
                    }

                    final int next = (len & 0x3f) << 8 | buf.readUnsignedByte();
                    if (next >= end) {
                        // Should not happen.
                        return null;
                    }
                    buf.readerIndex(next);

                    // check for loops
                    checked += 2;
                    if (checked >= end) {
                        // Name contains a loop; give up.
                        return null;
                    }
                } else {
                    name.append(buf.toString(buf.readerIndex(), len, CharsetUtil.UTF_8)).append('.');
                    buf.skipBytes(len);
                }
            }

            if (position != -1) {
                buf.readerIndex(position);
            }

            if (name.length() == 0) {
                return null;
            }

            return name.substring(0, name.length() - 1);
        } finally {
            buf.resetReaderIndex();
        }
    }

    private void followCname(InetSocketAddress nameServerAddr, String name, String cname) {

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
        query(nextAddr, new DefaultDnsQuestion(cname, DnsRecordType.A));
        query(nextAddr, new DefaultDnsQuestion(cname, DnsRecordType.AAAA));
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
