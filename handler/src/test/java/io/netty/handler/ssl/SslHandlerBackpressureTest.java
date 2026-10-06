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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for https://github.com/netty/netty/issues/17304: a large flushed plaintext backlog must not be
 * eagerly converted into pooled TLS output while the downstream transport is slow to drain it, and any application
 * data written after that flush must stay behind the next explicit flush boundary while wrapping is suspended.
 *
 * <p>Covers both branches of the backpressure check in {@code wrap()}: an inline {@code forceFlush()} that
 * actually frees up room should let wrapping continue synchronously without ever suspending (see
 * {@link #testInlineFlushRelievesSelfInducedBackpressureWithoutSuspending()}), while one that does not (a
 * genuinely slow transport) must still fall back to the suspend/resume path (the other two tests below).
 */
public class SslHandlerBackpressureTest {
    private static final int LOW_WATER_MARK = 2 * 1024;
    private static final int HIGH_WATER_MARK = 4 * 1024;
    private static final int CHUNK_SIZE = 1024;
    private static final int CHUNKS = 64;
    // The last burst can overshoot the high watermark by up to one more wrap of plaintext before the outbound
    // buffer accounting registers the transport as full; this margin only needs to rule out unbounded growth.
    private static final long ALLOWED_BURST = HIGH_WATER_MARK + 4L * CHUNK_SIZE;

    @Test
    public void testInlineFlushRelievesSelfInducedBackpressureWithoutSuspending() throws Exception {
        Fixture fixture = Fixture.handshaked();
        try {
            // Slow mode is never enabled here: the transport is a plain EmbeddedChannel that always fully drains
            // whatever it is asked to flush. The high watermark can still be crossed purely because wrap() writes
            // several TLS records to the real outbound buffer before wrapAndFlush()'s own flush ever runs. wrap()
            // should notice that, flush inline to actually attempt those writes against the transport, see that
            // this alone makes enough room, and keep going - completing the whole backlog synchronously, within
            // this single flush() call, without ever falling back to suspendWrapUntilTransportDrains(...).
            ChannelFuture flushedFuture = null;
            for (int i = 0; i < CHUNKS; i++) {
                flushedFuture = fixture.server.pipeline().write(zeroes(fixture.server, CHUNK_SIZE));
            }
            assertFalse(fixture.server.isWritable(), "queued plaintext should already count against writability");

            fixture.server.flush();
            fixture.server.runPendingTasks();

            assertTrue(flushedFuture.isDone(), "the batch should have drained");
            assertTrue(flushedFuture.isSuccess(), "the flushed batch failed to drain: " + flushedFuture.cause());
            assertTrue(fixture.server.isWritable(), "the channel should be writable again once fully drained");
            // Note isDone()/isSuccess() alone don't prove wrapping never suspended: EmbeddedChannel automatically
            // drains its task queue after every flush()/write(), so even a suspend that resumes immediately would
            // look, from the outside, the same as never having suspended at all. wrapSuspendCount is what actually
            // distinguishes "made progress via an inline flush" from "suspended and then resumed very quickly".
            assertEquals(0, fixture.serverSsl.wrapSuspendCount,
                    "wrapping should never have needed to suspend: the inline flush should have kept making " +
                            "progress by itself");
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testResumeCompletingSynchronouslyInsideActiveFlushCycleDoesNotRecurse() throws Exception {
        Fixture fixture = Fixture.handshaked();
        try {
            // Arm this only now: the handshake itself needs its flushes to actually go through.
            fixture.deferFirstFlush.arm();

            ChannelFuture flushedFuture = null;
            for (int i = 0; i < CHUNKS; i++) {
                flushedFuture = fixture.server.pipeline().write(zeroes(fixture.server, CHUNK_SIZE));
            }
            fixture.server.flush();
            fixture.server.runPendingTasks();

            // Sequence this reproduces: wrap() writes real TLS output to the real outbound buffer, then hits the
            // watermark and tries an inline flush to make room - which DeferFirstFlushHandler swallows, so wrap()
            // has no choice but to suspend, piggybacking on that still-outstanding write's promise. Control
            // unwinds back up to wrapAndFlush()'s own finally { forceFlush(ctx); }, whose flush is the *second*
            // one - which DeferFirstFlushHandler lets through - and EmbeddedChannel completes it synchronously,
            // firing WrapResumeListener nested inside wrapAndFlush()'s still-active cycle, before wrapAndFlush()
            // itself has returned. That must turn into a loop iteration (via STATE_WRAP_ACTIVE /
            // STATE_WRAP_RETRY_PENDING), not a reentrant wrap() call.
            assertTrue(fixture.serverSsl.wrapSuspendCount > 0,
                    "the first swallowed flush should have forced wrapping to suspend at least once");
            assertTrue(flushedFuture.isDone(),
                    "the nested, synchronous resume should have let wrapping finish the backlog by itself, " +
                            "within the original flush() call");
            assertTrue(flushedFuture.isSuccess(), "the flushed batch failed to drain: " + flushedFuture.cause());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testFlushedBacklogIsWrappedIncrementallyUnderBackpressure() throws Exception {
        Fixture fixture = Fixture.handshaked();
        try {
            fixture.slowTransport.enableSlowMode();

            ChannelFuture flushedFuture = null;
            for (int i = 0; i < CHUNKS; i++) {
                flushedFuture = fixture.server.pipeline().write(zeroes(fixture.server, CHUNK_SIZE));
            }
            // The plaintext is still owned by SslHandler at this point but already counts against the channel's
            // write-buffer watermark, which is exactly why a plain Channel.isWritable() check cannot drive the
            // backpressure decision (see the linked issue).
            assertFalse(fixture.server.isWritable(), "queued plaintext should already count against writability");

            fixture.server.flush();
            fixture.server.runPendingTasks();

            assertTrue(fixture.slowTransport.pendingBytes() <= ALLOWED_BURST,
                    "a single flush must not convert the whole plaintext backlog into transport-held TLS output");
            // The transport never drains anything until releaseAll() is called below, so the inline flush attempt
            // in wrap() cannot have made room by itself: confirm it actually fell back to suspending.
            assertTrue(fixture.serverSsl.wrapSuspendCount > 0,
                    "a genuinely stuck transport should still cause wrapping to suspend");

            fixture.drainUntilDone(flushedFuture, ALLOWED_BURST);

            assertTrue(flushedFuture.isSuccess(), "the flushed batch did not drain: " + flushedFuture.cause());
            assertTrue(fixture.slowTransport.maxPendingBytes() <= ALLOWED_BURST,
                    "TLS output exceeded the bounded burst target: " + fixture.slowTransport.maxPendingBytes());
        } finally {
            fixture.close();
        }
    }

    @Test
    public void testWritesAfterFlushStayBehindTheNextFlushBoundary() throws Exception {
        Fixture fixture = Fixture.handshaked();
        try {
            fixture.slowTransport.enableSlowMode();

            ChannelFuture flushedFuture = null;
            for (int i = 0; i < CHUNKS; i++) {
                flushedFuture = fixture.server.pipeline().write(zeroes(fixture.server, CHUNK_SIZE));
            }
            fixture.server.flush();
            fixture.server.runPendingTasks();

            // Written after the flush above: must not be pulled forward into the batch that is already
            // draining, even though wrapping resumes asynchronously while this write sits in the same queue.
            ChannelFuture unflushedFuture = fixture.server.pipeline().write(zeroes(fixture.server, CHUNK_SIZE));

            fixture.drainUntilDone(flushedFuture, ALLOWED_BURST);

            assertTrue(flushedFuture.isSuccess(), "the flushed batch did not drain: " + flushedFuture.cause());
            assertFalse(unflushedFuture.isDone(),
                    "data written after the flush boundary must not be sent before its own flush()");

            // Only an explicit flush() makes the later write eligible.
            fixture.server.flush();
            fixture.server.runPendingTasks();
            fixture.drainUntilDone(unflushedFuture, ALLOWED_BURST);

            assertTrue(unflushedFuture.isSuccess(), "the later, explicitly-flushed write did not drain: " +
                    unflushedFuture.cause());
        } finally {
            fixture.close();
        }
    }

    private static ByteBuf zeroes(EmbeddedChannel channel, int size) {
        return channel.alloc().buffer(size, size).writeZero(size);
    }

    private static final class Fixture {
        final EmbeddedChannel client;
        final EmbeddedChannel server;
        final SlowOutboundHandler slowTransport;
        final DeferFirstFlushHandler deferFirstFlush;
        final SslHandler serverSsl;

        private Fixture(EmbeddedChannel client, EmbeddedChannel server, SlowOutboundHandler slowTransport,
                DeferFirstFlushHandler deferFirstFlush, SslHandler serverSsl) {
            this.client = client;
            this.server = server;
            this.slowTransport = slowTransport;
            this.deferFirstFlush = deferFirstFlush;
            this.serverSsl = serverSsl;
        }

        static Fixture handshaked() throws Exception {
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
            DeferFirstFlushHandler deferFirstFlush = new DeferFirstFlushHandler();
            SslHandler serverSsl = serverContext.newHandler(UnpooledByteBufAllocator.DEFAULT);
            SslHandler clientSsl = clientContext.newHandler(UnpooledByteBufAllocator.DEFAULT);
            EmbeddedChannel server = new EmbeddedChannel(slowTransport, deferFirstFlush, serverSsl);
            EmbeddedChannel client = new EmbeddedChannel(clientSsl);
            server.config().setWriteBufferWaterMark(new WriteBufferWaterMark(LOW_WATER_MARK, HIGH_WATER_MARK));

            for (int i = 0; i < 100; i++) {
                if (clientSsl.handshakeFuture().isDone() && serverSsl.handshakeFuture().isDone()) {
                    break;
                }
                boolean progressed = transfer(client, server);
                progressed |= transfer(server, client);
                client.runPendingTasks();
                server.runPendingTasks();
                if (!progressed) {
                    client.runScheduledPendingTasks();
                    server.runScheduledPendingTasks();
                }
            }
            transfer(client, server);
            transfer(server, client);
            assertTrue(clientSsl.handshakeFuture().isSuccess(),
                    "client TLS handshake failed: " + clientSsl.handshakeFuture().cause());
            assertTrue(serverSsl.handshakeFuture().isSuccess(),
                    "server TLS handshake failed: " + serverSsl.handshakeFuture().cause());
            // The handshake itself (certificate exchange, etc.) can trip the small watermark used by these tests
            // and cause its own suspends; reset so tests only observe suspends from the data they write themselves.
            serverSsl.wrapSuspendCount = 0;

            return new Fixture(client, server, slowTransport, deferFirstFlush, serverSsl);
        }

        /**
         * Repeatedly releases whatever the slow transport is holding onto and re-runs pending tasks, asserting the
         * bounded-burst invariant on every iteration, until {@code future} completes or an iteration budget is
         * exhausted.
         */
        void drainUntilDone(ChannelFuture future, long allowedBurst) {
            int iterations = 0;
            while (!future.isDone() && iterations++ < 256) {
                assertTrue(slowTransport.pendingBytes() <= allowedBurst,
                        "each resumed TLS burst must remain bounded by the transport watermark");
                slowTransport.releaseAll();
                server.runPendingTasks();
            }
        }

        void close() {
            slowTransport.releaseAll();
            // Closing triggers one more flush (the TLS close_notify); let it pass straight through instead of
            // being diverted and forgotten by the slow-transport simulation.
            slowTransport.disableSlowMode();
            server.finishAndReleaseAll();
            client.finishAndReleaseAll();
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

    /**
     * Diverts outbound writes once "slow mode" is enabled instead of letting {@link EmbeddedChannel} complete them
     * synchronously, and drives the channel's real pending-outbound-bytes accounting directly so the bytes behave,
     * from SslHandler's perspective, like TLS output a real slow peer has not yet drained.
     */
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

        void disableSlowMode() {
            slowMode = false;
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

    /**
     * Lets writes and the first {@code flush()} call through completely untouched (so the handshake, which happens
     * before a test arms this, is unaffected). Once armed, exactly one subsequent {@code flush()} is swallowed
     * instead of forwarded - simulating a transport that accepted the flush request but has not actually drained
     * anything yet - and every {@code flush()} after that goes through normally. Since {@link EmbeddedChannel}
     * always fully and synchronously drains whatever it does flush, that first real flush afterward completes
     * everything at once, including whichever write's promise wrap() is using as its resume signal - reproducing
     * the case where that promise completes nested inside wrap()'s own still-active flush cycle.
     */
    private static final class DeferFirstFlushHandler extends ChannelDuplexHandler {
        private boolean armed;
        private boolean deferredOnce;

        void arm() {
            armed = true;
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            if (armed && !deferredOnce) {
                deferredOnce = true;
                return;
            }
            ctx.flush();
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
}
