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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.BackpressureDecompressionHandler;
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

public class BackpressureHttpContentDecoderTest extends HttpContentDecoderTest {
    private static final String READ_COMPLETE = "readComplete";

    @Override
    protected ChannelHandler createDecompressor() {
        return new HttpDecompressionHandler();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void maxMessages(boolean autoRead) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new BackpressureHttpContentDecoder() {

                    @Override
                    protected ChannelDuplexHandler newContentDecoder(ByteBufAllocator allocator, String contentEncoding) throws Exception {
                        return BackpressureDecompressionHandler.builder(new MockDecompressor.Builder()
                                .needInput()
                                .needOutput(4)
                                .complete())
                                .maxMessagesPerRead(2)
                                .build();
                    }
                },
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof HttpContent) {
                            ctx.fireChannelRead(((HttpContent) msg).content().readInt());
                            ((HttpContent) msg).release();
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
        );
        channel.config().setAutoRead(autoRead);

        channel.writeInbound(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", new DefaultHttpHeaders()
                        .add(HttpHeaderNames.CONTENT_TRANSFER_ENCODING, "mock")
                        .add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)),
                new DefaultHttpContent(numberedBuffer(0))
        );

        HttpRequest request = channel.readInbound();
        assertEquals("/", request.uri());

        if (!autoRead) {
            assertEquals(READ_COMPLETE, channel.readInbound());

            assertNull(channel.readInbound());
            channel.read();
        }

        assertEquals(1, channel.<Integer>readInbound());
        assertEquals(2, channel.<Integer>readInbound());

        if (!autoRead) {
            assertEquals(READ_COMPLETE, channel.readInbound());

            assertNull(channel.readInbound());
            channel.read();
        }

        assertEquals(3, channel.<Integer>readInbound());
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

            Builder() {
                super(ByteBufAllocator.DEFAULT);
            }

            Builder needInput() {
                events.add(NEED_INPUT);
                return this;
            }

            Builder needOutput(int count) {
                for (int i = 0; i < count; i++) {
                    events.add(NEED_OUTPUT);
                }
                return this;
            }

            Builder complete() {
                events.add(COMPLETE);
                return this;
            }

            @Override
            public Decompressor build() throws DecompressionException {
                return new MockDecompressor(events);
            }
        }
    }
}
