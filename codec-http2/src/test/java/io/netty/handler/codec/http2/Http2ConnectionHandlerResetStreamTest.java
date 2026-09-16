/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http2.Http2Error.CANCEL;
import static io.netty.handler.codec.http2.Http2Error.ENHANCE_YOUR_CALM;
import static io.netty.handler.codec.http2.Http2Error.FRAME_SIZE_ERROR;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.REFUSED_STREAM;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http2ConnectionHandlerResetStreamTest {
    enum Wrapping { NONE, REPLACE_PROMISE, DEFERRED, THROW }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void idleResetDoesNotWriteForEitherEndpoint(boolean server) {
        try (Fixture f = new Fixture(server, Wrapping.NONE, false)) {
            for (int id : new int[] {1, 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
                ChannelPromise promise = f.ctx.newPromise();
                f.handler.resetStream(f.ctx, id, CANCEL.code(), promise);
                assertTrue(promise.isSuccess());
                f.handler.resetStream(f.ctx, id, CANCEL.code(), f.ctx.voidPromise());
            }
            assertEquals(emptyList(), f.writer.resets);
            assertEquals(emptyList(), f.wireResets());
            assertTrue(f.channel.isActive());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void idleResetWithVoidPromiseReturnsListenableFuture(boolean server) {
        try (Fixture f = new Fixture(server, Wrapping.NONE, false)) {
            ChannelFuture future = f.handler.resetStream(f.ctx, 3, CANCEL.code(), f.ctx.voidPromise());
            future.addListener(future1 -> { });
            assertTrue(future.isSuccess());
            assertEquals(emptyList(), f.writer.resets);
        }
    }

    @ParameterizedTest
    @EnumSource(value = Wrapping.class, names = { "NONE", "REPLACE_PROMISE", "DEFERRED" })
    public void invalidInitialHeadersResetThroughEncoder(Wrapping wrapping) throws Exception {
        try (Fixture f = new Fixture(true, wrapping, false)) {
            f.headers(3, true, false, false);
            f.runDeferred();
            f.assertReset(3, PROTOCOL_ERROR);
            assertTrue(f.channel.isActive());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void invalidHeadersInContinuationResetStream(boolean priority) throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.REPLACE_PROMISE, false)) {
            f.headers(3, true, true, priority);
            f.assertReset(3, PROTOCOL_ERROR);
        }
    }

    @Test
    public void rejectedStreamCreationStillResetsReceivedHeaders() throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.REPLACE_PROMISE, false)) {
            f.connection.remote().maxActiveStreams(0);
            f.headers(3, false, false, false);
            f.assertReset(3, REFUSED_STREAM);
        }
    }

    @Test
    public void oversizedHeadersStillResetWhenStreamCreationFails() throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.REPLACE_PROMISE, false)) {
            f.connection.remote().maxActiveStreams(0);
            f.reader.headersConfiguration().maxHeaderListSize(1, 4096);
            f.headers(3, false, false, false);
            f.assertReset(3, PROTOCOL_ERROR);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3 })
    public void errorsOnIdleStreamsBecomeConnectionErrors(int variant) {
        try (Fixture f = new Fixture(true, Wrapping.NONE, false)) {
            f.channel.writeInbound(invalidFrame(variant));
            assertEquals(emptyList(), f.writer.resets);
            assertEquals(singletonList((variant >= 2 ? FRAME_SIZE_ERROR : PROTOCOL_ERROR).code()), f.writer.goaways);
            assertFalse(f.channel.isActive());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void malformedPushPromiseDoesNotOpenItsParentStream(boolean continuation) throws Exception {
        try (Fixture f = new Fixture(false, Wrapping.NONE, false)) {
            f.pushPromise(continuation, true);
            assertEquals(emptyList(), f.writer.resets);
            assertEquals(singletonList(PROTOCOL_ERROR.code()), f.writer.goaways);
            assertFalse(f.channel.isActive());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3 })
    public void errorsOnKnownStreamsStillResetOnlyTheStream(int variant) throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.REPLACE_PROMISE, false)) {
            f.connection.remote().createStream(3, false);
            f.channel.writeInbound(invalidFrame(variant));
            f.assertReset(3, variant >= 2 ? FRAME_SIZE_ERROR : PROTOCOL_ERROR);
            assertTrue(f.channel.isActive());
            assertEquals(emptyList(), f.writer.goaways);
        }
    }

    private static ByteBuf invalidFrame(int variant) {
        ByteBuf frame = Unpooled.buffer();
        if (variant == 0) {
            Http2CodecUtil.writeFrameHeaderInternal(frame, 5, Http2FrameTypes.PRIORITY, new Http2Flags(), 3);
            frame.writeInt(3).writeByte(15);
        } else if (variant == 1) {
            Http2CodecUtil.writeFrameHeaderInternal(frame, 4, Http2FrameTypes.WINDOW_UPDATE, new Http2Flags(), 3);
            frame.writeInt(0);
        } else if (variant == 2) {
            Http2CodecUtil.writeFrameHeaderInternal(frame, 0, Http2FrameTypes.PRIORITY, new Http2Flags(), 3);
        } else {
            Http2CodecUtil.writeFrameHeaderInternal(frame, 0, Http2FrameTypes.DATA,
                    new Http2Flags().paddingPresent(true), 3);
        }
        return frame;
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2 })
    public void rejectedPushResetsPromisedStreamNotParent(int rejectedCheck) throws Exception {
        Http2PromisedRequestVerifier verifier = new Http2PromisedRequestVerifier() {
            @Override
            public boolean isAuthoritative(ChannelHandlerContext ctx, Http2Headers headers) {
                return rejectedCheck != 0;
            }

            @Override
            public boolean isCacheable(Http2Headers headers) {
                return rejectedCheck != 1;
            }

            @Override
            public boolean isSafe(Http2Headers headers) {
                return rejectedCheck != 2;
            }
        };
        for (boolean continuation : new boolean[] { true, false }) {
            try (Fixture f = new Fixture(false, Wrapping.REPLACE_PROMISE, false, verifier)) {
                Http2Stream parent = f.connection.local().createStream(3, true).headersSent(false);
                f.pushPromise(continuation, false);
                f.assertReset(2, PROTOCOL_ERROR);
                assertEquals(Http2Stream.State.HALF_CLOSED_LOCAL, parent.state());
                assertTrue(f.channel.isActive());
                assertEquals(emptyList(), f.writer.goaways);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void validPushStillReservesPromisedStream(boolean continuation) throws Exception {
        try (Fixture f = new Fixture(false, Wrapping.NONE, false)) {
            f.connection.local().createStream(3, true).headersSent(false);
            f.pushPromise(continuation, false);
            assertEquals(Http2Stream.State.RESERVED_REMOTE, f.connection.stream(2).state());
            assertEquals(emptyList(), f.writer.resets);
            assertEquals(emptyList(), f.writer.goaways);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void headersCannotOpenAnUnannouncedServerStream(boolean server) throws Exception {
        try (Fixture f = new Fixture(server, Wrapping.NONE, false)) {
            f.headers(2, true, false, false);
            assertEquals(emptyList(), f.writer.resets);
            assertEquals(singletonList(PROTOCOL_ERROR.code()), f.writer.goaways);
            assertFalse(f.channel.isActive());
        }
    }

    @Test
    public void validHeadersAndPriorityKeepTheirNormalBehavior() throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.NONE, false)) {
            ByteBuf priority = Unpooled.buffer();
            Http2CodecUtil.writeFrameHeaderInternal(priority, 5, Http2FrameTypes.PRIORITY, new Http2Flags(), 3);
            priority.writeInt(0).writeByte(15);
            f.channel.writeInbound(priority);
            assertFalse(f.connection.streamMayHaveExisted(3));
            f.headers(3, false, false, false);
            assertNotNull(f.connection.stream(3));
            assertEquals(emptyList(), f.writer.resets);
            assertEquals(emptyList(), f.writer.goaways);
        }
    }

    @Test
    public void pendingResetDoesNotAffectAnotherIdleStream() throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.DEFERRED, false)) {
            f.headers(3, true, false, false);
            ChannelPromise idle = f.ctx.newPromise();
            f.handler.resetStream(f.ctx, 5, CANCEL.code(), idle);
            assertTrue(idle.isSuccess());
            assertEquals(emptyList(), f.writer.resets);
            f.runDeferred();
            f.assertReset(3, PROTOCOL_ERROR);
            f.handler.resetStream(f.ctx, 3, CANCEL.code(), f.ctx.newPromise());
            assertEquals(singletonList("3/1"), f.writer.resets);
        }
    }

    @Test
    public void overlappingResetsKeepTheirOwnCompletionLifetime() throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.DEFERRED, false)) {
            f.headers(3, true, false, false);
            f.headers(3, true, false, false);
            f.deferred.remove(1).run();
            f.channel.flushOutbound();
            f.channel.runPendingTasks();
            f.runDeferred();
            assertEquals(2, f.writer.resets.size());
            assertEquals("3/1", f.writer.resets.get(0));
            assertEquals("3/1", f.writer.resets.get(1));
            f.handler.resetStream(f.ctx, 3, CANCEL.code(), f.ctx.newPromise());
            assertEquals(2, f.writer.resets.size());
        }
    }

    @Test
    public void cancelledResetDoesNotLeaveAnIdleBypass() throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.DEFERRED, false)) {
            f.headers(3, true, false, false);
            assertTrue(f.requests.get(0).cancel(false));
            f.channel.runPendingTasks();
            f.handler.resetStream(f.ctx, 3, CANCEL.code(), f.ctx.newPromise());
            assertEquals(emptyList(), f.writer.resets);
        }
    }

    @Test
    public void throwingEncoderDoesNotLeaveAnIdleBypass() {
        try (Fixture f = new Fixture(true, Wrapping.THROW, false)) {
            assertThrows(Exception.class, () -> f.headers(3, true, false, false));
            assertTrue(f.requests.get(0).isDone());
            f.handler.resetStream(f.ctx, 3, CANCEL.code(), f.ctx.newPromise());
            assertEquals(emptyList(), f.writer.resets);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void errorResetsStillObeyEncoderLimits(boolean rstLimit) throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.REPLACE_PROMISE, rstLimit)) {
            f.writer.hold = !rstLimit;
            f.headers(3, true, false, false);
            f.headers(5, true, false, false);
            assertTrue(f.channel.isActive());
            f.headers(7, true, false, false);
            assertEquals(singletonList(ENHANCE_YOUR_CALM.code()), f.writer.goaways);
            assertFalse(f.channel.isActive());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void failedResetClosesConnection(boolean delayed) throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.REPLACE_PROMISE, false)) {
            f.writer.hold = delayed;
            if (!delayed) {
                f.writer.failure = new IllegalStateException("write failure");
            }
            f.headers(3, true, false, false);
            if (delayed) {
                assertEquals(1, f.writer.pending.size());
                f.writer.pending.get(0).setFailure(new IllegalStateException("delayed write failure"));
                f.channel.runPendingTasks();
                f.channel.flushOutbound();
            }
            assertEquals(singletonList("3/1"), f.writer.resets);
            assertEquals(singletonList(INTERNAL_ERROR.code()), f.writer.goaways);
            assertFalse(f.channel.isActive());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final DefaultHttp2Connection connection;
        final RecordingWriter writer = new RecordingWriter();
        final DefaultHttp2FrameReader reader = new DefaultHttp2FrameReader();
        final List<Runnable> deferred = new ArrayList<Runnable>();
        final List<ChannelPromise> requests = new ArrayList<ChannelPromise>();
        final Http2ConnectionHandler handler;
        final EmbeddedChannel channel;
        final ChannelHandlerContext ctx;

        Fixture(boolean server, Wrapping wrapping, boolean rstLimit) {
            this(server, wrapping, rstLimit, Http2PromisedRequestVerifier.ALWAYS_VERIFY);
        }

        Fixture(boolean server, Wrapping wrapping, boolean rstLimit, Http2PromisedRequestVerifier verifier) {
            connection = new DefaultHttp2Connection(server);
            Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
            if (wrapping != Wrapping.NONE) {
                encoder = new DecoratingHttp2ConnectionEncoder(encoder) {
                    @Override
                    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int id, long error,
                                                        ChannelPromise promise) {
                        requests.add(promise);
                        if (wrapping == Wrapping.THROW) {
                            throw new IllegalStateException("encoder failure");
                        }
                        ChannelPromise child = ctx.newPromise();
                        child.addListener(f -> {
                            if (f.isSuccess()) {
                                promise.trySuccess();
                            } else {
                                promise.tryFailure(f.cause());
                            }
                        });
                        if (wrapping == Wrapping.DEFERRED) {
                            deferred.add(() -> super.writeRstStream(ctx, id, error, child));
                        } else {
                            super.writeRstStream(ctx, id, error, child);
                        }
                        return promise;
                    }
                };
            }
            if (rstLimit) {
                encoder = new Http2MaxRstFrameLimitEncoder(encoder, 2, 60);
            }
            encoder = new Http2ControlFrameLimitEncoder(encoder, 2);
            Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader, verifier);
            handler = new Http2ConnectionHandlerBuilder().codec(decoder, encoder)
                    .frameListener(new Http2FrameAdapter()).build();
            channel = new EmbeddedChannel(handler);
            ctx = channel.pipeline().context(handler);
            ByteBuf preface = Unpooled.buffer();
            if (server) {
                ByteBuf magic = Http2CodecUtil.connectionPrefaceBuf();
                preface.writeBytes(magic);
                magic.release();
            }
            Http2CodecUtil.writeFrameHeaderInternal(preface, 0, Http2FrameTypes.SETTINGS, new Http2Flags(), 0);
            channel.writeInbound(preface);
            drain();
        }

        void headers(int id, boolean invalid, boolean continuation, boolean priority) throws Exception {
            headerBlock(id, Http2FrameTypes.HEADERS, invalid, continuation, priority);
        }

        void pushPromise(boolean continuation, boolean invalid) throws Exception {
            headerBlock(3, Http2FrameTypes.PUSH_PROMISE, invalid, continuation, false);
        }

        private void headerBlock(int id, byte type, boolean invalid, boolean continuation, boolean priority)
                throws Exception {
            Http2Headers headers = new DefaultHttp2Headers(false).method("GET")
                    .scheme("https").authority("example.org").path("/");
            if (invalid) {
                headers.add("UPPERCASE", "invalid");
            }
            ByteBuf block = Unpooled.buffer();
            ByteBuf frames = Unpooled.buffer();
            try {
                new DefaultHttp2HeadersEncoder().encodeHeaders(id, headers, block);
                int first = continuation ? block.readableBytes() / 2 : block.readableBytes();
                int extra = type == Http2FrameTypes.PUSH_PROMISE ? 4 : priority ? 5 : 0;
                Http2Flags flags = new Http2Flags().endOfHeaders(!continuation);
                if (type == Http2FrameTypes.HEADERS) {
                    flags.endOfStream(true).priorityPresent(priority);
                }
                Http2CodecUtil.writeFrameHeaderInternal(frames, first + extra, type, flags, id);
                if (type == Http2FrameTypes.PUSH_PROMISE) {
                    frames.writeInt(2);
                } else if (priority) {
                    frames.writeInt(0).writeByte(15);
                }
                frames.writeBytes(block, first);
                if (continuation) {
                    Http2CodecUtil.writeFrameHeaderInternal(frames, block.readableBytes(),
                            Http2FrameTypes.CONTINUATION, new Http2Flags().endOfHeaders(true), id);
                    frames.writeBytes(block);
                }
            } finally {
                block.release();
            }
            channel.writeInbound(frames);
        }

        void runDeferred() {
            while (!deferred.isEmpty()) {
                deferred.remove(0).run();
            }
            channel.flushOutbound();
            channel.runPendingTasks();
        }

        void assertReset(int id, Http2Error error) {
            assertEquals(singletonList(id + "/" + error.code()), wireResets());
            assertEquals(singletonList(id + "/" + error.code()), writer.resets);
        }

        List<String> wireResets() {
            channel.flushOutbound();
            ByteBuf all = Unpooled.buffer();
            List<String> resets = new ArrayList<String>();
            try {
                Object message;
                while ((message = channel.readOutbound()) != null) {
                    try {
                        all.writeBytes((ByteBuf) message);
                    } finally {
                        ReferenceCountUtil.release(message);
                    }
                }
                while (all.isReadable()) {
                    int size = all.readUnsignedMedium();
                    int type = all.readUnsignedByte();
                    all.skipBytes(1);
                    int id = all.readInt() & 0x7fffffff;
                    if (type == Http2FrameTypes.RST_STREAM) {
                        assertEquals(4, size);
                        resets.add(id + "/" + all.readUnsignedInt());
                    } else {
                        all.skipBytes(size);
                    }
                }
                return resets;
            } finally {
                all.release();
            }
        }

        void drain() {
            Object message;
            while ((message = channel.readOutbound()) != null) {
                ReferenceCountUtil.release(message);
            }
        }

        @Override
        public void close() {
            for (ChannelPromise promise : writer.pending) {
                promise.trySuccess();
            }
            for (ChannelPromise promise : requests) {
                promise.cancel(false);
            }
            deferred.clear();
            channel.finishAndReleaseAll();
        }
    }

    private static final class RecordingWriter extends DefaultHttp2FrameWriter {
        final List<String> resets = new ArrayList<String>();
        final List<Long> goaways = new ArrayList<Long>();
        final List<ChannelPromise> pending = new ArrayList<ChannelPromise>();
        boolean hold;
        Throwable failure;

        @Override
        public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int id, long error, ChannelPromise promise) {
            resets.add(id + "/" + error);
            if (failure != null) {
                return promise.setFailure(failure);
            }
            if (hold) {
                pending.add(promise);
                return promise;
            }
            return super.writeRstStream(ctx, id, error, promise);
        }

        @Override
        public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int id, long error,
                                        ByteBuf debug, ChannelPromise promise) {
            goaways.add(error);
            return super.writeGoAway(ctx, id, error, debug, promise);
        }
    }
}
