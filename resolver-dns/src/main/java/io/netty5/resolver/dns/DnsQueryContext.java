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

import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.dns.AbstractDnsOptPseudoRrRecord;
import io.netty5.handler.codec.dns.DnsOptPseudoRecord;
import io.netty5.handler.codec.dns.DnsQuery;
import io.netty5.handler.codec.dns.DnsQuestion;
import io.netty5.handler.codec.dns.DnsRecord;
import io.netty5.handler.codec.dns.DnsRecordType;
import io.netty5.handler.codec.dns.DnsResponse;
import io.netty5.handler.codec.dns.DnsSection;
import io.netty5.handler.codec.dns.TcpDnsQueryEncoder;
import io.netty5.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty5.util.ReferenceCounted;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.SystemPropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

abstract class DnsQueryContext {

    private static final Logger logger = LoggerFactory.getLogger(DnsQueryContext.class);

    private static final long ID_REUSE_ON_TIMEOUT_DELAY_MILLIS;

    static {
        ID_REUSE_ON_TIMEOUT_DELAY_MILLIS =
                SystemPropertyUtil.getLong("io.netty5.resolver.dns.idReuseOnTimeoutDelayMillis", 10000);
        logger.debug("-Dio.netty5.resolver.dns.idReuseOnTimeoutDelayMillis: {}", ID_REUSE_ON_TIMEOUT_DELAY_MILLIS);
    }

    private static final TcpDnsQueryEncoder TCP_ENCODER = new TcpDnsQueryEncoder();

    private final Future<? extends Channel> channelReadyFuture;
    private final Channel channel;
    private final InetSocketAddress nameServerAddr;
    private final DnsQueryContextManager queryContextManager;
    private final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise;

    private final DnsQuestion question;
    private final DnsRecord[] additionals;
    private final DnsRecord optResource;

    private final boolean recursionDesired;

    private final Bootstrap socketBootstrap;

    private final boolean retryWithTcpOnTimeout;
    private final long queryTimeoutMillis;

    private volatile Future<?> timeoutFuture;

    private int id = Integer.MIN_VALUE;

    DnsQueryContext(Channel channel,
                    Future<? extends Channel> channelReadyFuture,
                    InetSocketAddress nameServerAddr,
                    DnsQueryContextManager queryContextManager,
                    int maxPayLoadSize,
                    boolean recursionDesired,
                    long queryTimeoutMillis,
                    DnsQuestion question,
                    DnsRecord[] additionals,
                    Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise,
                    Bootstrap socketBootstrap,
                    boolean retryWithTcpOnTimeout) {
        this.channel = requireNonNull(channel, "channel");
        this.queryContextManager = requireNonNull(queryContextManager, "queryContextManager");
        this.channelReadyFuture = requireNonNull(channelReadyFuture, "channelReadyFuture");
        this.nameServerAddr = requireNonNull(nameServerAddr, "nameServerAddr");
        this.question = requireNonNull(question, "question");
        this.additionals = requireNonNull(additionals, "additionals");
        this.promise = requireNonNull(promise, "promise");
        this.recursionDesired = recursionDesired;
        this.queryTimeoutMillis = queryTimeoutMillis;
        this.socketBootstrap = socketBootstrap;
        this.retryWithTcpOnTimeout = retryWithTcpOnTimeout;

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
     * Write the query and return the {@link Future} that is completed once the write completes.
     *
     * @param flush                 {@code true} if {@link Channel#flush()} should be called as well.
     * @return                      the {@link Future} that is notified once once the write completes.
     */
    final Future<Void> writeQuery(boolean flush) {
        assert id == Integer.MIN_VALUE : this.getClass().getSimpleName() +
                ".writeQuery(...) can only be executed once.";

        if ((id = queryContextManager.add(nameServerAddr, this)) == -1) {
            // We did exhaust the id space, fail the query
            IllegalStateException e = new IllegalStateException("query ID space exhausted: " + question());
            finishFailure("failed to send a query via " + protocol(), e, false);
            return channel.newFailedFuture(e);
        }

        // Ensure we remove the id from the QueryContextManager once the query completes.
        promise.asFuture().addListener(f -> {
            // Cancel the timeout task.
            Future<?> timeoutFuture = DnsQueryContext.this.timeoutFuture;
            if (timeoutFuture != null) {
                DnsQueryContext.this.timeoutFuture = null;
                timeoutFuture.cancel();
            }
            Throwable cause = f.cause();
            if (cause instanceof DnsNameResolverTimeoutException || cause instanceof CancellationException) {
                // This query was failed due a timeout or cancellation. Let's delay the removal of the id to reduce
                // the risk of reusing the same id again while the remote nameserver might send the response after
                // the timeout.
                channel.executor().schedule(new Runnable() {
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

        return sendQuery(query, flush);
    }

    private void removeFromContextManager(InetSocketAddress nameServerAddr) {
        DnsQueryContext self = queryContextManager.remove(nameServerAddr, id);

        assert self == this : "Removed DnsQueryContext is not the correct instance";
    }

    private Future<Void> sendQuery(final DnsQuery query, final boolean flush) {
                final Promise<Void> writePromise = channel.newPromise();
        if (channelReadyFuture.isSuccess()) {
            writeQuery(query, flush, writePromise);
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
                        writeQuery(query, true, writePromise);
                    } else {
                        Throwable cause = future.cause();
                        failQuery(query, cause, writePromise);
                    }
                });
        }
        return writePromise.asFuture();
    }

    private void writeQuery(final DnsQuery query, final boolean flush, Promise<Void> promise) {
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
    void finishSuccess(AddressedEnvelope<? extends DnsResponse, InetSocketAddress> envelope, boolean truncated) {
        // Check if the response was not truncated or if a fallback to TCP is possible.
        if (!truncated || !retryWithTcp(envelope)) {
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
            if (retryWithTcpOnTimeout && retryWithTcp(e)) {
                // We did successfully retry with TCP.
                return false;
            }
        } else {
            e = new DnsNameResolverException(nameServerAddr, question, buf.toString(), cause);
        }
        return promise.tryFailure(e);
    }

    /**
     * Retry the original query with TCP if possible.
     *
     * @param originalResult    the result of the original {@link DnsQueryContext}.
     * @return                  {@code true} if retry via TCP is supported and so the ownership of
     *                          {@code originalResult} was transferred, {@code false} otherwise.
     */
    private boolean retryWithTcp(final Object originalResult) {
        if (socketBootstrap == null) {
            return false;
        }

        socketBootstrap.connect(nameServerAddr).addListener(future -> {
            if (!future.isSuccess()) {
                logger.debug("{} Unable to fallback to TCP [{}: {}]",
                        channel, id, nameServerAddr, future.cause());

                // TCP fallback failed, just use the truncated response or error.
                finishOriginal(originalResult, future);
                return;
            }
            final Channel tcpCh = future.getNow();
            Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise =
                    tcpCh.executor().newPromise();
            final TcpDnsQueryContext tcpCtx = new TcpDnsQueryContext(tcpCh, channelReadyFuture,
                    (InetSocketAddress) tcpCh.remoteAddress(), queryContextManager, 0,
                    recursionDesired, queryTimeoutMillis, question(), additionals, promise);
            tcpCh.pipeline().addLast(TCP_ENCODER);
            tcpCh.pipeline().addLast(new TcpDnsResponseDecoder());
            tcpCh.pipeline().addLast(new ChannelHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    Channel tcpCh = ctx.channel();
                    DnsResponse response = (DnsResponse) msg;
                    int queryId = response.id();

                    if (logger.isDebugEnabled()) {
                        logger.debug("{} RECEIVED: TCP [{}: {}], {}", tcpCh, queryId,
                                tcpCh.remoteAddress(), response);
                    }

                    DnsQueryContext foundCtx = queryContextManager.get(nameServerAddr, queryId);
                    if (foundCtx != null && foundCtx.isDone()) {
                        logger.debug("{} Received a DNS response for a query that was timed out or cancelled " +
                                ": TCP [{}: {}]", tcpCh, queryId, nameServerAddr);
                        response.release();
                    } else if (foundCtx == tcpCtx) {
                        tcpCtx.finishSuccess(new AddressedEnvelopeAdapter(
                                (InetSocketAddress) ctx.channel().remoteAddress(),
                                (InetSocketAddress) ctx.channel().localAddress(),
                                response), false);
                    } else {
                        response.release();
                        tcpCtx.finishFailure("Received TCP DNS response with unexpected ID", null, false);
                        if (logger.isDebugEnabled()) {
                            logger.debug("{} Received a DNS response with an unexpected ID: TCP [{}: {}]",
                                    tcpCh, queryId, tcpCh.remoteAddress());
                        }
                    }
                }

                @Override
                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    if (tcpCtx.finishFailure(
                            "TCP fallback error", cause, false) && logger.isDebugEnabled()) {
                        logger.debug("{} Error during processing response: TCP [{}: {}]",
                                ctx.channel(), id,
                                ctx.channel().remoteAddress(), cause);
                    }
                }
            });

            promise.asFuture().addListener(
                    future1 -> {
                        if (future1.isSuccess()) {
                            finishSuccess(future1.getNow(), false);
                            // Release the original result.
                            Resource.dispose(originalResult);
                        } else {
                            // TCP fallback failed, just use the truncated response or error.
                            finishOriginal(originalResult, future1);
                        }
                        tcpCh.close();
                    });
            tcpCtx.writeQuery(true);
        });
        return true;
    }

    @SuppressWarnings("unchecked")
    private void finishOriginal(Object originalResult, Future<?> future) {
        if (originalResult instanceof Throwable) {
            Throwable error = (Throwable) originalResult;
            error.addSuppressed(future.cause());
            promise.tryFailure(error);
        } else {
            finishSuccess((AddressedEnvelope<? extends DnsResponse, InetSocketAddress>) originalResult, false);
        }
    }

    private static final class AddressedEnvelopeAdapter
            implements AddressedEnvelope<DnsResponse, InetSocketAddress>, ReferenceCounted {
        private final InetSocketAddress sender;
        private final InetSocketAddress recipient;
        private final DnsResponse response;

        AddressedEnvelopeAdapter(InetSocketAddress sender, InetSocketAddress recipient, DnsResponse response) {
            this.sender = sender;
            this.recipient = recipient;
            this.response = response;
        }

        @Override
        public DnsResponse content() {
            return response;
        }

        @Override
        public InetSocketAddress sender() {
            return sender;
        }

        @Override
        public InetSocketAddress recipient() {
            return recipient;
        }

        @Override
        public int refCnt() {
            return response.refCnt();
        }

        @Override
        public AddressedEnvelopeAdapter retain() {
            response.retain();
            return this;
        }

        @Override
        public AddressedEnvelopeAdapter retain(int increment) {
            response.retain(increment);
            return this;
        }

        @Override
        public AddressedEnvelopeAdapter touch() {
            response.touch();
            return this;
        }

        @Override
        public AddressedEnvelopeAdapter touch(Object hint) {
            response.touch(hint);
            return this;
        }

        @Override
        public boolean release() {
            return response.release();
        }

        @Override
        public boolean release(int decrement) {
            return response.release(decrement);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof AddressedEnvelope)) {
                return false;
            }

            @SuppressWarnings("unchecked")
            final AddressedEnvelope<?, SocketAddress> that = (AddressedEnvelope<?, SocketAddress>) obj;
            if (sender() == null) {
                if (that.sender() != null) {
                    return false;
                }
            } else if (!sender().equals(that.sender())) {
                return false;
            }

            if (recipient() == null) {
                if (that.recipient() != null) {
                    return false;
                }
            } else if (!recipient().equals(that.recipient())) {
                return false;
            }

            return response.equals(obj);
        }

        @Override
        public int hashCode() {
            int hashCode = response.hashCode();
            if (sender() != null) {
                hashCode = hashCode * 31 + sender().hashCode();
            }
            if (recipient() != null) {
                hashCode = hashCode * 31 + recipient().hashCode();
            }
            return hashCode;
        }
    }
}
