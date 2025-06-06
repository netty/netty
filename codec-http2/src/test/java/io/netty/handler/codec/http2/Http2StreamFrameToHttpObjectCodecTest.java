/*
 * Copyright 2017 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Http2StreamFrameToHttpObjectCodecTest {

    @Test
    public void testUpgradeEmptyFullResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertEquals("200", headersFrame.headers().status().toString());
        assertTrue(headersFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void encode100ContinueAsHttp2HeadersFrameThatIsNotEndStream() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertEquals("100", headersFrame.headers().status().toString());
        assertFalse(headersFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void encodeNonFullHttpResponse100ContinueIsRejected() throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        assertThrows(EncoderException.class, new Executable() {
            @Override
            public void execute() {
                ch.writeOutbound(new DefaultHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
            }
        });
        ch.finishAndReleaseAll();
    }

    @Test
    public void testUpgradeNonEmptyFullResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello)));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertEquals("200", headersFrame.headers().status().toString());
        assertFalse(headersFrame.isEndStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertTrue(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyFullResponseWithTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders trailers = response.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(response));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertEquals("200", headersFrame.headers().status().toString());
        assertFalse(headersFrame.isEndStream());

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertEquals("value", trailersFrame.headers().get("key").toString());
        assertTrue(trailersFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeNonEmptyFullResponseWithTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello);
        HttpHeaders trailers = response.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(response));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertEquals("200", headersFrame.headers().status().toString());
        assertFalse(headersFrame.isEndStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertEquals("value", trailersFrame.headers().get("key").toString());
        assertTrue(trailersFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeHeaders() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertTrue(ch.writeOutbound(response));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertEquals("200", headersFrame.headers().status().toString());
        assertFalse(headersFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeChunk() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        HttpContent content = new DefaultHttpContent(hello);
        assertTrue(ch.writeOutbound(content));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyEnd() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        LastHttpContent end = LastHttpContent.EMPTY_LAST_CONTENT;
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame emptyFrame = ch.readOutbound();
        try {
            assertEquals(0, emptyFrame.content().readableBytes());
            assertTrue(emptyFrame.isEndStream());
        } finally {
            emptyFrame.release();
        }

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEnd() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent end = new DefaultLastHttpContent(hello, true);
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertTrue(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        LastHttpContent trailers = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertEquals("value", headerFrame.headers().get("key").toString());
        assertTrue(headerFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEndWithTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent trailers = new DefaultLastHttpContent(hello, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertEquals("value", headerFrame.headers().get("key").toString());
        assertTrue(headerFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeHeaders() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.path("/");
        headers.method("GET");

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

        HttpRequest request = ch.readInbound();
        assertEquals("/", request.uri());
        assertEquals(HttpMethod.GET, request.method());
        assertEquals(HttpVersion.HTTP_1_1, request.protocolVersion());
        assertThat(request).isNotInstanceOf(FullHttpRequest.class);
        assertTrue(HttpUtil.isTransferEncodingChunked(request));

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeHeadersWithContentLength() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.path("/");
        headers.method("GET");
        headers.setInt("content-length", 0);

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

        HttpRequest request = ch.readInbound();
        assertEquals("/", request.uri());
        assertEquals(HttpMethod.GET, request.method());
        assertEquals(HttpVersion.HTTP_1_1, request.protocolVersion());
        assertThat(request).isNotInstanceOf(FullHttpRequest.class);
        assertFalse(HttpUtil.isTransferEncodingChunked(request));

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeFullHeaders() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.path("/");
        headers.method("GET");

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

        FullHttpRequest request = ch.readInbound();
        try {
            assertEquals("/", request.uri());
            assertEquals(HttpMethod.GET, request.method());
            assertEquals(HttpVersion.HTTP_1_1, request.protocolVersion());
            assertEquals(0, request.content().readableBytes());
            assertTrue(request.trailingHeaders().isEmpty());
            assertFalse(HttpUtil.isTransferEncodingChunked(request));
        } finally {
            request.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.set("key", "value");
        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

        LastHttpContent trailers = ch.readInbound();
        try {
            assertEquals(0, trailers.content().readableBytes());
            assertEquals("value", trailers.trailingHeaders().get("key"));
            assertThat(trailers).isNotInstanceOf(FullHttpRequest.class);
        } finally {
            trailers.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeData() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello)));

        HttpContent content = ch.readInbound();
        try {
            assertEquals("hello world", content.content().toString(CharsetUtil.UTF_8));
            assertFalse(content instanceof LastHttpContent);
        } finally {
            content.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeEndData() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello, true)));

        LastHttpContent content = ch.readInbound();
        try {
            assertEquals("hello world", content.content().toString(CharsetUtil.UTF_8));
            assertTrue(content.trailingHeaders().isEmpty());
        } finally {
            content.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testPassThroughOther() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2ResetFrame reset = new DefaultHttp2ResetFrame(0);
        Http2GoAwayFrame goaway = new DefaultHttp2GoAwayFrame(0);
        assertTrue(ch.writeInbound(reset));
        assertTrue(ch.writeInbound(goaway.retain()));

        assertEquals(reset, ch.readInbound());

        Http2GoAwayFrame frame = ch.readInbound();
        try {
            assertEquals(goaway, frame);
            assertNull(ch.readInbound());
            assertFalse(ch.finish());
        } finally {
            goaway.release();
            frame.release();
        }
    }

    // client-specific tests
    @Test
    public void testEncodeEmptyFullRequest() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world")));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertEquals("http", headers.scheme().toString());
        assertEquals("GET", headers.method().toString());
        assertEquals("/hello/world", headers.path().toString());
        assertTrue(headersFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeHttpsSchemeWhenSslHandlerExists() throws Exception {
        final Queue<Http2StreamFrame> frames = new ConcurrentLinkedQueue<Http2StreamFrame>();

        final SslContext ctx = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).build();
        EmbeddedChannel ch = new EmbeddedChannel(ctx.newHandler(ByteBufAllocator.DEFAULT),
                new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        if (msg instanceof Http2StreamFrame) {
                            frames.add((Http2StreamFrame) msg);
                            ctx.write(Unpooled.EMPTY_BUFFER, promise);
                        } else {
                            ctx.write(msg, promise);
                        }
                    }
                }, new Http2StreamFrameToHttpObjectCodec(false));

        try {
            FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
            assertTrue(ch.writeOutbound(req));

            ch.finishAndReleaseAll();

            Http2HeadersFrame headersFrame = (Http2HeadersFrame) frames.poll();
            Http2Headers headers = headersFrame.headers();

            assertEquals("https", headers.scheme().toString());
            assertEquals("GET", headers.method().toString());
            assertEquals("/hello/world", headers.path().toString());
            assertTrue(headersFrame.isEndStream());
            assertNull(frames.poll());
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testEncodeNonEmptyFullRequest() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world", hello)));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertEquals("http", headers.scheme().toString());
        assertEquals("PUT", headers.method().toString());
        assertEquals("/hello/world", headers.path().toString());
        assertFalse(headersFrame.isEndStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertTrue(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeEmptyFullRequestWithTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world");

        HttpHeaders trailers = request.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(request));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertEquals("http", headers.scheme().toString());
        assertEquals("PUT", headers.method().toString());
        assertEquals("/hello/world", headers.path().toString());
        assertFalse(headersFrame.isEndStream());

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertEquals("value", trailersFrame.headers().get("key").toString());
        assertTrue(trailersFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeNonEmptyFullRequestWithTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world", hello);

        HttpHeaders trailers = request.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(request));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertEquals("http", headers.scheme().toString());
        assertEquals("PUT", headers.method().toString());
        assertEquals("/hello/world", headers.path().toString());
        assertFalse(headersFrame.isEndStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertEquals("value", trailersFrame.headers().get("key").toString());
        assertTrue(trailersFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeRequestHeaders() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
        assertTrue(ch.writeOutbound(request));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertEquals("http", headers.scheme().toString());
        assertEquals("GET", headers.method().toString());
        assertEquals("/hello/world", headers.path().toString());
        assertFalse(headersFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeChunkAsClient() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        HttpContent content = new DefaultHttpContent(hello);
        assertTrue(ch.writeOutbound(content));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeEmptyEndAsClient() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        LastHttpContent end = LastHttpContent.EMPTY_LAST_CONTENT;
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame emptyFrame = ch.readOutbound();
        try {
            assertEquals(0, emptyFrame.content().readableBytes());
            assertTrue(emptyFrame.isEndStream());
        } finally {
            emptyFrame.release();
        }

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeDataEndAsClient() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent end = new DefaultLastHttpContent(hello, true);
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertTrue(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeTrailersAsClient() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        LastHttpContent trailers = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertEquals("value", headerFrame.headers().get("key").toString());
        assertTrue(headerFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeDataEndWithTrailersAsClient() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent trailers = new DefaultLastHttpContent(hello, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertEquals("hello world", dataFrame.content().toString(CharsetUtil.UTF_8));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertEquals("value", headerFrame.headers().get("key").toString());
        assertTrue(headerFrame.isEndStream());

        assertNull(ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void decode100ContinueHttp2HeadersAsFullHttpResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.CONTINUE.codeAsText());

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, false)));

        final FullHttpResponse response = ch.readInbound();
        try {
            assertEquals(HttpResponseStatus.CONTINUE, response.status());
            assertEquals(HttpVersion.HTTP_1_1, response.protocolVersion());
        } finally {
            response.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    /**
     *    An informational response using a 1xx status code other than 101 is
     *    transmitted as a HEADERS frame, followed by zero or more CONTINUATION
     *    frames.
     *    Trailing header fields are sent as a header block after both the
     *    request or response header block and all the DATA frames have been
     *    sent.  The HEADERS frame starting the trailers header block has the
     *    END_STREAM flag set.
     */
    @Test
    public void decode103EarlyHintsHttp2HeadersAsFullHttpResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.EARLY_HINTS.codeAsText());
        headers.set("key", "value");

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, false)));

        final FullHttpResponse response = ch.readInbound();
        try {
            assertEquals(HttpResponseStatus.EARLY_HINTS, response.status());
            assertEquals(HttpVersion.HTTP_1_1, response.protocolVersion());
            assertEquals("value", response.headers().get("key"));
        } finally {
            response.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeResponseHeaders() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.OK.codeAsText());

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

        HttpResponse response = ch.readInbound();
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(HttpVersion.HTTP_1_1, response.protocolVersion());
        assertFalse(response instanceof FullHttpResponse);
        assertTrue(HttpUtil.isTransferEncodingChunked(response));

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeResponseHeadersWithContentLength() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.OK.codeAsText());
        headers.setInt("content-length", 0);

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

        HttpResponse response = ch.readInbound();
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(HttpVersion.HTTP_1_1, response.protocolVersion());
        assertThat(response).isNotInstanceOf(FullHttpResponse.class);
        assertFalse(HttpUtil.isTransferEncodingChunked(response));

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @ParameterizedTest()
    @ValueSource(strings = {"204", "304"})
    public void testDecodeResponseHeadersContentAlwaysEmpty(String statusCode) {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(statusCode);

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

        HttpResponse request = ch.readInbound();
        assertEquals(statusCode, request.status().codeAsText().toString());
        assertEquals(HttpVersion.HTTP_1_1, request.protocolVersion());
        assertThat(request).isNotInstanceOf(FullHttpResponse.class);
        assertFalse(HttpUtil.isTransferEncodingChunked(request));

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeFullResponseHeaders() throws Exception {
        testDecodeFullResponseHeaders(false);
    }

    @Test
    public void testDecodeFullResponseHeadersWithStreamID() throws Exception {
        testDecodeFullResponseHeaders(true);
    }

    private void testDecodeFullResponseHeaders(boolean withStreamId) throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.OK.codeAsText());

        Http2HeadersFrame frame = new DefaultHttp2HeadersFrame(headers, true);
        if (withStreamId) {
            frame.stream(new Http2FrameStream() {
                @Override
                public int id() {
                    return 1;
                }

                @Override
                public Http2Stream.State state() {
                    return Http2Stream.State.OPEN;
                }
            });
        }

        assertTrue(ch.writeInbound(frame));

        FullHttpResponse response = ch.readInbound();
        try {
            assertEquals(HttpResponseStatus.OK, response.status());
            assertEquals(HttpVersion.HTTP_1_1, response.protocolVersion());
            assertEquals(0, response.content().readableBytes());
            assertTrue(response.trailingHeaders().isEmpty());
            assertFalse(HttpUtil.isTransferEncodingChunked(response));
            if (withStreamId) {
                assertEquals(1,
                        (int) response.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()));
            }
        } finally {
            response.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeResponseTrailersAsClient() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.set("key", "value");
        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

        LastHttpContent trailers = ch.readInbound();
        try {
            assertEquals(0, trailers.content().readableBytes());
            assertEquals("value", trailers.trailingHeaders().get("key"));
            assertThat(trailers).isNotInstanceOf(FullHttpRequest.class);
        } finally {
            trailers.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeDataAsClient() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello)));

        HttpContent content = ch.readInbound();
        try {
            assertEquals("hello world", content.content().toString(CharsetUtil.UTF_8));
            assertThat(content).isNotInstanceOf(LastHttpContent.class);
        } finally {
            content.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeEndDataAsClient() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello, true)));

        LastHttpContent content = ch.readInbound();
        try {
            assertEquals("hello world", content.content().toString(CharsetUtil.UTF_8));
            assertTrue(content.trailingHeaders().isEmpty());
        } finally {
            content.release();
        }

        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testPassThroughOtherAsClient() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2ResetFrame reset = new DefaultHttp2ResetFrame(0);
        Http2GoAwayFrame goaway = new DefaultHttp2GoAwayFrame(0);
        assertTrue(ch.writeInbound(reset));
        assertTrue(ch.writeInbound(goaway.retain()));

        assertEquals(reset, ch.readInbound());

        Http2GoAwayFrame frame = ch.readInbound();
        try {
            assertEquals(goaway, frame);
            assertNull(ch.readInbound());
            assertFalse(ch.finish());
        } finally {
            goaway.release();
            frame.release();
        }
    }

    @Test
    public void testIsSharableBetweenChannels() throws Exception {
        final Queue<Http2StreamFrame> frames = new ConcurrentLinkedQueue<Http2StreamFrame>();
        final ChannelHandler sharedHandler = new Http2StreamFrameToHttpObjectCodec(false);

        final SslContext ctx = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).build();
        EmbeddedChannel tlsCh = new EmbeddedChannel(ctx.newHandler(ByteBufAllocator.DEFAULT),
            new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    if (msg instanceof Http2StreamFrame) {
                        frames.add((Http2StreamFrame) msg);
                        promise.setSuccess();
                    } else {
                        ctx.write(msg, promise);
                    }
                }
            }, sharedHandler);

        EmbeddedChannel plaintextCh = new EmbeddedChannel(
            new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    if (msg instanceof Http2StreamFrame) {
                        frames.add((Http2StreamFrame) msg);
                        promise.setSuccess();
                    } else {
                        ctx.write(msg, promise);
                    }
                }
            }, sharedHandler);

        FullHttpRequest req = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
        assertTrue(tlsCh.writeOutbound(req));
        assertTrue(tlsCh.finishAndReleaseAll());

        Http2HeadersFrame headersFrame = (Http2HeadersFrame) frames.poll();
        Http2Headers headers = headersFrame.headers();

        assertEquals("https", headers.scheme().toString());
        assertEquals("GET", headers.method().toString());
        assertEquals("/hello/world", headers.path().toString());
        assertTrue(headersFrame.isEndStream());
        assertNull(frames.poll());

        // Run the plaintext channel
        req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
        assertFalse(plaintextCh.writeOutbound(req));
        assertFalse(plaintextCh.finishAndReleaseAll());

        headersFrame = (Http2HeadersFrame) frames.poll();
        headers = headersFrame.headers();

        assertEquals("http", headers.scheme().toString());
        assertEquals("GET", headers.method().toString());
        assertEquals("/hello/world", headers.path().toString());
        assertTrue(headersFrame.isEndStream());
        assertNull(frames.poll());
    }
}
