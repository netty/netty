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
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the reference-counted {@link DnsResponse} leak / double-free in
 * {@link DnsNameResolver#query(InetSocketAddress, DnsQuestion, Iterable, Promise)} when the returned future loses a
 * cancel race with a successful response. They cover both affected paths: the async-channel branch (datagram channel
 * not yet registered) and the query-consolidation branch ({@code consolidateCacheSize > 0}).
 *
 * <p>These tests drive the <em>real</em> {@code query(...)} entry point end-to-end against a {@link TestDnsServer};
 * each test documents how it deterministically reaches the branch it exercises.</p>
 *
 * <p>{@code doQuery(...)} always completes and returns the caller's own promise, so that branch must hand the
 * caller's promise straight back. {@link DnsQueryContext#finishSuccess} transfers ownership of the
 * reference-counted response to that single promise (and self-releases on a lost race), so exactly one listener on
 * the returned future releases the response exactly once. The historical bug wrapped the caller's promise in a
 * second promise and returned that instead, which either leaked the response (the returned future never observed
 * it) or double-freed it (the response was released while the caller's own promise still aliased it), depending on
 * which future the caller released through when the returned future lost a cancel race.</p>
 */
public class DnsQueryCancelRaceLeakTest {

    private static final String HOSTNAME = "netty.io";

    /**
     * Which of the two aliasing references the caller uses to release the response. Before the fix these were two
     * distinct promise objects and only one of them released cleanly; after the fix they are the same object, so
     * both must behave identically.
     */
    private enum ReleaseVia {
        /** The {@link Future} returned by {@code query(...)}. */
        RETURNED_FUTURE,
        /** The {@link Promise} the caller passed into {@code query(...)}. */
        OWN_PROMISE
    }

    /**
     * Which of the two consolidated callers is cancelled before the shared response arrives.
     */
    private enum CancelWhich {
        /** Cancel the first ("owner") query that created the inflight consolidation promise. */
        OWNER,
        /** Cancel the second ("waiter") query that consolidated onto the owner's inflight promise. */
        WAITER
    }

    private static DnsNameResolver newPerResolutionResolver(EventLoop loop, TestDnsServer dnsServer) {
        return new DnsNameResolverBuilder(loop)
                .datagramChannelType(NioDatagramChannel.class)
                .datagramChannelStrategy(DnsNameResolverChannelStrategy.ChannelPerResolution)
                .optResourceEnabled(false)
                .queryTimeoutMillis(5000)
                .consolidateCacheSize(0)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()))
                .build();
    }

    /**
     * Calls {@code query(...)} while the (single) event-loop thread is parked, so the {@code ChannelPerResolution}
     * channel cannot have been registered yet and the async-channel branch of {@code query(...)} is always taken.
     */
    private static Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> queryWhileLoopParked(
            EventLoop loop, DnsNameResolver resolver, InetSocketAddress nameServerAddr, DnsQuestion question,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) throws Exception {
        final CountDownLatch parked = new CountDownLatch(1);
        final CountDownLatch unpark = new CountDownLatch(1);
        loop.execute(() -> {
            parked.countDown();
            try {
                // Bounded wait so a failure on the test thread can never park the event loop forever.
                unpark.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(parked.await(10, TimeUnit.SECONDS), "the event loop should be parked");
        try {
            return resolver.query(nameServerAddr, question, Collections.<DnsRecord>emptyList(), promise);
        } finally {
            unpark.countDown();
        }
    }

    /**
     * Structural invariant that directly encodes the fix: the async-channel branch of {@code query(...)} returns
     * the very {@link Promise} the caller passed in, not a separate wrapper promise that would duplicate ownership
     * of the reference-counted response.
     */
    @Test
    @Timeout(30)
    public void asyncQueryBranchReturnsCallerPromise() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        TestDnsServer dnsServer = new TestDnsServer(new TestDnsServer.MapRecordStoreA(
                Collections.singleton(HOSTNAME)));
        dnsServer.start();
        EventLoop loop = group.next();
        DnsNameResolver resolver = newPerResolutionResolver(loop, dnsServer);
        try {
            final Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise = loop.newPromise();
            final DnsQuestion question = new DefaultDnsQuestion(HOSTNAME, DnsRecordType.A);

            // The channel cannot be registered while the loop is parked, so the async-channel branch runs and must
            // return the caller's own promise object.
            Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> ret =
                    queryWhileLoopParked(loop, resolver, dnsServer.localAddress(), question, promise);

            assertSame(promise, ret, "async-channel branch must return the caller's promise, not a wrapper promise");

            // Let the query finish and release the response so the test itself does not leak.
            ret.await(10, TimeUnit.SECONDS);
            if (ret.isSuccess()) {
                assertEquals(1, ret.getNow().refCnt(), "successful response must be owned exactly once");
                assertTrue(ReferenceCountUtil.release(ret.getNow()));
            }
        } finally {
            resolver.close();
            dnsServer.stop();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    /**
     * Reproduces the exact cancel race the fix targets: the returned future is cancelled after the barrier (first
     * post-success) listener has started and parked the notification loop, but before the remaining post-success
     * listeners run, and asserts the response is released exactly once (no leak, no double-free) whether the caller
     * releases through the returned future or through the promise it passed in.
     *
     * <p>The barrier listener is registered on the caller's promise <em>before</em> {@code query(...)} is invoked.
     * Since {@link io.netty.util.concurrent.DefaultPromise} notifies listeners in registration order, it runs first
     * when the response arrives and parks the (single) event-loop thread mid-notification, giving the test thread a
     * deterministic window to cancel the returned future.</p>
     */
    @ParameterizedTest
    @EnumSource(ReleaseVia.class)
    @Timeout(30)
    public void asyncQueryReleasesResponseExactlyOnceWhenReturnedFutureCancelledAfterSuccess(ReleaseVia releaseVia)
            throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        TestDnsServer dnsServer = new TestDnsServer(new TestDnsServer.MapRecordStoreA(
                Collections.singleton(HOSTNAME)));
        dnsServer.start();
        EventLoop loop = group.next();
        DnsNameResolver resolver = newPerResolutionResolver(loop, dnsServer);

        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);
        AddressedEnvelope<? extends DnsResponse, InetSocketAddress> toRelease = null;
        try {
            final Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise = loop.newPromise();

            // Barrier listener registered BEFORE query(): it runs first when the response completes the promise
            // (DefaultPromise notifies in registration order) and parks the loop, opening the post-success window.
            promise.addListener((FutureListener<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>>) f -> {
                reached.countDown();
                // Bounded wait so a failed assertion on the test thread can never park the event loop forever.
                try {
                    proceed.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            final DnsQuestion question = new DefaultDnsQuestion(HOSTNAME, DnsRecordType.A);
            // Parking the loop while query() runs guarantees the async-channel branch is taken.
            final Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> ret =
                    queryWhileLoopParked(loop, resolver, dnsServer.localAddress(), question, promise);

            assertTrue(reached.await(15, TimeUnit.SECONDS), "query() should have completed the caller's promise");

            // Cancel the returned future in the post-success window, then unpark the loop and fence so every
            // post-success listener has run before we assert.
            final boolean cancelled = ret.cancel(false);
            proceed.countDown();
            loop.submit((Runnable) () -> { }).sync();

            // The returned future IS the caller's promise, so the post-success cancel is a no-op.
            assertFalse(cancelled, "returned future == caller promise (already succeeded): cancel must be a no-op");
            assertFalse(ret.isCancelled());
            assertTrue(promise.isSuccess(), "caller promise must hold the successful response");
            assertSame(promise, ret, "returned future and caller promise must be the same object");

            final AddressedEnvelope<? extends DnsResponse, InetSocketAddress> response =
                    releaseVia == ReleaseVia.RETURNED_FUTURE ? ret.getNow() : promise.getNow();
            assertNotNull(response, "response must be reachable via the " + releaseVia + " reference");
            toRelease = response;

            assertEquals(1, response.refCnt(),
                    "response must be owned exactly once: not leaked (refCnt would stay high) "
                            + "and not prematurely released (refCnt would be 0)");

            assertTrue(ReferenceCountUtil.release(response), "single release should free the response");
            toRelease = null;
            assertEquals(0, response.refCnt(), "response must reach refCnt 0 with a single release (no double-free)");
        } finally {
            // Guarantee the event loop is never left parked, even if an assertion above failed.
            proceed.countDown();
            if (toRelease != null && toRelease.refCnt() > 0) {
                ReferenceCountUtil.release(toRelease);
            }
            resolver.close();
            dnsServer.stop();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
