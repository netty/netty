/*
 * Copyright 2019 The Netty Project

 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:

 * https://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.util.internal;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.pool.PooledBufferAllocator;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioDatagramChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslHandler;
import io.netty5.handler.ssl.SslHandshakeCompletionEvent;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;
import io.netty5.resolver.dns.DnsNameResolverBuilder;
import io.netty5.resolver.dns.DnsServerAddressStreamProviders;
import io.netty5.util.HashedWheelTimer;
import io.netty5.util.concurrent.DefaultThreadFactory;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.FastThreadLocalThread;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.ImmediateExecutor;
import io.netty5.util.concurrent.SingleThreadEventExecutor;
import io.netty5.util.internal.Hidden.NettyBlockHoundIntegration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.blockhound.integration.BlockHoundIntegration;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static io.netty5.buffer.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.util.internal.SilentDispose.autoClosing;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class NettyBlockHoundIntegrationTest {

    @BeforeAll
    public static void setUpClass() {
        BlockHound.install();
    }

    @Test
    public void testServiceLoader() {
        for (BlockHoundIntegration integration : ServiceLoader.load(BlockHoundIntegration.class)) {
            if (integration instanceof NettyBlockHoundIntegration) {
                return;
            }
        }

        fail("NettyBlockHoundIntegration cannot be loaded with ServiceLoader");
    }

    @Test
    public void testBlockingCallsInNettyThreads() throws Exception {
        final FutureTask<Void> future = new FutureTask<>(() -> {
            Thread.sleep(0);
            return null;
        });
        GlobalEventExecutor.INSTANCE.execute(future);

        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Expected an exception due to a blocking call but none was thrown");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), Matchers.instanceOf(BlockingOperationError.class));
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testGlobalEventExecutorTakeTask() throws InterruptedException {
        testEventExecutorTakeTask(GlobalEventExecutor.INSTANCE);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testSingleThreadEventExecutorTakeTask() throws InterruptedException {
        SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(new DefaultThreadFactory("test")) {
                    @Override
                    protected void run() {
                        while (!confirmShutdown()) {
                            Runnable task = takeTask();
                            if (task != null) {
                                task.run();
                            }
                        }
                    }
                };
        testEventExecutorTakeTask(executor);
    }

    private static void testEventExecutorTakeTask(EventExecutor eventExecutor) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Future<?> f = eventExecutor.schedule(latch::countDown, 10, TimeUnit.MILLISECONDS);
        f.asStage().sync();
        latch.await();
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testSingleThreadEventExecutorAddTask() throws Exception {
        TestLinkedBlockingQueue<Runnable> taskQueue = new TestLinkedBlockingQueue<>();
        SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(new DefaultThreadFactory("test")) {
                    @Override
                    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
                        return taskQueue;
                    }

                    @Override
                    protected void run() {
                        while (!confirmShutdown()) {
                            Runnable task = takeTask();
                            if (task != null) {
                                task.run();
                            }
                        }
                    }
                };
        taskQueue.emulateContention();
        CountDownLatch latch = new CountDownLatch(1);
        executor.submit(() -> {
            executor.execute(() -> { }); // calls addTask
            latch.countDown();
        });
        taskQueue.waitUntilContented();
        taskQueue.removeContention();
        latch.await();
    }

    @Test
    void permittingBlockingCallsInFastThreadLocalThreadSubclass() throws Exception {
        final FutureTask<Void> future = new FutureTask<>(() -> {
            Thread.sleep(0);
            return null;
        });
        FastThreadLocalThread thread = new FastThreadLocalThread(future) {
            @Override
            public boolean permitBlockingCalls() {
                return true; // The Thread.sleep(0) call should not be flagged because we allow blocking calls.
            }
        };
        thread.start();
        future.get(5, TimeUnit.SECONDS);
        thread.join();
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testHashedWheelTimerStartStop() throws Exception {
        HashedWheelTimer timer = new HashedWheelTimer();
        GlobalEventExecutor.INSTANCE.submit(timer::start).asStage().get(5, TimeUnit.SECONDS);
        GlobalEventExecutor.INSTANCE.submit(timer::stop).asStage().get(5, TimeUnit.SECONDS);
    }

    // Tests copied from io.netty5.handler.ssl.SslHandlerTest
    @Test
    public void testHandshakeWithExecutorThatExecuteDirectory() throws Exception {
        testHandshakeWithExecutor(Runnable::run, "TLSv1.2");
    }

    @Test
    public void testHandshakeWithExecutorThatExecuteDirectoryTLSv13() throws Exception {
        assumeTrue(SslProvider.isTlsv13Supported(SslProvider.JDK));
        testHandshakeWithExecutor(Runnable::run, "TLSv1.3");
    }

    @Test
    public void testHandshakeWithImmediateExecutor() throws Exception {
        testHandshakeWithExecutor(ImmediateExecutor.INSTANCE, "TLSv1.2");
    }

    @Test
    public void testHandshakeWithImmediateExecutorTLSv13() throws Exception {
        assumeTrue(SslProvider.isTlsv13Supported(SslProvider.JDK));
        testHandshakeWithExecutor(ImmediateExecutor.INSTANCE, "TLSv1.3");
    }

    @Test
    public void testHandshakeWithImmediateEventExecutor() throws Exception {
        testHandshakeWithExecutor(ImmediateEventExecutor.INSTANCE, "TLSv1.2");
    }

    @Test
    public void testHandshakeWithImmediateEventExecutorTLSv13() throws Exception {
        assumeTrue(SslProvider.isTlsv13Supported(SslProvider.JDK));
        testHandshakeWithExecutor(ImmediateEventExecutor.INSTANCE, "TLSv1.3");
    }

    @Test
    public void testHandshakeWithExecutor() throws Exception {
        ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            testHandshakeWithExecutor(executorService, "TLSv1.2");
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void testHandshakeWithExecutorTLSv13() throws Exception {
        assumeTrue(SslProvider.isTlsv13Supported(SslProvider.JDK));
        ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            testHandshakeWithExecutor(executorService, "TLSv1.3");
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void testTrustManagerVerifyJDK() throws Exception {
        testTrustManagerVerify(SslProvider.JDK, "TLSv1.2");
    }

    @Test
    public void testTrustManagerVerifyTLSv13JDK() throws Exception {
        assumeTrue(SslProvider.isTlsv13Supported(SslProvider.JDK));
        testTrustManagerVerify(SslProvider.JDK, "TLSv1.3");
    }

    @Test
    public void testTrustManagerVerifyOpenSSL() throws Exception {
        testTrustManagerVerify(SslProvider.OPENSSL, "TLSv1.2");
    }

    @Test
    public void testTrustManagerVerifyTLSv13OpenSSL() throws Exception {
        assumeTrue(SslProvider.isTlsv13Supported(SslProvider.OPENSSL));
        testTrustManagerVerify(SslProvider.OPENSSL, "TLSv1.3");
    }

    @Test
    public void testSslHandlerWrapAllowsBlockingCalls() throws Exception {
        final SslContext sslClientCtx =
                SslContextBuilder.forClient()
                                 .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                 .sslProvider(SslProvider.JDK)
                        .endpointIdentificationAlgorithm(null)
                        .build();
        BufferAllocator alloc = offHeapAllocator();
        final SslHandler sslHandler = sslClientCtx.newHandler(alloc);
        final EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        final CountDownLatch activeLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        Channel sc = null;
        Channel cc = null;
        try (AutoCloseable ignore = autoClosing(sslClientCtx)) {
            try {
                sc = new ServerBootstrap()
                        .group(group)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelHandler() {
                        })
                        .bind(new InetSocketAddress(0)).asStage().get();

                cc = new Bootstrap()
                        .group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<Channel>() {

                            @Override
                            protected void initChannel(Channel ch) {
                                ch.pipeline().addLast(sslHandler);
                                ch.pipeline().addLast(new ChannelHandler() {

                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        activeLatch.countDown();
                                    }

                                    @Override
                                    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                                        if (evt instanceof SslHandshakeCompletionEvent &&
                                            ((SslHandshakeCompletionEvent) evt).cause() != null) {
                                            Throwable cause = ((SslHandshakeCompletionEvent) evt).cause();
                                            cause.printStackTrace();
                                            error.set(cause);
                                        }
                                        ctx.fireChannelInboundEvent(evt);
                                    }
                                });
                            }
                        })
                        .connect(sc.localAddress())
                        .addListener(future -> future.asStage().get().writeAndFlush(
                                    alloc.copyOf(new byte[] { 1, 2, 3, 4 })))
                        .asStage().get();

                assertTrue(activeLatch.await(5, TimeUnit.SECONDS));
                assertNull(error.get());
            } finally {
                if (cc != null) {
                    cc.close().asStage().sync();
                }
                if (sc != null) {
                    sc.close().asStage().sync();
                }
                group.shutdownGracefully();
            }
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void pooledBufferAllocation() throws Exception {
        try (PooledBufferAllocator allocator = (PooledBufferAllocator) BufferAllocator.onHeapPooled()) {
            AtomicLong iterationCounter = new AtomicLong();
            FutureTask<Void> task = new FutureTask<>(() -> {
                List<Buffer> buffers = new ArrayList<>();
                long count;
                do {
                    count = iterationCounter.get();
                } while (count == 0);
                for (int i = 0; i < 13; i++) {
                    int size = 8 << i;
                    buffers.add(allocator.allocate(size));
                }
                for (Buffer buffer : buffers) {
                    buffer.close();
                }
                return null;
            });
            FastThreadLocalThread thread = new FastThreadLocalThread(task);
            thread.start();
            do {
                allocator.dumpStats(); // This will take internal pool locks, and we'll race with the thread.
                iterationCounter.set(1);
            } while (thread.isAlive());
            thread.join();
            task.get();
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testUnixResolverDnsServerAddressStreamProvider_Parse() throws InterruptedException {
        doTestParseResolverFilesAllowsBlockingCalls(DnsServerAddressStreamProviders::unixDefault);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testHostsFileParser_Parse() throws InterruptedException {
        doTestParseResolverFilesAllowsBlockingCalls(DnsNameResolverBuilder::new);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testUnixResolverDnsServerAddressStreamProvider_ParseEtcResolverSearchDomainsAndOptions()
            throws InterruptedException {
        EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        try {
            DnsNameResolverBuilder builder = new DnsNameResolverBuilder(group.next())
                    .datagramChannelFactory(NioDatagramChannel::new);
            doTestParseResolverFilesAllowsBlockingCalls(builder::build);
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void doTestParseResolverFilesAllowsBlockingCalls(Callable<Object> callable)
            throws InterruptedException {
        SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(new DefaultThreadFactory("test")) {
                    @Override
                    protected void run() {
                        while (!confirmShutdown()) {
                            Runnable task = takeTask();
                            if (task != null) {
                                task.run();
                            }
                        }
                    }
                };
        try {
            CountDownLatch latch = new CountDownLatch(1);
            List<Object> result = new ArrayList<>();
            List<Throwable> error = new ArrayList<>();
            executor.execute(() -> {
                try {
                    result.add(callable.call());
                } catch (Throwable t) {
                    error.add(t);
                }
                latch.countDown();
            });
            latch.await();
            assertEquals(0, error.size());
            assertEquals(1, result.size());
        } finally {
            executor.shutdownGracefully();
        }
    }

    private static void testTrustManagerVerify(SslProvider provider, String tlsVersion) throws Exception {
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .sslProvider(provider)
                .protocols(tlsVersion)
                .endpointIdentificationAlgorithm(null)
                .trustManager(ResourcesUtil.getFile(
                        NettyBlockHoundIntegrationTest.class, "mutual_auth_ca.pem"))
                .build();

        File cert = ResourcesUtil.getFile(NettyBlockHoundIntegrationTest.class, "localhost_server.pem");
        File key = ResourcesUtil.getFile(NettyBlockHoundIntegrationTest.class, "localhost_server.key");
        final SslContext sslServerCtx = SslContextBuilder.forServer(cert, key, null)
                .sslProvider(provider)
                .protocols(tlsVersion)
                .build();

        final SslHandler clientSslHandler = sslClientCtx.newHandler(offHeapAllocator());
        final SslHandler serverSslHandler = sslServerCtx.newHandler(offHeapAllocator());

        testHandshake(sslClientCtx, clientSslHandler, serverSslHandler);
    }

    private static void testHandshakeWithExecutor(Executor executor, String tlsVersion) throws Exception {
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .endpointIdentificationAlgorithm(null)
                .sslProvider(SslProvider.JDK).protocols(tlsVersion).build();

        X509Bundle cert = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        final SslContext sslServerCtx = SslContextBuilder.forServer(cert.getKeyPair().getPrivate(),
                        cert.getCertificatePath())
                .sslProvider(SslProvider.JDK).protocols(tlsVersion).build();

        final SslHandler clientSslHandler = sslClientCtx.newHandler(offHeapAllocator(), executor);
        final SslHandler serverSslHandler = sslServerCtx.newHandler(offHeapAllocator(), executor);

        testHandshake(sslClientCtx, clientSslHandler, serverSslHandler);
    }

    private static void testHandshake(SslContext sslClientCtx, SslHandler clientSslHandler,
                                      SslHandler serverSslHandler) throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        Channel sc = null;
        Channel cc = null;
        try (AutoCloseable ignore = autoClosing(sslClientCtx)) {
            try {
                sc = new ServerBootstrap()
                        .group(group)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(serverSslHandler)
                        .bind(new InetSocketAddress(0)).asStage().get();

                Future<Channel> future = new Bootstrap()
                        .group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                ch.pipeline()
                                  .addLast(clientSslHandler)
                                  .addLast(new ChannelHandler() {

                                      @Override
                                      public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                                          if (evt instanceof SslHandshakeCompletionEvent &&
                                              ((SslHandshakeCompletionEvent) evt).cause() != null) {
                                              ((SslHandshakeCompletionEvent) evt).cause().printStackTrace();
                                          }
                                          ctx.fireChannelInboundEvent(evt);
                                      }
                                  });
                            }
                        }).connect(sc.localAddress());
                cc = future.asStage().get();

                clientSslHandler.handshakeFuture().asStage().sync();
                serverSslHandler.handshakeFuture().asStage().sync();
            } finally {
                if (cc != null) {
                    cc.close().asStage().sync();
                }
                if (sc != null) {
                    sc.close().asStage().sync();
                }
                group.shutdownGracefully();
            }
        }
    }

    private static class TestLinkedBlockingQueue<T> extends LinkedBlockingQueue<T> {

        private final ReentrantLock lock = new ReentrantLock();

        @Override
        public boolean offer(T t) {
            lock.lock();
            try {
                return super.offer(t);
            } finally {
                lock.unlock();
            }
        }

        void emulateContention() {
            lock.lock();
        }

        void waitUntilContented() throws InterruptedException {
            // wait until the lock gets contended
            while (lock.getQueueLength() == 0) {
                Thread.sleep(10L);
            }
        }

        void removeContention() {
            lock.unlock();
        }
    }
}
