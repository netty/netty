/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.BackpressureGauge;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.Decompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static io.netty.handler.codec.compression.Decompressor.Status.COMPLETE;
import static io.netty.handler.codec.compression.Decompressor.Status.NEED_INPUT;
import static io.netty.handler.codec.compression.Decompressor.Status.NEED_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompressingFrameListenerTest {
    private static final String EOF = "eof";
    private static final int STREAM_ID = 2;

    private ChannelHandlerContext ctx;
    private Mock mock;
    private DefaultHttp2Connection connection;

    @BeforeEach
    void setUp() throws Http2Exception {
        mock = new Mock();
        connection = new DefaultHttp2Connection(true);
        connection.local().flowController(mock);
        connection.local().createStream(STREAM_ID, false);
        ctx = new EmbeddedChannel().pipeline().addLast(new ChannelInboundHandlerAdapter()).firstContext();
    }

    private static Http2Headers requestUncompressed() {
        return new DefaultHttp2Headers();
    }

    private static Http2Headers request() {
        return requestUncompressed()
                .add(HttpHeaderNames.CONTENT_ENCODING, "compressed");
    }

    private static ByteBuf numberedBuffer(int index) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(4);
        buf.writeInt(index);
        return buf;
    }

    private int onDataRead(Http2FrameListener listener, int value, boolean endOfStream) throws Http2Exception {
        ByteBuf buf = numberedBuffer(value);
        try {
            return listener.onDataRead(ctx, STREAM_ID, buf, 0, endOfStream);
        } finally {
            buf.release();
        }
    }

    @Test
    public void simpleBackpressure() throws Http2Exception {
        Http2FrameListener decompressingListener = new MockDecompressor.Builder()
                .needInput()
                .needOutput(3)
                .needInput()
                .complete()

                .listenerBuilder()
                .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                .build(connection, mock);

        decompressingListener.onHeadersRead(ctx, STREAM_ID, request(), 0, false);
        assertEquals(requestUncompressed(), mock.events.poll());
        assertNull(mock.events.poll());

        mock.consumedBytes.add(0);
        mock.consumedBytes.add(0);
        assertEquals(4, onDataRead(decompressingListener, 0, false)); // consumed
        assertEquals(0, onDataRead(decompressingListener, 4, false)); // not consumed
        assertEquals(1, mock.events.poll());
        assertEquals(2, mock.events.poll());
        assertNull(mock.events.poll());

        mock.consumedBytes.add(0);
        mock.sentWindowUpdate.add(true);
        assertTrue(connection.local().flowController().consumeBytes(connection.stream(STREAM_ID), 8));
        assertEquals(3, mock.events.poll());
        assertEquals(EOF, mock.events.poll());
        assertEquals("consumeBytes 4", mock.events.poll()); // second input is consumed now
        assertNull(mock.events.poll());
    }

    @Test
    public void immediateConsumption() throws Http2Exception {
        Http2FrameListener decompressingListener = new MockDecompressor.Builder()
                .needInput()
                .needOutput(3)
                .complete()

                .listenerBuilder()
                .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                .build(connection, mock);

        decompressingListener.onHeadersRead(ctx, STREAM_ID, request(), 0, false);
        assertEquals(requestUncompressed(), mock.events.poll());
        assertNull(mock.events.poll());

        mock.consumedBytes.add(4);
        mock.consumedBytes.add(4);
        mock.consumedBytes.add(4);
        assertEquals(4, onDataRead(decompressingListener, 0, false));
        assertEquals(1, mock.events.poll());
        assertEquals(2, mock.events.poll());
        assertEquals(3, mock.events.poll());
        assertEquals(EOF, mock.events.poll());
        assertNull(mock.events.poll());
    }

    @Test
    public void endOfInput() throws Http2Exception {
        Http2FrameListener decompressingListener = new MockDecompressor.Builder()
                .needInput()
                .needOutput(1)
                .needInput()
                .needOutput(1)
                .complete()

                .listenerBuilder()
                .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                .build(connection, mock);

        decompressingListener.onHeadersRead(ctx, STREAM_ID, request(), 0, false);
        assertEquals(requestUncompressed(), mock.events.poll());
        assertNull(mock.events.poll());

        mock.consumedBytes.add(4);
        mock.consumedBytes.add(4);
        assertEquals(4, onDataRead(decompressingListener, 0, true));
        assertEquals(1, mock.events.poll());
        assertEquals(3, mock.events.poll());
        assertEquals(EOF, mock.events.poll());
        assertNull(mock.events.poll());
    }

    private static final class MockDecompressionException extends DecompressionException {
    }

    private static final class MockDecompressor implements Decompressor {
        private final List<Status> events;
        private final int failIndex;
        private int index;

        MockDecompressor(List<Status> events, int failIndex) {
            this.events = events;
            this.failIndex = failIndex;
        }

        @Override
        public Status status() throws DecompressionException {
            return events.get(index);
        }

        @Override
        public void addInput(ByteBuf buf) throws DecompressionException {
            assertEquals(NEED_INPUT, status());
            assertEquals(index, buf.readInt());
            buf.release();
            if (index == failIndex) {
                throw new MockDecompressionException();
            }
            index++;
        }

        @Override
        public void endOfInput() throws DecompressionException {
            assertEquals(NEED_INPUT, status());
            if (index == failIndex) {
                throw new MockDecompressionException();
            }
            index++;
        }

        @Override
        public ByteBuf takeOutput() throws DecompressionException {
            assertEquals(NEED_OUTPUT, status());
            if (index == failIndex) {
                throw new MockDecompressionException();
            }
            return numberedBuffer(index++);
        }

        @Override
        public void close() throws DecompressionException {
            if (failIndex != index) {
                assertEquals(COMPLETE, status());
            }
        }

        static final class Builder extends AbstractDecompressorBuilder {
            private final List<Status> events = new ArrayList<>();
            private int failIndex = -1;

            MockDecompressor.Builder needInput() {
                events.add(NEED_INPUT);
                return this;
            }

            MockDecompressor.Builder needOutput(int count) {
                for (int i = 0; i < count; i++) {
                    events.add(NEED_OUTPUT);
                }
                return this;
            }

            MockDecompressor.Builder complete() {
                events.add(COMPLETE);
                return this;
            }

            /**
             * Fail the previous operation.
             */
            MockDecompressor.Builder fail() {
                failIndex = events.size() - 1;
                return this;
            }

            @Override
            public Decompressor build(ByteBufAllocator allocator) throws DecompressionException {
                return new MockDecompressor(events, failIndex);
            }

            DecompressingFrameListener.Builder listenerBuilder() {
                return DecompressingFrameListener.builder()
                        .decompressionDecider(
                                contentEncoding -> "compressed".contentEquals(contentEncoding) ? this : null);
            }
        }
    }

    private static class Mock implements Http2FrameListener, Http2LocalFlowController {
        final Queue<Object> events = new ArrayDeque<>();
        final Queue<Integer> consumedBytes = new ArrayDeque<>();
        final Queue<Boolean> sentWindowUpdate = new ArrayDeque<>();

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
            boolean readable = data.isReadable();
            if (readable) {
                events.add(data.readInt());
            }
            if (endOfStream) {
                events.add(EOF);
            }
            return readable ? consumedBytes.remove() : 0;
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                                  boolean endOfStream) {
            events.add(headers);
            if (endOfStream) {
                events.add(EOF);
            }
        }

        @Override
        public boolean consumeBytes(Http2Stream stream, int numBytes) {
            events.add("consumeBytes " + numBytes);
            return sentWindowUpdate.remove();
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                                  short weight, boolean exclusive, int padding, boolean endOfStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                                   boolean exclusive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, long data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                      Http2Headers headers, int padding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                                   ByteBuf payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2LocalFlowController frameWriter(Http2FrameWriter frameWriter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void receiveFlowControlledFrame(Http2Stream stream, ByteBuf data, int padding, boolean endOfStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int unconsumedBytes(Http2Stream stream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int initialWindowSize(Http2Stream stream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void channelHandlerContext(ChannelHandlerContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void initialWindowSize(int newWindowSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int initialWindowSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int windowSize(Http2Stream stream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void incrementWindowSize(Http2Stream stream, int delta) {
            throw new UnsupportedOperationException();
        }
    }
}
