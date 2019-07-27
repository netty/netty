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

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.AbstractDnsOptPseudoRrRecord;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

final class DnsQueryContext implements FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsQueryContext.class);

    private final DnsNameResolver parent;
    private final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise;
    private final int id;
    private final DnsQuestion question;
    private final DnsRecord[] additionals;
    private final DnsRecord optResource;
    private final InetSocketAddress nameServerAddr;

    private final boolean recursionDesired;
    private volatile ScheduledFuture<?> timeoutFuture;

    DnsQueryContext(DnsNameResolver parent,
                    InetSocketAddress nameServerAddr,
                    DnsQuestion question,
                    DnsRecord[] additionals,
                    Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise) {

        this.parent = checkNotNull(parent, "parent");
        this.nameServerAddr = checkNotNull(nameServerAddr, "nameServerAddr");
        this.question = checkNotNull(question, "question");
        this.additionals = checkNotNull(additionals, "additionals");
        this.promise = checkNotNull(promise, "promise");
        recursionDesired = parent.isRecursionDesired();
        id = parent.queryContextManager.add(this);

        // Ensure we remove the id from the QueryContextManager once the query completes.
        promise.addListener(this);

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

    void query(boolean flush, ChannelPromise writePromise) {
        final DnsQuestion question = question();
        final InetSocketAddress nameServerAddr = nameServerAddr();
        final DatagramDnsQuery query = new DatagramDnsQuery(null, nameServerAddr, id);

        query.setRecursionDesired(recursionDesired);

        query.addRecord(DnsSection.QUESTION, question);

        for (DnsRecord record: additionals) {
            query.addRecord(DnsSection.ADDITIONAL, record);
        }

        if (optResource != null) {
            query.addRecord(DnsSection.ADDITIONAL, optResource);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} WRITE: [{}: {}], {}", parent.ch, id, nameServerAddr, question);
        }

        sendQuery(query, flush, writePromise);
    }

    private void sendQuery(final DnsQuery query, final boolean flush, final ChannelPromise writePromise) {
        if (parent.channelFuture.isDone()) {
            writeQuery(query, flush, writePromise);
        } else {
            parent.channelFuture.addListener(new GenericFutureListener<Future<? super Channel>>() {
                @Override
                public void operationComplete(Future<? super Channel> future) {
                    if (future.isSuccess()) {
                        // If the query is done in a late fashion (as the channel was not ready yet) we always flush
                        // to ensure we did not race with a previous flush() that was done when the Channel was not
                        // ready yet.
                        writeQuery(query, true, writePromise);
                    } else {
                        Throwable cause = future.cause();
                        promise.tryFailure(cause);
                        writePromise.setFailure(cause);
                    }
                }
            });
        }
    }

    private void writeQuery(final DnsQuery query, final boolean flush, final ChannelPromise writePromise) {
        final ChannelFuture writeFuture = flush ? parent.ch.writeAndFlush(query, writePromise) :
                parent.ch.write(query, writePromise);
        if (writeFuture.isDone()) {
            onQueryWriteCompletion(writeFuture);
        } else {
            writeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    onQueryWriteCompletion(writeFuture);
                }
            });
        }
    }

    private void onQueryWriteCompletion(ChannelFuture writeFuture) {
        if (!writeFuture.isSuccess()) {
            setFailure("failed to send a query", writeFuture.cause());
            return;
        }

        // Schedule a query timeout task if necessary.
        final long queryTimeoutMillis = parent.queryTimeoutMillis();
        if (queryTimeoutMillis > 0) {
            timeoutFuture = parent.ch.eventLoop().schedule(new Runnable() {
                @Override
                public void run() {
                    if (promise.isDone()) {
                        // Received a response before the query times out.
                        return;
                    }

                    setFailure("query timed out after " + queryTimeoutMillis + " milliseconds", null);
                }
            }, queryTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    void finish(AddressedEnvelope<? extends DnsResponse, InetSocketAddress> envelope) {
        final DnsResponse res = envelope.content();
        if (res.count(DnsSection.QUESTION) != 1) {
            logger.warn("Received a DNS response with invalid number of questions: {}", envelope);
            return;
        }

        if (!question().equals(res.recordAt(DnsSection.QUESTION))) {
            logger.warn("Received a mismatching DNS response: {}", envelope);
            return;
        }

        setSuccess(envelope);
    }

    private void setSuccess(AddressedEnvelope<? extends DnsResponse, InetSocketAddress> envelope) {
        Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise = this.promise;
        @SuppressWarnings("unchecked")
        AddressedEnvelope<DnsResponse, InetSocketAddress> castResponse =
                (AddressedEnvelope<DnsResponse, InetSocketAddress>) envelope.retain();
        if (!promise.trySuccess(castResponse)) {
            // We failed to notify the promise as it was failed before, thus we need to release the envelope
            envelope.release();
        }
    }

    private void setFailure(String message, Throwable cause) {
        final InetSocketAddress nameServerAddr = nameServerAddr();

        final StringBuilder buf = new StringBuilder(message.length() + 64);
        buf.append('[')
           .append(nameServerAddr)
           .append("] ")
           .append(message)
           .append(" (no stack trace available)");

        final DnsNameResolverException e;
        if (cause == null) {
            // This was caused by an timeout so use DnsNameResolverTimeoutException to allow the user to
            // handle it special (like retry the query).
            e = new DnsNameResolverTimeoutException(nameServerAddr, question(), buf.toString());
        } else {
            e = new DnsNameResolverException(nameServerAddr, question(), buf.toString(), cause);
        }
        promise.tryFailure(e);
    }

    @Override
    public void operationComplete(Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future) {
        // Cancel the timeout task.
        final ScheduledFuture<?> timeoutFuture = this.timeoutFuture;
        if (timeoutFuture != null) {
            this.timeoutFuture = null;
            timeoutFuture.cancel(false);
        }

        // Remove the id from the manager as soon as the query completes. This may be because of success, failure or
        // cancellation
        parent.queryContextManager.remove(nameServerAddr, id);
    }
}
