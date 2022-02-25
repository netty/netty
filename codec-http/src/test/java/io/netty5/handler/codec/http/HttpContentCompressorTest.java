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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Send;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.DecoderResult;
import io.netty5.handler.codec.EncoderException;
import io.netty5.handler.codec.compression.ZlibWrapper;
import io.netty5.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.netty5.buffer.ByteBufUtil.hexDump;
import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.buffer.api.adaptor.ByteBufAdaptor.intoByteBuf;
import static io.netty5.handler.codec.http.HttpHeadersTestUtils.of;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty5.util.CharsetUtil.US_ASCII;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("1f8b0800000000000000f248cdc901000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("cad7512807000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("ca2fca4901000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("0300c2a99ae70c000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes(), is(0));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.close();

        assertThat(ch.readOutbound(), is(nullValue()));
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
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("1f8b0800000000000000f248cdc901000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertNotNull(chunk);
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("cad7512807000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertNotNull(chunk);
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("ca2fca4901000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertNotNull(chunk);
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("0300c2a99ae70c000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes(), is(0));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.close();

        assertThat(ch.readOutbound(), is(nullValue()));
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
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("1f8b0800000000000000f248cdc901000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("cad7512807000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("ca2fca4901000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("0300c2a99ae70c000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes(), is(0));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.close();

        assertThat(ch.readOutbound(), is(nullValue()));
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
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8), is("Hell"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8), is("o, w"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8), is("orld"));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.close();

        assertThat(ch.readOutbound(), is(nullValue()));
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
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8), is("Hell"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8), is("o, w"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().toString(StandardCharsets.UTF_8), is("orld"));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.close();

        assertThat(ch.readOutbound(), is(nullValue()));
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
        content.trailingHeaders().set(of("X-Test"), of("Netty"));
        ch.writeOutbound(content);

        HttpContent<?> chunk;
        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("1f8b0800000000000000f248cdc901000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("cad7512807000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("ca2fca4901000000ffff"));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("0300c2a99ae70c000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes(), is(0));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        assertEquals("Netty", ((LastHttpContent<?>) chunk).trailingHeaders().get(of("X-Test")));
        assertEquals(DecoderResult.SUCCESS, chunk.decoderResult());
        chunk.close();

        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testFullContentWithContentLength() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse fullRes = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK,
                preferredAllocator().allocate(16).writeCharSequence("Hello, World", US_ASCII));
        fullRes.headers().set(HttpHeaderNames.CONTENT_LENGTH, fullRes.payload().readableBytes());
        ch.writeOutbound(fullRes);

        HttpResponse res = ch.readOutbound();
        assertThat(res, is(not(instanceOf(HttpContent.class))));

        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is(nullValue()));
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));

        long contentLengthHeaderValue = HttpUtil.getContentLength(res);
        long observedLength = 0;

        HttpContent<?> c = ch.readOutbound();
        observedLength += c.payload().readableBytes();
        assertThat(hexDump(intoByteBuf(c.payload())), is("1f8b0800000000000000f248cdc9c9d75108cf2fca4901000000ffff"));
        c.close();

        c = ch.readOutbound();
        observedLength += c.payload().readableBytes();
        assertThat(hexDump(intoByteBuf(c.payload())), is("0300c6865b260c000000"));
        c.close();

        LastHttpContent<?> last = ch.readOutbound();
        assertThat(last.payload().readableBytes(), is(0));
        last.close();

        assertThat(ch.readOutbound(), is(nullValue()));
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
        assertThat(hexDump(intoByteBuf(c.payload())), is("1f8b0800000000000000f248cdc9c9d75108cf2fca4901000000ffff"));
        c.close();

        c = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(c.payload())), is("0300c6865b260c000000"));
        c.close();

        LastHttpContent<?> last = ch.readOutbound();
        assertThat(last.payload().readableBytes(), is(0));
        last.close();

        assertThat(ch.readOutbound(), is(nullValue()));
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
        assertThat(hexDump(intoByteBuf(chunk.payload())), is("1f8b080000000000000003000000000000000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.close();

        chunk = ch.readOutbound();
        assertThat(chunk.payload().readableBytes(), is(0));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.close();

        assertThat(ch.readOutbound(), is(nullValue()));
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
        assertThat(o, is(instanceOf(FullHttpResponse.class)));

        res = (FullHttpResponse) o;
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is(nullValue()));

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is(nullValue()));
        assertThat(res.payload().readableBytes(), is(0));
        assertThat(res.payload().toString(US_ASCII), is(""));
        res.close();

        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testEmptyFullContentWithTrailer() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse res = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().allocate(0));
        res.trailingHeaders().set(of("X-Test"), of("Netty"));
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));

        res = (FullHttpResponse) o;
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is(nullValue()));

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is(nullValue()));
        assertThat(res.payload().readableBytes(), is(0));
        assertThat(res.payload().toString(US_ASCII), is(""));
        assertEquals("Netty", res.trailingHeaders().get(of("X-Test")));
        assertEquals(DecoderResult.SUCCESS, res.decoderResult());
        assertThat(ch.readOutbound(), is(nullValue()));
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
        res.trailingHeaders().set(of("X-Test"), of("Netty"));
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));

        res = (FullHttpResponse) o;
        assertEquals(continueResponse, res);
        res.close();

        o = ch.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));

        res = (FullHttpResponse) o;
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is(nullValue()));

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is(nullValue()));
        assertThat(res.payload().readableBytes(), is(0));
        assertThat(res.payload().toString(US_ASCII), is(""));
        assertEquals("Netty", res.trailingHeaders().get(of("X-Test")));
        assertEquals(DecoderResult.SUCCESS, res.decoderResult());
        assertThat(ch.readOutbound(), is(nullValue()));
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
            ReferenceCountUtil.release(message);
        }
        for (;;) {
            Object message = ch.readInbound();
            if (message == null) {
                break;
            }
            ReferenceCountUtil.release(message);
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
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, len);
        res.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.IDENTITY);
        assertTrue(ch.writeOutbound(res));

        FullHttpResponse response = ch.readOutbound();
        assertEquals(String.valueOf(len), response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals(HttpHeaderValues.IDENTITY.toString(), response.headers().get(HttpHeaderNames.CONTENT_ENCODING));
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
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, len);
        res.headers().set(HttpHeaderNames.CONTENT_ENCODING, "ascii");
        assertTrue(ch.writeOutbound(res));

        FullHttpResponse response = ch.readOutbound();
        assertEquals(String.valueOf(len), response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
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
        assertThat(response1023.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
        ch.releaseOutbound();

        assertTrue(ch.writeInbound(newRequest()));
        FullHttpResponse res1024 = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().copyOf(new byte[1024]));
        assertTrue(ch.writeOutbound(res1024));
        DefaultHttpResponse response1024 = ch.readOutbound();
        assertThat(response1024.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testCompressThresholdNotCompress() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor(6, 15, 8, 1024));
        assertTrue(ch.writeInbound(newRequest()));

        FullHttpResponse res1023 = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().copyOf(new byte[1023]));
        assertTrue(ch.writeOutbound(res1023));
        DefaultHttpResponse response1023 = ch.readOutbound();
        assertFalse(response1023.headers().contains(HttpHeaderNames.CONTENT_ENCODING));
        ch.releaseOutbound();

        assertTrue(ch.writeInbound(newRequest()));
        FullHttpResponse res1024 = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.OK, preferredAllocator().copyOf(new byte[1024]));
        assertTrue(ch.writeOutbound(res1024));
        DefaultHttpResponse response1024 = ch.readOutbound();
        assertThat(response1024.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
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
        assertThat(hexDump(intoByteBuf(c.payload())), is("1f8b080000000000000072afca2c5008cfcc03000000ffff"));
        c.close();

        c = ch.readOutbound();
        assertThat(hexDump(intoByteBuf(c.payload())), is("03001f2ebf0f08000000"));
        c.close();

        LastHttpContent<?> last = ch.readOutbound();
        assertThat(last.payload().readableBytes(), is(0));
        last.close();

        assertThat(ch.readOutbound(), is(nullValue()));
        assertTrue(ch.finishAndReleaseAll());
    }

    private static FullHttpRequest newRequest() {
        FullHttpRequest req = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/",
                                                         preferredAllocator().allocate(0));
        req.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
        return req;
    }

    private static void assertEncodedResponse(EmbeddedChannel ch) {
        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(HttpResponse.class)));

        HttpResponse res = (HttpResponse) o;
        assertThat(res, is(not(instanceOf(HttpContent.class))));
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is("chunked"));
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_LENGTH), is(nullValue()));
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
    }

    private static void assertAssembledEncodedResponse(EmbeddedChannel ch) {
        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(AssembledHttpResponse.class)));

        try (AssembledHttpResponse res = (AssembledHttpResponse) o) {
            assertThat(res, is(instanceOf(HttpContent.class)));
            assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is("chunked"));
            assertThat(res.headers().get(HttpHeaderNames.CONTENT_LENGTH), is(nullValue()));
            assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
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
