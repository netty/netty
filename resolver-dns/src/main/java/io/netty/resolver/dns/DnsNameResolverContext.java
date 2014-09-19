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
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.codec.dns.DnsClass;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.codec.dns.DnsType;
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

final class DnsNameResolverContext {

    private static final int INADDRSZ4 = 4;
    private static final int INADDRSZ6 = 16;

    private static final FutureListener<DnsResponse> RELEASE_RESPONSE = new FutureListener<DnsResponse>() {
        @Override
        public void operationComplete(Future<DnsResponse> future) {
            if (future.isSuccess()) {
                future.getNow().release();
            }
        }
    };

    private final DnsNameResolver parent;
    private final Promise<InetSocketAddress> promise;
    private final String hostname;
    private final int port;
    private final int maxAllowedQueries;
    private final InternetProtocolFamily[] resolveAddressTypes;

    private final Set<Future<DnsResponse>> queriesInProgress =
            Collections.newSetFromMap(new IdentityHashMap<Future<DnsResponse>, Boolean>());
    private List<InetAddress> resolvedAddresses;
    private StringBuilder trace;
    private int allowedQueries;
    private boolean triedCNAME;

    DnsNameResolverContext(DnsNameResolver parent, String hostname, int port, Promise<InetSocketAddress> promise) {
        this.parent = parent;
        this.promise = promise;
        this.hostname = hostname;
        this.port = port;

        maxAllowedQueries = parent.maxQueriesPerResolve();
        resolveAddressTypes = parent.resolveAddressTypesUnsafe();
        allowedQueries = maxAllowedQueries;
    }

    void resolve() {
        for (InternetProtocolFamily f: resolveAddressTypes) {
            final DnsType type;
            switch (f) {
            case IPv4:
                type = DnsType.A;
                break;
            case IPv6:
                type = DnsType.AAAA;
                break;
            default:
                throw new Error();
            }

            query(parent.nameServerAddresses, new DnsQuestion(hostname, type));
        }
    }

    private void query(Iterable<InetSocketAddress> nameServerAddresses, final DnsQuestion question) {
        if (allowedQueries == 0 || promise.isCancelled()) {
            return;
        }

        allowedQueries --;

        final Future<DnsResponse> f = parent.query(nameServerAddresses, question);
        queriesInProgress.add(f);

        f.addListener(new FutureListener<DnsResponse>() {
            @Override
            public void operationComplete(Future<DnsResponse> future) throws Exception {
                queriesInProgress.remove(future);

                if (promise.isDone()) {
                    return;
                }

                try {
                    if (future.isSuccess()) {
                        onResponse(question, future.getNow());
                    } else {
                        addTrace(future.cause());
                    }
                } finally {
                    tryToFinishResolve();
                }
            }
        });
    }

    void onResponse(final DnsQuestion question, final DnsResponse response) {
        final DnsType type = question.type();
        try {
            if (type == DnsType.A || type == DnsType.AAAA) {
                onResponseAorAAAA(type, question, response);
            } else if (type == DnsType.CNAME) {
                onResponseCNAME(question, response);
            }
        } finally {
            ReferenceCountUtil.safeRelease(response);
        }
    }

    private void onResponseAorAAAA(DnsType qType, DnsQuestion question, DnsResponse response) {
        // We often get a bunch of CNAMES as well when we asked for A/AAAA.
        final Map<String, String> cnames = buildAliasMap(response);

        boolean found = false;
        for (DnsResource r: response.answers()) {
            final DnsType type = r.type();
            if (type != DnsType.A && type != DnsType.AAAA) {
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

            final ByteBuf content = r.content();
            final int contentLen = content.readableBytes();
            if (contentLen != INADDRSZ4 && contentLen != INADDRSZ6) {
                continue;
            }

            final byte[] addrBytes = new byte[contentLen];
            content.getBytes(content.readerIndex(), addrBytes);

            try {
                InetAddress resolved = InetAddress.getByAddress(hostname, addrBytes);
                if (resolvedAddresses == null) {
                    resolvedAddresses = new ArrayList<InetAddress>();
                }
                resolvedAddresses.add(resolved);
                found = true;
            } catch (UnknownHostException e) {
                // Should never reach here.
                throw new Error(e);
            }
        }

        if (found) {
            return;
        }

        addTrace(response.sender(), "no matching " + qType + " record found");

        // We aked for A/AAAA but we got only CNAME.
        if (!cnames.isEmpty()) {
            onResponseCNAME(question, response, cnames, false);
        }
    }

    private void onResponseCNAME(DnsQuestion question, DnsResponse response) {
        onResponseCNAME(question, response, buildAliasMap(response), true);
    }

    private void onResponseCNAME(
            DnsQuestion question, DnsResponse response, Map<String, String> cnames, boolean trace) {

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
        } else if (trace) {
            addTrace(response.sender(), "no matching CNAME record found");
        }
    }

    private static Map<String, String> buildAliasMap(DnsResponse response) {
        Map<String, String> cnames = null;
        for (DnsResource r: response.answers()) {
            final DnsType type = r.type();
            if (type != DnsType.CNAME) {
                continue;
            }

            String content = decodeDomainName(r.content());
            if (content == null) {
                continue;
            }

            if (cnames == null) {
                cnames = new HashMap<String, String>();
            }

            cnames.put(r.name().toLowerCase(Locale.US), content.toLowerCase(Locale.US));
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
        if (resolvedAddresses == null) {
            // .. and we could not find any A/AAAA records.
            if (!triedCNAME) {
                // As the last resort, try to query CNAME, just in case the name server has it.
                triedCNAME = true;
                query(parent.nameServerAddresses, new DnsQuestion(hostname, DnsType.CNAME, DnsClass.IN));
                return;
            }
        }

        // We have at least one resolved address or tried CNAME as the last resort..
        finishResolve();
    }

    private boolean gotPreferredAddress() {
        if (resolvedAddresses == null) {
            return false;
        }

        final int size = resolvedAddresses.size();
        switch (resolveAddressTypes[0]) {
        case IPv4:
            for (int i = 0; i < size; i ++) {
                if (resolvedAddresses.get(i) instanceof Inet4Address) {
                    return true;
                }
            }
            break;
        case IPv6:
            for (int i = 0; i < size; i ++) {
                if (resolvedAddresses.get(i) instanceof Inet6Address) {
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
            for (Iterator<Future<DnsResponse>> i = queriesInProgress.iterator(); i.hasNext();) {
                Future<DnsResponse> f = i.next();
                i.remove();

                if (!f.cancel(false)) {
                    f.addListener(RELEASE_RESPONSE);
                }
            }
        }

        if (resolvedAddresses != null) {
            // Found at least one resolved address.
            for (InternetProtocolFamily f: resolveAddressTypes) {
                switch (f) {
                case IPv4:
                    if (finishResolveWithIPv4()) {
                        return;
                    }
                    break;
                case IPv6:
                    if (finishResolveWithIPv6()) {
                        return;
                    }
                    break;
                }
            }
        }

        // No resolved address found.
        int tries = maxAllowedQueries - allowedQueries;
        UnknownHostException cause;
        if (tries > 1) {
            cause = new UnknownHostException(
                    "failed to resolve " + hostname + " after " + tries + " queries:" +
                    trace);
        } else {
            cause = new UnknownHostException("failed to resolve " + hostname + ':' + trace);
        }

        promise.tryFailure(cause);
    }

    private boolean finishResolveWithIPv4() {
        final List<InetAddress> resolvedAddresses = this.resolvedAddresses;
        final int size = resolvedAddresses.size();

        for (int i = 0; i < size; i ++) {
            InetAddress a = resolvedAddresses.get(i);
            if (a instanceof Inet4Address) {
                promise.trySuccess(new InetSocketAddress(a, port));
                return true;
            }
        }

        return false;
    }

    private boolean finishResolveWithIPv6() {
        final List<InetAddress> resolvedAddresses = this.resolvedAddresses;
        final int size = resolvedAddresses.size();

        for (int i = 0; i < size; i ++) {
            InetAddress a = resolvedAddresses.get(i);
            if (a instanceof Inet6Address) {
                promise.trySuccess(new InetSocketAddress(a, port));
                return true;
            }
        }

        return false;
    }

    /**
     * Adapted from {@link DnsResponseDecoder#readName(ByteBuf)}.
     */
    static String decodeDomainName(ByteBuf buf) {
        buf.markReaderIndex();
        try {
            int position = -1;
            int checked = 0;
            int length = buf.writerIndex();
            StringBuilder name = new StringBuilder(64);
            for (int len = buf.readUnsignedByte(); buf.isReadable() && len != 0; len = buf.readUnsignedByte()) {
                boolean pointer = (len & 0xc0) == 0xc0;
                if (pointer) {
                    if (position == -1) {
                        position = buf.readerIndex() + 1;
                    }
                    buf.readerIndex((len & 0x3f) << 8 | buf.readUnsignedByte());
                    // check for loops
                    checked += 2;
                    if (checked >= length) {
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

    private void followCname(
            InetSocketAddress nameServerAddr, String name, String cname) {

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

        query(parent.nameServerAddresses, new DnsQuestion(cname, DnsType.A, DnsClass.IN));
        query(parent.nameServerAddresses, new DnsQuestion(cname, DnsType.AAAA, DnsClass.IN));
    }

    private void addTrace(InetSocketAddress nameServerAddr, String msg) {
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
        if (trace == null) {
            trace = new StringBuilder(128);
        }

        trace.append(StringUtil.NEWLINE);
        trace.append("Caused by: ");
        trace.append(cause);
    }
}
