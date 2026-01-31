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

package io.netty.handler.codec.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelShutdownDirection;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http3FrameToHttpObjectCodecTest {

    @Test
    public void testUpgradeEmptyFullResponse() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertFalse(ch.finish());
    }

    @Test
    public void encode100ContinueAsHttp2HeadersFrameThatIsNotEndStream() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("100"));
        assertFalse(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void encodeNonFullHttpResponse100ContinueIsRejected() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        assertThrows(EncoderException.class, () -> ch.writeOutbound(new DefaultHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)));
        ch.finishAndReleaseAll();
    }

    @Test
    public void testUpgradeNonEmptyFullResponse() throws Exception {
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

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyFullResponseWithTrailers() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders trailers = response.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(response));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));

        Http3HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeNonEmptyFullResponseWithTrailers() throws Exception {
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
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeHeaders() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertTrue(ch.writeOutbound(response));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeChunk() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        HttpContent content = new DefaultHttpContent(hello);
        assertTrue(ch.writeOutbound(content));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(ch.isShutdown(ChannelShutdownDirection.Outbound));
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyEnd() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().readableBytes(), is(0));
        } finally {
            dataFrame.release();
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEnd() throws Exception {
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

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEndWithTrailers() throws Exception {
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
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeHeaders() throws Exception {
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
    public void testDowngradeHeadersWithContentLength() throws Exception {
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
    public void testDowngradeTrailers() throws Exception {
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
    public void testDowngradeData() throws Exception {
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
    public void testDowngradeEndData() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(true));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInboundWithFin(new DefaultHttp3DataFrame(hello)));

        HttpContent content = ch.readInbound();
        try {
            assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            content.release();
        }

        LastHttpContent last = ch.readInbound();
        try {
            assertFalse(last.content().isReadable());
            assertTrue(last.trailingHeaders().isEmpty());
        } finally {
            last.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    // client-specific tests
    @Test
    public void testEncodeEmptyFullRequest() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world")));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeNonEmptyFullRequest() throws Exception {
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

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeEmptyFullRequestWithTrailers() throws Exception {
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

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeNonEmptyFullRequestWithTrailers() throws Exception {
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

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeRequestHeaders() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
        assertTrue(ch.writeOutbound(request));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertFalse(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeChunkAsClient() throws Exception {
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
        assertFalse(ch.isShutdown(ChannelShutdownDirection.Outbound));
        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeEmptyEndAsClient() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().readableBytes(), is(0));
        } finally {
            dataFrame.release();
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeDataEndAsClient() throws Exception {
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

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeTrailersAsClient() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        LastHttpContent trailers = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http3HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeDataEndWithTrailersAsClient() throws Exception {
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

        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeFullPromiseCompletes() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        Future<Void> writeFuture = ch.writeOneOutbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world"));
        ch.flushOutbound();
        assertTrue(writeFuture.isSuccess());

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeEmptyLastPromiseCompletes() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        Future<Void> f1 = ch.writeOneOutbound(new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world"));
        Future<Void> f2 = ch.writeOneOutbound(new DefaultLastHttpContent());
        ch.flushOutbound();
        assertTrue(f1.isSuccess());
        assertTrue(f2.isSuccess());

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        Http3DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().readableBytes(), is(0));
        } finally {
            dataFrame.release();
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeMultiplePromiseCompletes() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        Future<Void> f1 = ch.writeOneOutbound(new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world"));
        Future<Void> f2 = ch.writeOneOutbound(new DefaultLastHttpContent(
                Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8))));
        ch.flushOutbound();
        assertTrue(f1.isSuccess());
        assertTrue(f2.isSuccess());

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        Http3DataFrame dataFrame = ch.readOutbound();
        assertEquals("foo", dataFrame.content().toString(StandardCharsets.UTF_8));

        assertFalse(ch.finish());
    }

    @Test
    public void testEncodeTrailersCompletes() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        Future<Void> f1 = ch.writeOneOutbound(new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world"));
        LastHttpContent last = new DefaultLastHttpContent(
                Unpooled.wrappedBuffer("foo".getBytes(StandardCharsets.UTF_8)));
        last.trailingHeaders().add("foo", "bar");
        Future<Void> f2 = ch.writeOneOutbound(last);
        ch.flushOutbound();
        assertTrue(f1.isSuccess());
        assertTrue(f2.isSuccess());

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.method().toString(), is("GET"));
        assertThat(headers.path().toString(), is("/hello/world"));
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        Http3DataFrame dataFrame = ch.readOutbound();
        assertEquals("foo", dataFrame.content().toString(StandardCharsets.UTF_8));

        Http3HeadersFrame trailingHeadersFrame = ch.readOutbound();
        assertEquals("bar", trailingHeadersFrame.headers().get("foo").toString());

        assertFalse(ch.finish());
    }

    private static final class EncodeCombinationsArgumentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            List<Arguments> arguments = new ArrayList<>();
            for (boolean headers : new boolean[]{false, true}) {
                for (boolean last : new boolean[]{false, true}) {
                    for (boolean nonEmptyContent : new boolean[]{false, true}) {
                        for (boolean hasTrailers : new boolean[]{false, true}) {
                            // this test goes through all the branches of Http3FrameToHttpObjectCodec
                            // and ensures right functionality
                            arguments.add(Arguments.of(headers, last, nonEmptyContent, hasTrailers));
                        }
                    }
                }
            }
            return arguments.stream();
        }
    }

    @ParameterizedTest(name = "headers: {0}, last: {1}, nonEmptyContent: {2}, hasTrailers: {3}")
    @ArgumentsSource(value = EncodeCombinationsArgumentsProvider.class)
    public void testEncodeCombination(
            boolean headers,
            boolean last,
            boolean nonEmptyContent,
            boolean hasTrailers
    ) throws Exception {
        ByteBuf content = nonEmptyContent ? Unpooled.wrappedBuffer(new byte[1]) : Unpooled.EMPTY_BUFFER;
        HttpHeaders trailers = new DefaultHttpHeaders();
        if (hasTrailers) {
            trailers.add("foo", "bar");
        }
        HttpObject msg;
        if (headers) {
            if (last) {
                msg = new DefaultFullHttpRequest(
                        HttpVersion.HTTP_1_1, HttpMethod.POST, "/foo", content, new DefaultHttpHeaders(), trailers);
            } else {
                if (hasTrailers || nonEmptyContent) {
                    // not supported by the netty HTTP/1 model
                    content.release();
                    return;
                }
                msg = new DefaultHttpRequest(
                        HttpVersion.HTTP_1_1, HttpMethod.POST, "/foo", new DefaultHttpHeaders());
            }
        } else {
            if (last) {
                msg = new DefaultLastHttpContent(content, trailers);
            } else {
                if (hasTrailers) {
                    // makes no sense
                    content.release();
                    return;
                }
                msg = new DefaultHttpContent(content);
            }
        }

        List<CompletionHandler<Void>> frameHandlers = new ArrayList<>();
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(
                new ChannelOutboundHandler() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
                        frameHandlers.add(handler);
                        ctx.write(msg, CompletionHandler.ignore());
                    }
                },
                new Http3FrameToHttpObjectCodec(false)
        );

        Future<Void> fullPromise = ch.writeOneOutbound(msg);
        ch.flushOutbound();

        if (headers) {
            Http3HeadersFrame headersFrame = ch.readOutbound();
            assertThat(headersFrame.headers().scheme().toString(), is("https"));
            assertThat(headersFrame.headers().method().toString(), is("POST"));
            assertThat(headersFrame.headers().path().toString(), is("/foo"));
        }
        if (nonEmptyContent) {
            Http3DataFrame dataFrame = ch.readOutbound();
            assertThat(dataFrame.content().readableBytes(), is(1));
            dataFrame.release();
        }
        if (hasTrailers) {
            Http3HeadersFrame trailersFrame = ch.readOutbound();
            assertThat(trailersFrame.headers().get("foo"), is("bar"));
        } else if (!nonEmptyContent && !headers) {
            Http3DataFrame dataFrame = ch.readOutbound();
            assertThat(dataFrame.content().readableBytes(), is(0));
            dataFrame.release();
        }

        assertFalse(fullPromise.isDone());

        assertFalse(ch.isShutdown(ChannelShutdownDirection.Outbound));
        for (CompletionHandler<Void> frameHandler : frameHandlers) {
            frameHandler.success(null);
        }
        if (last) {
            assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));
        } else {
            assertFalse(ch.isShutdown(ChannelShutdownDirection.Outbound));
        }
        assertTrue(fullPromise.isDone());
        assertFalse(ch.finish());
    }

    @Test
    public void decode100ContinueHttp2HeadersAsFullHttpResponse() throws Exception {
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
    public void testDecodeResponseHeaders() throws Exception {
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
    public void testDecodeResponseHeadersWithContentLength() throws Exception {
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
    public void testDecodeResponseTrailersAsClient() throws Exception {
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
    public void testDecodeDataAsClient() throws Exception {
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
    public void testDecodeEndDataAsClient() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInboundWithFin(new DefaultHttp3DataFrame(hello)));

        HttpContent content = ch.readInbound();
        try {
            assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
        } finally {
            content.release();
        }

        LastHttpContent last = ch.readInbound();
        try {
            assertFalse(last.content().isReadable());
            assertTrue(last.trailingHeaders().isEmpty());
        } finally {
            last.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testHostTranslated() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
        request.headers().add(HttpHeaderNames.HOST, "example.com");
        assertTrue(ch.writeOutbound(request));

        Http3HeadersFrame headersFrame = ch.readOutbound();
        Http3Headers headers = headersFrame.headers();

        assertThat(headers.scheme().toString(), is("https"));
        assertThat(headers.authority().toString(), is("example.com"));
        assertTrue(ch.isShutdown(ChannelShutdownDirection.Outbound));

        assertFalse(ch.finish());
    }

    @Test
    public void multipleFramesInFin() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            // initialized below
                        }
                    })
                    .group(group);

            SelfSignedCertificate cert = new SelfSignedCertificate();

            Channel server = bootstrap.bind("127.0.0.1", 0).get();
            server.pipeline().addLast(Http3.newQuicServerCodecBuilder()
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .initialMaxStreamDataBidirectionalRemote(1000000)
                    .initialMaxStreamsBidirectional(100)
                    .sslContext(QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
                            .applicationProtocols(Http3.supportedApplicationProtocols()).build())
                    .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new Http3ServerConnectionHandler(new ChannelInboundHandler() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    if (msg instanceof Http3HeadersFrame) {
                                        DefaultHttp3HeadersFrame responseHeaders = new DefaultHttp3HeadersFrame();
                                        responseHeaders.headers().status(HttpResponseStatus.OK.codeAsText());
                                        ctx.write(responseHeaders, CompletionHandler.ignore());
                                        ctx.write(new DefaultHttp3DataFrame(ByteBufUtil.encodeString(
                                                ctx.alloc(), CharBuffer.wrap("foo"), CharsetUtil.UTF_8)),
                                                CompletionHandler.ignore());
                                        // send a fin, this also flushes
                                        ctx.channel().shutdown(ChannelShutdownType.newOutbound());
                                    } else {
                                        ctx.fireChannelRead(msg);
                                    }
                                }
                            }));
                        }
                    })
                    .build());

            Channel client = bootstrap.bind("127.0.0.1", 0).get();
            client.config().setAutoRead(true);
            client.pipeline().addLast(Http3.newQuicClientCodecBuilder()
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .sslContext(QuicSslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .applicationProtocols(Http3.supportedApplicationProtocols())
                            .build())
                    .build());

            QuicChannel quicChannel = QuicChannel.newBootstrap(client)
                    .handler(new ChannelInitializer<QuicChannel>() {
                        @Override
                        protected void initChannel(QuicChannel ch) throws Exception {
                            ch.pipeline().addLast(new Http3ClientConnectionHandler());
                        }
                    })
                    .remoteAddress(server.localAddress())
                    .localAddress(client.localAddress())
                    .connect().get();

            BlockingQueue<Object> received = new LinkedBlockingQueue<>();
            QuicStreamChannel stream = Http3.newRequestStream(quicChannel, new Http3RequestStreamInitializer() {
                @Override
                protected void initRequestStream(QuicStreamChannel ch) {
                    ch.pipeline()
                            .addLast(new Http3FrameToHttpObjectCodec(false))
                            .addLast(new ChannelInboundHandler() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    received.put(msg);
                                }
                            });
                }
            }).get();
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            request.headers().add(HttpHeaderNames.HOST, "localhost");
            stream.writeAndFlush(request);

            HttpResponse respHeaders = (HttpResponse) received.poll(20, TimeUnit.SECONDS);
            assertThat(respHeaders, notNullValue());
            assertThat(respHeaders.status(), is(HttpResponseStatus.OK));
            assertThat(respHeaders, not(instanceOf(LastHttpContent.class)));
            HttpContent respBody = (HttpContent) received.poll(20, TimeUnit.SECONDS);
            assertThat(respBody, notNullValue());
            assertThat(respBody.content().toString(CharsetUtil.UTF_8), is("foo"));
            respBody.release();

            LastHttpContent last = (LastHttpContent) received.poll(20, TimeUnit.SECONDS);
            assertThat(last, notNullValue());
            last.release();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testUnsupportedIncludeSomeDetails() throws Exception {
        EmbeddedQuicStreamChannel ch = new EmbeddedQuicStreamChannel(new Http3FrameToHttpObjectCodec(false));
        UnsupportedMessageTypeException ex = assertThrows(
                UnsupportedMessageTypeException.class, () -> ch.writeOutbound("unsupported"));
        assertNotNull(ex.getMessage());
        assertFalse(ch.finish());
    }
}
