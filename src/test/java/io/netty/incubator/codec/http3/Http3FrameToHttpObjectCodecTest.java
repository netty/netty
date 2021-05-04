/*
 * Copyright 2021 The Netty Project
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

package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Http3FrameToHttpObjectCodecTest {

    @Test
    public void testUpgradeEmptyFullResponse() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertTrue(ch.isOutputShutdown());

        assertFalse(ch.finish());
    }

    @Test
    public void encode100ContinueAsHttp2HeadersFrameThatIsNotEndStream() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("100"));
        assertFalse(ch.isOutputShutdown());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test (expected = EncoderException.class)
    public void encodeNonFullHttpResponse100ContinueIsRejected() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        try {
            ch.writeOutbound(new DefaultHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testUpgradeNonEmptyFullResponse() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello)));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            dataFrame.release();
        }

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyFullResponseWithTrailers() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders trailers = response.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(response));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));

        Http3HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));
        assertTrue(ch.isOutputShutdown());

        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeNonEmptyFullResponseWithTrailers() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello);
        HttpHeaders trailers = response.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(response));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            dataFrame.release();
        }

        Http3HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));
        assertTrue(ch.isOutputShutdown());

        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeHeaders() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertTrue(ch.writeOutbound(response));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(ch.isOutputShutdown());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeChunk() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        HttpContent content = new DefaultHttpContent(hello);
        assertTrue(ch.writeOutbound(content));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(ch.isOutputShutdown());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyEnd() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        LastHttpContent end = LastHttpContent.EMPTY_LAST_CONTENT;
        assertTrue(ch.writeOutbound(end));

        Http3HeadersFrame emptyFrame = ch.readOutbound();
        assertTrue(emptyFrame.headers().isEmpty());

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEnd() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent end = new DefaultLastHttpContent(hello, true);
        assertTrue(ch.writeOutbound(end));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            dataFrame.release();
        }

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEndWithTrailers() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent trailers = new DefaultLastHttpContent(hello, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            dataFrame.release();
        }

        Http3HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));
        assertTrue(ch.isOutputShutdown());

        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeHeaders() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        Http3Headers headers = new DefaultHttp3Headers();
        headers.path("/");
        headers.method("GET");

        assertTrue(ch.writeInbound(new DefaultHttp3HeadersFrame(headers)));

        HttpRequest request = ch.readInbound();
        assertThat(request.uri(), is("/"));
        assertThat(request.method(), is(HttpMethod.GET));
        assertThat(request.protocolVersion(), is(HttpVersion.HTTP_1_1));
        assertFalse(request instanceof FullHttpRequest);
        assertTrue(HttpUtil.isTransferEncodingChunked(request));

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeHeadersWithContentLength() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        Http3Headers headers = new DefaultHttp3Headers();
        headers.path("/");
        headers.method("GET");
        headers.setInt("content-length", 0);

        assertTrue(ch.writeInbound(new DefaultHttp3HeadersFrame(headers)));

        HttpRequest request = ch.readInbound();
        assertThat(request.uri(), is("/"));
        assertThat(request.method(), is(HttpMethod.GET));
        assertThat(request.protocolVersion(), is(HttpVersion.HTTP_1_1));
        assertFalse(request instanceof FullHttpRequest);
        assertFalse(HttpUtil.isTransferEncodingChunked(request));

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeFullHeaders() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        Http3Headers headers = new DefaultHttp3Headers();
        headers.path("/");
        headers.method("GET");

        assertTrue(ch.writeInboundWithFin(new DefaultHttp3HeadersFrame(headers)));

        FullHttpRequest request = ch.readInbound();
        try {
            assertThat(request.uri(), is("/"));
            assertThat(request.method(), is(HttpMethod.GET));
            assertThat(request.protocolVersion(), is(HttpVersion.HTTP_1_1));
            assertThat(request.content().readableBytes(), is(0));
            assertTrue(request.trailingHeaders().isEmpty());
            assertFalse(HttpUtil.isTransferEncodingChunked(request));
        } finally {
            request.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeTrailers() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        Http3Headers headers = new DefaultHttp3Headers();
        headers.set("key", "value");

        assertTrue(ch.writeInboundWithFin(new DefaultHttp3HeadersFrame(headers)));

        LastHttpContent trailers = ch.readInbound();
        try {
            assertThat(trailers.content().readableBytes(), is(0));
            assertThat(trailers.trailingHeaders().get("key"), is("value"));
            assertFalse(trailers instanceof FullHttpRequest);
        } finally {
            trailers.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeData() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp3DataFrame(hello)));

        HttpContent content = ch.readInbound();
        try {
            assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(content instanceof LastHttpContent);
        } finally {
            content.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeEndData() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInboundWithFin(new DefaultHttp3DataFrame(hello)));

        LastHttpContent content = ch.readInbound();
        try {
            assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(content.trailingHeaders().isEmpty());
        } finally {
            content.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    // client-specific tests
    @Test
    public void testEncodeEmptyFullRequest() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world")));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertTrue(ch.isOutputShutdown());

        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeNonEmptyFullRequest() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world", hello)));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("PUT"));
        assertThat(headers.path().toString(), is("/hello/world"));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            dataFrame.release();
        }

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeEmptyFullRequestWithTrailers() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world");

        HttpHeaders trailers = request.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(request));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("PUT"));
        assertThat(headers.path().toString(), is("/hello/world"));

        Http3HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeNonEmptyFullRequestWithTrailers() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world", hello);

        HttpHeaders trailers = request.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(request));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("PUT"));
        assertThat(headers.path().toString(), is("/hello/world"));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            dataFrame.release();
        }

        Http3HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeRequestHeaders() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
        assertTrue(ch.writeOutbound(request));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertFalse(ch.isOutputShutdown());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeChunkAsClient() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        HttpContent content = new DefaultHttpContent(hello);
        assertTrue(ch.writeOutbound(content));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            dataFrame.release();
        }
        assertFalse(ch.isOutputShutdown());
        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeEmptyEndAsClient() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        LastHttpContent end = LastHttpContent.EMPTY_LAST_CONTENT;
        assertTrue(ch.writeOutbound(end));

        Http3HeadersFrame emptyFrame = ch.readOutbound();
        assertTrue(emptyFrame.headers().isEmpty());

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeDataEndAsClient() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent end = new DefaultLastHttpContent(hello, true);
        assertTrue(ch.writeOutbound(end));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            dataFrame.release();
        }

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeTrailersAsClient() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        LastHttpContent trailers = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http3HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeDataEndWithTrailersAsClient() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent trailers = new DefaultLastHttpContent(hello, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            dataFrame.release();
        }

        Http3HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));

        assertTrue(ch.isOutputShutdown());
        assertFalse(ch.finish());
    }

    @Test
    public void decode100ContinueHttp2HeadersAsFullHttpResponse() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        Http3Headers headers = new DefaultHttp3Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.CONTINUE.codeAsText());

        assertTrue(ch.writeInbound(new DefaultHttp3HeadersFrame(headers)));

        final FullHttpResponse response = ch.readInbound();
        try {
            assertThat(response.status(), is(HttpResponseStatus.CONTINUE));
            assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
        } finally {
            response.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeResponseHeaders() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        Http3Headers headers = new DefaultHttp3Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.OK.codeAsText());

        assertTrue(ch.writeInbound(new DefaultHttp3HeadersFrame(headers)));

        HttpResponse response = ch.readInbound();
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
        assertFalse(response instanceof FullHttpResponse);
        assertTrue(HttpUtil.isTransferEncodingChunked(response));

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeResponseHeadersWithContentLength() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        Http3Headers headers = new DefaultHttp3Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.OK.codeAsText());
        headers.setInt("content-length", 0);

        assertTrue(ch.writeInbound(new DefaultHttp3HeadersFrame(headers)));

        HttpResponse response = ch.readInbound();
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
        assertFalse(response instanceof FullHttpResponse);
        assertFalse(HttpUtil.isTransferEncodingChunked(response));

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeFullResponseHeaders() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        Http3Headers headers = new DefaultHttp3Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.OK.codeAsText());

        Http3HeadersFrame frame = new DefaultHttp3HeadersFrame(headers);

        assertTrue(ch.writeInboundWithFin(frame));

        FullHttpResponse response = ch.readInbound();
        try {
            assertThat(response.status(), is(HttpResponseStatus.OK));
            assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
            assertThat(response.content().readableBytes(), is(0));
            assertTrue(response.trailingHeaders().isEmpty());
            assertFalse(HttpUtil.isTransferEncodingChunked(response));
            assertEquals(0,
                    (int) response.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()));
        } finally {
            response.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeResponseTrailersAsClient() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        Http3Headers headers = new DefaultHttp3Headers();
        headers.set("key", "value");
        assertTrue(ch.writeInboundWithFin(new DefaultHttp3HeadersFrame(headers)));

        LastHttpContent trailers = ch.readInbound();
        try {
            assertThat(trailers.content().readableBytes(), is(0));
            assertThat(trailers.trailingHeaders().get("key"), is("value"));
            assertFalse(trailers instanceof FullHttpRequest);
        } finally {
            trailers.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeDataAsClient() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp3DataFrame(hello)));

        HttpContent content = ch.readInbound();
        try {
            assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(content instanceof LastHttpContent);
        } finally {
            content.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeEndDataAsClient() {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInboundWithFin(new DefaultHttp3DataFrame(hello)));

        LastHttpContent content = ch.readInbound();
        try {
            assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(content.trailingHeaders().isEmpty());
        } finally {
            content.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }
}
