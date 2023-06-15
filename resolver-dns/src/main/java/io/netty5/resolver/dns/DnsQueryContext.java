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
import io.netty5.handler.codec.dns.DnsOptPseudoRecord;
import io.netty5.handler.codec.dns.DnsQuery;
import io.netty5.handler.codec.dns.DnsQuestion;
import io.netty5.handler.codec.dns.DnsRecord;
import io.netty5.handler.codec.dns.DnsRecordType;
import io.netty5.handler.codec.dns.DnsResponse;
import io.netty5.handler.codec.dns.DnsSection;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SilentDispose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

abstract class DnsQueryContext {

    private static final Logger logger = LoggerFactory.getLogger(DnsQueryContext.class);

    private final Future<? extends Channel> channelReadyFuture;
    private final Channel channel;
    private final InetSocketAddress nameServerAddr;
    private final DnsQueryContextManager queryContextManager;
    private final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise;

    private final DnsQuestion question;
    private final DnsRecord[] additionals;
    private final DnsRecord optResource;

    private final boolean recursionDesired;
    private volatile Future<?> timeoutFuture;

    private int id = -1;

    DnsQueryContext(Channel channel,
                    Future<? extends Channel> channelReadyFuture,
                    InetSocketAddress nameServerAddr,
                    DnsQueryContextManager queryContextManager,
                    int maxPayLoadSize,
                    boolean recursionDesired,
                    DnsQuestion question,
                    DnsRecord[] additionals,
                    Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise) {
        this.channel = requireNonNull(channel, "channel");
        this.queryContextManager = requireNonNull(queryContextManager, "queryContextManager");
        this.channelReadyFuture = requireNonNull(channelReadyFuture, "channelReadyFuture");
        this.nameServerAddr = requireNonNull(nameServerAddr, "nameServerAddr");
        this.question = requireNonNull(question, "question");
        this.additionals = requireNonNull(additionals, "additionals");
        this.promise = requireNonNull(promise, "promise");
        this.recursionDesired = recursionDesired;

        if (maxPayLoadSize > 0 &&
                // Only add the extra OPT record if there is not already one. This is required as only one is allowed
                // as per RFC:
                //  - https://datatracker.ietf.org/doc/html/rfc6891#section-6.1.1
                !hasOptRecord(additionals)) {
            optResource = new AbstractDnsOptPseudoRrRecord(maxPayLoadSize, 0, 0) {
                // We may want to remove this in the future and let the user just specify the opt record in the query.
                @Override
                public DnsOptPseudoRecord copy() {
                    return this; // This instance is immutable.
                }
            };
        } else {
            optResource = null;
        }
    }

    private static boolean hasOptRecord(DnsRecord[] additionals) {
        if (additionals != null && additionals.length > 0) {
            for (DnsRecord additional: additionals) {
                if (additional.type() == DnsRecordType.OPT) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the {@link DnsQuestion} that will be written as part of the {@link DnsQuery}.
     *
     * @return the question.
     */
    final DnsQuestion question() {
        return question;
    }

    /**
     * Creates and returns a new {@link DnsQuery}.
     *
     * @param id                the transaction id to use.
     * @param nameServerAddr    the nameserver to which the query will be send.
     * @return                  the new query.
     */
    protected abstract DnsQuery newQuery(int id, InetSocketAddress nameServerAddr);

    /**
     * Returns the protocol that is used for the query.
     *
     * @return  the protocol.
     */
    protected abstract String protocol();

    /**
     * Write the query and return the {@link Future} that is completed once the write completes.
     *
     * @param queryTimeoutMillis    the timeout after which the query is considered timeout and the original
     *                              {@link Promise} will be failed.
     * @param flush                 {@code true} if {@link Channel#flush()} should be called as well.
     * @return                      the {@link Future} that is notified once once the write completes.
     */
    final Future<Void> writeQuery(long queryTimeoutMillis,  boolean flush) {
        assert id == -1 : this.getClass().getSimpleName() + ".writeQuery(...) can only be executed once.";
        id = queryContextManager.add(nameServerAddr, this);

        // Ensure we remove the id from the QueryContextManager once the query completes.
        promise.asFuture().addListener(f -> {
            // Cancel the timeout task.
            Future<?> timeoutFuture = DnsQueryContext.this.timeoutFuture;
            if (timeoutFuture != null) {
                DnsQueryContext.this.timeoutFuture = null;
                timeoutFuture.cancel();
            }

            // Remove the id from the manager as soon as the query completes. This may be because of success,
            // failure or cancellation
            DnsQueryContext self = queryContextManager.remove(nameServerAddr, id);

            assert self == DnsQueryContext.this : "Removed DnsQueryContext is not the correct instance";
        });

        final DnsQuestion question = question();
        final DnsQuery query = newQuery(id, nameServerAddr);

        query.setRecursionDesired(recursionDesired);

        query.addRecord(DnsSection.QUESTION, question);

        for (DnsRecord record: additionals) {
            query.addRecord(DnsSection.ADDITIONAL, record);
        }

        if (optResource != null) {
            query.addRecord(DnsSection.ADDITIONAL, optResource);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} WRITE: {}, [{}: {}], {}",
                    channel, protocol(), id, nameServerAddr, question);
        }

        return sendQuery(nameServerAddr, query, queryTimeoutMillis, flush);
    }

    private Future<Void> sendQuery(final InetSocketAddress nameServerAddr, final DnsQuery query,
                                    final long queryTimeoutMillis, final boolean flush) {
        final Promise<Void> writePromise = channel.newPromise();
        if (channelReadyFuture.isSuccess()) {
            writeQuery(nameServerAddr, query, queryTimeoutMillis, flush, writePromise);
        } else if (channelReadyFuture.isFailed()) {
            Throwable cause = channelReadyFuture.cause();
            // the promise failed before so we should also fail this query.
            failQuery(query, cause, writePromise);
        } else {
            // The promise is not complete yet, let's delay the query.
            channelReadyFuture.addListener(future -> {
                if (future.isSuccess()) {
                    // If the query is done in a late fashion (as the channel was not ready yet) we always flush
                    // to ensure we did not race with a previous flush() that was done when the Channel was not
                    // ready yet.
                    writeQuery(nameServerAddr, query, queryTimeoutMillis, true, writePromise);
                } else {
                    failQuery(query, future.cause(), writePromise);
                }
            });
        }
        return writePromise.asFuture();
    }

    private void writeQuery(final InetSocketAddress nameServerAddr, final DnsQuery query, final long queryTimeoutMillis,
                            final boolean flush, Promise<Void> promise) {
        final Future<Void> writeFuture = flush ? channel.writeAndFlush(query) :
                channel.write(query);
        if (writeFuture.isDone()) {
            onQueryWriteCompletion(queryTimeoutMillis, writeFuture, promise);
        } else {
            writeFuture.addListener(f -> {
                onQueryWriteCompletion(queryTimeoutMillis, writeFuture, promise);
            });
        }
    }

    private void failQuery(DnsQuery query, Throwable cause, Promise<Void> writePromise) {
        try {
            promise.tryFailure(cause);
            writePromise.setFailure(cause);
        } catch (Throwable throwable) {
            SilentDispose.dispose(query, logger);
            throw throwable;
        }
        Resource.dispose(query);
    }

    private void onQueryWriteCompletion(final long queryTimeoutMillis,
                                        Future<Void> writeFuture, Promise<Void> writePromise) {
        if (!writeFuture.isSuccess()) {
            writePromise.setFailure(writeFuture.cause());
            finishFailure("failed to send a query '" + id + "' via " + protocol(), writeFuture.cause(), false);
            return;
        }
        writePromise.setSuccess(null);
        // Schedule a query timeout task if necessary.
        if (queryTimeoutMillis > 0) {
            timeoutFuture = channel.executor().schedule(() -> {
                if (promise.isDone()) {
                    // Received a response before the query times out.
                    return;
                }

                finishFailure("query '" + id + "' via " + protocol() + " timed out after " +
                        queryTimeoutMillis + " milliseconds", null, true);
                }, queryTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Notifies the original {@link Promise} that the response for the query was received.
     * This method takes ownership of passed {@link AddressedEnvelope}.
     */
    void finishSuccess(AddressedEnvelope<? extends DnsResponse, InetSocketAddress> envelope) {
        final DnsResponse res = envelope.content();
        if (res.count(DnsSection.QUESTION) != 1) {
            logger.warn("{} Received a DNS response with invalid number of questions. Expected: 1, found: {}",
                    channel, envelope);
        } else if (!question().equals(res.recordAt(DnsSection.QUESTION))) {
            logger.warn("{} Received a mismatching DNS response. Expected: [{}], found: {}",
                    channel, question(), envelope);
        } else if (trySuccess(envelope)) {
            return; // Ownership transferred, don't release
        }
        Resource.dispose(envelope);
    }

    @SuppressWarnings("unchecked")
    private boolean trySuccess(AddressedEnvelope<? extends DnsResponse, InetSocketAddress> envelope) {
        return promise.trySuccess((AddressedEnvelope<DnsResponse, InetSocketAddress>) envelope);
    }

    /**
     * Notifies the original {@link Promise} that the query completes because of an failure.
     */
    final boolean finishFailure(String message, Throwable cause, boolean timeout) {
        if (promise.isDone()) {
            return false;
        }
        final DnsQuestion question = question();

        final StringBuilder buf = new StringBuilder(message.length() + 128);
        buf.append('[')
           .append(id)
           .append(": ")
           .append(nameServerAddr)
           .append("] ")
           .append(question)
           .append(' ')
           .append(message)
           .append(" (no stack trace available)");

        final DnsNameResolverException e;
        if (timeout) {
            // This was caused by an timeout so use DnsNameResolverTimeoutException to allow the user to
            // handle it special (like retry the query).
            e = new DnsNameResolverTimeoutException(nameServerAddr, question, buf.toString());
        } else {
            e = new DnsNameResolverException(nameServerAddr, question, buf.toString(), cause);
        }
        return promise.tryFailure(e);
    }
}
