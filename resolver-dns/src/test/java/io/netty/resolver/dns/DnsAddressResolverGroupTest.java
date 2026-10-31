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

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.NameResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.RecordStore;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.resolver.dns.TestDnsServer.newARecord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void testSharedDNSCacheAcrossEventLoops() throws Exception {
        final String hostname = "netty.io";
        final String expectedIp = "1.2.3.4";
        TestDnsServer dnsServer = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                if (question.getRecordType() == RecordType.A && question.getDomainName().equalsIgnoreCase(hostname)) {
                    return Collections.singleton(newARecord(hostname, expectedIp));
                }
                return null;
            }
        });
        dnsServer.start();
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).datagramChannelType(NioDatagramChannel.class)
                .optResourceEnabled(false)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()));
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        EventLoopGroup defaultEventLoopGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        EventLoop eventLoop1 = defaultEventLoopGroup.next();
        EventLoop eventLoop2 = defaultEventLoopGroup.next();
        try {
            final Promise<InetSocketAddress> promise1 = loop.newPromise();
            InetSocketAddressResolver resolver1 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop1);
            InetAddress address1 =
                    resolve(resolver1, InetSocketAddress.createUnresolved(hostname, 80), promise1);
            final Promise<InetSocketAddress> promise2 = loop.newPromise();
            InetSocketAddressResolver resolver2 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop2);
            InetAddress address2 =
                    resolve(resolver2, InetSocketAddress.createUnresolved(hostname, 80), promise2);
            assertSame(address1, address2);
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
            defaultEventLoopGroup.shutdownGracefully();
            dnsServer.stop();
        }
    }

    /**
     * Reproduces <a href="https://github.com/netty/netty/issues/17039">#17039</a>: the first
     * caller's event loop is shut down while its resolution is still in flight. The in-flight
     * slot must be released, so that a second client resolving the same hostname starts a new
     * resolution instead of attaching to a promise that can never complete.
     */
    @Test
    public void testInflightResolveIsClearedWhenClientEventLoopShutsDown() throws Exception {
        EventLoopGroup clientGroup1 = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup clientGroup2 = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        HoldingNameResolver delegate = new HoldingNameResolver();
        DnsAddressResolverGroup group = new TestDnsAddressResolverGroup(delegate);
        try {
            Future<InetSocketAddress> first = group.getResolver(clientGroup1.next())
                    .resolve(InetSocketAddress.createUnresolved("netty.io", 80));
            assertFalse(first.isDone());
            assertEquals(1, delegate.resolveCalls());

            clientGroup1.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
            // AddressResolverGroup closes the resolver of the terminated executor, which in turn
            // has to drop the in-flight entry even though the promise never completed.
            assertTrue(delegate.awaitClosed(5, TimeUnit.SECONDS),
                    "resolver was not closed after its executor terminated");

            delegate.hold(false);
            Future<InetSocketAddress> second = group.getResolver(clientGroup2.next())
                    .resolve(InetSocketAddress.createUnresolved("netty.io", 80));
            assertTrue(second.await(5, TimeUnit.SECONDS), "second client did not complete");
            assertTrue(second.isSuccess(), "second client failed: " + second.cause());
            assertEquals(2, delegate.resolveCalls(), "second client must start a fresh resolution");
            assertEquals(loopback(), second.getNow().getAddress());
        } finally {
            group.close();
            clientGroup1.shutdownGracefully();
            clientGroup2.shutdownGracefully();
        }
    }

    /**
     * A caller that attached to an in-flight resolution before the first caller's event loop was
     * shut down must be completed. It cannot get a result anymore, but it must not hang either.
     */
    @Test
    public void testPendingWaiterIsCancelledWhenFirstClientEventLoopShutsDown() throws Exception {
        EventLoopGroup clientGroup1 = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup clientGroup2 = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        HoldingNameResolver delegate = new HoldingNameResolver();
        DnsAddressResolverGroup group = new TestDnsAddressResolverGroup(delegate);
        try {
            AddressResolver<InetSocketAddress> resolver1 = group.getResolver(clientGroup1.next());
            AddressResolver<InetSocketAddress> resolver2 = group.getResolver(clientGroup2.next());

            Future<InetSocketAddress> first =
                    resolver1.resolve(InetSocketAddress.createUnresolved("netty.io", 80));
            Future<InetSocketAddress> second =
                    resolver2.resolve(InetSocketAddress.createUnresolved("netty.io", 80));
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            assertEquals(1, delegate.resolveCalls(), "second client must share the in-flight resolution");

            clientGroup1.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
            assertTrue(delegate.awaitClosed(5, TimeUnit.SECONDS),
                    "resolver was not closed after its executor terminated");

            assertTrue(second.await(5, TimeUnit.SECONDS), "pending waiter must not hang");
            assertTrue(second.isCancelled(), "pending waiter was expected to be cancelled: " + second.cause());

            delegate.hold(false);
            Future<InetSocketAddress> third =
                    resolver2.resolve(InetSocketAddress.createUnresolved("netty.io", 80));
            assertTrue(third.await(5, TimeUnit.SECONDS));
            assertTrue(third.isSuccess(), "third resolve failed: " + third.cause());
            assertEquals(2, delegate.resolveCalls(), "a new resolution must be started");
        } finally {
            group.close();
            clientGroup1.shutdownGracefully();
            clientGroup2.shutdownGracefully();
        }
    }

    /**
     * Concurrent resolutions of the same hostname from different event loops must still be
     * consolidated into a single delegate call, and all the callers must share its result.
     */
    @Test
    public void testInflightResolveIsSharedAcrossEventLoops() throws Exception {
        EventLoopGroup clients = new MultiThreadIoEventLoopGroup(4, NioIoHandler.newFactory());
        HoldingNameResolver delegate = new HoldingNameResolver();
        DnsAddressResolverGroup group = new TestDnsAddressResolverGroup(delegate);
        try {
            List<Future<InetSocketAddress>> futures = new ArrayList<Future<InetSocketAddress>>();
            for (int i = 0; i < 4; i++) {
                futures.add(group.getResolver(clients.next())
                        .resolve(InetSocketAddress.createUnresolved("netty.io", 80)));
            }
            assertEquals(1, delegate.resolveCalls(), "concurrent resolves must share one resolution");

            delegate.complete();

            for (Future<InetSocketAddress> future : futures) {
                assertTrue(future.await(5, TimeUnit.SECONDS), "resolve did not complete");
                assertTrue(future.isSuccess(), "resolve failed: " + future.cause());
                assertEquals(loopback(), future.getNow().getAddress());
            }
        } finally {
            group.close();
            clients.shutdownGracefully();
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

    private static InetAddress loopback() {
        return InetAddress.getLoopbackAddress();
    }

    /**
     * A {@link DnsAddressResolverGroup} whose resolvers all delegate to a preconfigured
     * {@link NameResolver}, so that the tests can control when a resolution completes.
     */
    private static final class TestDnsAddressResolverGroup extends DnsAddressResolverGroup {
        private final NameResolver<InetAddress> nameResolver;

        TestDnsAddressResolverGroup(NameResolver<InetAddress> nameResolver) {
            super(new DnsNameResolverBuilder());
            this.nameResolver = nameResolver;
        }

        @Override
        protected NameResolver<InetAddress> newNameResolver(
                EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
                DnsServerAddressStreamProvider nameServerProvider) {
            return nameResolver;
        }
    }

    /**
     * A {@link NameResolver} that holds the promise of the first resolution until
     * {@link #complete()} or {@link #hold(boolean)} is called. It also keeps track of the number of
     * resolutions that reached it, which is how the tests verify whether a slot was released or
     * reused.
     */
    private static final class HoldingNameResolver implements NameResolver<InetAddress> {
        private final AtomicInteger resolveCalls = new AtomicInteger();
        private final AtomicReference<Promise<InetAddress>> inFlight = new AtomicReference<Promise<InetAddress>>();
        private final CountDownLatch closed = new CountDownLatch(1);
        private volatile boolean hold = true;

        @Override
        public Future<InetAddress> resolve(String inetHost) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<InetAddress> resolve(String inetHost, Promise<InetAddress> promise) {
            resolveCalls.incrementAndGet();
            if (hold) {
                inFlight.set(promise);
            } else {
                promise.setSuccess(loopback());
            }
            return promise;
        }

        @Override
        public Future<List<InetAddress>> resolveAll(String inetHost) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<List<InetAddress>> resolveAll(String inetHost, Promise<List<InetAddress>> promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            closed.countDown();
        }

        void hold(boolean hold) {
            this.hold = hold;
        }

        void complete() {
            Promise<InetAddress> promise = inFlight.getAndSet(null);
            if (promise == null) {
                throw new IllegalStateException("no in-flight resolution");
            }
            promise.setSuccess(loopback());
        }

        int resolveCalls() {
            return resolveCalls.get();
        }

        boolean awaitClosed(long timeout, TimeUnit unit) throws InterruptedException {
            return closed.await(timeout, unit);
        }
    }
}
