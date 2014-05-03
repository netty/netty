/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static org.junit.Assert.assertEquals;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for encoding/decoding HTTP2 header blocks.
 */
public class Http2HeaderBlockIOTest {

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
                new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                        .authority("example.org").path("/some/path/resource2")
                        .add("accept", "image/png").add("cache-control", "no-cache")
                        .add("custom", "value1").add("custom", "value2")
                        .add("custom", "value3").add("custom", "custom4").build();
        assertRoundtripSuccessful(in);
    }

    @Test
    public void successiveCallsShouldSucceed() throws Http2Exception {
        Http2Headers in =
                new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                        .authority("example.org").path("/some/path")
                        .add("accept", "*/*").build();
        assertRoundtripSuccessful(in);

        in =
                new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                        .authority("example.org").path("/some/path/resource1")
                        .add("accept", "image/jpeg").add("cache-control", "no-cache")
                        .build();
        assertRoundtripSuccessful(in);

        in =
                new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                        .authority("example.org").path("/some/path/resource2")
                        .add("accept", "image/png").add("cache-control", "no-cache")
                        .build();
        assertRoundtripSuccessful(in);
    }

    @Test
    public void setMaxHeaderSizeShouldBeSuccessful() throws Http2Exception {
        encoder.maxHeaderTableSize(10);
        Http2Headers in =
                new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                        .authority("example.org").path("/some/path/resource2")
                        .add("accept", "image/png").add("cache-control", "no-cache")
                        .add("custom", "value1").add("custom", "value2")
                        .add("custom", "value3").add("custom", "custom4").build();
        assertRoundtripSuccessful(in);
        assertEquals(10, decoder.maxHeaderTableSize());
    }

    private void assertRoundtripSuccessful(Http2Headers in) throws Http2Exception {
        encoder.encodeHeaders(in, buffer);

        Http2Headers out = decoder.decodeHeaders(buffer);
        assertEquals(in, out);
    }
}
