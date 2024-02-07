/*
 * Copyright 2023 The Netty Project
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
package io.netty.microbench.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
public class HttpRequestResponseBenchmark extends AbstractMicrobenchmark {

    ByteBuf GET;
    int readerIndex;
    int writeIndex;
    EmbeddedChannel nettyChannel;
    @Param({ "true", "false" })
    boolean websocket;

    static class Alloc implements ByteBufAllocator {

        private final ByteBuf buf = Unpooled.buffer();
        private final int capacity = buf.capacity();

        @Override
        public ByteBuf buffer() {
            buf.clear();
            return buf;
        }

        @Override
        public ByteBuf buffer(int initialCapacity) {
            if (initialCapacity <= capacity) {
                return buffer();
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public ByteBuf buffer(int initialCapacity, int maxCapacity) {
            if (initialCapacity <= capacity) {
                return buffer();
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public ByteBuf ioBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf ioBuffer(int initialCapacity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf heapBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf heapBuffer(int initialCapacity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf directBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf directBuffer(int initialCapacity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompositeByteBuf compositeBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompositeByteBuf compositeBuffer(int maxNumComponents) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompositeByteBuf compositeHeapBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompositeByteBuf compositeDirectBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDirectBufferPooled() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
            throw new UnsupportedOperationException();
        }
    }

    @Setup
    public void setup() {
        HttpRequestDecoder httpRequestDecoder = new HttpRequestDecoder(
                HttpRequestDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH, HttpRequestDecoder.DEFAULT_MAX_HEADER_SIZE,
                HttpRequestDecoder.DEFAULT_MAX_CHUNK_SIZE, false);
        HttpResponseEncoder httpResponseEncoder = new HttpResponseEncoder();
        ChannelInboundHandlerAdapter inboundHandlerAdapter = new ChannelInboundHandlerAdapter() {

            private final byte[] STATIC_PLAINTEXT = "Hello, World!".getBytes(CharsetUtil.UTF_8);
            private final int STATIC_PLAINTEXT_LEN = STATIC_PLAINTEXT.length;
            private final ByteBuf PLAINTEXT_CONTENT_BUFFER =
                    Unpooled.unreleasableBuffer(Unpooled.directBuffer().writeBytes(STATIC_PLAINTEXT));
            private final CharSequence PLAINTEXT_CLHEADER_VALUE = new AsciiString(String.valueOf(STATIC_PLAINTEXT_LEN));

            private final CharSequence TYPE_PLAIN = new AsciiString("text/plain");
            private final CharSequence SERVER_NAME = new AsciiString("Netty");
            private final CharSequence CONTENT_TYPE_ENTITY = HttpHeaderNames.CONTENT_TYPE;
            private final CharSequence DATE_ENTITY = HttpHeaderNames.DATE;
            private final CharSequence CONTENT_LENGTH_ENTITY = HttpHeaderNames.CONTENT_LENGTH;
            private final CharSequence SERVER_ENTITY = HttpHeaderNames.SERVER;

            private final DateFormat FORMAT = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
            private final CharSequence date = new AsciiString(FORMAT.format(new Date()));

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object o) {
                // this is saving a slow type check on LastHttpContent vs HttpRequest
                if (o == LastHttpContent.EMPTY_LAST_CONTENT) {
                    return;
                }
                if (o.getClass() == DefaultHttpRequest.class) {
                    writeResponse(ctx, PLAINTEXT_CONTENT_BUFFER.duplicate(), TYPE_PLAIN,
                                  PLAINTEXT_CLHEADER_VALUE);
                } else if (o instanceof HttpRequest) {
                    try {
                        // slow path: shouldn't happen here
                        writeResponse(ctx, PLAINTEXT_CONTENT_BUFFER.duplicate(), TYPE_PLAIN, PLAINTEXT_CLHEADER_VALUE);
                    } finally {
                        ReferenceCountUtil.release(o);
                    }
                } else {
                    ReferenceCountUtil.release(o);
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                ctx.flush();
            }

            private void writeResponse(ChannelHandlerContext ctx, ByteBuf buf, CharSequence contentType,
                                       CharSequence contentLength) {
                // Build the response object.
                FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf,
                        DefaultHttpHeadersFactory.headersFactory().withValidation(false),
                        DefaultHttpHeadersFactory.trailersFactory().withValidation(false));
                HttpHeaders headers = response.headers();
                headers.set(CONTENT_TYPE_ENTITY, contentType);
                headers.set(SERVER_ENTITY, SERVER_NAME);
                headers.set(DATE_ENTITY, date);
                headers.set(CONTENT_LENGTH_ENTITY, contentLength);
                ctx.write(response, ctx.voidPromise());
            }
        };
        if (websocket) {
            nettyChannel = new EmbeddedChannel(httpRequestDecoder, httpResponseEncoder,
                                               new WebSocketServerExtensionHandler(
                                                       new WebSocketServerExtensionHandshaker() {
                                                           @Override
                                                           public WebSocketServerExtension handshakeExtension(
                                                                   WebSocketExtensionData extensionData) {
                                                               return null;
                                                           }
                                                       }), inboundHandlerAdapter);
        } else {
            nettyChannel = new EmbeddedChannel(httpRequestDecoder, httpResponseEncoder, inboundHandlerAdapter);
        }
        nettyChannel.config().setAllocator(new Alloc());
        GET = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("GET / HTTP/1.1\r\n\r\n".getBytes()));
        readerIndex = GET.readerIndex();
        writeIndex = GET.writerIndex();
    }

    @Benchmark
    public Object netty() {
        GET.setIndex(readerIndex, writeIndex);
        nettyChannel.writeInbound(GET);
        return nettyChannel.outboundMessages().poll();
    }
}
