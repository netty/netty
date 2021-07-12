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
package io.netty.handler.codec.http;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpContentCompressorOptionsTest {

    @Test
    void testGetBrTargetContentEncoding() {
        HttpContentCompressor compressor = new HttpContentCompressor();

        String[] tests = {
                // Accept-Encoding -> Content-Encoding
                "", null,
                "*", "br",
                "*;q=0.0", null,
                "br", "br",
                "compress, br;q=0.5", "br",
                "br; q=0.5, identity", "br",
                "br; q=0, deflate", "br",
        };
        for (int i = 0; i < tests.length; i += 2) {
            String acceptEncoding = tests[i];
            String contentEncoding = tests[i + 1];
            String targetEncoding = compressor.determineEncoding(acceptEncoding);
            assertEquals(contentEncoding, targetEncoding);
        }
    }

    @Test
    void testGetZstdTargetContentEncoding() {
        HttpContentCompressor compressor = new HttpContentCompressor();

        String[] tests = {
                // Accept-Encoding -> Content-Encoding
                "", null,
                "*;q=0.0", null,
                "zstd", "zstd",
                "compress, zstd;q=0.5", "zstd",
                "zstd; q=0.5, identity", "zstd",
                "zstd; q=0, deflate", "zstd",
        };
        for (int i = 0; i < tests.length; i += 2) {
            String acceptEncoding = tests[i];
            String contentEncoding = tests[i + 1];
            String targetEncoding = compressor.determineEncoding(acceptEncoding);
            assertEquals(contentEncoding, targetEncoding);
        }
    }

    @Test
    void testAcceptEncodingHttpRequest() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor(null));
        ch.writeInbound(newRequest());
        FullHttpRequest fullHttpRequest = ch.readInbound();
        fullHttpRequest.release();

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        assertEncodedResponse(ch);

        assertTrue(ch.close().isSuccess());
    }

    private static void assertEncodedResponse(EmbeddedChannel ch) {
        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(HttpResponse.class)));

        assertEncodedResponse((HttpResponse) o);
    }

    private static void assertEncodedResponse(HttpResponse res) {
        assertThat(res, is(not(instanceOf(HttpContent.class))));
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is("chunked"));
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_LENGTH), is(nullValue()));
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("br"));
    }

    private static FullHttpRequest newRequest() {
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        req.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "br, zstd, gzip, deflate");
        return req;
    }
}
