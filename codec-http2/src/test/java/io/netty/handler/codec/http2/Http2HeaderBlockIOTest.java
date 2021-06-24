/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.http2.Http2TestUtil.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for encoding/decoding HTTP2 header blocks.
 */
public class Http2HeaderBlockIOTest {

    private DefaultHttp2HeadersDecoder decoder;
    private DefaultHttp2HeadersEncoder encoder;
    private ByteBuf buffer;

    @BeforeEach
    public void setup() {
        encoder = new DefaultHttp2HeadersEncoder();
        decoder = new DefaultHttp2HeadersDecoder(false);
        buffer = Unpooled.buffer();
    }

    @AfterEach
    public void teardown() {
        buffer.release();
    }

    @Test
    public void roundtripShouldBeSuccessful() throws Http2Exception {
        Http2Headers in = headers();
        assertRoundtripSuccessful(in);
    }

    @Test
    public void successiveCallsShouldSucceed() throws Http2Exception {
        Http2Headers in = new DefaultHttp2Headers().method(new AsciiString("GET")).scheme(new AsciiString("https"))
                        .authority(new AsciiString("example.org")).path(new AsciiString("/some/path"))
                        .add(new AsciiString("accept"), new AsciiString("*/*"));
        assertRoundtripSuccessful(in);

        in = new DefaultHttp2Headers().method(new AsciiString("GET")).scheme(new AsciiString("https"))
                        .authority(new AsciiString("example.org")).path(new AsciiString("/some/path/resource1"))
                        .add(new AsciiString("accept"), new AsciiString("image/jpeg"))
                        .add(new AsciiString("cache-control"), new AsciiString("no-cache"));
        assertRoundtripSuccessful(in);

        in = new DefaultHttp2Headers().method(new AsciiString("GET")).scheme(new AsciiString("https"))
                        .authority(new AsciiString("example.org")).path(new AsciiString("/some/path/resource2"))
                        .add(new AsciiString("accept"), new AsciiString("image/png"))
                        .add(new AsciiString("cache-control"), new AsciiString("no-cache"));
        assertRoundtripSuccessful(in);
    }

    @Test
    public void setMaxHeaderSizeShouldBeSuccessful() throws Http2Exception {
        encoder.maxHeaderTableSize(10);
        Http2Headers in = headers();
        assertRoundtripSuccessful(in);
        assertEquals(10, decoder.maxHeaderTableSize());
    }

    private void assertRoundtripSuccessful(Http2Headers in) throws Http2Exception {
        encoder.encodeHeaders(3 /* randomly chosen */, in, buffer);

        Http2Headers out = decoder.decodeHeaders(0, buffer);
        assertEquals(in, out);
    }

    private static Http2Headers headers() {
        return new DefaultHttp2Headers(false).method(new AsciiString("GET")).scheme(new AsciiString("https"))
        .authority(new AsciiString("example.org")).path(new AsciiString("/some/path/resource2"))
                .add(new AsciiString("accept"), new AsciiString("image/png"))
                .add(new AsciiString("cache-control"), new AsciiString("no-cache"))
                .add(new AsciiString("custom"), new AsciiString("value1"))
                .add(new AsciiString("custom"), new AsciiString("value2"))
                .add(new AsciiString("custom"), new AsciiString("value3"))
                .add(new AsciiString("custom"), new AsciiString("custom4"))
                .add(randomString(), randomString());
    }
}
