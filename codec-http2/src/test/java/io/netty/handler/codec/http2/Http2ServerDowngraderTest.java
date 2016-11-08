/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
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
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class Http2ServerDowngraderTest {

    @Test
    public void testUpgradeEmptyFullResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertTrue(headersFrame.endStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeNonEmptyFullResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello)));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(headersFrame.endStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(dataFrame.endStream());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyFullResponseWithTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders trailers = response.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(response));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(headersFrame.endStream());

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));
        assertTrue(trailersFrame.endStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeNonEmptyFullResponseWithTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello);
        HttpHeaders trailers = response.trailingHeaders();
        trailers.set("key", "value");
        assertTrue(ch.writeOutbound(response));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(headersFrame.endStream());

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(dataFrame.endStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame trailersFrame = ch.readOutbound();
        assertThat(trailersFrame.headers().get("key").toString(), is("value"));
        assertTrue(trailersFrame.endStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeHeaders() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertTrue(ch.writeOutbound(response));

        Http2HeadersFrame headersFrame = ch.readOutbound();
        assertThat(headersFrame.headers().status().toString(), is("200"));
        assertFalse(headersFrame.endStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeChunk() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        HttpContent content = new DefaultHttpContent(hello);
        assertTrue(ch.writeOutbound(content));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(dataFrame.endStream());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeEmptyEnd() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        LastHttpContent end = LastHttpContent.EMPTY_LAST_CONTENT;
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame emptyFrame = ch.readOutbound();
        try {
            assertThat(emptyFrame.content().readableBytes(), is(0));
            assertTrue(emptyFrame.endStream());
        } finally {
            emptyFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEnd() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent end = new DefaultLastHttpContent(hello, true);
        assertTrue(ch.writeOutbound(end));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertTrue(dataFrame.endStream());
        } finally {
            dataFrame.release();
        }

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        LastHttpContent trailers = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));
        assertTrue(headerFrame.endStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testUpgradeDataEndWithTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        LastHttpContent trailers = new DefaultLastHttpContent(hello, true);
        HttpHeaders headers = trailers.trailingHeaders();
        headers.set("key", "value");
        assertTrue(ch.writeOutbound(trailers));

        Http2DataFrame dataFrame = ch.readOutbound();
        try {
            assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
            assertFalse(dataFrame.endStream());
        } finally {
            dataFrame.release();
        }

        Http2HeadersFrame headerFrame = ch.readOutbound();
        assertThat(headerFrame.headers().get("key").toString(), is("value"));
        assertTrue(headerFrame.endStream());

        assertThat(ch.readOutbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeHeaders() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
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
    public void testDowngradeHeadersWithContentLength() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
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
    public void testDowngradeFullHeaders() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        Http2Headers headers = new DefaultHttp2Headers();
        headers.path("/");
        headers.method("GET");

        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

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
    public void testDowngradeTrailers() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        Http2Headers headers = new DefaultHttp2Headers();
        headers.set("key", "value");
        assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

        LastHttpContent trailers = ch.readInbound();
        try {
            assertThat(trailers.content().readableBytes(), is(0));
            assertThat(trailers.trailingHeaders().get("key").toString(), is("value"));
            assertFalse(trailers instanceof FullHttpRequest);
        } finally {
            trailers.release();
        }

        assertThat(ch.readInbound(), is(nullValue()));
        assertFalse(ch.finish());
    }

    @Test
    public void testDowngradeData() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello)));

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
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
        ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
        assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello, true)));

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

    @Test
    public void testPassThroughOther() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new Http2ServerDowngrader());
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
}
