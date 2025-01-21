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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.dns.AbstractDnsOptPseudoRrRecord;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsQueryEncoder;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
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

    private static final TcpDnsQueryEncoder TCP_ENCODER = new TcpDnsQueryEncoder();

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
        this.channel = checkNotNull(channel, "channel");
        this.queryContextManager = checkNotNull(queryContextManager, "queryContextManager");
        this.nameServerAddr = checkNotNull(nameServerAddr, "nameServerAddr");
        this.question = checkNotNull(question, "question");
        this.additionals = checkNotNull(additionals, "additionals");
        this.promise = checkNotNull(promise, "promise");
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
     * @param flush                 {@code true} if {@link Channel#flush()} should be called as well.
     * @return                      the {@link ChannelFuture} that is notified once once the write completes.
     */
    final ChannelFuture writeQuery(boolean flush) {
        assert id == Integer.MIN_VALUE : this.getClass().getSimpleName() +
                ".writeQuery(...) can only be executed once.";

        if ((id = queryContextManager.add(nameServerAddr, this)) == -1) {
            // We did exhaust the id space, fail the query
            IllegalStateException e = new IllegalStateException("query ID space exhausted: " + question());
            finishFailure("failed to send a query via " + protocol(), e, false);
            return channel.newFailedFuture(e);
        }

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

        return sendQuery(query, flush);
    }

    private void removeFromContextManager(InetSocketAddress nameServerAddr) {
        DnsQueryContext self = queryContextManager.remove(nameServerAddr, id);

        assert self == this : "Removed DnsQueryContext is not the correct instance";
    }

    private ChannelFuture sendQuery(final DnsQuery query, final boolean flush) {
        final ChannelPromise writePromise = channel.newPromise();
        writeQuery(query, flush, writePromise);
        return writePromise;
    }

    private void writeQuery(final DnsQuery query,
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
            envelope.release();
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
            // This was caused by a timeout so use DnsNameResolverTimeoutException to allow the user to
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

        socketBootstrap.connect(nameServerAddr).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (!future.isSuccess()) {
                    logger.debug("{} Unable to fallback to TCP [{}: {}]",
                            future.channel(), id, nameServerAddr, future.cause());

                    // TCP fallback failed, just use the truncated response or error.
                    finishOriginal(originalResult, future);
                    return;
                }
                final Channel tcpCh = future.channel();
                Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise =
                        tcpCh.eventLoop().newPromise();
                final TcpDnsQueryContext tcpCtx = new TcpDnsQueryContext(tcpCh,
                        (InetSocketAddress) tcpCh.remoteAddress(), queryContextManager, 0,
                        recursionDesired, queryTimeoutMillis, question(), additionals, promise);
                tcpCh.pipeline().addLast(TCP_ENCODER);
                tcpCh.pipeline().addLast(new TcpDnsResponseDecoder());
                tcpCh.pipeline().addLast(new ChannelInboundHandlerAdapter() {
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
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        if (tcpCtx.finishFailure(
                                "TCP fallback error", cause, false) && logger.isDebugEnabled()) {
                            logger.debug("{} Error during processing response: TCP [{}: {}]",
                                    ctx.channel(), id,
                                    ctx.channel().remoteAddress(), cause);
                        }
                    }
                });

                promise.addListener(
                        new FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>() {
                            @Override
                            public void operationComplete(
                                    Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> future) {
                                if (future.isSuccess()) {
                                    finishSuccess(future.getNow(), false);
                                    // Release the original result.
                                    ReferenceCountUtil.release(originalResult);
                                } else {
                                    // TCP fallback failed, just use the truncated response or error.
                                    finishOriginal(originalResult, future);
                                }
                                tcpCh.close();
                            }
                        });
                tcpCtx.writeQuery(true);
            }
        });
        return true;
    }

    @SuppressWarnings("unchecked")
    private void finishOriginal(Object originalResult, Future<?> future) {
        if (originalResult instanceof Throwable) {
            Throwable error = (Throwable) originalResult;
            ThrowableUtil.addSuppressed(error, future.cause());
            promise.tryFailure(error);
        } else {
            finishSuccess((AddressedEnvelope<? extends DnsResponse, InetSocketAddress>) originalResult, false);
        }
    }

    private static final class AddressedEnvelopeAdapter implements AddressedEnvelope<DnsResponse, InetSocketAddress> {
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
        public AddressedEnvelope<DnsResponse, InetSocketAddress> retain() {
            response.retain();
            return this;
        }

        @Override
        public AddressedEnvelope<DnsResponse, InetSocketAddress> retain(int increment) {
            response.retain(increment);
            return this;
        }

        @Override
        public AddressedEnvelope<DnsResponse, InetSocketAddress> touch() {
            response.touch();
            return this;
        }

        @Override
        public AddressedEnvelope<DnsResponse, InetSocketAddress> touch(Object hint) {
            response.touch(hint);
            return this;
        }

        @Override
        public int refCnt() {
            return response.refCnt();
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
