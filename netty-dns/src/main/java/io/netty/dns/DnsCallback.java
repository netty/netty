/*
 * Copyright 2013 The Netty Project
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
package io.netty.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.dns.decoder.RecordDecoderFactory;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link DnsCallback} can represent a single {@link DnsQuery} or a group of queries, and when called returns a
 * {@link List} of all cached responses that answer the first query that is responded to. This class is only used for
 * new queries, which are created when desired information has not been cached or has expired. If multiple queries are
 * using a single instance of {@link DnsCallback} this means that all the {@link DnsQuery}s have the same id.
 * {@link DnsCallback} will listen for the first {@link DnsResponse} with the shared id, and will return the result of
 * this {@link DnsResponse} (assuming it is valid) when called. This is useful when multiple queries can be used to
 * receive one similar piece of data (i.e. querying for both A and AAAA records when a user wants to connect to a server
 * and either an IPv4 or IPv6 address is sufficient). If a {@link DnsCallback} fails, null will be returned. For
 * obtaining single values, as opposed to a {@link List}, {@link DnsSingleResultCallback} is used.
 *
 * @param <T>
 *            a {@link List} of all answers for a specified type (i.e. if type is A, the {@link List} would be for
 *            {@link ByteBuf}s)
 */
public class DnsCallback<T extends List<?>> implements Callable<T> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsCallback.class);
    private static final List<Object> DEFAULT = new ArrayList<Object>();
    private static final Map<Integer, DnsCallback<?>> callbacks = new HashMap<Integer, DnsCallback<?>>();

    /**
     * Called by the {@link DnsInboundMessageHandler} when a {@link DnsResponse} is received from a DNS server. This
     * method checks all existing {@link DnsCallback}s for a callback with an id matching the {@link DnsResponse}s id
     * and sets the value for the callback as one or more of the response's resource records (if the response contains
     * valid resource records).
     *
     * @param response
     *            the {@link DnsResponse} received from the DNS server when queried
     */
    static void finish(DnsResponse response) {
        DnsCallback<?> callback = callbacks.get(response.getHeader().getId());
        if (callback != null) {
            if (response.getHeader().getResponseCode() != 0) {
                if (callback.failsIncremented() >= callback.queries.length) {
                    callbacks.remove(response.getHeader().getId());
                    callback.complete();
                    return;
                }
            }
            List<DnsResource> resources = new ArrayList<DnsResource>();
            resources.addAll(response.getAnswers());
            resources.addAll(response.getAuthorityResources());
            resources.addAll(response.getAdditionalResources());
            for (int i = 0; i < resources.size(); i++) {
                DnsResource resource = resources.get(i);
                for (int n = 0; n < callback.queries.length; n++) {
                    Object result = RecordDecoderFactory.getFactory().decode(resource.type(), response, resource);
                    if (result != null) {
                        callback.resolver.cache().submitRecord(resource.name(), resource.type(), resource.timeToLive(),
                                result);
                        if (callback.queries[n].getQuestions().get(0).type() == resource.type()) {
                            callbacks.remove(response.getHeader().getId());
                            callback.flagValid(resource.type());
                        }
                    }
                }
            }
            callback.complete();
        }
    }

    private final AtomicInteger fails = new AtomicInteger();
    private final DnsAsynchronousResolver resolver;
    private final DnsQuery[] queries;

    @SuppressWarnings("unchecked")
    private T result = (T) DEFAULT;

    private int serverIndex;
    private int validType = -1;

    /**
     * Constructs a {@link DnsCallback} with a specified DNS resolver, DNS server index, and an array of (or a single)
     * query.
     *
     * @param resolver
     *            the {@link DnsAsynchronousResolver} making the query
     * @param serverIndex
     *            the index at which the DNS server address is located in {@link DnsAsynchronousResolver#dnsServers}, or
     *            -1 if it is not in the {@link List}
     * @param queries
     *            the {@link DnsQuery}(s) this callback is listening to for responses
     */
    DnsCallback(DnsAsynchronousResolver resolver, int serverIndex, DnsQuery... queries) {
        if (queries == null) {
            throw new NullPointerException("Argument 'queries' cannot be null.");
        }
        if (queries.length == 0) {
            throw new IllegalArgumentException("Argument 'queries' must contain minimum one valid DnsQuery.");
        }
        callbacks.put(queries[0].getHeader().getId(), this);
        this.resolver = resolver;
        this.queries = queries;
        this.serverIndex = serverIndex;
    }

    /**
     * Returns the {@link DnsAsynchronousResolver} attached to this {@link DnsCallback}.
     */
    public DnsAsynchronousResolver resolver() {
        return resolver;
    }

    /**
     * Called when a {@link DnsResponse} contains an invalid response code (not 0). Increments a counter, and cancels
     * this callback when the counter equals the total number of {@link DnsQuery}s (meaning all queries have failed).
     */
    private synchronized int failsIncremented() {
        return fails.getAndIncrement();
    }

    /**
     * Called when a response has been decided for this callback. Sets the response and notifies the callback that a
     * response has been set so that it may stop blocking on {@link #call()}.
     */
    @SuppressWarnings("unchecked")
    private void complete() {
        if (validType != -1) {
            DnsQuestion question = queries[0].getQuestions().get(0);
            result = (T) resolver.cache().getRecords(question.name(), validType);
        } else {
            result = null;
        }
        synchronized (this) {
            notify();
        }
    }

    /**
     * Notifies {@link DnsCallback} that a query returned a valid result for the given resource record type.
     *
     * @param validType
     *            the resource record type that should be returned by this {@link DnsCallback} (i.e. AAAA)
     */
    private void flagValid(int validType) {
        this.validType = validType;
    }

    /**
     * Called in the event that a response from a DNS server times out. This method uses the next DNS server in line, if
     * one exists, and attempts to re-send all queries and listen again for a response.
     */
    private void nextDns() {
        if (serverIndex == -1) {
            result = null;
        } else {
            InetAddress dnsServerAddress = resolver.getDnsServer(++serverIndex);
            if (dnsServerAddress == null) {
                result = null;
            } else {
                try {
                    Channel channel = resolver.channelForAddress(dnsServerAddress);
                    for (int i = 0; i < queries.length; i++) {
                        channel.write(queries[i]).sync();
                    }
                } catch (Exception e) {
                    if (logger.isErrorEnabled()) {
                        logger.error(
                                "Failed to write to next DNS server in queue with address "
                                        + dnsServerAddress.getHostAddress(), e);
                    }
                    result = null;
                }
            }
        }
    }

    /**
     * Returns the result for a the queries when it is sent from a DNS server. Blocks until this response has been sent,
     * or all DNS servers have failed, in which case null is returned.
     */
    @Override
    public T call() throws InterruptedException {
        while (result == DEFAULT) {
            synchronized (this) {
                if (result == DEFAULT) {
                    wait(DnsAsynchronousResolver.REQUEST_TIMEOUT);
                }
            }
            if (result == DEFAULT) {
                nextDns();
            }
        }
        return result == DEFAULT ? null : result;
    }

}
