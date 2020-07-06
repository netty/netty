/*
 * Copyright 2019 The Netty Project

 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:

 * http://www.apache.org/licenses/LICENSE-2.0

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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
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
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    // Tests copied from io.netty.handler.ssl.SslHandlerTest
    @Test
    public void testHandshakeWithExecutorThatExecuteDirectory() throws Exception {
        testHandshakeWithExecutor(Runnable::run);
    }

    @Test
    public void testHandshakeWithImmediateExecutor() throws Exception {
        testHandshakeWithExecutor(ImmediateExecutor.INSTANCE);
    }

    @Test
    public void testHandshakeWithImmediateEventExecutor() throws Exception {
        testHandshakeWithExecutor(ImmediateEventExecutor.INSTANCE);
    }

    @Test
    public void testHandshakeWithExecutor() throws Exception {
        ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            testHandshakeWithExecutor(executorService);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void testTrustManagerVerify() throws Exception {
        final SslContext sslClientCtx =
                SslContextBuilder.forClient()
                                 .trustManager(ResourcesUtil.getFile(getClass(), "mutual_auth_ca.pem"))
                                 .build();

        final SslContext sslServerCtx =
                SslContextBuilder.forServer(ResourcesUtil.getFile(getClass(), "localhost_server.pem"),
                                            ResourcesUtil.getFile(getClass(), "localhost_server.key"),
                                            null)
                                 .build();

        final SslHandler clientSslHandler = sslClientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);
        final SslHandler serverSslHandler = sslServerCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);

        testHandshake(sslClientCtx, clientSslHandler, serverSslHandler);
    }

    private static void testHandshakeWithExecutor(Executor executor) throws Exception {
        String tlsVersion = "TLSv1.2";
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

            assertTrue(clientSslHandler.handshakeFuture().await().isSuccess());
            assertTrue(serverSslHandler.handshakeFuture().await().isSuccess());
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
}
