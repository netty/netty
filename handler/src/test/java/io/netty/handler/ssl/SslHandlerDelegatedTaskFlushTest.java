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
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.util.CachedSelfSignedCertificate;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: a {@code flush()} that arrives while a delegated SSLEngine task (e.g. trust-manager
 * verification offloaded to a background executor) is still in flight gets silently dropped by the
 * {@code STATE_PROCESS_TASK} check in {@code flush()} - by design, since {@code resumeOnEventExecutor()} is
 * supposed to pick the pending write back up once the task completes. That pickup calls {@code wrap()} directly
 * (via {@code wrapGuarded}), which only ever processes plaintext up to {@code flushedPlaintextBytes}. Unless that
 * boundary is re-established to cover everything currently queued before resuming, a write made during the
 * dropped flush() sits in {@code pendingUnencryptedWrites} forever: {@code flushedPlaintextBytes} never advances
 * again on its own, so the write's promise never completes.
 */
public class SslHandlerDelegatedTaskFlushTest {

    /**
     * Blocks {@code checkServerTrusted} on a latch the test controls, so the client's delegated trust-verification
     * task can be held "in flight" for as long as needed before letting it complete.
     */
    private static final class BlockingTrustManager extends X509ExtendedTrustManager {
        private final CountDownLatch entered;
        private final CountDownLatch release;

        BlockingTrustManager(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new CertificateException("release latch never opened");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CertificateException(e);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    @Test
    @Timeout(30)
    public void testFlushDroppedDuringDelegatedTaskIsNotLost() throws Exception {
        SelfSignedCertificate ssc = CachedSelfSignedCertificate.getCachedCertificate();
        CountDownLatch trustCheckEntered = new CountDownLatch(1);
        CountDownLatch releaseTrustCheck = new CountDownLatch(1);
        ExecutorService delegatedTaskExecutor = Executors.newSingleThreadExecutor();

        try {
            SslContext serverCtx = SslContextBuilder
                    .forServer(ssc.certificate(), ssc.privateKey())
                    .sslProvider(SslProvider.JDK)
                    .build();
            SslContext clientCtx = SslContextBuilder
                    .forClient()
                    .trustManager(new BlockingTrustManager(trustCheckEntered, releaseTrustCheck))
                    .sslProvider(SslProvider.JDK)
                    .build();

            SslHandler serverSsl = serverCtx.newHandler(UnpooledByteBufAllocator.DEFAULT);
            // The delegated task executor must be a real, separate thread: runDelegatedTasks() only offloads
            // asynchronously (taking the code path this test targets) when it is not already on the event loop.
            SslHandler clientSsl = clientCtx.newHandler(UnpooledByteBufAllocator.DEFAULT, delegatedTaskExecutor);
            BlockingQueue<ByteBuf> serverReceived = new LinkedBlockingQueue<ByteBuf>();
            EmbeddedChannel server = new EmbeddedChannel(serverSsl, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    serverReceived.add((ByteBuf) msg);
                }
            });
            EmbeddedChannel client = new EmbeddedChannel(clientSsl);

            try {
                // Drive the handshake far enough that the client reaches server-certificate verification, which
                // JDK's SSLEngine always offloads as a delegated task. The trust manager blocks there, on the
                // background executor, until the test releases it below.
                for (int i = 0; i < 200 && !trustCheckEntered.await(10, TimeUnit.MILLISECONDS); i++) {
                    transfer(client, server);
                    transfer(server, client);
                    client.runPendingTasks();
                    server.runPendingTasks();
                }
                assertTrue(trustCheckEntered.await(5, TimeUnit.SECONDS),
                        "client never reached server-certificate verification");

                // The delegated task is now in flight on a background thread: STATE_PROCESS_TASK is set on the
                // client's SslHandler. A flush() now is exactly the one flush() silently dropped by the
                // STATE_PROCESS_TASK check - resumeOnEventExecutor() must not forget it once the task completes.
                ChannelFuture writeFuture = client.writeAndFlush(client.alloc().buffer(4).writeInt(42));

                releaseTrustCheck.countDown();

                // The background thread's task completion schedules resumeOnEventExecutor() via
                // ctx.executor().execute(...); pump the client's task queue and the handshake transfer loop until
                // the write finishes (or the deadline below is hit, which is the failure mode this test guards
                // against).
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!writeFuture.isDone() && System.nanoTime() < deadline) {
                    transfer(client, server);
                    transfer(server, client);
                    client.runPendingTasks();
                    server.runPendingTasks();
                    Thread.sleep(5);
                }
                // Let the server catch up on whatever the client's last flight produced (e.g. its own handshake
                // completion and/or the delivered application data) before asserting or tearing down.
                for (int i = 0; i < 20 && !serverSsl.handshakeFuture().isDone(); i++) {
                    transfer(client, server);
                    transfer(server, client);
                    client.runPendingTasks();
                    server.runPendingTasks();
                }

                assertTrue(writeFuture.isDone(),
                        "the write made while a delegated task was in flight never completed - its flush() was "
                                + "dropped and never picked back up");
                assertTrue(writeFuture.isSuccess(), "the write failed: " + writeFuture.cause());
                assertTrue(clientSsl.handshakeFuture().isDone() && clientSsl.handshakeFuture().isSuccess(),
                        "handshake did not complete successfully: " + clientSsl.handshakeFuture().cause());

                ByteBuf received = serverReceived.poll();
                assertTrue(received != null && received.readableBytes() == 4 && received.readInt() == 42,
                        "server never received the plaintext payload");
                received.release();
            } finally {
                releaseTrustCheck.countDown();
                server.finishAndReleaseAll();
                client.finishAndReleaseAll();
            }
        } finally {
            delegatedTaskExecutor.shutdownNow();
        }
    }

    private static boolean transfer(EmbeddedChannel from, EmbeddedChannel to) {
        boolean progressed = false;
        for (;;) {
            Object msg = from.readOutbound();
            if (msg == null) {
                return progressed;
            }
            progressed = true;
            to.writeInbound(msg);
        }
    }
}
