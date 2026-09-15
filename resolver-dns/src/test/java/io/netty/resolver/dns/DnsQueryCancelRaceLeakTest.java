/*
 * Copyright 2026 The Netty Project
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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the reference-counted {@link DnsResponse} leak in the async-channel branch of
 * {@link DnsNameResolver#query(InetSocketAddress, io.netty.handler.codec.dns.DnsQuestion, Iterable, Promise)}.
 *
 * <p>When the datagram channel is not yet registered (e.g. immediately after a resolver is created/rebuilt),
 * {@code query(...)} returns a fresh promise {@code p} and bridges the inner query future {@code qf} to it via
 * {@link PromiseNotifier#cascade(Future, Promise)}. {@code DnsQueryContext.finishSuccess} completes {@code qf}
 * with a fully-owned {@link DatagramDnsResponse} (ownership held by {@code qf}'s result, to be released by a
 * listener on the returned promise {@code p}). If {@code p} is cancelled from another thread in the window after
 * {@code qf} succeeds but before the cascade notifier delivers the value, {@code p.trySuccess(env)} fails and the
 * notifier only logs - nobody releases the response, which is then GC'd unreleased.</p>
 */
public class DnsQueryCancelRaceLeakTest {

    private static final InetSocketAddress SENDER = new InetSocketAddress("10.0.0.1", 53);
    private static final InetSocketAddress RECIPIENT = new InetSocketAddress("10.0.0.2", 12345);

    /**
     * Deterministically forces the cross-thread ordering (qf succeeds -> p cancelled -> cascade notifier runs) and
     * shows that the raw {@link PromiseNotifier#cascade(Future, Promise)} bridge leaks the response.
     */
    @Test
    @Timeout(30)
    public void promiseNotifierCascadeLeaksResponseOnCancelRace() throws Exception {
        EventLoopGroup loop = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> qf =
                    new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(loop.next());
            final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> p =
                    new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(loop.next());

            final CountDownLatch reached = new CountDownLatch(1);
            final CountDownLatch cancelDone = new CountDownLatch(1);
            final CountDownLatch notified = new CountDownLatch(1);

            // (1) Barrier listener, registered on qf BEFORE the cascade notifier. DefaultPromise notifies listeners
            // in registration order, so this runs first and parks the event-loop thread mid-notification.
            qf.addListener((FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>) f -> {
                reached.countDown();
                // Bounded wait so a failed assertion on the test thread can never park the event-loop thread
                // forever (which would hang shutdownGracefully().sync()).
                cancelDone.await(10, TimeUnit.SECONDS);
            });

            // (2) The code under test: bridge qf -> p.
            PromiseNotifier.cascade(qf, p);

            // (3) Trailing listener to know when the cascade notifier has run.
            qf.addListener((FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>) f ->
                    notified.countDown());

            // A fully-decoded, reference-counted response, exactly the type seen leaked in production.
            final DatagramDnsResponse env = new DatagramDnsResponse(SENDER, RECIPIENT, 0);
            assertEquals(1, env.refCnt(), "precondition: freshly created response has refCnt 1");
            @SuppressWarnings("unchecked")
            final AddressedEnvelope<DnsResponse, InetSocketAddress> envelope =
                    (AddressedEnvelope<DnsResponse, InetSocketAddress>) (AddressedEnvelope<?, InetSocketAddress>) env;

            // (4) Complete qf on the event-loop thread; the barrier listener parks that thread.
            loop.execute(() -> qf.setSuccess(envelope));
            assertTrue(reached.await(5, TimeUnit.SECONDS), "qf success notification should have started");

            // (5) From another thread, cancel p AFTER qf already succeeded. Because qf is already done, cascade's
            // p->qf back-cancel listener is a no-op; qf keeps its success and its owned response.
            assertTrue(p.cancel(false), "p should be cancellable in the race window");

            // (6) Release the event-loop thread; the cascade notifier now runs p.trySuccess(env) -> false -> only logs.
            cancelDone.countDown();
            assertTrue(notified.await(5, TimeUnit.SECONDS), "cascade notifier should have run");

            // (7) The response is stranded: nobody released it. This is the leak.
            assertEquals(1, env.refCnt(),
                    "BUG: response was neither delivered nor released after the cancel race");

            // Clean up so the test itself does not leak.
            ReferenceCountUtil.release(env);
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    /**
     * The fix: {@link DnsNameResolver#cascadeQueryResponse(Future, Promise)} releases the response when it cannot be
     * handed to the returned promise, so the same cancel race no longer leaks.
     */
    @Test
    @Timeout(30)
    public void cascadeQueryResponseReleasesResponseOnCancelRace() throws Exception {
        EventLoopGroup loop = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> qf =
                    new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(loop.next());
            final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> p =
                    new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(loop.next());

            final CountDownLatch reached = new CountDownLatch(1);
            final CountDownLatch cancelDone = new CountDownLatch(1);
            final CountDownLatch notified = new CountDownLatch(1);

            qf.addListener((FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>) f -> {
                reached.countDown();
                // Bounded wait so a failed assertion on the test thread can never park the event-loop thread
                // forever (which would hang shutdownGracefully().sync()).
                cancelDone.await(10, TimeUnit.SECONDS);
            });

            // The code under test with the fix applied.
            DnsNameResolver.cascadeQueryResponse(qf, p);

            qf.addListener((FutureListener<AddressedEnvelope<DnsResponse, InetSocketAddress>>) f ->
                    notified.countDown());

            final DatagramDnsResponse env = new DatagramDnsResponse(SENDER, RECIPIENT, 0);
            assertEquals(1, env.refCnt(), "precondition: freshly created response has refCnt 1");
            @SuppressWarnings("unchecked")
            final AddressedEnvelope<DnsResponse, InetSocketAddress> envelope =
                    (AddressedEnvelope<DnsResponse, InetSocketAddress>) (AddressedEnvelope<?, InetSocketAddress>) env;

            loop.execute(() -> qf.setSuccess(envelope));
            assertTrue(reached.await(5, TimeUnit.SECONDS), "qf success notification should have started");

            assertTrue(p.cancel(false), "p should be cancellable in the race window");

            cancelDone.countDown();
            assertTrue(notified.await(5, TimeUnit.SECONDS), "bridge notifier should have run");

            // The response was released by the bridge because it could not be handed to the cancelled promise.
            assertEquals(0, env.refCnt(), "response should have been released after the failed hand-off");
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    /**
     * The common, non-cancelled success path: {@link DnsNameResolver#cascadeQueryResponse(Future, Promise)} must hand
     * the reference-counted response to the aggregate promise <em>without</em> releasing it (the receiver's listener
     * owns the release). Guards against a regression that unconditionally released on success and would therefore
     * over-release / double-free every successful async-channel query.
     */
    @Test
    public void cascadeQueryResponseDeliversResponseWithoutOverReleaseOnSuccess() {
        // ImmediateEventExecutor fires listeners synchronously on the calling thread, so the ordering below is
        // fully deterministic without an event loop or latches.
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> qf =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> p =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);

        DnsNameResolver.cascadeQueryResponse(qf, p);

        final DatagramDnsResponse env = new DatagramDnsResponse(SENDER, RECIPIENT, 0);
        assertEquals(1, env.refCnt(), "precondition: freshly created response has refCnt 1");
        @SuppressWarnings("unchecked")
        final AddressedEnvelope<DnsResponse, InetSocketAddress> envelope =
                (AddressedEnvelope<DnsResponse, InetSocketAddress>) (AddressedEnvelope<?, InetSocketAddress>) env;

        qf.setSuccess(envelope);

        assertTrue(p.isSuccess(), "response should have been delivered to the aggregate promise");
        assertSame(envelope, p.getNow(), "the same response instance should be handed over");
        assertEquals(1, env.refCnt(),
                "delivered response must not be over-released; ownership transfers to the promise");

        // Mirror the production listener on the returned promise, which owns the release.
        ReferenceCountUtil.release(env);
    }

    /**
     * The release branch fires whenever the hand-off fails, not only on cancellation. A concurrent {@code tryFailure}
     * on the aggregate promise (e.g. a timeout) is a distinct route into the {@code trySuccess() == false} window and
     * must also release the response.
     */
    @Test
    public void cascadeQueryResponseReleasesResponseWhenPromiseAlreadyFailed() {
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> qf =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> p =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);

        DnsNameResolver.cascadeQueryResponse(qf, p);

        final DatagramDnsResponse env = new DatagramDnsResponse(SENDER, RECIPIENT, 0);
        assertEquals(1, env.refCnt(), "precondition: freshly created response has refCnt 1");
        @SuppressWarnings("unchecked")
        final AddressedEnvelope<DnsResponse, InetSocketAddress> envelope =
                (AddressedEnvelope<DnsResponse, InetSocketAddress>) (AddressedEnvelope<?, InetSocketAddress>) env;

        // Fail the aggregate promise first, then complete the query: the hand-off (trySuccess) fails.
        assertTrue(p.tryFailure(new RuntimeException("simulated concurrent timeout")));
        qf.setSuccess(envelope);

        assertEquals(0, env.refCnt(), "response should have been released after hand-off to an already-failed promise");
    }

    /**
     * Failure propagation parity with {@link PromiseNotifier#cascade(Future, Promise)}: a failed query future forwards
     * its cause to the aggregate promise unchanged.
     */
    @Test
    public void cascadeQueryResponsePropagatesFailureFromQueryFuture() {
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> qf =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> p =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);

        DnsNameResolver.cascadeQueryResponse(qf, p);

        final RuntimeException cause = new RuntimeException("boom");
        qf.setFailure(cause);

        assertFalse(p.isSuccess());
        assertFalse(p.isCancelled());
        assertSame(cause, p.cause(), "failure cause should be forwarded unchanged");
    }

    /**
     * Forward cancellation parity: cancelling the query future cancels the aggregate promise.
     */
    @Test
    public void cascadeQueryResponsePropagatesCancellationFromQueryFuture() {
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> qf =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> p =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);

        DnsNameResolver.cascadeQueryResponse(qf, p);

        assertTrue(qf.cancel(false));
        assertTrue(p.isCancelled(), "aggregate promise should be cancelled when the query future is cancelled");
    }

    /**
     * Back-cancellation parity (the other half of the two-way contract): cancelling the aggregate promise before the
     * query completes cancels the in-flight query future. The leak tests above deliberately cancel {@code p} only
     * <em>after</em> {@code qf} has already succeeded, where this back-cancel listener is a documented no-op.
     */
    @Test
    public void cascadeQueryResponseBackCancelsQueryFutureWhenPromiseCancelledBeforeCompletion() {
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> qf =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> p =
                new DefaultPromise<AddressedEnvelope<DnsResponse, InetSocketAddress>>(ImmediateEventExecutor.INSTANCE);

        DnsNameResolver.cascadeQueryResponse(qf, p);

        assertTrue(p.cancel(false));
        assertTrue(qf.isCancelled(), "cancelling the aggregate promise should back-cancel the query future");
    }
}
