/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpHeaders.Names;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HttpContentCompressorTest {
    @Test
    public void testGetTargetContentEncoding() throws Exception {
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
    public void testEmptyContentCompression() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        req.headers().set(Names.ACCEPT_ENCODING, "deflate");
        ch.writeInbound(req);

        ch.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));

        HttpResponse res = (HttpResponse) ch.readOutbound();
        assertThat(res, is(not(instanceOf(FullHttpResponse.class))));
        assertThat(res.headers().get(Names.TRANSFER_ENCODING), is("chunked"));
        assertThat(res.headers().get(Names.CONTENT_LENGTH), is(nullValue()));
        assertThat(res.headers().get(Names.CONTENT_ENCODING), is("deflate"));

        ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        HttpContent chunk;
        chunk = (HttpContent) ch.readOutbound();
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        assertThat(chunk.content().isReadable(), is(true));

        chunk = (HttpContent) ch.readOutbound();
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        assertThat(chunk.content().isReadable(), is(false));

        assertThat(ch.readOutbound(), is(nullValue()));
    }
}
