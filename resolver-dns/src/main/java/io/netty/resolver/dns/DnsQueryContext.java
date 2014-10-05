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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

final class DnsQueryContext extends DefaultPromise<DnsResponse> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsQueryContext.class);

    private final DnsNameResolver parent;
    private final int id;
    private final DnsQuestion question;
    private final Iterator<InetSocketAddress> nameServerAddresses;

    private final boolean recursionDesired;
    private final int maxTries;
    private int remainingTries;
    private volatile ScheduledFuture<?> timeoutFuture;
    private StringBuilder trace;

    DnsQueryContext(DnsNameResolver parent,
                    Iterable<InetSocketAddress> nameServerAddresses,
                    DnsQuestion question) throws UnknownHostException {

        super(parent.executor());

        this.parent = parent;
        this.question = question;

        id = allocateId();
        recursionDesired = parent.isRecursionDesired();
        maxTries = parent.maxTries();
        remainingTries = maxTries;

        this.nameServerAddresses = nameServerAddresses.iterator();
    }

    private int allocateId() throws UnknownHostException {
        int id = ThreadLocalRandom.current().nextInt(parent.promises.length());
        final int maxTries = parent.promises.length() << 1;
        int tries = 0;
        for (;;) {
            if (parent.promises.compareAndSet(id, null, this)) {
                return id;
            }

            id = id + 1 & 0xFFFF;

            if (++ tries >= maxTries) {
                throw new UnknownHostException("query ID space exhausted: " + question);
            }
        }
    }

    DnsQuestion question() {
        return question;
    }

    ScheduledFuture<?> timeoutFuture() {
        return timeoutFuture;
    }

    void query() {
        final DnsQuestion question = this.question;

        if (remainingTries <= 0 || !nameServerAddresses.hasNext()) {
            parent.promises.lazySet(id, null);

            int tries = maxTries - remainingTries;
            UnknownHostException cause;
            if (tries > 1) {
                cause = new UnknownHostException(
                        "failed to resolve " + question + " after " + tries + " attempts:" +
                        trace);
            } else {
                cause = new UnknownHostException("failed to resolve " + question + ':' + trace);
            }

            cache(question, cause);
            setFailure(cause);
            return;
        }

        remainingTries --;

        final InetSocketAddress nameServerAddr = nameServerAddresses.next();
        final DnsQuery query = new DnsQuery(id, nameServerAddr);
        query.addQuestion(question);
        query.header().setRecursionDesired(recursionDesired);

        logger.debug("Sending {} to {}", question, nameServerAddr);

        final ChannelFuture writeFuture = parent.ch.writeAndFlush(query);
        if (writeFuture.isDone()) {
            onQueryWriteCompletion(writeFuture, nameServerAddr);
        } else {
            writeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    onQueryWriteCompletion(writeFuture, nameServerAddr);
                }
            });
        }
    }

    private void onQueryWriteCompletion(ChannelFuture writeFuture, final InetSocketAddress nameServerAddr) {
        if (!writeFuture.isSuccess()) {
            retry(nameServerAddr, "failed to send a query: " + writeFuture.cause());
            return;
        }

        // Schedule a query timeout task if necessary.
        final int queryTimeoutMillis = parent.timeoutMillis();
        if (queryTimeoutMillis > 0) {
            timeoutFuture = parent.ch.eventLoop().schedule(new OneTimeTask() {
                @Override
                public void run() {
                    if (isDone()) {
                        // Received a response before the query times out.
                        return;
                    }

                    retry(nameServerAddr, "query timed out after " + queryTimeoutMillis + " milliseconds");
                }
            }, queryTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    void retry(InetSocketAddress nameServerAddr, String message) {
        if (trace == null) {
            trace = new StringBuilder(128);
        }

        trace.append(StringUtil.NEWLINE);
        trace.append("\tfrom ");
        trace.append(nameServerAddr);
        trace.append(": ");
        trace.append(message);
        query();
    }

    private void cache(final DnsQuestion question, Throwable cause) {
        final int negativeTtl = parent.negativeTtl();
        if (negativeTtl == 0) {
            return;
        }

        parent.queryCache.put(question, cause);
        parent.scheduleCacheExpiration(question, negativeTtl);
    }
}
