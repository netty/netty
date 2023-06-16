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
package io.netty.resolver.dns;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.dns.AbstractDnsOptPseudoRrRecord;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

abstract class DnsQueryContext {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsQueryContext.class);
    private static final long ID_REUSE_ON_TIMEOUT_DELAY_MILLIS;

    static {
        ID_REUSE_ON_TIMEOUT_DELAY_MILLIS =
                SystemPropertyUtil.getLong("io.netty.resolver.dns.idReuseOnTimeoutDelayMillis", 10000);
        logger.debug("-Dio.netty.resolver.dns.idReuseOnTimeoutDelayMillis: {}", ID_REUSE_ON_TIMEOUT_DELAY_MILLIS);
    }

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
        this.channel = checkNotNull(channel, "channel");
        this.queryContextManager = checkNotNull(queryContextManager, "queryContextManager");
        this.channelReadyFuture = checkNotNull(channelReadyFuture, "channelReadyFuture");
        this.nameServerAddr = checkNotNull(nameServerAddr, "nameServerAddr");
        this.question = checkNotNull(question, "question");
        this.additionals = checkNotNull(additionals, "additionals");
        this.promise = checkNotNull(promise, "promise");
        this.recursionDesired = recursionDesired;

        if (maxPayLoadSize > 0 &&
                // Only add the extra OPT record if there is not already one. This is required as only one is allowed
                // as per RFC:
                //  - https://datatracker.ietf.org/doc/html/rfc6891#section-6.1.1
                !hasOptRecord(additionals)) {
            optResource = new AbstractDnsOptPseudoRrRecord(maxPayLoadSize, 0, 0) {
                // We may want to remove this in the future and let the user just specify the opt record in the query.
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
     * Returns {@code true} if the query was completed already.
     *
     * @return {@code true} if done.
     */
    final boolean isDone() {
        return promise.isDone();
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
     * Write the query and return the {@link ChannelFuture} that is completed once the write completes.
     *
     * @param queryTimeoutMillis    the timeout after which the query is considered timeout and the original
     *                              {@link Promise} will be failed.
     * @param flush                 {@code true} if {@link Channel#flush()} should be called as well.
     * @return                      the {@link ChannelFuture} that is notified once once the write completes.
     */
    final ChannelFuture writeQuery(long queryTimeoutMillis, boolean flush) {
        assert id == -1 : this.getClass().getSimpleName() + ".writeQuery(...) can only be executed once.";
        id = queryContextManager.add(nameServerAddr, this);

        // Ensure we remove the id from the QueryContextManager once the query completes.
        promise.addListener(new FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>() {
            @Override
            public void operationComplete(Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future) {
                // Cancel the timeout task.
                Future<?> timeoutFuture = DnsQueryContext.this.timeoutFuture;
                if (timeoutFuture != null) {
                    DnsQueryContext.this.timeoutFuture = null;
                    timeoutFuture.cancel(false);
                }

                Throwable cause = future.cause();
                if (cause instanceof DnsNameResolverTimeoutException || cause instanceof CancellationException) {
                    // This query was failed due a timeout or cancellation. Let's delay the removal of the id to reduce
                    // the risk of reusing the same id again while the remote nameserver might send the response after
                    // the timeout.
                    channel.eventLoop().schedule(new Runnable() {
                        @Override
                        public void run() {
                            removeFromContextManager(nameServerAddr);
                        }
                    }, ID_REUSE_ON_TIMEOUT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
                } else {
                    // Remove the id from the manager as soon as the query completes. This may be because of success,
                    // failure or cancellation
                    removeFromContextManager(nameServerAddr);
                }
            }
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

    private void removeFromContextManager(InetSocketAddress nameServerAddr) {
        DnsQueryContext self = queryContextManager.remove(nameServerAddr, id);

        assert self == this : "Removed DnsQueryContext is not the correct instance";
    }

    private ChannelFuture sendQuery(final InetSocketAddress nameServerAddr, final DnsQuery query,
                                    final long queryTimeoutMillis, final boolean flush) {
        final ChannelPromise writePromise = channel.newPromise();
        if (channelReadyFuture.isSuccess()) {
            writeQuery(nameServerAddr, query, queryTimeoutMillis, flush, writePromise);
        } else {
            Throwable cause = channelReadyFuture.cause();
            if (cause != null) {
                // the promise failed before so we should also fail this query.
                failQuery(query, cause, writePromise);
            } else {
                // The promise is not complete yet, let's delay the query.
                channelReadyFuture.addListener(new GenericFutureListener<Future<? super Channel>>() {
                    @Override
                    public void operationComplete(Future<? super Channel> future) {
                        if (future.isSuccess()) {
                            // If the query is done in a late fashion (as the channel was not ready yet) we always flush
                            // to ensure we did not race with a previous flush() that was done when the Channel was not
                            // ready yet.
                            writeQuery(nameServerAddr, query, queryTimeoutMillis, true, writePromise);
                        } else {
                            Throwable cause = future.cause();
                            failQuery(query, cause, writePromise);
                        }
                    }
                });
            }
        }
        return writePromise;
    }

    private void failQuery(DnsQuery query, Throwable cause, ChannelPromise writePromise) {
        try {
            promise.tryFailure(cause);
            writePromise.tryFailure(cause);
        } finally {
            ReferenceCountUtil.release(query);
        }
    }

    private void writeQuery(final InetSocketAddress nameServerAddr, final DnsQuery query, final long queryTimeoutMillis,
                            final boolean flush, ChannelPromise promise) {
        final ChannelFuture writeFuture = flush ? channel.writeAndFlush(query, promise) :
                channel.write(query, promise);
        if (writeFuture.isDone()) {
            onQueryWriteCompletion(queryTimeoutMillis, writeFuture);
        } else {
            writeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    onQueryWriteCompletion(queryTimeoutMillis, writeFuture);
                }
            });
        }
    }

    private void onQueryWriteCompletion(final long queryTimeoutMillis,
                                        ChannelFuture writeFuture) {
        if (!writeFuture.isSuccess()) {
            finishFailure("failed to send a query '" + id + "' via " + protocol(), writeFuture.cause(), false);
            return;
        }

        // Schedule a query timeout task if necessary.
        if (queryTimeoutMillis > 0) {
            timeoutFuture = channel.eventLoop().schedule(new Runnable() {
                @Override
                public void run() {
                    if (promise.isDone()) {
                        // Received a response before the query times out.
                        return;
                    }

                    finishFailure("query '" + id + "' via " + protocol() + " timed out after " +
                            queryTimeoutMillis + " milliseconds", null, true);
                }
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
        envelope.release();
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
            // This was caused by a timeout so use DnsNameResolverTimeoutException to allow the user to
            // handle it special (like retry the query).
            e = new DnsNameResolverTimeoutException(nameServerAddr, question, buf.toString());
        } else {
            e = new DnsNameResolverException(nameServerAddr, question, buf.toString(), cause);
        }
        return promise.tryFailure(e);
    }
}
