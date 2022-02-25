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

package io.netty5.handler.codec.http2;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.EncoderException;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.DefaultHttpContent;
import io.netty5.handler.codec.http.DefaultHttpRequest;
import io.netty5.handler.codec.http.DefaultHttpResponse;
import io.netty5.handler.codec.http.DefaultLastHttpContent;
import io.netty5.handler.codec.http.EmptyLastHttpContent;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpContent;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpScheme;
import io.netty5.handler.codec.http.HttpUtil;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.handler.codec.http.LastHttpContent;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http2StreamFrameToHttpObjectCodecTest {

    @Test
    public void testUpgradeEmptyFullResponse() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0))));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertTrue(headersFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void encode100ContinueAsHttp2HeadersFrameThatIsNotEndStream() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE,
                preferredAllocator().allocate(0))));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("100"));
        assertFalse(headersFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void encodeNonFullHttpResponse100ContinueIsRejected() {
        final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        assertThrows(EncoderException.class, () -> ch.writeOutbound(new DefaultHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)));
        ch.finishAndReleaseAll();
    }

    @Test
    public void testUpgradeNonEmptyFullResponse() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello)));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(headersFrame.isEndStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyFullResponseWithTrailers() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0));
        HttpHeaders trailers = response.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(response));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(headersFrame.isEndStream());

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));
        assertTrue(trailersFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeNonEmptyFullResponseWithTrailers() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello);
        HttpHeaders trailers = response.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(response));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(headersFrame.isEndStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));
        assertTrue(trailersFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeHeaders() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertTrue(ch.writeOutbound(response));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(headersFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeChunk() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        HttpContent<?> content = new DefaultHttpContent(hello);
        assertTrue(ch.writeOutbound(content));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyEnd() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        LastHttpContent<?> end = new EmptyLastHttpContent(preferredAllocator());
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame emptyFrame = ch.readOutbound();
        try {
            assertThat(emptyFrame.content().readableBytes(), is(0));
            assertTrue(emptyFrame.isEndStream());
        } finally {
            emptyFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEnd() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        LastHttpContent<?> end = new DefaultLastHttpContent(hello, true);
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeTrailers() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        LastHttpContent<?> trailers = new DefaultLastHttpContent(preferredAllocator().allocate(0), true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));
        assertTrue(headerFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEndWithTrailers() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        LastHttpContent<?> trailers = new DefaultLastHttpContent(hello, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));
        assertTrue(headerFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeHeaders() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.path("/");
        headers.method("GET");

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

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
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.path("/");
        headers.method("GET");
        headers.setInt("content-length", 0);

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

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
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.path("/");
        headers.method("GET");

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

        try (FullHttpRequest request = ch.readInbound()) {
            assertThat(request.uri(), is("/"));
            assertThat(request.method(), is(HttpMethod.GET));
            assertThat(request.protocolVersion(), is(HttpVersion.HTTP_1_1));
            assertThat(request.payload().readableBytes(), is(0));
            assertTrue(request.trailingHeaders().isEmpty());
            assertFalse(HttpUtil.isTransferEncodingChunked(request));
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeTrailers() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.set("key", "value");
        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

        try (LastHttpContent<?> trailers = ch.readInbound()) {
            assertThat(trailers.payload().readableBytes(), is(0));
            assertThat(trailers.trailingHeaders().get("key"), is("value"));
            assertFalse(trailers instanceof FullHttpRequest);
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeData() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello)));

        try (HttpContent<?> content = ch.readInbound()) {
            assertThat(content.payload().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(content instanceof LastHttpContent);
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeEndData() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello, true)));

        try (LastHttpContent<?> content = ch.readInbound()) {
            assertThat(content.payload().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(content.trailingHeaders().isEmpty());
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testPassThroughOther() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
        Http2ResetFrame reset = new DefaultHttp2ResetFrame(0);
        Http2GoAwayFrame goaway = new DefaultHttp2GoAwayFrame(0);
        assertTrue(ch.writeInbound(reset));
        assertTrue(ch.writeInbound(goaway.retain()));

        assertEquals(reset, ch.readInbound());

        Http2GoAwayFrame frame = ch.readInbound();
        try {
            assertEquals(goaway, frame);
            assertThat(ch.readInbound(), is(nullValue()));
            assertFalse(ch.finish());
        } finally {
            goaway.release();
            frame.release();
        }
    }

    // client-specific tests
    @Test
    public void testEncodeEmptyFullRequest() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world", preferredAllocator().allocate(0))));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("http"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertTrue(headersFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeHttpsSchemeWhenSslHandlerExists() throws Exception {
        final Queue<Http2StreamFrame> frames = new ConcurrentLinkedQueue<>();

        final SslContext ctx = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).build();
        EmbeddedChannel ch = new EmbeddedChannel(ctx.newHandler(ByteBufAllocator.DEFAULT),
                new ChannelHandler() {
                    @Override
                    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof Http2StreamFrame) {
                            frames.add((Http2StreamFrame) msg);
                            return ctx.write(Unpooled.EMPTY_BUFFER);
                        }
                        return ctx.write(msg);
                    }
                }, new Http2StreamFrameToHttpObjectCodec(false));

        try {
            FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world",
                                                             preferredAllocator().allocate(0));
            assertTrue(ch.writeOutbound(req));

            ch.finishAndReleaseAll();

            Http2HeadersFrame headersFrame = (Http2HeadersFrame) frames.poll();
            Http2Headers headers = headersFrame.headers();

            assertThat(headers.scheme().toString(), is("https"));
            assertThat(headers.method().toString(), is("GET"));
            assertThat(headers.path().toString(), is("/hello/world"));
            assertTrue(headersFrame.isEndStream());
            assertNull(frames.poll());
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void testEncodeNonEmptyFullRequest() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world", hello)));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("http"));
        assertThat(headers.method().toString(), is("PUT"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertFalse(headersFrame.isEndStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeEmptyFullRequestWithTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world",
                preferredAllocator().allocate(0));

        HttpHeaders trailers = request.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(request));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("http"));
        assertThat(headers.method().toString(), is("PUT"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertFalse(headersFrame.isEndStream());

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));
        assertTrue(trailersFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeNonEmptyFullRequestWithTrailers() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world", hello);

        HttpHeaders trailers = request.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(request));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("http"));
        assertThat(headers.method().toString(), is("PUT"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertFalse(headersFrame.isEndStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));
        assertTrue(trailersFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeRequestHeaders() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
        assertTrue(ch.writeOutbound(request));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        Http2Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("http"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertFalse(headersFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeChunkAsClient() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        HttpContent<?> content = new DefaultHttpContent(hello);
        assertTrue(ch.writeOutbound(content));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeEmptyEndAsClient() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        LastHttpContent<?> end = new EmptyLastHttpContent(preferredAllocator());
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame emptyFrame = ch.readOutbound();
        try {
            assertThat(emptyFrame.content().readableBytes(), is(0));
            assertTrue(emptyFrame.isEndStream());
        } finally {
            emptyFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeDataEndAsClient() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        LastHttpContent<?> end = new DefaultLastHttpContent(hello, true);
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeTrailersAsClient() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        LastHttpContent<?> trailers = new DefaultLastHttpContent(
                preferredAllocator().allocate(0), true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));
        assertTrue(headerFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeDataEndWithTrailersAsClient() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Buffer hello = preferredAllocator().allocate(16).writeCharSequence("hello world", CharsetUtil.UTF_8);
        LastHttpContent<?> trailers = new DefaultLastHttpContent(hello, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(dataFrame.isEndStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));
        assertTrue(headerFrame.isEndStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void decode100ContinueHttp2HeadersAsFullHttpResponse() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.CONTINUE.codeAsText());

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, false)));

        try (FullHttpResponse response = ch.readInbound()) {
            assertThat(response.status(), is(HttpResponseStatus.CONTINUE));
            assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeResponseHeaders() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.OK.codeAsText());

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

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
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.scheme(HttpScheme.HTTP.name());
        headers.status(HttpResponseStatus.OK.codeAsText());
        headers.setInt("content-length", 0);

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

        HttpResponse response = ch.readInbound();
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
        assertFalse(response instanceof FullHttpResponse);
        assertFalse(HttpUtil.isTransferEncodingChunked(response));

        assertThat(ch.readInbound(), is(nullValue()));
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

    private void testDecodeFullResponseHeaders(boolean withStreamId) {
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

        try (FullHttpResponse response = ch.readInbound()) {
            assertThat(response.status(), is(HttpResponseStatus.OK));
            assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
            assertThat(response.payload().readableBytes(), is(0));
            assertTrue(response.trailingHeaders().isEmpty());
            assertFalse(HttpUtil.isTransferEncodingChunked(response));
            if (withStreamId) {
                assertEquals(1,
                        (int) response.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()));
            }
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeResponseTrailersAsClient() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2Headers headers = new DefaultHttp2Headers();
        headers.set("key", "value");
        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

        try (LastHttpContent<?> trailers = ch.readInbound()) {
            assertThat(trailers.payload().readableBytes(), is(0));
            assertThat(trailers.trailingHeaders().get("key"), is("value"));
            assertFalse(trailers instanceof FullHttpRequest);
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeDataAsClient() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello)));

        try (HttpContent<?> content = ch.readInbound()) {
            assertThat(content.payload().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(content instanceof LastHttpContent);
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeEndDataAsClient() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello, true)));

        try (LastHttpContent<?> content = ch.readInbound()) {
            assertThat(content.payload().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(content.trailingHeaders().isEmpty());
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testPassThroughOtherAsClient() {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
        Http2ResetFrame reset = new DefaultHttp2ResetFrame(0);
        Http2GoAwayFrame goaway = new DefaultHttp2GoAwayFrame(0);
        assertTrue(ch.writeInbound(reset));
        assertTrue(ch.writeInbound(goaway.retain()));

        assertEquals(reset, ch.readInbound());

        Http2GoAwayFrame frame = ch.readInbound();
        try {
            assertEquals(goaway, frame);
            assertThat(ch.readInbound(), is(nullValue()));
            assertFalse(ch.finish());
        } finally {
            goaway.release();
            frame.release();
        }
    }

    @Test
    public void testIsSharableBetweenChannels() throws Exception {
        final Queue<Http2StreamFrame> frames = new ConcurrentLinkedQueue<>();
        final ChannelHandler sharedHandler = new Http2StreamFrameToHttpObjectCodec(false);

        final SslContext ctx = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).build();
        EmbeddedChannel tlsCh = new EmbeddedChannel(ctx.newHandler(ByteBufAllocator.DEFAULT),
            new ChannelHandler() {
                @Override
                public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof Http2StreamFrame) {
                        frames.add((Http2StreamFrame) msg);
                        return ctx.newSucceededFuture();
                    }
                    return ctx.write(msg);
                }
            }, sharedHandler);

        EmbeddedChannel plaintextCh = new EmbeddedChannel(
            new ChannelHandler() {
                @Override
                public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof Http2StreamFrame) {
                        frames.add((Http2StreamFrame) msg);
                        return ctx.newSucceededFuture();
                    }
                    return ctx.write(msg);
                }
            }, sharedHandler);

        FullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world",
                preferredAllocator().allocate(0));
        assertTrue(tlsCh.writeOutbound(req));
        assertTrue(tlsCh.finishAndReleaseAll());

        Http2HeadersFrame headersFrame = (Http2HeadersFrame) frames.poll();
        Http2Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertTrue(headersFrame.isEndStream());
        assertNull(frames.poll());

        // Run the plaintext channel
        req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world",
                                         preferredAllocator().allocate(0));
        assertFalse(plaintextCh.writeOutbound(req));
        assertFalse(plaintextCh.finishAndReleaseAll());

        headersFrame = (Http2HeadersFrame) frames.poll();
        headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("http"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertTrue(headersFrame.isEndStream());
        assertNull(frames.poll());
    }
}
