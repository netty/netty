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
import io.netty.handler.codec.dns.DnsType;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.StringUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;

final class DnsNameResolverContext extends DefaultPromise<SocketAddress> {

    private static final int INADDRSZ4 = 4;
    private static final int INADDRSZ6 = 16;

    private final DnsNameResolver parent;
    private final String hostname;
    private final int port;
    private final int maxRecursionLevel;
    private final InternetProtocolFamily preferredProtocolFamily;
    private final boolean followCname;
    private final boolean followNs;
    private final boolean followSoa;

    private int remainingRecursions;
    private StringBuilder trace;

    DnsNameResolverContext(DnsNameResolver parent, String hostname, int port) {
        super(parent.executor());

        this.parent = parent;
        this.hostname = hostname;
        this.port = port;

        maxRecursionLevel = parent.maxRecursionLevel();
        preferredProtocolFamily = parent.preferredProtocolFamily();
        followCname = parent.isFollowCname();
        followNs = parent.isFollowNs();
        followSoa = parent.isFollowSoa();

        remainingRecursions = maxRecursionLevel;
    }

    DnsNameResolverContext resolve(final Iterable<InetSocketAddress> nameServerAddresses, final DnsQuestion question) {

        if (remainingRecursions <= 0) {
            setResolveFailure();
            return this;
        }

        remainingRecursions --;

        final Future<DnsResponse> queryFuture = parent.query(nameServerAddresses, question);
        if (queryFuture.isDone()) {
            // Query has been finished immediately - probably cached result.
            onResponse(nameServerAddresses, question, queryFuture);
        } else {
            queryFuture.addListener(new FutureListener<DnsResponse>() {
                @Override
                public void operationComplete(Future<DnsResponse> future) throws Exception {
                    onResponse(nameServerAddresses, question, future);
                }
            });
        }

        return this;
    }

    void onResponse(Iterable<InetSocketAddress> nameServerAddresses,
                    DnsQuestion question, Future<DnsResponse> queryFuture) {

        final DnsResponse response = queryFuture.getNow();
        try {
            if (queryFuture.isSuccess()) {
                onResponse(nameServerAddresses, question, response);
            } else {
                addTrace(queryFuture.cause());
                setResolveFailure();
            }
        } finally {
            ReferenceCountUtil.safeRelease(response);
        }
    }

    private void onResponse(final Iterable<InetSocketAddress> nameServerAddresses, final DnsQuestion question,
                            final DnsResponse response) {

        final List<DnsResource> answerList = response.answers();
        final DnsResource[] answers = answerList.toArray(new DnsResource[answerList.size()]);

        // Try to find A or AAAA record first.
        switch (preferredProtocolFamily) {
        case IPv4:
            for (final DnsResource a: answers) {
                if (handleAddress(DnsType.A, a)) {
                    return;
                }
            }
            for (final DnsResource a: answers) {
                if (handleAddress(DnsType.AAAA, a)) {
                    return;
                }
            }
            break;
        case IPv6:
            for (final DnsResource a: answers) {
                if (handleAddress(DnsType.AAAA, a)) {
                    return;
                }
            }
            for (final DnsResource a: answers) {
                if (handleAddress(DnsType.A, a)) {
                    return;
                }
            }
            break;
        }

        // If no A/AAAA record has been found, try to find CNAME record.
        if (followCname) {
            for (int i = answers.length - 1; i >= 0; i --) {
                if (handleCname(nameServerAddresses, question, response, answers[i])) {
                    return;
                }
            }
        }

        // If no A/AAAA/CNAME record has been found,
        // try to find NS record and query the first authoritative DNS server.
        if (followNs) {
            for (final DnsResource a: answers) {
                if (handleNsOrSoa(DnsType.NS, question, response, a)) {
                    return;
                }
            }

            for (final DnsResource a: response.authorityResources()) {
                if (handleNsOrSoa(DnsType.NS, question, response, a)) {
                    return;
                }
            }
        }

        if (followSoa) {
            for (final DnsResource a: answers) {
                if (handleNsOrSoa(DnsType.SOA, question, response, a)) {
                    return;
                }
            }

            for (final DnsResource a: response.authorityResources()) {
                if (handleNsOrSoa(DnsType.SOA, question, response, a)) {
                    return;
                }
            }
        }

        // A/AAAA/CNAME/NS/SOA not found - failed to resolve.
        addTrace(response.sender(), "no address record found for " + question.name());
        setResolveFailure();
    }

    private boolean handleAddress(DnsType expectedType, DnsResource a) {
        if (a.dnsClass() != DnsClass.IN) {
            return false;
        }

        final DnsType type = a.type();

        if (type != expectedType) {
            return false;
        }

        final ByteBuf content = a.content();
        final byte[] addrBytes;

        if (type == DnsType.A && content.readableBytes() == INADDRSZ4) {
            addrBytes = new byte[INADDRSZ4];
        } else if (type == DnsType.AAAA && content.readableBytes() == INADDRSZ6) {
            addrBytes = new byte[INADDRSZ6];
        } else {
            return false;
        }

        content.getBytes(content.readerIndex(), addrBytes);

        try {
            setSuccess(new InetSocketAddress(InetAddress.getByAddress(hostname, addrBytes), port));
            return true;
        } catch (UnknownHostException ignore) {
            // Should never happen
        }

        return false;
    }

    private boolean handleCname(
            Iterable<InetSocketAddress> nameServerAddresses,
            DnsQuestion question, DnsResponse response, DnsResource a) {

        if (a.dnsClass() != DnsClass.IN) {
            return false;
        }

        final DnsType type = a.type();
        if (type != DnsType.CNAME) {
            return false;
        }

        final String content = decodeDomainName(a.content());

        followCname(nameServerAddresses, response.sender(), question.name(), a.name(), content);
        return true;
    }

    private boolean handleNsOrSoa(
            final DnsType expectedType,
            final DnsQuestion question, final DnsResponse response, DnsResource a) {

        if (a.dnsClass() != DnsClass.IN) {
            return false;
        }

        final DnsType type = a.type();
        if (type != expectedType) {
            return false;
        }

        final String nsName = decodeDomainName(a.content());
        if (nsName == null) {
            return false;
        }

        final String name = a.name();

        addTrace(response.sender(), name + ' ' + expectedType + ' ' + nsName);

        final Future<SocketAddress> f = parent.resolve(nsName, 53);
        f.addListener(new FutureListener<SocketAddress>() {
            @Override
            public void operationComplete(Future<SocketAddress> future) throws Exception {
                if (!future.isSuccess()) {
                    addTrace(response.sender(), future.cause().toString());
                    setResolveFailure();
                    return;
                }

                final InetSocketAddress nsAddr = (InetSocketAddress) future.getNow();
                followNsOrSoa(expectedType, nsAddr, response.sender(), question.name(), name);
            }
        });

        return true;
    }

    /**
     * Adapted from {@link io.netty.handler.codec.dns.DnsResponseDecoder#readName(io.netty.buffer.ByteBuf)}.
     */
    private static String decodeDomainName(ByteBuf buf) {
        buf.markReaderIndex();
        try {
            int position = -1;
            int checked = 0;
            int length = buf.writerIndex();
            StringBuilder name = new StringBuilder();
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
            Iterable<InetSocketAddress> nameServerAddresses,
            InetSocketAddress nameServerAddr, String nameInQuestion, String nameInAnswer, String cname) {

        if (trace == null) {
            trace = new StringBuilder(128);
        }

        trace.append(StringUtil.NEWLINE);
        trace.append("\tfrom ");
        trace.append(nameServerAddr);
        trace.append(": ");
        trace.append(nameInQuestion);
        trace.append(" => ");
        trace.append(nameInAnswer);
        trace.append(" CNAME ");
        trace.append(cname);

        resolve(nameServerAddresses, new DnsQuestion(cname, DnsType.ANY, DnsClass.IN));
    }

    void followNsOrSoa(
            DnsType type, InetSocketAddress authoritativeNsAddr,
            InetSocketAddress nameServerAddr, String nameInQuestion, String nameInAnswer) {

        if (trace == null) {
            trace = new StringBuilder(128);
        }

        trace.append(StringUtil.NEWLINE);
        trace.append("\tfrom ");
        trace.append(nameServerAddr);
        trace.append(": ");
        trace.append(nameInQuestion);
        trace.append(" => ");
        trace.append(nameInAnswer);
        trace.append(' ');
        trace.append(type);
        trace.append(' ');
        trace.append(authoritativeNsAddr);

        final DnsQuestion question = new DnsQuestion(nameInQuestion, DnsType.ANY, DnsClass.IN);
        Object cachedResult = parent.queryCache.get(question);
        if (cachedResult != null) {
            if (!(cachedResult instanceof DnsResponse) ||
                !((DnsResponse) cachedResult).sender().equals(authoritativeNsAddr)) {
                parent.queryCache.remove(question);
            }
        }

        resolve(DnsServerAddresses.singleton(authoritativeNsAddr), question);
    }

    private void addTrace(Throwable cause) {
        if (trace == null) {
            trace = new StringBuilder(128);
        }

        trace.append(StringUtil.NEWLINE);
        trace.append("Caused by: ");
        trace.append(cause);
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

    private void setResolveFailure() {
        int depth = maxRecursionLevel - remainingRecursions;
        UnknownHostException cause;
        if (depth > 1) {
            cause = new UnknownHostException(
                    "failed to resolve " + hostname + " after " + depth + " recursions:" +
                    trace);
        } else {
            cause = new UnknownHostException("failed to resolve " + hostname + ':' + trace);
        }

        setFailure(cause);
    }
}
