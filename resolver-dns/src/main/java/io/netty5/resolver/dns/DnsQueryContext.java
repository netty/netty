/*
 * Copyright 2014 The Netty Project
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
package io.netty5.resolver.dns;

import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.Channel;
import io.netty5.handler.codec.dns.AbstractDnsOptPseudoRrRecord;
import io.netty5.handler.codec.dns.DnsQuery;
import io.netty5.handler.codec.dns.DnsQuestion;
import io.netty5.handler.codec.dns.DnsRecord;
import io.netty5.handler.codec.dns.DnsResponse;
import io.netty5.handler.codec.dns.DnsSection;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

abstract class DnsQueryContext implements FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsQueryContext.class);

    private final DnsNameResolver parent;
    private final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise;
    private final int id;
    private final DnsQuestion question;
    private final DnsRecord[] additionals;
    private final DnsRecord optResource;
    private final InetSocketAddress nameServerAddr;

    private final boolean recursionDesired;
    private volatile Future<?> timeoutFuture;

    DnsQueryContext(DnsNameResolver parent,
                    InetSocketAddress nameServerAddr,
                    DnsQuestion question,
                    DnsRecord[] additionals,
                    Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise) {

        this.parent = requireNonNull(parent, "parent");
        this.nameServerAddr = requireNonNull(nameServerAddr, "nameServerAddr");
        this.question = requireNonNull(question, "question");
        this.additionals = requireNonNull(additionals, "additionals");
        this.promise = requireNonNull(promise, "promise");
        recursionDesired = parent.isRecursionDesired();
        id = parent.queryContextManager.add(this);

        // Ensure we remove the id from the QueryContextManager once the query completes.
        promise.asFuture().addListener(this);

        if (parent.isOptResourceEnabled()) {
            optResource = new AbstractDnsOptPseudoRrRecord(parent.maxPayloadSize(), 0, 0) {
                // We may want to remove this in the future and let the user just specify the opt record in the query.
            };
        } else {
            optResource = null;
        }
    }

    InetSocketAddress nameServerAddr() {
        return nameServerAddr;
    }

    DnsQuestion question() {
        return question;
    }

    DnsNameResolver parent() {
        return parent;
    }

    protected abstract DnsQuery newQuery(int id);
    protected abstract Channel channel();
    protected abstract String protocol();

    void query(boolean flush, Promise<Void> writePromise) {
        final DnsQuestion question = question();
        final InetSocketAddress nameServerAddr = nameServerAddr();
        final DnsQuery query = newQuery(id);

        query.setRecursionDesired(recursionDesired);

        query.addRecord(DnsSection.QUESTION, question);

        for (DnsRecord record: additionals) {
            query.addRecord(DnsSection.ADDITIONAL, record);
        }

        if (optResource != null) {
            query.addRecord(DnsSection.ADDITIONAL, optResource);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} WRITE: {}, [{}: {}], {}", channel(), protocol(), id, nameServerAddr, question);
        }

        sendQuery(query, flush, writePromise);
    }

    private void sendQuery(final DnsQuery query, final boolean flush, final Promise<Void> writePromise) {
        if (parent.channelReadyPromise.isSuccess()) {
            writeQuery(query, flush, writePromise);
        } else if (parent.channelReadyPromise.isFailed()) {
            failQuery(query, parent.channelReadyPromise.cause(), writePromise);
        } else {
            parent.channelReadyPromise.asFuture().addListener(future -> {
                if (future.isSuccess()) {
                    // If the query is done in a late fashion (as the channel was not ready yet) we always flush
                    // to ensure we did not race with a previous flush() that was done when the Channel was not
                    // ready yet.
                    writeQuery(query, true, writePromise);
                } else {
                    failQuery(query, future.cause(), writePromise);
                }
            });
        }
    }

    private void failQuery(DnsQuery query, Throwable cause, Promise<Void> writePromise) {
        try {
            promise.tryFailure(cause);
            writePromise.setFailure(cause);
        } finally {
            ReferenceCountUtil.release(query);
        }
    }

    private void writeQuery(final DnsQuery query, final boolean flush, final Promise<Void> writePromise) {
        final Future<Void> writeFuture = flush ? channel().writeAndFlush(query) :
                channel().write(query);
        if (writeFuture.isDone()) {
            onQueryWriteCompletion(writeFuture, writePromise);
        } else {
            writeFuture.addListener(future ->
                    onQueryWriteCompletion(future, writePromise));
        }
    }

    private void onQueryWriteCompletion(Future<?> writeFuture, Promise<Void> writePromise) {
        if (writeFuture.isFailed()) {
            writePromise.setFailure(writeFuture.cause());
            tryFailure("failed to send a query via " + protocol(), writeFuture.cause(), false);
            return;
        }
        writePromise.setSuccess(null);
        // Schedule a query timeout task if necessary.
        final long queryTimeoutMillis = parent.queryTimeoutMillis();
        if (queryTimeoutMillis > 0) {
            timeoutFuture = parent.ch.executor().schedule(() -> {
                if (promise.isDone()) {
                    // Received a response before the query times out.
                    return;
                }

                tryFailure("query via " + protocol() + " timed out after " +
                        queryTimeoutMillis + " milliseconds", null, true);
                }, queryTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Takes ownership of passed envelope
     */
    void finish(AddressedEnvelope<? extends DnsResponse, InetSocketAddress> envelope) {
        final DnsResponse res = envelope.content();
        if (res.count(DnsSection.QUESTION) != 1) {
            logger.warn("Received a DNS response with invalid number of questions: {}", envelope);
        } else if (!question().equals(res.recordAt(DnsSection.QUESTION))) {
            logger.warn("Received a mismatching DNS response: {}", envelope);
        } else if (trySuccess(envelope)) {
            return; // Ownership transferred, don't release
        }
        ReferenceCountUtil.release(envelope);
    }

    @SuppressWarnings("unchecked")
    private boolean trySuccess(AddressedEnvelope<? extends DnsResponse, InetSocketAddress> envelope) {
        return promise.trySuccess((AddressedEnvelope<DnsResponse, InetSocketAddress>) envelope);
    }

    boolean tryFailure(String message, Throwable cause, boolean timeout) {
        if (promise.isDone()) {
            return false;
        }
        final InetSocketAddress nameServerAddr = nameServerAddr();

        final StringBuilder buf = new StringBuilder(message.length() + 64);
        buf.append('[')
           .append(nameServerAddr)
           .append("] ")
           .append(message)
           .append(" (no stack trace available)");

        final DnsNameResolverException e;
        if (timeout) {
            // This was caused by an timeout so use DnsNameResolverTimeoutException to allow the user to
            // handle it special (like retry the query).
            e = new DnsNameResolverTimeoutException(nameServerAddr, question(), buf.toString());
        } else {
            e = new DnsNameResolverException(nameServerAddr, question(), buf.toString(), cause);
        }
        return promise.tryFailure(e);
    }

    @Override
    public void operationComplete(Future<? extends AddressedEnvelope<DnsResponse, InetSocketAddress>> future) {
        // Cancel the timeout task.
        final Future<?> timeoutFuture = this.timeoutFuture;
        if (timeoutFuture != null) {
            this.timeoutFuture = null;
            timeoutFuture.cancel();
        }

        // Remove the id from the manager as soon as the query completes. This may be because of success, failure or
        // cancellation
        parent.queryContextManager.remove(nameServerAddr, id);
    }
}
