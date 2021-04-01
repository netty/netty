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
package io.netty.util.internal;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.util.HashedWheelTimer;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.ImmediateExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.Hidden.NettyBlockHoundIntegration;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.Test;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.blockhound.integration.BlockHoundIntegration;

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
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class NettyBlockHoundIntegrationTest {

    @BeforeClass
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

    @Test(timeout = 5000L)
    public void testGlobalEventExecutorTakeTask() throws InterruptedException {
        testEventExecutorTakeTask(GlobalEventExecutor.INSTANCE);
    }

    @Test(timeout = 5000L)
    public void testSingleThreadEventExecutorTakeTask() throws InterruptedException {
        SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(null, new DefaultThreadFactory("test"), true) {
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
        ScheduledFuture<?> f = eventExecutor.schedule(latch::countDown, 10, TimeUnit.MILLISECONDS);
        f.sync();
        latch.await();
    }

    @Test(timeout = 5000L)
    public void testSingleThreadEventExecutorAddTask() throws Exception {
        TestLinkedBlockingQueue<Runnable> taskQueue = new TestLinkedBlockingQueue<>();
        SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(null, new DefaultThreadFactory("test"), true) {
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

    @Test(timeout = 5000L)
    public void testHashedWheelTimerStartStop() throws Exception {
        HashedWheelTimer timer = new HashedWheelTimer();
        Future<?> futureStart = GlobalEventExecutor.INSTANCE.submit(timer::start);
        futureStart.get(5, TimeUnit.SECONDS);
        Future<?> futureStop = GlobalEventExecutor.INSTANCE.submit(timer::stop);
        futureStop.get(5, TimeUnit.SECONDS);
    }

    // Tests copied from io.netty.handler.ssl.SslHandlerTest
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
    public void testTrustManagerVerify() throws Exception {
        testTrustManagerVerify("TLSv1.2");
    }

    @Test
    public void testTrustManagerVerifyTLSv13() throws Exception {
        assumeTrue(SslProvider.isTlsv13Supported(SslProvider.JDK));
        testTrustManagerVerify("TLSv1.3");
    }

    @Test
    public void testSslHandlerWrapAllowsBlockingCalls() throws Exception {
        final SslContext sslClientCtx =
                SslContextBuilder.forClient()
                                 .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                 .sslProvider(SslProvider.JDK)
                                 .build();
        final SslHandler sslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);
        final EventLoopGroup group = new NioEventLoopGroup();
        final CountDownLatch activeLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        Channel sc = null;
        Channel cc = null;
        try {
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(0))
                    .syncUninterruptibly()
                    .channel();

            cc = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {

                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(sslHandler);
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {

                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    activeLatch.countDown();
                                }

                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof SslHandshakeCompletionEvent &&
                                            ((SslHandshakeCompletionEvent) evt).cause() != null) {
                                        Throwable cause = ((SslHandshakeCompletionEvent) evt).cause();
                                        cause.printStackTrace();
                                        error.set(cause);
                                    }
                                    ctx.fireUserEventTriggered(evt);
                                }
                            });
                        }
                    })
                    .connect(sc.localAddress())
                    .addListener((ChannelFutureListener) future ->
                        future.channel().writeAndFlush(wrappedBuffer(new byte [] { 1, 2, 3, 4 })))
                    .syncUninterruptibly()
                    .channel();

            assertTrue(activeLatch.await(5, TimeUnit.SECONDS));
            assertNull(error.get());
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
            ReferenceCountUtil.release(sslClientCtx);
        }
    }

    @Test(timeout = 5000L)
    public void testUnixResolverDnsServerAddressStreamProvider_Parse() throws InterruptedException {
        doTestParseResolverFilesAllowsBlockingCalls(DnsServerAddressStreamProviders::unixDefault);
    }

    @Test(timeout = 5000L)
    public void testHostsFileParser_Parse() throws InterruptedException {
        doTestParseResolverFilesAllowsBlockingCalls(DnsNameResolverBuilder::new);
    }

    @Test(timeout = 5000L)
    public void testUnixResolverDnsServerAddressStreamProvider_ParseEtcResolverSearchDomainsAndOptions()
            throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            DnsNameResolverBuilder builder = new DnsNameResolverBuilder(group.next())
                    .channelFactory(NioDatagramChannel::new);
            doTestParseResolverFilesAllowsBlockingCalls(builder::build);
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void doTestParseResolverFilesAllowsBlockingCalls(Callable<Object> callable)
            throws InterruptedException {
        SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(null, new DefaultThreadFactory("test"), true) {
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

    private static void testTrustManagerVerify(String tlsVersion) throws Exception {
        final SslContext sslClientCtx =
                SslContextBuilder.forClient()
                                 .protocols(tlsVersion)
                                 .trustManager(ResourcesUtil.getFile(
                                         NettyBlockHoundIntegrationTest.class, "mutual_auth_ca.pem"))
                                 .build();

        final SslContext sslServerCtx =
                SslContextBuilder.forServer(ResourcesUtil.getFile(
                        NettyBlockHoundIntegrationTest.class, "localhost_server.pem"),
                                            ResourcesUtil.getFile(
                                                    NettyBlockHoundIntegrationTest.class, "localhost_server.key"),
                                            null)
                                 .protocols(tlsVersion)
                                 .build();

        final SslHandler clientSslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);
        final SslHandler serverSslHandler = sslServerCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);

        testHandshake(sslClientCtx, clientSslHandler, serverSslHandler);
    }

    private static void testHandshakeWithExecutor(Executor executor, String tlsVersion) throws Exception {
        final SslContext sslClientCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(SslProvider.JDK).protocols(tlsVersion).build();

        final SelfSignedCertificate cert = new SelfSignedCertificate();
        final SslContext sslServerCtx = SslContextBuilder.forServer(cert.key(), cert.cert())
                .sslProvider(SslProvider.JDK).protocols(tlsVersion).build();

        final SslHandler clientSslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT, executor);
        final SslHandler serverSslHandler = sslServerCtx.newHandler(UnpooledByteBufAllocator.DEFAULT, executor);

        testHandshake(sslClientCtx, clientSslHandler, serverSslHandler);
    }

    private static void testHandshake(SslContext sslClientCtx, SslHandler clientSslHandler,
                                      SslHandler serverSslHandler) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        Channel sc = null;
        Channel cc = null;
        try {
            sc = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(serverSslHandler)
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

            ChannelFuture future = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                              .addLast(clientSslHandler)
                              .addLast(new ChannelInboundHandlerAdapter() {

                                  @Override
                                  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                      if (evt instanceof SslHandshakeCompletionEvent &&
                                              ((SslHandshakeCompletionEvent) evt).cause() != null) {
                                          ((SslHandshakeCompletionEvent) evt).cause().printStackTrace();
                                      }
                                      ctx.fireUserEventTriggered(evt);
                                  }
                              });
                        }
                    }).connect(sc.localAddress());
            cc = future.syncUninterruptibly().channel();

            clientSslHandler.handshakeFuture().await().sync();
            serverSslHandler.handshakeFuture().await().sync();
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
            ReferenceCountUtil.release(sslClientCtx);
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
