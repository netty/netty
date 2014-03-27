/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.http2.draft10.frame;

import static org.junit.Assert.assertEquals;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.Http2Headers;
import io.netty.handler.codec.http2.draft10.frame.decoder.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.draft10.frame.encoder.DefaultHttp2HeadersEncoder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for encoding/decoding HTTP2 header blocks.
 */
public class HeaderBlockRoundtripTest {

    private DefaultHttp2HeadersDecoder decoder;
    private DefaultHttp2HeadersEncoder encoder;
    private ByteBuf buffer;

    @Before
    public void setup() {
        encoder = new DefaultHttp2HeadersEncoder();
        decoder = new DefaultHttp2HeadersDecoder();
        buffer = Unpooled.buffer();
    }

    @After
    public void teardown() {
        buffer.release();
    }

    @Test
    public void roundtripShouldBeSuccessful() throws Http2Exception {
        Http2Headers in =
                new Http2Headers.Builder().setMethod("GET").setScheme("https")
                        .setAuthority("example.org").setPath("/some/path/resource2")
                        .addHeader("accept", "image/png").addHeader("cache-control", "no-cache")
                        .addHeader("custom", "value1").addHeader("custom", "value2")
                        .addHeader("custom", "value3").addHeader("custom", "custom4").build();
        assertRoundtripSuccessful(in);
    }

    @Test
    public void successiveCallsShouldSucceed() throws Http2Exception {
        Http2Headers in =
                new Http2Headers.Builder().setMethod("GET").setScheme("https")
                        .setAuthority("example.org").setPath("/some/path")
                        .addHeader("accept", "*/*").build();
        assertRoundtripSuccessful(in);

        in =
                new Http2Headers.Builder().setMethod("GET").setScheme("https")
                        .setAuthority("example.org").setPath("/some/path/resource1")
                        .addHeader("accept", "image/jpeg").addHeader("cache-control", "no-cache")
                        .build();
        assertRoundtripSuccessful(in);

        in =
                new Http2Headers.Builder().setMethod("GET").setScheme("https")
                        .setAuthority("example.org").setPath("/some/path/resource2")
                        .addHeader("accept", "image/png").addHeader("cache-control", "no-cache")
                        .build();
        assertRoundtripSuccessful(in);
    }

    private void assertRoundtripSuccessful(Http2Headers in) throws Http2Exception {
        encoder.encodeHeaders(in, buffer);

        Http2Headers out = decoder.decodeHeaders(buffer);
        assertEquals(in, out);
    }
}
