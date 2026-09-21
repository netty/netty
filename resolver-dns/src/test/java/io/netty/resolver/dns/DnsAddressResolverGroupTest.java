/*
 * Copyright 2020 The Netty Project
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

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.NameResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DnsAddressResolverGroupTest {
    @Test
    public void testUseConfiguredEventLoop() throws InterruptedException {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();

        EventLoopGroup defaultEventLoopGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).datagramChannelType(NioDatagramChannel.class);
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        try {
            final Promise<?> promise = loop.newPromise();
            AddressResolver<?> resolver = resolverGroup.getResolver(defaultEventLoopGroup.next());
            resolver.resolve(new SocketAddress() {
                private static final long serialVersionUID = 3169703458729818468L;
            }).addListener((FutureListener<Object>) future -> {
                try {
                    assertInstanceOf(UnsupportedAddressTypeException.class, future.cause());
                    assertTrue(loop.inEventLoop());
                    promise.setSuccess(null);
                } catch (Throwable cause) {
                    promise.setFailure(cause);
                }
            }).await();
            promise.sync();
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
            defaultEventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    public void testSharedDNSCacheAcrossEventLoops() throws InterruptedException, ExecutionException {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).datagramChannelType(NioDatagramChannel.class);
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        EventLoopGroup defaultEventLoopGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        EventLoop eventLoop1 = defaultEventLoopGroup.next();
        EventLoop eventLoop2 = defaultEventLoopGroup.next();
        try {
            final Promise<InetSocketAddress> promise1 = loop.newPromise();
            InetSocketAddressResolver resolver1 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop1);
            InetAddress address1 =
                    resolve(resolver1, InetSocketAddress.createUnresolved("netty.io", 80), promise1);
            final Promise<InetSocketAddress> promise2 = loop.newPromise();
            InetSocketAddressResolver resolver2 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop2);
            InetAddress address2 =
                    resolve(resolver2, InetSocketAddress.createUnresolved("netty.io", 80), promise2);
            assertSame(address1, address2);
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
            defaultEventLoopGroup.shutdownGracefully();
        }
    }

    private InetAddress resolve(InetSocketAddressResolver resolver, SocketAddress socketAddress,
                                final Promise<InetSocketAddress> promise)
            throws InterruptedException, ExecutionException {
        resolver.resolve(socketAddress)
                .addListener((FutureListener<InetSocketAddress>) future -> {
                    try {
                        promise.setSuccess(future.get());
                    } catch (Throwable cause) {
                        promise.setFailure(cause);
                    }
                }).await();
        promise.sync();
        InetSocketAddress inetSocketAddress = promise.get();
        return inetSocketAddress.getAddress();
    }

    // ---------------------------------------------------------------------------------------------
    // Tests for https://github.com/netty/netty/issues/17039
    //
    // DnsAddressResolverGroup used to keep in-flight DNS resolves in two shared ConcurrentMaps keyed
    // by hostname. When the first caller's underlying channel was closed without cancelling the
    // promise (e.g. a Bootstrap#resolver() caller whose EventLoopGroup was shutdownGracefully()
    // before the DNS response arrived), the entry stayed in the map forever and every subsequent
    // caller for the same hostname attached to the dead promise instead of issuing a new query.
    //
    // The three tests below exercise the fix in InflightNameResolver which replaces the
    // promise-value map with an InflightEntry (refCnt + delegate promise + hostname) so that the
    // map is cleaned up the moment the last caller gives up.
    // ---------------------------------------------------------------------------------------------

    /**
     * Reproduces the exact scenario from issue #17039: the first caller's EventLoopGroup is
     * shut down while its DNS resolve is still in flight. Before the fix the in-flight entry
     * stayed in the shared map forever and starved every subsequent caller for the same
     * hostname. After the fix the safety-net listener attached to the executor's termination
     * future releases the entry and drops the map slot, so the next caller for the same
     * hostname goes through to the delegate and actually performs a fresh lookup.
     *
     * <p>This test uses a controllable mock delegate so the assertion is made directly against
     * the InflightNameResolver's bookkeeping (map size and delegate call count) rather than
     * relying on the timing-sensitive behaviour of a real UDP round-trip.
     */
    @Test
    public void testInflightPromiseIsNotLeakedOnClientShutdown() throws Exception {
        EventLoopGroup clientGroup1 = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup clientGroup2 = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            EventLoop loop1 = clientGroup1.next();
            EventLoop loop2 = clientGroup2.next();

            // A mock delegate that holds the first caller's promise forever, simulating a
            // server that never replies. The second caller's resolve must reach this delegate
            // a second time after the first client is shut down.
            final HoldingNameResolver<InetAddress> delegate = new HoldingNameResolver<InetAddress>();

            // Construct an InflightNameResolver directly with the shared maps that
            // DnsAddressResolverGroup would normally own. We bypass the real
            // DnsAddressResolverGroup because we want to assert on the exact call count of
            // the delegate, not on the UDP traffic of a real DNS round-trip.
            final ConcurrentMap<String, InflightNameResolver.InflightEntry<InetAddress>> resolvesMap =
                    new ConcurrentHashMap<String, InflightNameResolver.InflightEntry<InetAddress>>();
            final ConcurrentMap<String, InflightNameResolver.InflightEntry<List<InetAddress>>> resolveAllsMap =
                    new ConcurrentHashMap<String, InflightNameResolver.InflightEntry<List<InetAddress>>>();
            final InflightNameResolver<InetAddress> resolver1 = new InflightNameResolver<InetAddress>(
                    loop1, delegate, resolvesMap, resolveAllsMap);

            // 1. client#1 starts a resolve. The delegate holds the promise, so the entry
            //    stays in the inflight map.
            Promise<InetAddress> promise1 = loop1.<InetAddress>newPromise();
            resolver1.resolve("example.com", promise1);
            assertEquals(1, delegate.resolveCalls.get());
            assertEquals(1, resolvesMap.size());
            assertFalse(promise1.isDone(), "the mock delegate is holding promise1 by design");

            // 2. Shutdown client#1. The InflightNameResolver's safety-net listener (attached
            //    to the executor's termination future) must release the in-flight entry and
            //    remove the map slot, even though the delegate's promise is still pending.
            clientGroup1.shutdownGracefully().sync();

            // 3. After the shutdown the in-flight map must be empty. A second caller for the
            //    same hostname must reach the delegate again (delegate.resolveCalls == 2).
            awaitEmptyMap(resolvesMap, "example.com", 5, TimeUnit.SECONDS);

            InflightNameResolver<InetAddress> resolver2 = new InflightNameResolver<InetAddress>(
                    loop2, delegate, resolvesMap, resolveAllsMap);
            Promise<InetAddress> promise2 = loop2.<InetAddress>newPromise();
            resolver2.resolve("example.com", promise2);
            assertEquals(2, delegate.resolveCalls.get(),
                    "the second client must reach the delegate, not attach to a leaked entry");
            assertEquals(1, resolvesMap.size());
            assertFalse(promise2.isDone(), "the mock delegate is now holding promise2");
        } finally {
            clientGroup2.shutdownGracefully().sync();
        }
    }

    /**
     * Verifies that when the last caller of an in-flight resolve cancels, the entry is removed
     * from the in-flight map and the internal {@code AbortedInflightResolveException} carrier
     * class is properly defined and routed through the abort path.
     */
    @Test
    public void testRefCountReleasesOnLastCallerCancel() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            EventLoop loop = group.next();
            CountingNameResolver<InetAddress> delegate = new CountingNameResolver<InetAddress>();
            // Start holding the promise so the entry stays in the inflight map and the delegate
            // counts as "in progress" for subsequent assertions.
            delegate.startHoldingPromise();
            ConcurrentMap<String, InflightNameResolver.InflightEntry<InetAddress>> resolvesMap =
                    new ConcurrentHashMap<String, InflightNameResolver.InflightEntry<InetAddress>>();
            ConcurrentMap<String, InflightNameResolver.InflightEntry<List<InetAddress>>> resolveAllsMap =
                    new ConcurrentHashMap<String, InflightNameResolver.InflightEntry<List<InetAddress>>>();
            InflightNameResolver<InetAddress> resolver = new InflightNameResolver<InetAddress>(
                    loop, delegate, resolvesMap, resolveAllsMap);

            // First caller — drives the delegate.
            Promise<InetAddress> firstPromise = loop.<InetAddress>newPromise();
            resolver.resolve("example.com", firstPromise);
            assertEquals(1, delegate.resolveCalls.get());
            assertEquals(1, resolvesMap.size());

            // Three more callers attach to the same inflight entry.
            Promise<InetAddress> p2 = loop.<InetAddress>newPromise();
            Promise<InetAddress> p3 = loop.<InetAddress>newPromise();
            Promise<InetAddress> p4 = loop.<InetAddress>newPromise();
            resolver.resolve("example.com", p2);
            resolver.resolve("example.com", p3);
            resolver.resolve("example.com", p4);
            assertEquals(1, delegate.resolveCalls.get(), "delegate must only be called once");
            assertEquals(1, resolvesMap.size(), "all callers must share one inflight entry");
            assertEquals(4, resolvesMap.get("example.com").refCnt());

            // Cancel every caller. Each cancellation drops a refCnt slot through
            // ReleaseListener / FirstCallerCleanupListener; when the last slot is dropped, the
            // map is cleared and the delegate's promise is aborted.
            assertTrue(p2.cancel(true));
            assertTrue(p3.cancel(true));
            assertTrue(p4.cancel(true));
            assertTrue(firstPromise.cancel(true));

            // The delegate's promise is the first caller's promise; cancelling it triggers
            // FirstCallerCleanupListener which releases the last slot, drops the map entry, and
            // then (since the promise was not done beforehand) routes the abort through
            // ReleaseListener's !isDone() branch.
            assertTrue(firstPromise.await(5, TimeUnit.SECONDS));
            awaitEmptyMap(resolvesMap, "example.com", 5, TimeUnit.SECONDS);
            assertEquals(1, delegate.resolveCalls.get(), "delegate must still have been called only once");
            assertTrue(firstPromise.isDone());
            assertFalse(firstPromise.isSuccess(),
                    "first promise should have failed after the last caller cancelled, cause=" + firstPromise.cause());

            // The AbortedInflightResolveException carrier class is package-private; verify it
            // exists, is a RuntimeException, and was loaded from the expected location.
            Class<?> aborted = Class.forName(
                    "io.netty.resolver.dns.InflightNameResolver$AbortedInflightResolveException");
            assertTrue(RuntimeException.class.isAssignableFrom(aborted),
                    "AbortedInflightResolveException must extend RuntimeException");
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    /**
     * Eight threads concurrently resolve the same hostname against a single
     * {@link InflightNameResolver} with a mocked {@link NameResolver} delegate. Only the first
     * thread to win the {@code putIfAbsent} must reach the delegate; the remaining seven attach
     * to the same inflight entry, share its result, and the map must drain back to empty once
     * all callers complete.
     */
    @Test
    public void testConcurrentAcquireDoesNotLeakMap() throws Exception {
        final int callers = 8;
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            final EventLoop loop = group.next();
            final CountingNameResolver<InetAddress> delegate = new CountingNameResolver<InetAddress>();
            delegate.startHoldingPromise();
            final ConcurrentMap<String, InflightNameResolver.InflightEntry<InetAddress>> resolvesMap =
                    new ConcurrentHashMap<String, InflightNameResolver.InflightEntry<InetAddress>>();
            final ConcurrentMap<String, InflightNameResolver.InflightEntry<List<InetAddress>>> resolveAllsMap =
                    new ConcurrentHashMap<String, InflightNameResolver.InflightEntry<List<InetAddress>>>();
            final InflightNameResolver<InetAddress> resolver = new InflightNameResolver<InetAddress>(
                    loop, delegate, resolvesMap, resolveAllsMap);

            final InetAddress expected = InetAddress.getByName("127.0.0.1");
            final CyclicBarrier barrier = new CyclicBarrier(callers);
            final Promise<?>[] promises = new Promise<?>[callers];
            final CountDownLatch done = new CountDownLatch(callers);

            for (int i = 0; i < callers; i++) {
                final int idx = i;
                pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            barrier.await();
                            Promise<InetAddress> p = loop.<InetAddress>newPromise();
                            promises[idx] = p;
                            resolver.resolve("example.com", p);
                        } catch (Throwable t) {
                            promises[idx] = loop.<InetAddress>newPromise().setFailure(t);
                        } finally {
                            done.countDown();
                        }
                    }
                });
            }

            // Wait until every thread has issued its resolve, then succeed the delegate's promise
            // so all attached callers transfer the result and release.
            done.await(5, TimeUnit.SECONDS);
            delegate.completeHeldPromise(expected);

            for (int i = 0; i < callers; i++) {
                @SuppressWarnings("unchecked")
                Promise<InetAddress> p = (Promise<InetAddress>) promises[i];
                assertTrue(p.await(5, TimeUnit.SECONDS), "caller " + i + " did not complete in time");
                assertTrue(p.isSuccess(), "caller " + i + " should have succeeded, cause=" + p.cause());
                assertSame(expected, p.getNow());
            }

            awaitEmptyMap(resolvesMap, "example.com", 5, TimeUnit.SECONDS);
            assertEquals(1, delegate.resolveCalls.get(),
                    "delegate must be called exactly once across " + callers + " concurrent callers");
        } finally {
            pool.shutdownNow();
            group.shutdownGracefully().sync();
        }
    }

    private static void awaitEmptyMap(ConcurrentMap<?, ?> map, String key, long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadlineNanos) {
            if (!map.containsKey(key)) {
                return;
            }
            Thread.sleep(10);
        }
        fail("map still contains key '" + key + "' after " + timeout + " " + unit);
    }

    // ---------------------------------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * Minimal {@link NameResolver} that captures and holds the first caller's promise forever
     * (simulating a DNS server that never replies). The test asserts that after the first
     * caller's EventLoop is shut down the in-flight map is cleaned up and a subsequent caller
     * reaches this delegate again — the key property of the fix for #17039.
     */
    private static final class HoldingNameResolver<T> implements NameResolver<T> {
        final AtomicInteger resolveCalls = new AtomicInteger();

        @Override
        public Future<T> resolve(String inetHost) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<T> resolve(String inetHost, Promise<T> promise) {
            resolveCalls.incrementAndGet();
            // Hold the promise forever so the in-flight entry stays in the map until the
            // InflightNameResolver's safety-net listener releases it.
            return promise;
        }

        @Override
        public Future<List<T>> resolveAll(String inetHost) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<List<T>> resolveAll(String inetHost, Promise<List<T>> promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // no-op
        }
    }

    /**
     * Test {@link NameResolver} that counts how many times {@link #resolve(String, Promise)} is
     * invoked. When {@link #startHoldingPromise()} has been called, the first resolve captures
     * the promise and leaves it pending; {@link #completeHeldPromise(Object)} is used to
     * complete the captured promise with a value and propagate the result to every attached
     * caller.
     */
    private static final class CountingNameResolver<T> implements NameResolver<T> {
        final AtomicInteger resolveCalls = new AtomicInteger();
        final AtomicReference<Promise<T>> heldPromise = new AtomicReference<Promise<T>>();
        final CountDownLatch delegateStarted = new CountDownLatch(1);
        private volatile boolean hold;

        void startHoldingPromise() {
            hold = true;
        }

        @Override
        public Future<T> resolve(String inetHost) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<T> resolve(String inetHost, Promise<T> promise) {
            resolveCalls.incrementAndGet();
            delegateStarted.countDown();
            if (hold) {
                if (!heldPromise.compareAndSet(null, promise)) {
                    throw new IllegalStateException("CountingNameResolver only supports a single held promise");
                }
            } else {
                promise.trySuccess(null);
            }
            return promise;
        }

        @Override
        public Future<List<T>> resolveAll(String inetHost) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<List<T>> resolveAll(String inetHost, Promise<List<T>> promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // no-op
        }

        void completeHeldPromise(T value) {
            Promise<T> p = heldPromise.getAndSet(null);
            if (p == null) {
                throw new IllegalStateException("no held promise to complete");
            }
            p.trySuccess(value);
        }
    }
}
