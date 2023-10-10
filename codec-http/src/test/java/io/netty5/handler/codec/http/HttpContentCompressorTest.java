/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.DecoderResult;
import io.netty5.handler.codec.EncoderException;
import io.netty5.handler.codec.compression.CompressionOptions;
import io.netty5.handler.codec.compression.ZlibWrapper;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.Resource;
import io.netty5.util.Send;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.netty5.buffer.BufferUtil.hexDump;
import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HttpContentCompressorTest {

    @Test
    public void testGetTargetContentEncoding() {
        HttpContentCompressor compressor = new HttpContentCompressor();

        String[] tests = {
            // Accept-Encoding -> Content-Encoding
            "", null,
            "*", "gzip",
            "*;q=0.0", null,
            "gzip", "gzip",
            "compress, gzip;q=0.5", "gzip",
            "gzip; q=0.5, identity", "gzip",
            "gzip ; q=0.1", "gzip",
            "gzip; q=0, deflate", "deflate",
            " deflate ; q=0 , *;q=0.5", "gzip",
        };
        for (int i = 0; i < tests.length; i += 2) {
            String acceptEncoding = tests[i];
            String contentEncoding = tests[i + 1];
            ZlibWrapper targetWrapper = compressor.determineWrapper(acceptEncoding);
            String targetEncoding = null;
            if (targetWrapper != null) {
                switch (targetWrapper) {
                case GZIP:
                    targetEncoding = "gzip";
                    break;
                case ZLIB:
                    targetEncoding = "deflate";
                    break;
                default:
                    fail();
                }
            }
            assertEquals(contentEncoding, targetEncoding);
        }
    }

    @Test
    public void testDetermineEncoding() throws Exception {
        HttpContentCompressor compressor = new HttpContentCompressor((CompressionOptions []) null);

        String[] tests = {
                // Accept-Encoding -> Content-Encoding
                "", null,
                ",", null,
                "identity", null,
                "unknown", null,
                "*", "br",
                "br", "br",
                "br ; q=0.1", "br",
                "unknown, br", "br",
                "br, gzip", "br",
                "gzip, br", "br",
                "identity, br", "br",
                "gzip", "gzip",
                "gzip ; q=0.1", "gzip",
        };
        for (int i = 0; i < tests.length; i += 2) {
            final String acceptEncoding = tests[i];
            final String expectedEncoding = tests[i + 1];
            final String targetEncoding = compressor.determineEncoding(acceptEncoding);
            assertEquals(expectedEncoding, targetEncoding);
        }
    }

    @Test
    public void testSplitContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        ch.writeOutbound(new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK));
        BufferAllocator allocator = preferredAllocator();
        ch.writeOutbound(new DefaultHttpContent(allocator.allocate(16).writeCharSequence("Hell", US_ASCII)));
        ch.writeOutbound(new DefaultHttpContent(allocator.allocate(16).writeCharSequence("o, w", US_ASCII)));
        ch.writeOutbound(new DefaultLastHttpContent(allocator.allocate(16).writeCharSequence("orld", US_ASCII)));

        assertEncodedResponse(ch);

        HttpContent<?> chunk;
        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("1f8b0800000000000000f248cdc901000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("cad7512807000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("ca2fca4901000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("0300c2a99ae70c000000");
        assertThat(chunk).isInstanceOf(HttpContent.class);
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes()).isZero();
        assertThat(chunk).isInstanceOf(LastHttpContent.class);
        chunk.close();

        assertThat((Object) ch.readOutbound()).isNull();
    }

    @Test
    public void testChunkedContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        HttpResponse res = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        assertEncodedResponse(ch);

        BufferAllocator allocator = preferredAllocator();
        ch.writeOutbound(new DefaultHttpContent(allocator.allocate(16).writeCharSequence("Hell", US_ASCII)));
        ch.writeOutbound(new DefaultHttpContent(allocator.allocate(16).writeCharSequence("o, w", US_ASCII)));
        ch.writeOutbound(new DefaultLastHttpContent(allocator.allocate(16).writeCharSequence("orld", US_ASCII)));

        HttpContent<?> chunk;
        chunk = ch.readOutbound();
        assertNotNull(chunk);
        assertThat(hexDump(chunk.payload())).isEqualTo("1f8b0800000000000000f248cdc901000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertNotNull(chunk);
        assertThat(hexDump(chunk.payload())).isEqualTo("cad7512807000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertNotNull(chunk);
        assertThat(hexDump(chunk.payload())).isEqualTo("ca2fca4901000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertNotNull(chunk);
        assertThat(hexDump(chunk.payload())).isEqualTo("0300c2a99ae70c000000");
        assertThat(chunk).isInstanceOf(HttpContent.class);
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes()).isZero();
        assertThat(chunk).isInstanceOf(LastHttpContent.class);
        chunk.close();

        assertThat((Object) ch.readOutbound()).isNull();
    }

    @Test
    public void testChunkedContentWithAssembledResponse() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        BufferAllocator allocator = preferredAllocator();
        HttpResponse res = new AssembledHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, allocator.allocate(16).writeCharSequence("Hell", US_ASCII));
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        assertAssembledEncodedResponse(ch);

        ch.writeOutbound(new DefaultHttpContent(allocator.allocate(16).writeCharSequence("o, w", US_ASCII)));
        ch.writeOutbound(new DefaultLastHttpContent(allocator.allocate(16).writeCharSequence("orld", US_ASCII)));

        HttpContent<?> chunk;
        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("1f8b0800000000000000f248cdc901000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("cad7512807000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("ca2fca4901000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("0300c2a99ae70c000000");
        assertThat(chunk).isInstanceOf(HttpContent.class);
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes()).isZero();
        assertThat(chunk).isInstanceOf(LastHttpContent.class);
        chunk.close();

        assertThat((Object) ch.readOutbound()).isNull();
    }

    @Test
    public void testChunkedContentWithAssembledResponseIdentityEncoding() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        BufferAllocator allocator = preferredAllocator();
        ch.writeInbound(new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/", allocator.allocate(0)));

        HttpResponse res = new AssembledHttpResponse(HTTP_1_1, HttpResponseStatus.OK,
                                                     allocator.allocate(16).writeCharSequence("Hell", US_ASCII));
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        ch.writeOutbound(new DefaultHttpContent(allocator.allocate(16).writeCharSequence("o, w", US_ASCII)));
        ch.writeOutbound(new DefaultLastHttpContent(allocator.allocate(16).writeCharSequence("orld", US_ASCII)));

        HttpContent<?> chunk;
        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8)).isEqualTo("Hell");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8)).isEqualTo("o, w");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8)).isEqualTo("orld");
        assertThat(chunk).isInstanceOf(LastHttpContent.class);
        chunk.close();

        assertThat((Object) ch.readOutbound()).isNull();
    }

    @Test
    public void testContentWithAssembledResponseIdentityEncodingHttp10() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        BufferAllocator allocator = preferredAllocator();
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/", allocator.allocate(0)));

        HttpResponse res = new AssembledHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK,
                                                     allocator.allocate(16).writeCharSequence("Hell", US_ASCII));
        ch.writeOutbound(res);

        ch.writeOutbound(new DefaultHttpContent(allocator.allocate(16).writeCharSequence("o, w", US_ASCII)));
        ch.writeOutbound(new DefaultLastHttpContent(allocator.allocate(16).writeCharSequence("orld", US_ASCII)));

        HttpContent<?> chunk;
        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8)).isEqualTo("Hell");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8)).isEqualTo("o, w");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8)).isEqualTo("orld");
        assertThat(chunk).isInstanceOf(LastHttpContent.class);
        chunk.close();

        assertThat((Object) ch.readOutbound()).isNull();
    }

    @Test
    public void testChunkedContentWithTrailingHeader() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        HttpResponse res = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        assertEncodedResponse(ch);

        BufferAllocator allocator = preferredAllocator();
        ch.writeOutbound(new DefaultHttpContent(allocator.allocate(16).writeCharSequence("Hell", US_ASCII)));
        ch.writeOutbound(new DefaultHttpContent(allocator.allocate(16).writeCharSequence("o, w", US_ASCII)));
        LastHttpContent<?> content = new DefaultLastHttpContent(
                allocator.allocate(16).writeCharSequence("orld", US_ASCII));
        content.trailingHeaders().set("X-Test", "Netty");
        ch.writeOutbound(content);

        HttpContent<?> chunk;
        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("1f8b0800000000000000f248cdc901000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("cad7512807000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("ca2fca4901000000ffff");
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("0300c2a99ae70c000000");
        assertThat(chunk).isInstanceOf(HttpContent.class);
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes()).isZero();
        assertThat(chunk).isInstanceOf(LastHttpContent.class);
        assertEquals("Netty", ((LastHttpContent<?>) chunk).trailingHeaders().get("X-Test"));
        assertEquals(DecoderResult.success(), chunk.decoderResult());
        chunk.close();

        assertThat((Object) ch.readOutbound()).isNull();
    }

    @Test
    public void testFullContentWithContentLength() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse fullRes = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK,
                preferredAllocator().allocate(16).writeCharSequence("Hello, World", US_ASCII));
        fullRes.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(fullRes.payload().readableBytes()));
        ch.writeOutbound(fullRes);

        HttpResponse res = ch.readOutbound();
        assertThat(res).isNotInstanceOf(HttpContent.class);

        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING)).isNull();
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isEqualToIgnoringCase("gzip");

        long contentLengthHeaderValue = HttpUtil.getContentLength(res);
        long observedLength = 0;

        HttpContent<?> c = ch.readOutbound();
        observedLength += c.payload().readableBytes();
        assertThat(hexDump(c.payload())).isEqualTo("1f8b0800000000000000f248cdc9c9d75108cf2fca4901000000ffff");
        c.close();

        c = ch.readOutbound();
        observedLength += c.payload().readableBytes();
        assertThat(hexDump(c.payload())).isEqualTo("0300c6865b260c000000");
        c.close();

        LastHttpContent<?> last = ch.readOutbound();
        assertThat(last.payload().readableBytes()).isZero();
        last.close();

        assertThat((Object) ch.readOutbound()).isNull();
        assertEquals(contentLengthHeaderValue, observedLength);
    }

    @Test
    public void testFullContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse res = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK,
                preferredAllocator().allocate(16).writeCharSequence("Hello, World", US_ASCII));
        ch.writeOutbound(res);

        assertEncodedResponse(ch);
        HttpContent<?> c = ch.readOutbound();
        assertThat(hexDump(c.payload())).isEqualTo("1f8b0800000000000000f248cdc9c9d75108cf2fca4901000000ffff");
        c.close();

        c = ch.readOutbound();
        assertThat(hexDump(c.payload())).isEqualTo("0300c6865b260c000000");
        c.close();

        LastHttpContent<?> last = ch.readOutbound();
        assertThat(last.payload().readableBytes()).isZero();
        last.close();

        assertThat((Object) ch.readOutbound()).isNull();
    }

    /**
     * If the length of the content is unknown, {@link HttpContentEncoder} should not skip encoding the content
     * even if the actual length is turned out to be 0.
     */
    @Test
    public void testEmptySplitContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        ch.writeOutbound(new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK));
        assertEncodedResponse(ch);

        ch.writeOutbound(new EmptyLastHttpContent(preferredAllocator()));
        HttpContent<?> chunk = ch.readOutbound();
        assertThat(hexDump(chunk.payload())).isEqualTo("1f8b080000000000000003000000000000000000");
        assertThat(chunk).isInstanceOf(HttpContent.class);
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes()).isZero();
        assertThat(chunk).isInstanceOf(LastHttpContent.class);
        chunk.close();

        assertThat((Object) ch.readOutbound()).isNull();
    }

    /**
     * If the length of the content is 0 for sure, {@link HttpContentEncoder} should skip encoding.
     */
    @Test
    public void testEmptyFullContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse res = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0));
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertThat(o).isInstanceOf(FullHttpResponse.class);

        res = (FullHttpResponse) o;
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING)).isNull();

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isNull();
        assertThat(res.payload().readableBytes()).isZero();
        assertThat(res.payload().toString(US_ASCII)).isEqualTo("");
        res.close();

        assertThat((Object) ch.readOutbound()).isNull();
    }

    @Test
    public void testEmptyFullContentWithTrailer() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse res = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0));
        res.trailingHeaders().set("X-Test", "Netty");
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertThat(o).isInstanceOf(FullHttpResponse.class);

        res = (FullHttpResponse) o;
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING)).isNull();

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isNull();
        assertThat(res.payload().readableBytes()).isZero();
        assertThat(res.payload().toString(US_ASCII)).isEqualTo("");
        assertEquals("Netty", res.trailingHeaders().get("X-Test"));
        assertEquals(DecoderResult.success(), res.decoderResult());
        assertThat((Object) ch.readOutbound()).isNull();
        res.close();
    }

    @Test
    public void test100Continue() {
        FullHttpRequest request = newRequest();
        HttpUtil.set100ContinueExpected(request, true);

        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(request);

        FullHttpResponse continueResponse = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.CONTINUE, preferredAllocator().allocate(0));

        ch.writeOutbound(continueResponse);

        FullHttpResponse res = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0));
        res.trailingHeaders().set("X-Test", "Netty");
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertThat(o).isInstanceOf(FullHttpResponse.class);

        res = (FullHttpResponse) o;
        assertEquals(continueResponse, res);
        res.close();

        o = ch.readOutbound();
        assertThat(o).isInstanceOf(FullHttpResponse.class);

        res = (FullHttpResponse) o;
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING)).isNull();

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isNull();
        assertThat(res.payload().readableBytes()).isZero();
        assertThat(res.payload().toString(US_ASCII)).isEqualTo("");
        assertEquals("Netty", res.trailingHeaders().get("X-Test"));
        assertEquals(DecoderResult.success(), res.decoderResult());
        assertThat((Object) ch.readOutbound()).isNull();
        res.close();
    }

    @Test
    public void testMultiple1xxInformationalResponse() throws Exception {
        FullHttpRequest request = newRequest();
        HttpUtil.set100ContinueExpected(request, true);

        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(request);

        FullHttpResponse continueResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, preferredAllocator().allocate(0));
        ch.writeOutbound(continueResponse);

        FullHttpResponse earlyHintsResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.EARLY_HINTS, preferredAllocator().allocate(0));
        earlyHintsResponse.trailingHeaders().set("X-Test", "Netty");
        ch.writeOutbound(earlyHintsResponse);

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0));
        res.trailingHeaders().set("X-Test", "Netty");
        ch.writeOutbound(res);

        try (FullHttpResponse o = ch.readOutbound()) {
            assertEquals(continueResponse, o);
        }
        try (FullHttpResponse o = ch.readOutbound()) {
            assertEquals(earlyHintsResponse, o);
        }

        try (FullHttpResponse o = ch.readOutbound()) {
            assertNull(o.headers().get(HttpHeaderNames.TRANSFER_ENCODING));

            // Content encoding shouldn't be modified.
            assertNull(o.headers().get(HttpHeaderNames.CONTENT_ENCODING));
            assertEquals(0, o.payload().readableBytes());
            assertEquals("", o.payload().toString(StandardCharsets.US_ASCII));
            assertEquals("Netty", o.trailingHeaders().get("X-Test"));
            assertEquals(DecoderResult.success(), o.decoderResult());
        }

        assertNull(ch.readOutbound());
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void test103EarlyHintsResponse() throws Exception {
        FullHttpRequest request = newRequest();

        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(request);

        FullHttpResponse earlyHintsResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.EARLY_HINTS, preferredAllocator().allocate(0));
        earlyHintsResponse.trailingHeaders().set("X-Test", "Netty");
        ch.writeOutbound(earlyHintsResponse);

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0));
        res.trailingHeaders().set("X-Test", "Netty");
        ch.writeOutbound(res);

        try (FullHttpResponse o = ch.readOutbound()) {
            assertEquals(earlyHintsResponse, o);
        }

        try (FullHttpResponse o = ch.readOutbound()) {
            assertNull(o.headers().get(HttpHeaderNames.TRANSFER_ENCODING));

            // Content encoding shouldn't be modified.
            assertNull(o.headers().get(HttpHeaderNames.CONTENT_ENCODING));
            assertEquals(0, o.payload().readableBytes());
            assertEquals("", o.payload().toString(StandardCharsets.US_ASCII));
            assertEquals("Netty", res.trailingHeaders().get("X-Test"));
            assertEquals(DecoderResult.success(), res.decoderResult());
        }

        assertNull(ch.readOutbound());
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testTooManyResponses() {
        FullHttpRequest request = newRequest();
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(request);

        ch.writeOutbound(new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0)));

        try {
            ch.writeOutbound(new DefaultFullHttpResponse(
                    HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0)));
            fail();
        } catch (EncoderException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
        assertTrue(ch.finish());
        for (;;) {
            Object message = ch.readOutbound();
            if (message == null) {
                break;
            }
            Resource.dispose(message);
        }
        for (;;) {
            Object message = ch.readInbound();
            if (message == null) {
                break;
            }
            Resource.dispose(message);
        }
    }

    @Test
    public void testIdentity() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        assertTrue(ch.writeInbound(newRequest()));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK,
                preferredAllocator().allocate(16).writeCharSequence("Hello, World", US_ASCII));
        int len = res.payload().readableBytes();
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));
        res.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.IDENTITY);
        assertTrue(ch.writeOutbound(res));

        FullHttpResponse response = ch.readOutbound();
        assertThat(response.headers().get(HttpHeaderNames.CONTENT_LENGTH)).isEqualToIgnoringCase(String.valueOf(len));
        assertThat(response.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isEqualToIgnoringCase(
                HttpHeaderValues.IDENTITY);
        assertEquals("Hello, World", response.payload().toString(US_ASCII));
        response.close();

        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testCustomEncoding() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        assertTrue(ch.writeInbound(newRequest()));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK,
                preferredAllocator().allocate(16).writeCharSequence("Hello, World", US_ASCII));
        int len = res.payload().readableBytes();
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));
        res.headers().set(HttpHeaderNames.CONTENT_ENCODING, "ascii");
        assertTrue(ch.writeOutbound(res));

        FullHttpResponse response = ch.readOutbound();
        assertThat(response.headers().get(HttpHeaderNames.CONTENT_LENGTH)).isEqualToIgnoringCase(String.valueOf(len));
        assertEquals("ascii", response.headers().get(HttpHeaderNames.CONTENT_ENCODING));
        assertEquals("Hello, World", response.payload().toString(US_ASCII));
        response.close();

        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testCompressThresholdAllCompress() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        assertTrue(ch.writeInbound(newRequest()));

        FullHttpResponse res1023 = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK,
                preferredAllocator().copyOf(new byte[1023]));
        assertTrue(ch.writeOutbound(res1023));
        DefaultHttpResponse response1023 = ch.readOutbound();
        assertThat(response1023.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isEqualToIgnoringCase("gzip");
        ch.releaseOutbound();

        assertTrue(ch.writeInbound(newRequest()));
        FullHttpResponse res1024 = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().copyOf(new byte[1024]));
        assertTrue(ch.writeOutbound(res1024));
        DefaultHttpResponse response1024 = ch.readOutbound();
        assertThat(response1024.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isEqualToIgnoringCase("gzip");
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testCompressThresholdNotCompress() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor(6, 1024));
        assertTrue(ch.writeInbound(newRequest()));

        FullHttpResponse res1023 = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().copyOf(new byte[1023]));
        assertTrue(ch.writeOutbound(res1023));
        DefaultHttpResponse response1023 = ch.readOutbound();
        assertFalse(response1023.headers().contains(HttpHeaderNames.CONTENT_ENCODING));
        assertThat(response1023).isInstanceOf(FullHttpResponse.class);
        ((FullHttpResponse) response1023).close();
        ch.releaseOutbound();

        assertTrue(ch.writeInbound(newRequest()));
        FullHttpResponse res1024 = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().copyOf(new byte[1024]));
        assertTrue(ch.writeOutbound(res1024));
        DefaultHttpResponse response1024 = ch.readOutbound();
        assertThat(response1024.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isEqualToIgnoringCase("gzip");
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testMultipleAcceptEncodingHeaders() {
        FullHttpRequest request = newRequest();
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "unknown; q=1.0")
               .add(HttpHeaderNames.ACCEPT_ENCODING, "gzip; q=0.5")
               .add(HttpHeaderNames.ACCEPT_ENCODING, "deflate; q=0");

        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());

        assertTrue(ch.writeInbound(request));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK,
                preferredAllocator().allocate(16).writeCharSequence("Gzip Win", US_ASCII));
        assertTrue(ch.writeOutbound(res));

        assertEncodedResponse(ch);
        HttpContent<?> c = ch.readOutbound();
        assertThat(hexDump(c.payload())).isEqualTo("1f8b080000000000000072afca2c5008cfcc03000000ffff");
        c.close();

        c = ch.readOutbound();
        assertThat(hexDump(c.payload())).isEqualTo("03001f2ebf0f08000000");
        c.close();

        LastHttpContent<?> last = ch.readOutbound();
        assertThat(last.payload().readableBytes()).isZero();
        last.close();

        assertThat((Object) ch.readOutbound()).isNull();
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testEmpty() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        assertTrue(ch.writeInbound(newRequest()));

        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, "0");
        assertTrue(ch.writeOutbound(response));
        assertTrue(ch.writeOutbound(new DefaultHttpContent(preferredAllocator().allocate(0))));
        assertTrue(ch.writeOutbound(new DefaultLastHttpContent(preferredAllocator().allocate(0))));

        ch.checkException();
        ch.finishAndReleaseAll();
    }

    private static FullHttpRequest newRequest() {
        FullHttpRequest req = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/",
                                                         preferredAllocator().allocate(0));
        req.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
        return req;
    }

    private static void assertEncodedResponse(EmbeddedChannel ch) {
        Object o = ch.readOutbound();
        assertThat(o).isInstanceOf(HttpResponse.class);

        HttpResponse res = (HttpResponse) o;
        assertThat(res).isNotInstanceOf(HttpContent.class);
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING)).isEqualToIgnoringCase("chunked");
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_LENGTH)).isNull();
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isEqualToIgnoringCase("gzip");
    }

    private static void assertAssembledEncodedResponse(EmbeddedChannel ch) {
        Object o = ch.readOutbound();
        assertThat(o).isInstanceOf(AssembledHttpResponse.class);

        try (AssembledHttpResponse res = (AssembledHttpResponse) o) {
            assertThat(res).isInstanceOf(HttpContent.class);
            assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING)).isEqualToIgnoringCase("chunked");
            assertThat(res.headers().get(HttpHeaderNames.CONTENT_LENGTH)).isNull();
            assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING)).isEqualToIgnoringCase("gzip");
        }
    }

    static class AssembledHttpResponse extends DefaultHttpResponse implements HttpContent<AssembledHttpResponse> {

        private final Buffer payload;

        AssembledHttpResponse(HttpVersion version, HttpResponseStatus status, Buffer payload) {
            super(version, status);
            this.payload = payload;
        }

        AssembledHttpResponse(
                HttpVersion version, HttpResponseStatus status, HttpHeaders headers, Buffer payload) {
            super(version, status, headers);
            this.payload = payload;
        }

        @Override
        public Buffer payload() {
            return payload;
        }

        @Override
        public Send<AssembledHttpResponse> send() {
            return payload.send().map(AssembledHttpResponse.class,
                    p -> new AssembledHttpResponse(protocolVersion(), status(), headers(), p));
        }

        @Override
        public AssembledHttpResponse copy() {
            return new AssembledHttpResponse(protocolVersion(), status(), headers().copy(), payload.copy());
        }

        @Override
        public void close() {
            payload.close();
        }

        @Override
        public boolean isAccessible() {
            return payload.isAccessible();
        }

        @Override
        public AssembledHttpResponse touch(Object hint) {
            payload.touch(hint);
            return this;
        }
    }
}
