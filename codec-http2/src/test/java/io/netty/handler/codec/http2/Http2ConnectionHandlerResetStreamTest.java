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
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.REFUSED_STREAM;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Http2ConnectionHandlerResetStreamTest {
    enum Wrapping { NONE, REPLACE_PROMISE, DEFERRED }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void localIdleResetDoesNotWriteForEitherEndpoint(boolean server) {
        try (Fixture f = new Fixture(server, Wrapping.NONE, false)) {
            for (int id : new int[] {server ? 2 : 1, server ? Integer.MAX_VALUE - 1 : Integer.MAX_VALUE}) {
                ChannelPromise promise = f.ctx.newPromise();
                f.handler.resetStream(f.ctx, id, CANCEL.code(), promise);
                f.channel.flushOutbound();
                assertThat(promise.isSuccess()).isTrue();
                ChannelFuture future = f.handler.resetStream(f.ctx, id, CANCEL.code(), f.ctx.voidPromise());
                future.addListener(done -> { });
                f.channel.flushOutbound();
                assertThat(future.isSuccess()).isTrue();
            }
            assertThat(f.writer.resets).isEmpty();
            assertThat(f.wireResets()).isEmpty();
            assertThat(f.channel.isActive()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void removedLocalStreamStillResets(boolean server) throws Exception {
        try (Fixture f = new Fixture(server, Wrapping.NONE, false)) {
            int id = server ? 2 : 3;
            f.connection.local().createStream(id, false).close();
            ChannelFuture future = f.handler.resetStream(f.ctx, id, CANCEL.code(), f.ctx.newPromise());
            f.assertReset(id, CANCEL);
            assertThat(future.isSuccess()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void unknownRemoteStreamStillResets(boolean server) {
        try (Fixture f = new Fixture(server, Wrapping.NONE, false)) {
            int id = server ? 3 : 2;
            ChannelFuture future = f.handler.resetStream(f.ctx, id, CANCEL.code(), f.ctx.voidPromise());
            future.addListener(done -> { });
            f.assertReset(id, CANCEL);
            assertThat(future.isSuccess()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, Integer.MIN_VALUE })
    public void invalidStreamIdStillFails(int id) {
        try (Fixture f = new Fixture(true, Wrapping.NONE, false)) {
            ChannelFuture future = f.handler.resetStream(f.ctx, id, CANCEL.code(), f.ctx.newPromise());
            assertThat(future.isDone()).isTrue();
            assertThat(future.isSuccess()).isFalse();
            assertThat(future.cause()).isInstanceOf(IllegalArgumentException.class);
            assertThat(f.writer.resets).isEqualTo(singletonList(id + "/" + CANCEL.code()));
            assertThat(f.wireResets()).isEmpty();
            assertThat(f.channel.isActive()).isFalse();
        }
    }

    @Test
    public void connectionStreamStillRejectsReset() {
        try (Fixture f = new Fixture(true, Wrapping.NONE, false)) {
            assertThatThrownBy(() -> f.handler.resetStream(f.ctx, 0, CANCEL.code(), f.ctx.newPromise()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(f.writer.resets).isEmpty();
            assertThat(f.wireResets()).isEmpty();
            assertThat(f.channel.isActive()).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(value = Wrapping.class, names = { "NONE", "REPLACE_PROMISE", "DEFERRED" })
    public void invalidInitialHeadersResetThroughEncoder(Wrapping wrapping) throws Exception {
        try (Fixture f = new Fixture(true, wrapping, false)) {
            f.headers(3, true, false, false);
            f.runDeferred();
            f.assertReset(3, PROTOCOL_ERROR);
            assertThat(f.channel.isActive()).isTrue();
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
                assertThat(parent.state()).isEqualTo(Http2Stream.State.HALF_CLOSED_LOCAL);
                assertThat(f.channel.isActive()).isTrue();
                assertThat(f.writer.goaways).isEmpty();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void errorResetsStillObeyEncoderLimits(boolean rstLimit) throws Exception {
        try (Fixture f = new Fixture(true, Wrapping.REPLACE_PROMISE, rstLimit)) {
            f.writer.hold = !rstLimit;
            f.headers(3, true, false, false);
            f.headers(5, true, false, false);
            assertThat(f.channel.isActive()).isTrue();
            f.headers(7, true, false, false);
            assertThat(f.writer.goaways).isEqualTo(singletonList(ENHANCE_YOUR_CALM.code()));
            assertThat(f.channel.isActive()).isFalse();
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
                assertThat(f.writer.pending.size()).isEqualTo(1);
                f.writer.pending.get(0).setFailure(new IllegalStateException("delayed write failure"));
                f.channel.runPendingTasks();
                f.channel.flushOutbound();
            }
            assertThat(f.writer.resets).isEqualTo(singletonList("3/1"));
            assertThat(f.writer.goaways).isEqualTo(singletonList(INTERNAL_ERROR.code()));
            assertThat(f.channel.isActive()).isFalse();
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
            assertThat(wireResets()).isEqualTo(singletonList(id + "/" + error.code()));
            assertThat(writer.resets).isEqualTo(singletonList(id + "/" + error.code()));
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
                        assertThat(size).isEqualTo(4);
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
