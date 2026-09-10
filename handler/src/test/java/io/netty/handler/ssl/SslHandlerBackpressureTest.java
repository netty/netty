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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SslTestPendingBytesAccess;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.util.CachedSelfSignedCertificate;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SslHandlerBackpressureTest {
    private static final int LOW_WATER_MARK = 2 * 1024;
    private static final int HIGH_WATER_MARK = 4 * 1024;
    private static final int CHUNK_SIZE = 1024;
    private static final int CHUNKS = 64;

    @Test
    public void testFlushConvertsPlaintextIncrementallyAndPreservesFlushBoundary() throws Exception {
        SelfSignedCertificate certificate = CachedSelfSignedCertificate.getCachedCertificate();
        SslContext serverContext = SslContextBuilder
                .forServer(certificate.certificate(), certificate.privateKey())
                .sslProvider(SslProvider.JDK)
                .build();
        SslContext clientContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(SslProvider.JDK)
                .build();

        SlowOutboundHandler slowTransport = new SlowOutboundHandler();
        SslHandler serverSsl = serverContext.newHandler(UnpooledByteBufAllocatorHolder.ALLOCATOR);
        SslHandler clientSsl = clientContext.newHandler(UnpooledByteBufAllocatorHolder.ALLOCATOR);
        EmbeddedChannel server = new EmbeddedChannel(slowTransport, serverSsl);
        EmbeddedChannel client = new EmbeddedChannel(clientSsl);
        server.config().setWriteBufferWaterMark(new WriteBufferWaterMark(LOW_WATER_MARK, HIGH_WATER_MARK));

        try {
            completeHandshake(client, server, clientSsl, serverSsl);
            slowTransport.enableSlowMode();

            ChannelFuture lastFlushedFuture = null;
            for (int i = 0; i < CHUNKS; i++) {
                ByteBuf payload = server.alloc().heapBuffer(CHUNK_SIZE, CHUNK_SIZE).writeZero(CHUNK_SIZE);
                lastFlushedFuture = server.pipeline().write(payload);
            }
            final ChannelFuture flushedFuture = lastFlushedFuture;

            assertFalse(server.isWritable(), "queued plaintext should participate in channel writability");
            server.flush();
            server.runPendingTasks();

            long allowedBurst = HIGH_WATER_MARK + 2048L;
            assertTrue(slowTransport.pendingBytes() <= allowedBurst,
                    "a single flush must not convert the complete plaintext batch into transport-held TLS output");

            // This write occurs after the first flush. It must not be consumed by asynchronous resume work.
            ChannelFuture unflushedFuture = server.pipeline().write(
                    server.alloc().heapBuffer(CHUNK_SIZE, CHUNK_SIZE).writeZero(CHUNK_SIZE));

            int iterations = 0;
            while (!flushedFuture.isDone() && iterations++ < 128) {
                assertTrue(slowTransport.pendingBytes() <= allowedBurst,
                        "each resumed TLS burst must remain bounded by the transport watermark");
                slowTransport.releaseAll();
                server.runPendingTasks();
            }

            assertTrue(flushedFuture.isSuccess(),
                    "the originally flushed batch did not drain: " + flushedFuture.cause());
            assertFalse(unflushedFuture.isDone(),
                    "data written after the flush boundary must remain unflushed during asynchronous resume");
            assertTrue(slowTransport.maxPendingBytes() <= allowedBurst,
                    "TLS output exceeded the bounded burst target: " + slowTransport.maxPendingBytes());

            // A later explicit flush is what makes the post-boundary write eligible for TLS conversion.
            server.flush();
            server.runPendingTasks();
            iterations = 0;
            while (!unflushedFuture.isDone() && iterations++ < 32) {
                slowTransport.releaseAll();
                server.runPendingTasks();
            }
            assertTrue(unflushedFuture.isSuccess(),
                    "the later explicitly-flushed write did not drain: " + unflushedFuture.cause());
        } finally {
            slowTransport.releaseAll();
            server.finishAndReleaseAll();
            client.finishAndReleaseAll();
        }
    }

    private static void completeHandshake(
            EmbeddedChannel client,
            EmbeddedChannel server,
            SslHandler clientSsl,
            SslHandler serverSsl) {
        for (int i = 0; i < 100; i++) {
            if (clientSsl.handshakeFuture().isDone() && serverSsl.handshakeFuture().isDone()) {
                break;
            }
            boolean progressed = transferOutbound(client, server);
            progressed |= transferOutbound(server, client);
            client.runPendingTasks();
            server.runPendingTasks();
            if (!progressed) {
                client.runScheduledPendingTasks();
                server.runScheduledPendingTasks();
            }
        }
        transferOutbound(client, server);
        transferOutbound(server, client);
        assertTrue(clientSsl.handshakeFuture().isSuccess(),
                "client TLS handshake failed: " + clientSsl.handshakeFuture().cause());
        assertTrue(serverSsl.handshakeFuture().isSuccess(),
                "server TLS handshake failed: " + serverSsl.handshakeFuture().cause());
    }

    private static boolean transferOutbound(EmbeddedChannel from, EmbeddedChannel to) {
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

    private static final class SlowOutboundHandler extends ChannelDuplexHandler {
        private final List<PendingWrite> pending = new ArrayList<PendingWrite>();
        private boolean slowMode;
        private long pendingBytes;
        private long maxPendingBytes;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!slowMode || !(msg instanceof ByteBuf)) {
                ctx.write(msg, promise);
                return;
            }

            ByteBuf buffer = (ByteBuf) msg;
            int bytes = buffer.readableBytes();
            SslTestPendingBytesAccess.increment(ctx.channel(), bytes);
            pendingBytes += bytes;
            maxPendingBytes = Math.max(maxPendingBytes, pendingBytes);
            pending.add(new PendingWrite(buffer, promise, bytes));
        }

        void enableSlowMode() {
            slowMode = true;
        }

        long pendingBytes() {
            return pendingBytes;
        }

        long maxPendingBytes() {
            return maxPendingBytes;
        }

        void releaseAll() {
            if (pending.isEmpty()) {
                return;
            }
            List<PendingWrite> draining = new ArrayList<PendingWrite>(pending);
            pending.clear();
            pendingBytes = 0;
            for (PendingWrite write : draining) {
                SslTestPendingBytesAccess.decrement(write.promise.channel(), write.bytes);
                ReferenceCountUtil.safeRelease(write.buffer);
                write.promise.trySuccess();
            }
        }
    }

    private static final class PendingWrite {
        final ByteBuf buffer;
        final ChannelPromise promise;
        final int bytes;

        PendingWrite(ByteBuf buffer, ChannelPromise promise, int bytes) {
            this.buffer = buffer;
            this.promise = promise;
            this.bytes = bytes;
        }
    }

    /** Avoids depending on the channel allocator choice in this focused JDK-SSLEngine test. */
    private static final class UnpooledByteBufAllocatorHolder {
        static final io.netty.buffer.ByteBufAllocator ALLOCATOR = io.netty.buffer.UnpooledByteBufAllocator.DEFAULT;
    }
}
