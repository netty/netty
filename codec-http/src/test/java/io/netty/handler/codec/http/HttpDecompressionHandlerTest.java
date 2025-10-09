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
import static org.junit.jupiter.api.Assertions.assertNull;

public class HttpDecompressionHandlerTest extends HttpContentDecompressorTest {
    private static final String READ_COMPLETE = "readComplete";
    private static final String LAST = "last";
    private static final DefaultHttpRequest REQUEST = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", new DefaultHttpHeaders()
            .add(HttpHeaderNames.CONTENT_TRANSFER_ENCODING, "mock")
            .add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED));

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
                        .makeHandler(2),
                new HttpContentNumberDecoder()
        );
        channel.config().setAutoRead(autoRead);

        channel.writeInbound(REQUEST, new DefaultHttpContent(numberedBuffer(0)));

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
                        .makeHandler(2),
                new HttpContentNumberDecoder()
        );
        channel.config().setAutoRead(autoRead);

        channel.writeInbound(REQUEST, new DefaultLastHttpContent(numberedBuffer(0)));

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
                        .makeHandler(2),
                new HttpContentNumberDecoder(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelReadComplete(ChannelHandlerContext ctx) {
                        ctx.read();
                    }
                }
        );
        channel.config().setAutoRead(autoRead);

        channel.writeInbound(REQUEST, new DefaultLastHttpContent(numberedBuffer(0)));

        assertEquals(REQUEST, channel.readInbound());
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
                        .makeHandler(2),
                new HttpContentNumberDecoder()
        );
        channel.config().setAutoRead(autoRead);

        int n = 3;
        List<Object> in = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            in.add(REQUEST);
            in.add(new DefaultLastHttpContent(numberedBuffer(0)));
        }

        channel.writeInbound(in.toArray(new Object[0]));

        for (int i = 0; i < n; i++) {
            assertEquals(REQUEST, channel.readInbound());
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
    }

    private static final class MockDecompressor implements Decompressor {
        private final List<Status> events;
        private int index;

        MockDecompressor(List<Status> events) {
            this.events = events;
        }

        @Override
        public Status status() throws DecompressionException {
            return events.get(index);
        }

        @Override
        public void addInput(ByteBuf buf) throws DecompressionException {
            assertEquals(NEED_INPUT, status());
            assertEquals(index++, buf.readInt());
            buf.release();
        }

        @Override
        public void endOfInput() throws DecompressionException {
            assertEquals(NEED_INPUT, status());
            index++;
        }

        @Override
        public ByteBuf takeOutput() throws DecompressionException {
            assertEquals(NEED_OUTPUT, status());
            return numberedBuffer(index++);
        }

        @Override
        public void close() throws DecompressionException {
            assertEquals(COMPLETE, status());
        }

        static final class Builder extends AbstractDecompressorBuilder {
            private final List<Status> events = new ArrayList<>();

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

            @Override
            public Decompressor build(ByteBufAllocator allocator) throws DecompressionException {
                return new MockDecompressor(events);
            }

            ChannelHandler makeHandler(int messagesPerRead) {
                return HttpDecompressionHandler.builder()
                        .messagesPerRead(messagesPerRead)
                        .decompressionDecider(contentEncoding -> this)
                        .build();
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
