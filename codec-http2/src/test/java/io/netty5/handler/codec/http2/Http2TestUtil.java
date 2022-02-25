/*
 * Copyright 2014 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufUtil;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.AsciiString;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.mockito.Mockito;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.netty5.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static io.netty5.util.ReferenceCountUtil.release;
import static java.lang.Math.min;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Utilities for the integration tests.
 */
@SuppressWarnings("unchecked")
public final class Http2TestUtil {
    /**
     * Interface that allows for running a operation that throws a {@link Http2Exception}.
     */
    interface Http2Runnable {
        void run() throws Http2Exception;
    }

    /**
     * Runs the given operation within the event loop thread of the given {@link Channel}.
     */
    static void runInChannel(Channel channel, final Http2Runnable runnable) {
        channel.executor().execute(() -> {
            try {
                runnable.run();
            } catch (Http2Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Returns a byte array filled with random data.
     */
    public static byte[] randomBytes() {
        return randomBytes(100);
    }

    /**
     * Returns a byte array filled with random data.
     */
    public static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new Random().nextBytes(data);
        return data;
    }

    /**
     * Returns an {@link AsciiString} that wraps a randomly-filled byte array.
     */
    public static AsciiString randomString() {
        return new AsciiString(randomBytes());
    }

    public static CharSequence of(String s) {
        return s;
    }

    public static HpackEncoder newTestEncoder() {
        try {
            return newTestEncoder(true, MAX_HEADER_LIST_SIZE, MAX_HEADER_TABLE_SIZE);
        } catch (Http2Exception e) {
            throw new Error("max size not allowed?", e);
        }
    }

    public static HpackEncoder newTestEncoder(boolean ignoreMaxHeaderListSize,
                                              long maxHeaderListSize, long maxHeaderTableSize) throws Http2Exception {
        HpackEncoder hpackEncoder = new HpackEncoder(false, 16, 0);
        ByteBuf buf = Unpooled.buffer();
        try {
            hpackEncoder.setMaxHeaderTableSize(buf, maxHeaderTableSize);
            hpackEncoder.setMaxHeaderListSize(maxHeaderListSize);
        } finally  {
            buf.release();
        }
        return hpackEncoder;
    }

    public static HpackDecoder newTestDecoder() {
        try {
            return newTestDecoder(MAX_HEADER_LIST_SIZE, MAX_HEADER_TABLE_SIZE);
        } catch (Http2Exception e) {
            throw new Error("max size not allowed?", e);
        }
    }

    public static HpackDecoder newTestDecoder(long maxHeaderListSize, long maxHeaderTableSize) throws Http2Exception {
        HpackDecoder hpackDecoder = new HpackDecoder(maxHeaderListSize);
        hpackDecoder.setMaxHeaderTableSize(maxHeaderTableSize);
        return hpackDecoder;
    }

    private Http2TestUtil() {
    }

    /**
     * A decorator around a {@link Http2FrameListener} that counts down the latch so that we can await the completion of
     * the request.
     */
    static class FrameCountDown implements Http2FrameListener {
        private final Http2FrameListener listener;
        private final CountDownLatch messageLatch;
        private final CountDownLatch settingsAckLatch;
        private final CountDownLatch dataLatch;
        private final CountDownLatch trailersLatch;
        private final CountDownLatch goAwayLatch;

        FrameCountDown(Http2FrameListener listener, CountDownLatch settingsAckLatch, CountDownLatch messageLatch,
                CountDownLatch dataLatch, CountDownLatch trailersLatch) {
            this(listener, settingsAckLatch, messageLatch, dataLatch, trailersLatch, messageLatch);
        }

        FrameCountDown(Http2FrameListener listener, CountDownLatch settingsAckLatch, CountDownLatch messageLatch,
                CountDownLatch dataLatch, CountDownLatch trailersLatch, CountDownLatch goAwayLatch) {
            this.listener = listener;
            this.messageLatch = messageLatch;
            this.settingsAckLatch = settingsAckLatch;
            this.dataLatch = dataLatch;
            this.trailersLatch = trailersLatch;
            this.goAwayLatch = goAwayLatch;
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
                throws Http2Exception {
            int numBytes = data.readableBytes();
            int processed = listener.onDataRead(ctx, streamId, data, padding, endOfStream);
            messageLatch.countDown();
            if (dataLatch != null) {
                for (int i = 0; i < numBytes; ++i) {
                    dataLatch.countDown();
                }
            }
            return processed;
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                boolean endStream) throws Http2Exception {
            listener.onHeadersRead(ctx, streamId, headers, padding, endStream);
            messageLatch.countDown();
            if (trailersLatch != null && endStream) {
                trailersLatch.countDown();
            }
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
            listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
            messageLatch.countDown();
            if (trailersLatch != null && endStream) {
                trailersLatch.countDown();
            }
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                boolean exclusive) throws Http2Exception {
            listener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
            messageLatch.countDown();
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            listener.onRstStreamRead(ctx, streamId, errorCode);
            messageLatch.countDown();
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            listener.onSettingsAckRead(ctx);
            settingsAckLatch.countDown();
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            listener.onSettingsRead(ctx, settings);
            messageLatch.countDown();
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
            listener.onPingRead(ctx, data);
            messageLatch.countDown();
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
            listener.onPingAckRead(ctx, data);
            messageLatch.countDown();
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                Http2Headers headers, int padding) throws Http2Exception {
            listener.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
            messageLatch.countDown();
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                throws Http2Exception {
            listener.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
            goAwayLatch.countDown();
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                throws Http2Exception {
            listener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
            messageLatch.countDown();
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                ByteBuf payload) throws Http2Exception {
            listener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
            messageLatch.countDown();
        }
    }

    static final class TestStreamByteDistributorStreamState implements StreamByteDistributor.StreamState {
        private final Http2Stream stream;
        boolean isWriteAllowed;
        long pendingBytes;
        boolean hasFrame;

        TestStreamByteDistributorStreamState(Http2Stream stream, long pendingBytes, boolean hasFrame,
                                             boolean isWriteAllowed) {
            this.stream = stream;
            this.isWriteAllowed = isWriteAllowed;
            this.pendingBytes = pendingBytes;
            this.hasFrame = hasFrame;
        }

        @Override
        public Http2Stream stream() {
            return stream;
        }

        @Override
        public long pendingBytes() {
            return pendingBytes;
        }

        @Override
        public boolean hasFrame() {
            return hasFrame;
        }

        @Override
        public int windowSize() {
            return isWriteAllowed ? (int) min(pendingBytes, Integer.MAX_VALUE) : -1;
        }
    }

    static Http2FrameWriter mockedFrameWriter() {
        Http2FrameWriter.Configuration configuration = new Http2FrameWriter.Configuration() {
            private final Http2HeadersEncoder.Configuration headerConfiguration =
                    new Http2HeadersEncoder.Configuration() {
                @Override
                public void maxHeaderTableSize(long max)  {
                    // NOOP
                }

                @Override
                public long maxHeaderTableSize() {
                    return 0;
                }

                @Override
                public void maxHeaderListSize(long max) {
                    // NOOP
                }

                @Override
                public long maxHeaderListSize() {
                    return 0;
                }
            };

            private final Http2FrameSizePolicy policy = new Http2FrameSizePolicy() {
                @Override
                public void maxFrameSize(int max) {
                    // NOOP
                }

                @Override
                public int maxFrameSize() {
                    return 0;
                }
            };
            @Override
            public Http2HeadersEncoder.Configuration headersConfiguration() {
                return headerConfiguration;
            }

            @Override
            public Http2FrameSizePolicy frameSizePolicy() {
                return policy;
            }
        };

        final ConcurrentLinkedQueue<ByteBuf> buffers = new ConcurrentLinkedQueue<>();

        Http2FrameWriter frameWriter = Mockito.mock(Http2FrameWriter.class);
        doAnswer(invocationOnMock -> {
            for (;;) {
                ByteBuf buf = buffers.poll();
                if (buf == null) {
                    break;
                }
                buf.release();
            }
            return null;
        }).when(frameWriter).close();

        when(frameWriter.configuration()).thenReturn(configuration);
        when(frameWriter.writeSettings(any(ChannelHandlerContext.class), any(Http2Settings.class)))
                .thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));

        when(frameWriter.writeSettingsAck(any(ChannelHandlerContext.class)))
                .thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));

        when(frameWriter.writePing(any(ChannelHandlerContext.class), anyBoolean(), anyLong()))
                .thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));
        when(frameWriter.writeGoAway(any(ChannelHandlerContext.class), anyInt(),
                anyLong(), any(ByteBuf.class))).thenAnswer(invocationOnMock -> {
                    buffers.offer((ByteBuf) invocationOnMock.getArgument(3));
                    return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
                });
        when(frameWriter.writeHeaders(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class), anyInt(),
                anyBoolean())).thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));

        when(frameWriter.writeHeaders(any(ChannelHandlerContext.class), anyInt(),
                any(Http2Headers.class), anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean()))
                .thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));

        when(frameWriter.writeData(any(ChannelHandlerContext.class), anyInt(), any(ByteBuf.class), anyInt(),
                anyBoolean())).thenAnswer(invocationOnMock -> {
                    buffers.offer((ByteBuf) invocationOnMock.getArgument(2));
                    return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
                });

        when(frameWriter.writeRstStream(any(ChannelHandlerContext.class), anyInt(),
                anyLong())).thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));

        when(frameWriter.writeWindowUpdate(any(ChannelHandlerContext.class), anyInt(), anyInt()))
                .thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));

        when(frameWriter.writePushPromise(any(ChannelHandlerContext.class), anyInt(), anyInt(), any(Http2Headers.class),
                anyInt())).thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));

        when(frameWriter.writeFrame(any(ChannelHandlerContext.class), anyByte(), anyInt(), any(Http2Flags.class),
                any(ByteBuf.class))).thenAnswer(invocationOnMock -> {
                    buffers.offer((ByteBuf) invocationOnMock.getArgument(4));
                    return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
                });
        return frameWriter;
    }

    static Promise<Void> anyChannelPromise() {
        return any(Promise.class);
    }

    static Http2Settings anyHttp2Settings() {
        return any(Http2Settings.class);
    }

    static ByteBuf bb(String s) {
        return ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, s);
    }

    static ByteBuf bb(int size) {
        return UnpooledByteBufAllocator.DEFAULT.buffer().writeZero(size);
    }

    static void assertEqualsAndRelease(Http2Frame expected, Http2Frame actual) {
        try {
            assertEquals(expected, actual);
        } finally {
            release(expected);
            release(actual);
            // Will return -1 when not implements ReferenceCounted.
            assertTrue(ReferenceCountUtil.refCnt(expected) <= 0);
            assertTrue(ReferenceCountUtil.refCnt(actual) <= 0);
        }
    }

}
