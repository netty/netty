/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import junit.framework.AssertionFailedError;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static java.lang.Math.min;

/**
 * Utilities for the integration tests.
 */
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
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Http2Exception e) {
                    throw new RuntimeException(e);
                }
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
        HpackEncoder hpackEncoder = new HpackEncoder();
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
        HpackDecoder hpackDecoder = new HpackDecoder(maxHeaderListSize, 32);
        hpackDecoder.setMaxHeaderTableSize(maxHeaderTableSize);
        return hpackDecoder;
    }

    private Http2TestUtil() {
    }

    static class FrameAdapter extends ByteToMessageDecoder {
        private final Http2Connection connection;
        private final Http2FrameListener listener;
        private final DefaultHttp2FrameReader reader;
        private final CountDownLatch latch;

        FrameAdapter(Http2FrameListener listener, CountDownLatch latch) {
            this(null, listener, latch);
        }

        FrameAdapter(Http2Connection connection, Http2FrameListener listener, CountDownLatch latch) {
            this(connection, new DefaultHttp2FrameReader(false), listener, latch);
        }

        FrameAdapter(Http2Connection connection, DefaultHttp2FrameReader reader, Http2FrameListener listener,
                CountDownLatch latch) {
            this.connection = connection;
            this.listener = listener;
            this.reader = reader;
            this.latch = latch;
        }

        private Http2Stream getOrCreateStream(int streamId, boolean halfClosed) throws Http2Exception {
            return getOrCreateStream(connection, streamId, halfClosed);
        }

        public static Http2Stream getOrCreateStream(Http2Connection connection, int streamId, boolean halfClosed)
                throws Http2Exception {
            if (connection != null) {
                Http2Stream stream = connection.stream(streamId);
                if (stream == null) {
                    if (connection.isServer() && streamId % 2 == 0 || !connection.isServer() && streamId % 2 != 0) {
                        stream = connection.local().createStream(streamId, halfClosed);
                    } else {
                        stream = connection.remote().createStream(streamId, halfClosed);
                    }
                }
                return stream;
            }
            return null;
        }

        private void closeStream(Http2Stream stream) {
            closeStream(stream, false);
        }

        protected void closeStream(Http2Stream stream, boolean dataRead) {
            if (stream != null) {
                stream.close();
            }
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            reader.readFrame(ctx, in, new Http2FrameListener() {
                @Override
                public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                        boolean endOfStream) throws Http2Exception {
                    Http2Stream stream = getOrCreateStream(streamId, endOfStream);
                    int processed = listener.onDataRead(ctx, streamId, data, padding, endOfStream);
                    if (endOfStream) {
                        closeStream(stream, true);
                    }
                    latch.countDown();
                    return processed;
                }

                @Override
                public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                        boolean endStream) throws Http2Exception {
                    Http2Stream stream = getOrCreateStream(streamId, endStream);
                    listener.onHeadersRead(ctx, streamId, headers, padding, endStream);
                    if (endStream) {
                        closeStream(stream);
                    }
                    latch.countDown();
                }

                @Override
                public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                        int streamDependency, short weight, boolean exclusive, int padding, boolean endStream)
                        throws Http2Exception {
                    Http2Stream stream = getOrCreateStream(streamId, endStream);
                    listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding,
                            endStream);
                    if (endStream) {
                        closeStream(stream);
                    }
                    latch.countDown();
                }

                @Override
                public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                        boolean exclusive) throws Http2Exception {
                    listener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
                    latch.countDown();
                }

                @Override
                public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                        throws Http2Exception {
                    Http2Stream stream = getOrCreateStream(streamId, false);
                    listener.onRstStreamRead(ctx, streamId, errorCode);
                    closeStream(stream);
                    latch.countDown();
                }

                @Override
                public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
                    listener.onSettingsAckRead(ctx);
                    latch.countDown();
                }

                @Override
                public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
                    listener.onSettingsRead(ctx, settings);
                    latch.countDown();
                }

                @Override
                public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
                    listener.onPingRead(ctx, data);
                    latch.countDown();
                }

                @Override
                public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
                    listener.onPingAckRead(ctx, data);
                    latch.countDown();
                }

                @Override
                public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                        Http2Headers headers, int padding) throws Http2Exception {
                    getOrCreateStream(promisedStreamId, false);
                    listener.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
                    latch.countDown();
                }

                @Override
                public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                        throws Http2Exception {
                    listener.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
                    latch.countDown();
                }

                @Override
                public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                        throws Http2Exception {
                    getOrCreateStream(streamId, false);
                    listener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
                    latch.countDown();
                }

                @Override
                public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                        ByteBuf payload) throws Http2Exception {
                    listener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
                    latch.countDown();
                }
            });
        }
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

        FrameCountDown(Http2FrameListener listener, CountDownLatch settingsAckLatch, CountDownLatch messageLatch) {
            this(listener, settingsAckLatch, messageLatch, null, null);
        }

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

    static ChannelPromise newVoidPromise(final Channel channel) {
        return new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE) {
            @Override
            public ChannelPromise addListener(
                    GenericFutureListener<? extends Future<? super Void>> listener) {
                throw new AssertionFailedError();
            }

            @Override
            public ChannelPromise addListeners(
                    GenericFutureListener<? extends Future<? super Void>>... listeners) {
                throw new AssertionFailedError();
            }

            @Override
            public boolean isVoid() {
                return true;
            }

            @Override
            public boolean tryFailure(Throwable cause) {
                channel().pipeline().fireExceptionCaught(cause);
                return true;
            }

            @Override
            public ChannelPromise setFailure(Throwable cause) {
                tryFailure(cause);
                return this;
            }

            @Override
            public ChannelPromise unvoid() {
                ChannelPromise promise =
                        new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
                promise.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            channel().pipeline().fireExceptionCaught(future.cause());
                        }
                    }
                });
                return promise;
            }
        };
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
}
