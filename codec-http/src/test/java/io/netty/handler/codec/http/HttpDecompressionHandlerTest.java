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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.BackpressureGauge;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.Decompressor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.compression.Decompressor.Status.COMPLETE;
import static io.netty.handler.codec.compression.Decompressor.Status.NEED_INPUT;
import static io.netty.handler.codec.compression.Decompressor.Status.NEED_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HttpDecompressionHandlerTest extends HttpContentDecompressorTest {
    private static final String READ_COMPLETE = "readComplete";
    private static final String LAST = "last";

    private static DefaultHttpRequest request() {
        return new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/", new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_ENCODING, "compressed")
                .add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED));
    }

    private static DefaultHttpRequest requestUncompressed() {
        return new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/", new DefaultHttpHeaders()
                .add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED));
    }

    @Override
    protected ChannelHandler createDecompressor() {
        return HttpDecompressionHandler.create();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void maxMessages(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new MockDecompressor.Builder()
                        .needInput()
                        .needOutput(4)
                        .complete()

                        .handlerBuilder()
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new HttpContentNumberDecoder()
        );
        channel.config().setAutoRead(autoRead);

        channel.writeInbound(request(), new DefaultHttpContent(numberedBuffer(0)));

        HttpRequest request = channel.readInbound();
        assertEquals("/", request.uri());
        assertEquals(1, channel.<Integer>readInbound());

        if (!autoRead) {
            assertEquals(READ_COMPLETE, channel.readInbound());

            assertNull(channel.readInbound());
            channel.read();
        }

        assertEquals(2, channel.<Integer>readInbound());
        assertEquals(3, channel.<Integer>readInbound());

        if (!autoRead) {
            assertEquals(READ_COMPLETE, channel.readInbound());

            assertNull(channel.readInbound());
            channel.read();
        }

        assertEquals(4, channel.<Integer>readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void maxBytes(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new MockDecompressor.Builder()
                        .needInput()
                        .needOutput(4)
                        .complete()

                        .handlerBuilder()
                        .backpressureGaugeBuilder(BackpressureGauge.builder().bytesPerRead(5))
                        .build(),
                new HttpContentNumberDecoder()
        );
        channel.config().setAutoRead(autoRead);

        channel.writeInbound(request(), new DefaultHttpContent(numberedBuffer(0)));

        HttpRequest request = channel.readInbound();
        assertEquals("/", request.uri());
        assertEquals(1, channel.<Integer>readInbound());

        if (!autoRead) {
            assertEquals(READ_COMPLETE, channel.readInbound());

            assertNull(channel.readInbound());
            channel.read();
        }

        assertEquals(2, channel.<Integer>readInbound());
        assertEquals(3, channel.<Integer>readInbound());

        if (!autoRead) {
            assertEquals(READ_COMPLETE, channel.readInbound());

            assertNull(channel.readInbound());
            channel.read();
        }

        assertEquals(4, channel.<Integer>readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    private static ByteBuf numberedBuffer(int index) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(4);
        buf.writeInt(index);
        return buf;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void lastHttpContent(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new MockDecompressor.Builder()
                        .needInput()
                        .needOutput(2)
                        .needInput()
                        .needOutput(1)
                        .complete()

                        .handlerBuilder()
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new HttpContentNumberDecoder()
        );
        channel.config().setAutoRead(autoRead);

        channel.writeInbound(request(), new DefaultLastHttpContent(numberedBuffer(0)));

        HttpRequest request = channel.readInbound();
        assertEquals("/", request.uri());
        assertEquals(1, channel.<Integer>readInbound());

        if (!autoRead) {
            assertEquals(READ_COMPLETE, channel.readInbound());

            assertNull(channel.readInbound());
            channel.read();
        }

        assertEquals(2, channel.<Integer>readInbound());
        assertEquals(4, channel.<Integer>readInbound());

        assertEquals(LAST, channel.readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void downstreamReadsInReadComplete(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new MockDecompressor.Builder()
                        .needInput()
                        .needOutput(2)
                        .needInput()
                        .needOutput(1)
                        .complete()

                        .handlerBuilder()
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new HttpContentNumberDecoder(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelReadComplete(ChannelHandlerContext ctx) {
                        ctx.read();
                    }
                }
        );
        channel.config().setAutoRead(autoRead);

        channel.writeInbound(request(), new DefaultLastHttpContent(numberedBuffer(0)));

        assertEquals(requestUncompressed(), channel.readInbound());
        assertEquals(1, channel.<Integer>readInbound());
        if (!autoRead) {
            assertEquals(READ_COMPLETE, channel.readInbound());
        }
        assertEquals(2, channel.<Integer>readInbound());
        assertEquals(4, channel.<Integer>readInbound());
        assertEquals(LAST, channel.readInbound());
        assertEquals(READ_COMPLETE, channel.readInbound());
        assertNull(channel.readInbound());

        channel.finish();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void pipelining(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new MockDecompressor.Builder()
                        .needInput()
                        .needOutput(2)
                        .complete()

                        .handlerBuilder()
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new HttpContentNumberDecoder()
        );
        channel.config().setAutoRead(autoRead);

        int n = 3;
        List<Object> in = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            in.add(request());
            in.add(new DefaultLastHttpContent(numberedBuffer(0)));
        }

        channel.writeInbound(in.toArray(new Object[0]));

        for (int i = 0; i < n; i++) {
            assertEquals(requestUncompressed(), channel.readInbound());
            assertEquals(1, channel.<Integer>readInbound());
            if (!autoRead) {
                assertEquals(READ_COMPLETE, channel.readInbound());
                assertNull(channel.readInbound());
                channel.read();
            }
            assertEquals(2, channel.<Integer>readInbound());
            assertEquals(LAST, channel.readInbound());
        }
        assertEquals(READ_COMPLETE, channel.readInbound());

        channel.finish();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void failInput(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new MockDecompressor.Builder()
                        .needInput().fail()

                        .handlerBuilder()
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new HttpContentNumberDecoder(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        ctx.fireChannelRead(cause);
                    }
                }
        );
        channel.config().setAutoRead(autoRead);

        for (int i = 0; i < 2; i++) {
            channel.writeInbound(request(), new DefaultLastHttpContent(numberedBuffer(0)));

            assertEquals(requestUncompressed(), channel.readInbound());
            assertInstanceOf(MockDecompressionException.class, channel.readInbound());
            assertEquals(LAST, channel.readInbound());
            assertEquals(READ_COMPLETE, channel.readInbound());
        }

        channel.finish();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void failOutput(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new MockDecompressor.Builder()
                        .needInput()
                        .needOutput(1).fail()

                        .handlerBuilder()
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new HttpContentNumberDecoder(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        ctx.fireChannelRead(cause);
                    }
                }
        );
        channel.config().setAutoRead(autoRead);

        for (int i = 0; i < 2; i++) {
            channel.writeInbound(request(), new DefaultLastHttpContent(numberedBuffer(0)));

            assertEquals(requestUncompressed(), channel.readInbound());
            assertInstanceOf(MockDecompressionException.class, channel.readInbound());
            assertEquals(LAST, channel.readInbound());
            assertEquals(READ_COMPLETE, channel.readInbound());
        }

        channel.finish();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void mixedUncompressed(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new MockDecompressor.Builder()
                        .needInput()
                        .needOutput(1)
                        .needInput()
                        .needOutput(1)
                        .complete()

                        .handlerBuilder()
                        .backpressureGaugeBuilder(BackpressureGauge.builder().messagesPerRead(2))
                        .build(),
                new HttpContentNumberDecoder()
        );
        channel.config().setAutoRead(autoRead);

        for (int i = 0; i < 4; i++) {
            boolean compress = i % 2 == 0;
            if (compress) {
                channel.writeInbound(
                        request(),
                        new DefaultHttpContent(numberedBuffer(0)),
                        new DefaultLastHttpContent(numberedBuffer(2)));
            } else {
                channel.writeInbound(
                        requestUncompressed(),
                        new DefaultHttpContent(numberedBuffer(1)),
                        new DefaultLastHttpContent(numberedBuffer(3)));
            }

            assertEquals(requestUncompressed(), channel.readInbound());
            assertEquals(1, channel.<Integer>readInbound());
            if (!autoRead && compress) {
                assertEquals(READ_COMPLETE, channel.readInbound());
                assertNull(channel.readInbound());
                channel.read();
            }
            assertEquals(3, channel.<Integer>readInbound());
            assertEquals(LAST, channel.readInbound());
            assertEquals(READ_COMPLETE, channel.readInbound());
        }

        channel.finish();
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

            HttpDecompressionHandler.Builder handlerBuilder() {
                return HttpDecompressionHandler.builder()
                        .decompressionDecider(
                                contentEncoding -> "compressed".contentEquals(contentEncoding) ? this : null);
            }
        }
    }

    private static class HttpContentNumberDecoder extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpContent) {
                ByteBuf buf = ((HttpContent) msg).content();
                if (buf.isReadable()) {
                    ctx.fireChannelRead(buf.readInt());
                }
                buf.release();
                if (msg instanceof LastHttpContent) {
                    ctx.fireChannelRead(LAST);
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelRead(READ_COMPLETE);
            ctx.fireChannelReadComplete();
        }
    }
}
