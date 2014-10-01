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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Utilities for the integration tests.
 */
final class Http2TestUtil {
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
     * Converts a {@link String} into an {@link AsciiString}.
     */
    public static AsciiString as(String value) {
        return new AsciiString(value);
    }

    /**
     * Converts a byte array into an {@link AsciiString}.
     */
    public static AsciiString as(byte[] value) {
        return new AsciiString(value);
    }

    /**
     * Returns a byte array filled with random data.
     */
    public static byte[] randomBytes() {
        byte[] data = new byte[100];
        new Random().nextBytes(data);
        return data;
    }

    /**
     * Returns an {@link AsciiString} that wraps a randomly-filled byte array.
     */
    public static AsciiString randomString() {
        return as(randomBytes());
    }

    private Http2TestUtil() {
    }

    static class FrameAdapter extends ByteToMessageDecoder {
        private final Http2Connection connection;
        private final Http2FrameListener listener;
        private final DefaultHttp2FrameReader reader;
        private volatile CountDownLatch latch;

        FrameAdapter(Http2FrameListener listener, CountDownLatch latch) {
            this(null, listener, latch);
        }

        FrameAdapter(Http2Connection connection, Http2FrameListener listener, CountDownLatch latch) {
            this(connection, new DefaultHttp2FrameReader(), listener, latch);
        }

        FrameAdapter(Http2Connection connection, DefaultHttp2FrameReader reader, Http2FrameListener listener,
                CountDownLatch latch) {
            this.connection = connection;
            this.listener = listener;
            this.reader = reader;
            latch(latch);
        }

        public void latch(CountDownLatch latch) {
            this.latch = latch;
        }

        public Http2Stream getOrCreateStream(int streamId, boolean halfClosed) throws Http2Exception {
            return getOrCreateStream(connection, streamId, halfClosed);
        }

        public static Http2Stream getOrCreateStream(Http2Connection connection, int streamId, boolean halfClosed)
                throws Http2Exception {
            if (connection != null) {
                Http2Stream stream = connection.stream(streamId);
                if (stream == null) {
                    if ((connection.isServer() && streamId % 2 == 0) || (!connection.isServer() && streamId % 2 != 0)) {
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
                public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                        boolean endOfStream) throws Http2Exception {
                    Http2Stream stream = getOrCreateStream(streamId, endOfStream);
                    listener.onDataRead(ctx, streamId, data, padding, endOfStream);
                    if (endOfStream) {
                        closeStream(stream, true);
                    }
                    latch.countDown();
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
                    if (stream != null) {
                        stream.setPriority(streamDependency, weight, exclusive);
                    }
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
                    Http2Stream stream = getOrCreateStream(streamId, false);
                    if (stream != null) {
                        stream.setPriority(streamDependency, weight, exclusive);
                    }
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
                public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
                    listener.onPingRead(ctx, data);
                    latch.countDown();
                }

                @Override
                public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
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
                        ByteBuf payload) {
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
        private volatile CountDownLatch messageLatch;
        private volatile CountDownLatch dataLatch;

        public FrameCountDown(Http2FrameListener listener, CountDownLatch messageLatch) {
            this(listener, messageLatch, null);
        }

        public FrameCountDown(Http2FrameListener listener, CountDownLatch messageLatch, CountDownLatch dataLatch) {
            this.listener = listener;
            this.messageLatch = messageLatch;
            this.dataLatch = dataLatch;
        }

        public void messageLatch(CountDownLatch latch) {
            messageLatch = latch;
        }

        public void dataLatch(CountDownLatch latch) {
            dataLatch = latch;
        }

        @Override
        public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
                throws Http2Exception {
            int numBytes = data.readableBytes();
            listener.onDataRead(ctx, streamId, data, padding, endOfStream);
            messageLatch.countDown();
            if (dataLatch != null) {
                for (int i = 0; i < numBytes; ++i) {
                    dataLatch.countDown();
                }
            }
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                boolean endStream) throws Http2Exception {
            listener.onHeadersRead(ctx, streamId, headers, padding, endStream);
            messageLatch.countDown();
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
            listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
            messageLatch.countDown();
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
            messageLatch.countDown();
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            listener.onSettingsRead(ctx, settings);
            messageLatch.countDown();
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            listener.onPingRead(ctx, data);
            messageLatch.countDown();
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
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
            messageLatch.countDown();
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                throws Http2Exception {
            listener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
            messageLatch.countDown();
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                ByteBuf payload) {
            listener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
            messageLatch.countDown();
        }
    }
}
